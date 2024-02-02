package no.nav.bidrag.behandling.service

import io.getunleash.Unleash
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.tilPersonident
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
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class VedtakService(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
    private val grunnlagService: GrunnlagService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val unleashInstance: Unleash,
) {
    fun fatteVedtak(behandlingId: Long): OpprettVedtakResponseDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

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

        return vedtakConsumer.fatteVedtak(request)
    }
}
