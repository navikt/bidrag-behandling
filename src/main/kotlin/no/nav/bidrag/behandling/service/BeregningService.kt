package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.beregning.validerForSærbidrag
import no.nav.bidrag.behandling.transformers.finnDelberegningSumLøpendeBidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.beregn.core.bo.Periode
import no.nav.bidrag.beregn.core.bo.Sjablon
import no.nav.bidrag.beregn.core.bo.SjablonInnhold
import no.nav.bidrag.beregn.core.bo.SjablonNøkkel
import no.nav.bidrag.beregn.core.bo.SjablonPeriode
import no.nav.bidrag.beregn.core.dto.SjablonPeriodeCore
import no.nav.bidrag.beregn.core.service.mapper.CoreMapper
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.beregn.særbidrag.BeregnSærbidragApi
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.beregning.BidragsevneBeregning
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo.AntallBarnIHusstand
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo.BostatusVoksneIHusstand
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo.GrunnlagBeregning
import no.nav.bidrag.beregn.særbidrag.core.felles.bo.SjablonListe
import no.nav.bidrag.commons.service.sjablon.SjablonProvider
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.math.RoundingMode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatBeregning as ResultatBeregningSærbidrag
import no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatPeriode as ResultatPeriodeSærbidrag

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() = ResultatRolle(tilPersonident(), hentNavn(), fødselsdato)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
    private val mapper: VedtakGrunnlagMapper,
) {
    private val beregnApi = BeregnForskuddApi()
    private val beregnSærbidragApi = BeregnSærbidragApi()

    fun beregneForskudd(behandling: Behandling): List<ResultatForskuddsberegningBarn> {
        behandling.run {
            mapper.run {
                validering.run { validerForBeregning() }
                return if (avslag != null) {
                    søknadsbarn.map {
                        tilResultatAvslag(it)
                    }
                } else {
                    søknadsbarn.map { rolle ->
                        val beregnForskudd = byggGrunnlagForBeregning(behandling, rolle)

                        try {
                            ResultatForskuddsberegningBarn(
                                rolle.mapTilResultatBarn(),
                                beregnApi.beregn(beregnForskudd),
                            )
                        } catch (e: Exception) {
                            LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
                        }
                    }
                }
            }
        }
    }

    fun beregneSærbidrag(behandling: Behandling): BeregnetSærbidragResultat {
        mapper.validering.run {
            behandling.validerTekniskForBeregningAvSærbidrag()
            behandling.validerForBeregningSærbidrag()
        }

        val søknasdbarn = behandling.søknadsbarn.first()
        return if (mapper.validering.run { behandling.erDirekteAvslagUtenBeregning() }) {
            behandling.tilResultatAvslagSærbidrag()
        } else {
            try {
                val grunnlagBeregning =
                    mapper.byggGrunnlagForBeregning(behandling, søknasdbarn)
                beregnSærbidragApi.beregn(grunnlagBeregning, behandling.opprinneligVedtakstype ?: behandling.vedtakstype).let { resultat ->
                    resultat.validerForSærbidrag()
                    resultat
                }
            } catch (e: Exception) {
                LOGGER.warn(e) { "Det skjedde en feil ved beregning av særbidrag: ${e.message}" }
                throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
            }
        }
    }

    fun beregneForskudd(behandlingsid: Long): List<ResultatForskuddsberegningBarn> {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneForskudd(behandling)
    }

    fun beregneSærbidrag(behandlingsid: Long): BeregnetSærbidragResultat {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneSærbidrag(behandling)
    }

    private fun Behandling.tilResultatAvslagSærbidrag() =
        BeregnetSærbidragResultat(
            beregnetSærbidragPeriodeListe =
                listOf(
                    ResultatPeriodeSærbidrag(
                        grunnlagsreferanseListe = emptyList(),
                        periode = ÅrMånedsperiode(virkningstidspunkt!!, virkningstidspunkt!!.plusMonths(1)),
                        resultat =
                            ResultatBeregningSærbidrag(
                                beløp = BigDecimal.ZERO,
                                resultatkode = mapper.validering.run { tilSærbidragAvslagskode()!! },
                            ),
                    ),
                ),
            grunnlagListe = emptyList(),
        )

    private fun Behandling.tilResultatAvslag(barn: Rolle) =
        ResultatForskuddsberegningBarn(
            barn.mapTilResultatBarn(),
            BeregnetForskuddResultat(
                beregnetForskuddPeriodeListe =
                    listOf(
                        ResultatPeriode(
                            periode = ÅrMånedsperiode(virkningstidspunkt!!, null),
                            grunnlagsreferanseListe = emptyList(),
                            resultat =
                                ResultatBeregning(
                                    belop = BigDecimal.ZERO,
                                    kode = avslag!!,
                                    regel = "",
                                ),
                        ),
                    ),
            ),
        )

    // TODO: For testing av evnevurdering. Skal fjernes når testing er ferdig
    fun beregnSærbidragInnteksgrense(behandling: Behandling): BigDecimal {
        val bidragsevneBeregning = BidragsevneBeregning()
        val sjablonListe = hentSjabloner()
        val beregninResultat = beregneSærbidrag(behandling)
        val delberegningSumLøpendeBidrag =
            beregninResultat.grunnlagListe.finnDelberegningSumLøpendeBidrag(
                beregninResultat.beregnetSærbidragPeriodeListe.first().grunnlagsreferanseListe,
            )
        var low = 0
        var high = 1000000000
        var result = BigDecimal.ZERO
        val sumLøpendeBidrag = delberegningSumLøpendeBidrag!!.sumLøpendeBidrag.setScale(0, RoundingMode.HALF_UP)
        while (low <= high) {
            val inntektBeløp = (low + high) / 2
            val antallBarnIHusstand =
                behandling.husstandsmedlem
                    .filter {
                        it.perioder.any {
                            it.bostatus == Bostatuskode.MED_FORELDER ||
                                it.bostatus == Bostatuskode.DELT_BOSTED
                        }
                    }.count()
            val antallVoksneIHustand =
                behandling.husstandsmedlem
                    .filter {
                        it.perioder.any {
                            it.bostatus ==
                                Bostatuskode.BOR_MED_ANDRE_VOKSNE
                        }
                    }.count()

            val mapper = object : CoreMapper() {}
            val sjablontallMap = HashMap<String, SjablonTallNavn>()
            for (sjablonTallNavn in SjablonTallNavn.entries) {
                sjablontallMap[sjablonTallNavn.id] = sjablonTallNavn
            }
            val sjablonPeriodeCoreListe = ArrayList<SjablonPeriodeCore>()

            sjablonPeriodeCoreListe.addAll(
                mapper.mapSjablonSjablontall(
                    beregnDatoFra = behandling.virkningstidspunkt!!,
                    beregnDatoTil = behandling.virkningstidspunkt!!.plusMonths(1),
                    sjablonSjablontallListe = sjablonListe.sjablonSjablontallResponse,
                    sjablontallMap = sjablontallMap,
                    criteria = { it.bidragsevne },
                ),
            )
            sjablonPeriodeCoreListe.addAll(
                mapper.mapSjablonBidragsevne(
                    beregnDatoFra = behandling.virkningstidspunkt!!,
                    beregnDatoTil = behandling.virkningstidspunkt!!.plusMonths(1),
                    sjablonBidragsevneListe = sjablonListe.sjablonBidragsevneResponse,
                ),
            )
            sjablonPeriodeCoreListe.addAll(
                mapper.mapSjablonTrinnvisSkattesats(
                    beregnDatoFra = behandling.virkningstidspunkt!!,
                    beregnDatoTil = behandling.virkningstidspunkt!!.plusMonths(1),
                    sjablonTrinnvisSkattesatsListe = sjablonListe.sjablonTrinnvisSkattesatsResponse,
                ),
            )
            val resultatBPsEvne =
                bidragsevneBeregning.beregn(
                    GrunnlagBeregning(
                        inntekt =
                            no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo
                                .Inntekt("", inntektBeløp.toBigDecimal()),
                        antallBarnIHusstand = AntallBarnIHusstand("", antallBarnIHusstand.toDouble()),
                        bostatusVoksneIHusstand = BostatusVoksneIHusstand("", antallVoksneIHustand != 0),
                        sjablonListe = mapSjablonPeriodeListe(sjablonPeriodeCoreListe),
                    ),
                )
            val beregnetBPsEvne = resultatBPsEvne.beløp.setScale(0, RoundingMode.HALF_UP)
            if (sumLøpendeBidrag == BigDecimal.ZERO && beregnetBPsEvne > BigDecimal.ZERO) {
                result = inntektBeløp.toBigDecimal()
            } else if (beregnetBPsEvne == sumLøpendeBidrag) {
                result = inntektBeløp.toBigDecimal()
                high = inntektBeløp - 1
            } else if (resultatBPsEvne.beløp < delberegningSumLøpendeBidrag.sumLøpendeBidrag) {
                low = inntektBeløp + 1
            } else {
                high = inntektBeløp - 1
            }
        }
        return result
    }

    protected fun mapSjablonPeriodeListe(sjablonPeriodeListeCore: List<SjablonPeriodeCore>): List<SjablonPeriode> {
        val sjablonPeriodeListe = mutableListOf<SjablonPeriode>()
        sjablonPeriodeListeCore.forEach {
            val sjablonNøkkelListe = mutableListOf<SjablonNøkkel>()
            val sjablonInnholdListe = mutableListOf<SjablonInnhold>()
            it.nøkkelListe!!.forEach { nøkkel ->
                sjablonNøkkelListe.add(SjablonNøkkel(navn = nøkkel.navn, verdi = nøkkel.verdi))
            }
            it.innholdListe.forEach { innhold ->
                sjablonInnholdListe.add(SjablonInnhold(navn = innhold.navn, verdi = innhold.verdi))
            }
            sjablonPeriodeListe.add(
                SjablonPeriode(
                    sjablonPeriode = Periode(datoFom = it.periode.datoFom, datoTil = it.periode.datoTil),
                    sjablon = Sjablon(navn = it.navn, nøkkelListe = sjablonNøkkelListe, innholdListe = sjablonInnholdListe),
                ),
            )
        }
        return sjablonPeriodeListe
    }

    private fun hentSjabloner(): SjablonListe {
        // Henter sjabloner for sjablontall
        val sjablontallListe = SjablonProvider.hentSjablontall()

        // Henter sjabloner for bidragsevne
        val sjablonBidragsevneListe = SjablonProvider.hentSjablonBidragsevne()

        // Henter sjabloner for samværsfradrag
        val sjablonSamværsfradragListe = SjablonProvider.hentSjablonSamværsfradrag()

        // Henter sjabloner for trinnvis skattesats
        val sjablonTrinnvisSkattesatsListe = SjablonProvider.hentSjablonTrinnvisSkattesats()

        return SjablonListe(
            sjablonSjablontallResponse = sjablontallListe,
            sjablonBidragsevneResponse = sjablonBidragsevneListe,
            sjablonTrinnvisSkattesatsResponse = sjablonTrinnvisSkattesatsListe,
            sjablonSamværsfradragResponse = sjablonSamværsfradragListe,
        )
    }
}
