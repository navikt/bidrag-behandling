package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.annotation.Timed
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.transformers.beregning.EvnevurderingBeregningResultat
import no.nav.bidrag.beregn.vedtak.Vedtaksfiltrering
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningRequestDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.stonad.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.vedtak.request.HentVedtakForStønadRequest
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakForStønad
import no.nav.bidrag.transport.behandling.vedtak.response.søknadsid
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.math.BigDecimal

private val log = KotlinLogging.logger {}

@Service
@Import(Vedtaksfiltrering::class)
class BeregningEvnevurderingService(
    private val bidragStønadConsumer: BidragStønadConsumer,
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
            val beregnetBeløpListe = sisteLøpendeVedtak.hentBeregning()
            secureLogger.info { "Hentet beregnet beløp $beregnetBeløpListe og behandling ${behandling.id}" }
            return EvnevurderingBeregningResultat(beregnetBeløpListe, løpendeStønader)
        } catch (e: Exception) {
            log.error(e) { "Det skjedden en feil ved opprettelse av grunnlag for løpende bidrag for BP evnevurdering: ${e.message}" }
            throw e
        }
    }

    private fun List<VedtakForStønad>.hentBeregning(): BidragBeregningResponsDto {
        val res =
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

        return res.copy(
            beregningListe =
                res.beregningListe.map { b ->
                    val vedtak = find { it.stønadsendring.kravhaver == b.personidentBarn }
                    val reskode =
                        vedtak
                            ?.stønadsendring!!
                            .periodeListe
                            .first()
                            .resultatkode
                    if (reskode !in listOf("6MB", "7M", "101", "VO")) {
                        b.copy(
                            faktiskBeløp = BigDecimal.ZERO,
                            beregnetBeløp = BigDecimal.ZERO,
                        )
                    } else {
                        b
                    }
                },
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
            beregingVedtaksfiltrering.finneSisteManuelleVedtak(vedtakListe, it.kravhaver)
        }
}
