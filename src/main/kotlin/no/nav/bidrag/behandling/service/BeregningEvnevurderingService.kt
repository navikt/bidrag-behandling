package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.annotation.Timed
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.beregning.finnSluttberegningIReferanser
import no.nav.bidrag.behandling.transformers.beregning.EvnevurderingBeregningResultat
import no.nav.bidrag.beregn.vedtak.Vedtaksfiltrering
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.util.avrundetTilNærmesteTier
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningRequestDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.HentVedtakForStønadRequest
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakForStønad
import no.nav.bidrag.transport.behandling.vedtak.response.søknadsid
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Service
@Import(Vedtaksfiltrering::class)
class BeregningEvnevurderingService(
    private val bidragStønadConsumer: BidragBeløpshistorikkConsumer,
    private val bidragVedtakConsumer: BidragVedtakConsumer,
    private val bidragBBMConsumer: BidragBBMConsumer,
    private val beregingVedtaksfiltrering: Vedtaksfiltrering,
) {
    @Timed
    fun hentLøpendeBidragForBehandling(behandling: Behandling): EvnevurderingBeregningResultat {
        try {
            log.info { "Henter evnevurdering for behandling ${behandling.id}" }
            val bpIdent = Personident(behandling.bidragspliktig!!.ident!!)
            val løpendeStønader = hentSisteLøpendeStønader(bpIdent)
            secureLogger.info { "Hentet løpende stønader $løpendeStønader for BP ${bpIdent.verdi} og behandling ${behandling.id}" }
            val sisteLøpendeVedtak = løpendeStønader.hentLøpendeVedtak(bpIdent)
            secureLogger.info { "Hentet siste løpende vedtak $sisteLøpendeVedtak for BP ${bpIdent.verdi} og behandling ${behandling.id}" }
            val beregnetBeløpListe = sisteLøpendeVedtak.hentBeregningNy()
            secureLogger.info { "Hentet beregnet beløp $beregnetBeløpListe og behandling ${behandling.id}" }
            return EvnevurderingBeregningResultat(beregnetBeløpListe, løpendeStønader)
        } catch (e: Exception) {
            log.error(e) { "Det skjedde en feil ved opprettelse av grunnlag for løpende bidrag for BP evnevurdering: ${e.message}" }
            throw e
        }
    }

    private fun List<VedtakForStønad>.hentBeregningGammel(): BidragBeregningResponsDto =
        bidragBBMConsumer.hentBeregning(
            BidragBeregningRequestDto(
                map {
                    BidragBeregningRequestDto.HentBidragBeregning(
                        stønadstype = it.stønadsendring.type,
                        søknadsid = it.behandlingsreferanser.søknadsid.toString(),
                        saksnummer = it.stønadsendring.sak.verdi,
                        personidentBarn = it.stønadsendring.kravhaver,
                    )
                },
            ),
        )

    private fun List<VedtakForStønad>.hentBeregning(): BidragBeregningResponsDto {
        // Henter beregningsgrunnlag fra BBM
        var bidragBeregningResponsDto =
            bidragBBMConsumer.hentBeregning(
                BidragBeregningRequestDto(
                    map {
                        BidragBeregningRequestDto.HentBidragBeregning(
                            stønadstype = it.stønadsendring.type,
                            søknadsid = it.behandlingsreferanser.søknadsid.toString(),
                            saksnummer = it.stønadsendring.sak.verdi,
                            personidentBarn = it.stønadsendring.kravhaver,
                        )
                    },
                ),
            )

        // Hvis beregningsgrunnlag ikke er funnet i BBM hentes det fra bidrag-vedtak (antar at siste bidragsvedtak er fattet i ny løsning)
        if (bidragBeregningResponsDto.beregningListe.isEmpty()) {
            log.warn { "Fant ingen beregning i BBM. Prøver å hente fra bidrag-vedtak i stedet." }
            val beregningListe = mutableListOf<BidragBeregningResponsDto.BidragBeregning>()

            map {
                val beregning = finnBeregningIBidragVedtak(it)
                secureLogger.info { "Behandler VedtakForStønad: $it" }
                if (beregning != null) {
                    secureLogger.info { "Legger til følgende beregning for vedtak ${it.vedtaksid} i bidrag-vedtak: $beregning" }
                    beregningListe.add(beregning)
                }
            }
            bidragBeregningResponsDto = BidragBeregningResponsDto(beregningListe)
        }
        return bidragBeregningResponsDto
    }

    private fun List<VedtakForStønad>.hentBeregningNy(): BidragBeregningResponsDto {
        val hentBeregningFraBidragVedtakListe = mutableListOf<VedtakForStønad>()
        val hentBeregningFraBBMListe = mutableListOf<VedtakForStønad>()

        // Bestemmer hvilke vedtak som skal hentes fra bidrag-vedtak og hvilke som skal hentes fra BBM og lager en liste for hver
        map {
            if (it.behandlingsreferanser.any { it.kilde == BehandlingsrefKilde.BEHANDLING_ID }) {
                hentBeregningFraBidragVedtakListe.add(it)
            } else {
                hentBeregningFraBBMListe.add(it)
            }
        }

        // Henter beregningsgrunnlag fra BBM
        var bidragBeregningResponsDtoFraBBM = BidragBeregningResponsDto(emptyList())
        if (hentBeregningFraBBMListe.isNotEmpty()) {
            secureLogger.info { "Følgende beregninger skal hentes fra BBM: $hentBeregningFraBBMListe" }
            bidragBeregningResponsDtoFraBBM =
                bidragBBMConsumer.hentBeregning(
                    BidragBeregningRequestDto(
                        map {
                            BidragBeregningRequestDto.HentBidragBeregning(
                                stønadstype = it.stønadsendring.type,
                                søknadsid = it.behandlingsreferanser.søknadsid.toString(),
                                saksnummer = it.stønadsendring.sak.verdi,
                                personidentBarn = it.stønadsendring.kravhaver,
                            )
                        },
                    ),
                )
            secureLogger.info { "Respons fra BBM: $bidragBeregningResponsDtoFraBBM" }
        }

        // Henter beregningsgrunnlag fra bidrag-vedtak
        var bidragBeregningResponsDtoFraBidragVedtak = BidragBeregningResponsDto(emptyList())
        if (hentBeregningFraBidragVedtakListe.isNotEmpty()) {
            secureLogger.info { "Følgende beregninger skal hentes fra bidrag-vedtak: $hentBeregningFraBidragVedtakListe" }
            val beregningListe = mutableListOf<BidragBeregningResponsDto.BidragBeregning>()

            map {
                secureLogger.info { "Behandler VedtakForStønad: $it" }
                val beregning = finnBeregningIBidragVedtak(it)
                if (beregning != null) {
                    secureLogger.info { "Legger til følgende beregning for vedtak ${it.vedtaksid} i bidrag-vedtak: $beregning" }
                    beregningListe.add(beregning)
                }
            }
            bidragBeregningResponsDtoFraBidragVedtak = BidragBeregningResponsDto(beregningListe)
        }

        // Returnerer sammenslått beregningsgrunnlag fra BBM og bidrag-vedtak
        return BidragBeregningResponsDto(
            bidragBeregningResponsDtoFraBBM.beregningListe + bidragBeregningResponsDtoFraBidragVedtak.beregningListe,
        )
    }

    private fun finnBeregningIBidragVedtak(vedtakForStønad: VedtakForStønad): BidragBeregningResponsDto.BidragBeregning? {
        // Henter vedtak fra bidrag-vedtak (med fullstendige opplysninger)
        val vedtakDto = bidragVedtakConsumer.hentVedtak(vedtakForStønad.vedtaksid)
        if (vedtakDto == null) {
            secureLogger.warn { "Fant ikke vedtak for vedtaksid ${vedtakForStønad.vedtaksid} i bidrag-vedtak." }
            return null
        }

        // Henter stønadsendringen fra vedtaket som matcher med det som ligger i VedtakForStønad
        val stønadsendringDto =
            vedtakDto.stønadsendringListe
                .filter { stønadsendringDto ->
                    stønadsendringDto.type == vedtakForStønad.stønadsendring.type &&
                        stønadsendringDto.sak == vedtakForStønad.stønadsendring.sak &&
                        stønadsendringDto.skyldner == vedtakForStønad.stønadsendring.skyldner &&
                        stønadsendringDto.kravhaver == vedtakForStønad.stønadsendring.kravhaver
                }.firstOrNull()
        if (stønadsendringDto == null) {
            secureLogger.warn { "Fant ikke stønadsendring for vedtak ${vedtakForStønad.vedtaksid} i bidrag-vedtak." }
            return null
        }
        secureLogger.info { "Fant stønadsendring for vedtak ${vedtakForStønad.vedtaksid} i bidrag-vedtak: $stønadsendringDto" }

        // Finner siste periode i stønadsendringen
        val sistePeriode =
            stønadsendringDto.periodeListe.maxByOrNull { periode -> periode.periode.fom } ?: run {
                secureLogger.warn {
                    "Fant ikke siste periode for vedtak ${vedtakForStønad.vedtaksid} og stønadsendring $stønadsendringDto i " +
                        "bidrag-vedtak."
                }
                return null
            }

        // Finner sluttberegning-grunnlaget
        val sluttberegningReferanse =
            sistePeriode.grunnlagReferanseListe.firstOrNull { grunnlagsReferanse ->
                grunnlagsReferanse.lowercase().contains("sluttberegning")
            } ?: ""
        val sluttberegningGrunnlag =
            vedtakDto.grunnlagListe.finnSluttberegningIReferanser(listOf(sluttberegningReferanse)) ?: run {
                secureLogger.warn {
                    "Fant ikke sluttberegning i siste periode i grunnlag for vedtak ${vedtakForStønad.vedtaksid} og " +
                        "stønadsendring $stønadsendringDto i bidrag-vedtak."
                }
                return null
            }
        secureLogger.info { "Fant sluttberegning-grunnlag: $sluttberegningGrunnlag" }
        val sluttberegningObjekt = sluttberegningGrunnlag.innholdTilObjekt<SluttberegningBarnebidrag>()

        // Henter ut alle grunnlag som refereres av sluttberegning
        val grunnlagListeSluttberegningSistePeriode =
            vedtakDto.grunnlagListe.filter { grunnlag -> grunnlag.referanse in sluttberegningGrunnlag.grunnlagsreferanseListe }

        // Finner samværsklasse
        val samværsklasse =
            (
                grunnlagListeSluttberegningSistePeriode
                    .filtrerOgKonverterBasertPåEgenReferanse<SamværsperiodeGrunnlag>(Grunnlagstype.SAMVÆRSPERIODE)
                    .firstOrNull()
                    ?: run {
                        secureLogger.warn { "Fant ikke tilhørende samværsklasse i sluttberegning med referanse $sluttberegningReferanse." }
                        return null
                    }
            ).innhold.samværsklasse
        secureLogger.info { "Samværsklasse: $samværsklasse" }

        return BidragBeregningResponsDto.BidragBeregning(
            saksnummer = vedtakForStønad.stønadsendring.sak.verdi,
            personidentBarn = vedtakForStønad.stønadsendring.kravhaver,
            datoSøknad = LocalDate.now(), // Brukes ikke
            beregnetBeløp = sluttberegningObjekt.bruttoBidragEtterBarnetilleggBM.avrundetTilNærmesteTier,
            faktiskBeløp = sluttberegningObjekt.bruttoBidragEtterBarnetilleggBP.avrundetTilNærmesteTier,
            beløpSamvær = BigDecimal.ZERO, // Brukes ikke
            stønadstype = Stønadstype.BIDRAG,
            samværsklasse = samværsklasse,
        )
    }

    private fun hentSisteLøpendeStønader(bpIdent: Personident): List<LøpendeBidragssak> =
        bidragStønadConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bpIdent)).bidragssakerListe

    private fun List<LøpendeBidragssak>.hentLøpendeVedtak(bpIdent: Personident): List<VedtakForStønad> =
        mapNotNull {
            val vedtakListe =
                bidragVedtakConsumer
                    .hentVedtakForStønad(
                        HentVedtakForStønadRequest(
                            skyldner = bpIdent,
                            sak = it.sak,
                            kravhaver = it.kravhaver,
                            type = it.type,
                        ),
                    ).vedtakListe
            beregingVedtaksfiltrering.finneVedtakForEvnevurderingNy(vedtakListe, it.kravhaver)
        }
}
