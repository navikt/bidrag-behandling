package no.nav.bidrag.behandling.service

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.tilPersonident
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.hentRolleMedFnr
import no.nav.bidrag.behandling.transformers.vedtak.StønadsendringPeriode
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagNotater
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagSøknad
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagVirkningsttidspunkt
import no.nav.bidrag.behandling.transformers.vedtak.byggStønadsendringerForVedtak
import no.nav.bidrag.behandling.transformers.vedtak.reelMottakerEllerBidragsmottaker
import no.nav.bidrag.behandling.transformers.vedtak.tilBehandlingreferanseList
import no.nav.bidrag.behandling.transformers.vedtak.tilOpprettRequestDto
import no.nav.bidrag.behandling.transformers.vedtak.tilSkyldner
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettStønadsendringRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Service
class VedtakService(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
    private val grunnlagService: GrunnlagService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val unleashInstance: Unleash,
) {
    fun fatteVedtak(behandlingId: Long): Int {
        val isEnabled = unleashInstance.isEnabled("behandling.fattevedtak", false)
        if (isEnabled.not()) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Fattevedtak er ikke aktivert",
            )
        }

        val behandling = behandlingService.hentBehandlingById(behandlingId)
        if (behandling.vedtaksid != null) behandling.vedtakAlleredeFattet()

        val sak = sakConsumer.hentSak(behandling.saksnummer)
        val grunnlag = grunnlagService.hentAlleSistAktiv(behandlingId)
        val beregning = beregningService.beregneForskudd(behandlingId)

        val stønadsendringPeriode =
            beregning.resultatBarn.map { it.byggStønadsendringerForVedtak(behandling, grunnlag) }

        val stønadsendringGrunnlagListe =
            behandling.byggGrunnlagNotater() + behandling.byggGrunnlagVirkningsttidspunkt() + behandling.byggGrunnlagSøknad()

        val grunnlagsListe =
            stønadsendringPeriode
                .flatMap(StønadsendringPeriode::grunnlag)
                .distinctBy { it.referanse } +
                stønadsendringGrunnlagListe
        val request =
            OpprettVedtakRequestDto(
                enhetsnummer = Enhetsnummer(behandling.behandlerEnhet),
                vedtakstidspunkt = LocalDateTime.now(),
                type = behandling.vedtakstype,
                stønadsendringListe =
                    stønadsendringPeriode.map {
                        OpprettStønadsendringRequestDto(
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            skyldner = behandling.tilSkyldner(),
                            kravhaver =
                                it.barn.tilPersonident()
                                    ?: rolleManglerIdent(Rolletype.BARN, behandlingId),
                            mottaker =
                                behandling.roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(it.barn.ident!!),
                                    ),
                            sak = Saksnummer(behandling.saksnummer),
                            type = behandling.stonadstype!!,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = stønadsendringGrunnlagListe.map(GrunnlagDto::referanse),
                            periodeListe = it.perioder,
                        )
                    },
                engangsbeløpListe = emptyList(),
                behandlingsreferanseListe = behandling.tilBehandlingreferanseList(),
                grunnlagListe = grunnlagsListe.map(GrunnlagDto::tilOpprettRequestDto),
                kilde = Vedtakskilde.MANUELT,
                fastsattILand = null,
                innkrevingUtsattTilDato = null,
                // Settes automatisk av bidrag-vedtak basert på token
                opprettetAv = null,
            )

        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterBehandling(
            behandlingId,
            OppdaterBehandlingRequestV2(vedtaksid = response.vedtaksid),
        )
        LOGGER.info { "Fattet vedtak for behandling $behandlingId med vedtaksid ${response.vedtaksid}" }
        return response.vedtaksid
    }

    private fun Behandling.vedtakAlleredeFattet(): Nothing =
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er allerede fattet for behandling $id med vedtakId $vedtaksid",
        )
}
