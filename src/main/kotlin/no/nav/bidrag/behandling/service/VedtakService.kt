package no.nav.bidrag.behandling.service

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.grunnlag.StønadsendringPeriode
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForStønad
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForStønadAvslag
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForVedtak
import no.nav.bidrag.behandling.transformers.grunnlag.byggStønadsendringerForVedtak
import no.nav.bidrag.behandling.transformers.hentRolleMedFnr
import no.nav.bidrag.behandling.transformers.vedtak.reelMottakerEllerBidragsmottaker
import no.nav.bidrag.behandling.transformers.vedtak.tilBehandling
import no.nav.bidrag.behandling.transformers.vedtak.tilBehandlingreferanseListe
import no.nav.bidrag.behandling.transformers.vedtak.tilOpprettRequestDto
import no.nav.bidrag.behandling.transformers.vedtak.tilSkyldner
import no.nav.bidrag.behandling.transformers.vedtak.validerGrunnlagsreferanser
import no.nav.bidrag.commons.util.MermaidResponse
import no.nav.bidrag.commons.util.TreeChild
import no.nav.bidrag.commons.util.tilVedtakDto
import no.nav.bidrag.commons.util.toMermaid
import no.nav.bidrag.commons.util.toTree
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettStønadsendringRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Service
class VedtakService(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val unleashInstance: Unleash,
) {
    fun konverterVedtakTilBehandling(vedtakId: Long): Behandling? {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null

//        val behandling =
//            Behandling(
//                saksnummer = vedtak.saksnummer,
//            )
        return vedtak.tilBehandling(vedtakId)
    }

    fun fatteVedtak(behandlingId: Long): Int {
        val isEnabled = unleashInstance.isEnabled("behandling.fattevedtak", true)
        if (isEnabled.not()) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Fattevedtak er ikke aktivert",
            )
        }

        val behandling = behandlingService.hentBehandlingById(behandlingId, true)
        if (behandling.vedtaksid != null) behandling.vedtakAlleredeFattet()
        val request =
            if (behandling.avslag != null) {
                behandling.byggOpprettVedtakRequestForAvslag()
            } else {
                behandling.byggOpprettVedtakRequest()
            }

        request.validerGrunnlagsreferanser()
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterBehandling(
            behandlingId,
            OppdaterBehandlingRequestV2(vedtaksid = response.vedtaksid.toLong()),
        )
        LOGGER.info {
            "Fattet vedtak for behandling $behandlingId med ${
                behandling.årsak?.let { "årsakstype $it" }
                    ?: "avslagstype ${behandling.avslag}"
            } med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun behandlingTilVedtakDto(behandlingId: Long): VedtakDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId, true)
        val request =
            if (behandling.avslag != null) behandling.byggOpprettVedtakRequestForAvslag() else behandling.byggOpprettVedtakRequest()

        return request.tilVedtakDto()
    }

    fun vedtakTilMermaid(behandlingId: Long): MermaidResponse {
        val behandling = behandlingService.hentBehandlingById(behandlingId, true)
        val request =
            if (behandling.avslag != null) behandling.byggOpprettVedtakRequestForAvslag() else behandling.byggOpprettVedtakRequest()

        return request.toMermaid()
    }

    fun vedtakTilTreeMap(behandlingId: Long): TreeChild {
        val behandling = behandlingService.hentBehandlingById(behandlingId, true)
        val request =
            if (behandling.avslag != null) behandling.byggOpprettVedtakRequestForAvslag() else behandling.byggOpprettVedtakRequest()

        return request.toTree()
    }

    private fun Behandling.byggOpprettVedtakRequestForAvslag(): OpprettVedtakRequestDto {
        val sak = sakConsumer.hentSak(saksnummer)
        val grunnlagListe = byggGrunnlagForStønadAvslag()

        return OpprettVedtakRequestDto(
            enhetsnummer = Enhetsnummer(behandlerEnhet),
            vedtakstidspunkt = LocalDateTime.now(),
            type = vedtakstype,
            stønadsendringListe =
                søknadsbarn.map {
                    OpprettStønadsendringRequestDto(
                        innkreving = Innkrevingstype.MED_INNKREVING,
                        skyldner = tilSkyldner(),
                        kravhaver =
                            it.tilNyestePersonident()
                                ?: rolleManglerIdent(Rolletype.BARN, id!!),
                        mottaker =
                            roller
                                .reelMottakerEllerBidragsmottaker(
                                    sak.hentRolleMedFnr(it.ident!!),
                                ),
                        sak = Saksnummer(saksnummer),
                        type = stonadstype!!,
                        beslutning = Beslutningstype.ENDRING,
                        grunnlagReferanseListe = grunnlagListe.map { it.referanse },
                        periodeListe =
                            listOf(
                                OpprettPeriodeRequestDto(
                                    periode = ÅrMånedsperiode(søktFomDato, null),
                                    beløp = BigDecimal.ZERO,
                                    resultatkode = avslag!!.name,
                                    valutakode = "NOK",
                                    grunnlagReferanseListe = emptyList(),
                                ),
                            ),
                    )
                },
            engangsbeløpListe = emptyList(),
            behandlingsreferanseListe = tilBehandlingreferanseListe(),
            grunnlagListe = grunnlagListe.map(GrunnlagDto::tilOpprettRequestDto),
            kilde = Vedtakskilde.MANUELT,
            fastsattILand = null,
            innkrevingUtsattTilDato = null,
            // Settes automatisk av bidrag-vedtak basert på token
            opprettetAv = null,
        )
    }

    private fun Behandling.byggOpprettVedtakRequest(): OpprettVedtakRequestDto {
        val sak = sakConsumer.hentSak(saksnummer)
        val beregning = beregningService.beregneForskudd(id!!)

        val stønadsendringPerioder =
            beregning.resultatBarn.map { it.byggStønadsendringerForVedtak(this) }

        val grunnlagListeVedtak = byggGrunnlagForVedtak()
        val stønadsendringGrunnlagListe = byggGrunnlagForStønad()

        val grunnlagListe =
            (grunnlagListeVedtak + stønadsendringPerioder.flatMap(StønadsendringPeriode::grunnlag) + stønadsendringGrunnlagListe).toSet()

        return OpprettVedtakRequestDto(
            enhetsnummer = Enhetsnummer(behandlerEnhet),
            vedtakstidspunkt = LocalDateTime.now(),
            type = vedtakstype,
            stønadsendringListe =
                stønadsendringPerioder.map {
                    OpprettStønadsendringRequestDto(
                        innkreving = Innkrevingstype.MED_INNKREVING,
                        skyldner = tilSkyldner(),
                        kravhaver =
                            it.barn.tilNyestePersonident()
                                ?: rolleManglerIdent(Rolletype.BARN, id!!),
                        mottaker =
                            roller
                                .reelMottakerEllerBidragsmottaker(
                                    sak.hentRolleMedFnr(it.barn.ident!!),
                                ),
                        sak = Saksnummer(saksnummer),
                        type = stonadstype!!,
                        beslutning = Beslutningstype.ENDRING,
                        grunnlagReferanseListe = stønadsendringGrunnlagListe.map(GrunnlagDto::referanse),
                        periodeListe = it.perioder,
                    )
                },
            engangsbeløpListe = emptyList(),
            behandlingsreferanseListe = tilBehandlingreferanseListe(),
            grunnlagListe = grunnlagListe.map(GrunnlagDto::tilOpprettRequestDto),
            kilde = Vedtakskilde.MANUELT,
            fastsattILand = null,
            innkrevingUtsattTilDato = null,
            // Settes automatisk av bidrag-vedtak basert på token
            opprettetAv = null,
        )
    }

    private fun Behandling.vedtakAlleredeFattet(): Nothing =
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er allerede fattet for behandling $id med vedtakId $vedtaksid",
        )
}
