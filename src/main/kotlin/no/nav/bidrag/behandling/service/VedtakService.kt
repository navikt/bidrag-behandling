package no.nav.bidrag.behandling.service

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.toggleFatteVedtakName
import no.nav.bidrag.behandling.transformers.grunnlag.StønadsendringPeriode
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForStønad
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForStønadAvslag
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForVedtak
import no.nav.bidrag.behandling.transformers.grunnlag.byggStønadsendringerForVedtak
import no.nav.bidrag.behandling.transformers.grunnlag.inntekterOgYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.tilPersonobjekter
import no.nav.bidrag.behandling.transformers.hentRolleMedFnr
import no.nav.bidrag.behandling.transformers.vedtak.reelMottakerEllerBidragsmottaker
import no.nav.bidrag.behandling.transformers.vedtak.tilBehandling
import no.nav.bidrag.behandling.transformers.vedtak.tilBehandlingreferanseListe
import no.nav.bidrag.behandling.transformers.vedtak.tilBeregningResultat
import no.nav.bidrag.behandling.transformers.vedtak.tilOpprettRequestDto
import no.nav.bidrag.behandling.transformers.vedtak.tilSkyldner
import no.nav.bidrag.behandling.transformers.vedtak.validerGrunnlagsreferanser
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.commons.util.tilVedtakDto
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
import no.nav.bidrag.transport.behandling.vedtak.response.behandlingId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Service
class VedtakService(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
    private val beregningService: BeregningService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val unleashInstance: Unleash,
) {
    fun konverterVedtakTilBehandlingForLesemodus(vedtakId: Long): Behandling? {
        LOGGER.info { "Konverterer vedtak $vedtakId for lesemodus" }
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null
        secureLogger.info { "Konverterer vedtak $vedtakId for lesemodus med innhold $vedtak" }
        return vedtak.tilBehandling(vedtakId, lesemodus = true)
    }

    @Transactional
    fun opprettBehandlingFraVedtak(
        request: OpprettBehandlingFraVedtakRequest,
        refVedtaksid: Long,
    ): OpprettBehandlingResponse {
        val konvertertBehandling =
            konverterVedtakTilBehandling(request, refVedtaksid)
                ?: throw RuntimeException("Fant ikke vedtak for vedtakid $refVedtaksid")
        val behandlingDo = behandlingService.opprettBehandling(konvertertBehandling)

        grunnlagService.oppdatereGrunnlagForBehandling(behandlingDo)

        LOGGER.info {
            "Opprettet behandling ${behandlingDo.id} fra vedtak $refVedtaksid med søktAv ${request.søknadFra}, " +
                "søktFomDato ${request.søktFomDato}, mottatDato ${request.mottattdato}, søknadId ${request.søknadsid}: $request"
        }
        return OpprettBehandlingResponse(behandlingDo.id!!)
    }

    fun konverterVedtakTilBehandling(
        request: OpprettBehandlingFraVedtakRequest,
        refVedtaksid: Long,
    ): Behandling? {
        // TODO: Sjekk tilganger
        val vedtak =
            vedtakConsumer.hentVedtak(refVedtaksid) ?: return null
        if (vedtak.behandlingId == null) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Vedtak $refVedtaksid er ikke fattet gjennom ny løsning og kan derfor ikke konverteres til behandling",
            )
        }
        return vedtak.tilBehandling(
            vedtakId = refVedtaksid,
            søktFomDato = request.søktFomDato,
            mottattdato = request.mottattdato,
            soknadFra = request.søknadFra,
            vedtakType = request.vedtakstype,
            søknadRefId = request.søknadsreferanseid,
            enhet = request.behandlerenhet,
            søknadId = request.søknadsid,
            lesemodus = false,
        )
    }

    fun konverterVedtakTilBeregningResultat(vedtakId: Long): List<ResultatBeregningBarnDto> {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return emptyList()
        return vedtak.tilBeregningResultat()
    }

    fun fatteVedtak(behandlingId: Long): Int {
        val isEnabled = unleashInstance.isEnabled(toggleFatteVedtakName, false)
        if (isEnabled.not()) {
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Fattevedtak er ikke aktivert",
            )
        }

        val behandling = behandlingService.hentBehandlingById(behandlingId)
        behandling.validerKanFatteVedtak()

        val request =
            if (behandling.avslag != null) {
                behandling.byggOpprettVedtakRequestForAvslag()
            } else {
                behandling.byggOpprettVedtakRequest()
            }

        request.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for behandling $behandlingId med forespørsel $request" }
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterVedtakFattetStatus(
            behandlingId,
            vedtaksid = response.vedtaksid.toLong(),
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
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val request =
            if (behandling.avslag != null) behandling.byggOpprettVedtakRequestForAvslag() else behandling.byggOpprettVedtakRequest()

        return request.tilVedtakDto()
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
                        omgjørVedtakId = refVedtaksid?.toInt(),
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
                                    periode = ÅrMånedsperiode(virkningstidspunktEllerSøktFomDato, null),
                                    beløp = null,
                                    resultatkode = avslag!!.name,
                                    valutakode = "NOK",
                                    grunnlagReferanseListe = emptyList(),
                                ),
                            ),
                    )
                },
            engangsbeløpListe = emptyList(),
            behandlingsreferanseListe = tilBehandlingreferanseListe(),
            grunnlagListe = (grunnlagListe + tilPersonobjekter()).map(GrunnlagDto::tilOpprettRequestDto),
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
            beregning.map { it.byggStønadsendringerForVedtak(this) }

        val grunnlagListeVedtak = byggGrunnlagForVedtak()
        val stønadsendringGrunnlagListe = byggGrunnlagForStønad()

        val grunnlagListe =
            (
                grunnlagListeVedtak +
                    stønadsendringPerioder.flatMap(
                        StønadsendringPeriode::grunnlag,
                    ) + stønadsendringGrunnlagListe
            ).toSet()

        return OpprettVedtakRequestDto(
            enhetsnummer = Enhetsnummer(behandlerEnhet),
            vedtakstidspunkt = LocalDateTime.now(),
            type = vedtakstype,
            stønadsendringListe =
                stønadsendringPerioder.map {
                    OpprettStønadsendringRequestDto(
                        innkreving = Innkrevingstype.MED_INNKREVING,
                        skyldner = tilSkyldner(),
                        omgjørVedtakId = refVedtaksid?.toInt(),
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
                        // Settes null for forskudd men skal settes til riktig verdi for bidrag
                        førsteIndeksreguleringsår = null,
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

    val grunnlagstyperSomIkkeSkalSjekkes = listOf(Grunnlagsdatatype.SIVILSTAND, Grunnlagsdatatype.BOFORHOLD)

    private fun Behandling.validerKanFatteVedtak() {
        if (erVedtakFattet) vedtakAlleredeFattet()
        val ikkeAktivertGrunnlag = grunnlag.filter { it.aktiv == null && !grunnlagstyperSomIkkeSkalSjekkes.contains(it.type) }
        val ikkeAktivertGrunnlagIkkeInntekt =
            ikkeAktivertGrunnlag.filter { !inntekterOgYtelser.contains(it.type) }
        val feilmelding = "Kan ikke fatte vedtak fordi nyeste opplysninger ikke er hentet inn"
        if (avslag == null && !erKlageEllerOmgjøring && ikkeAktivertGrunnlag.isNotEmpty()) {
//            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, feilmelding)
            LOGGER.warn { feilmelding }
        }

        if (erKlageEllerOmgjøring && ikkeAktivertGrunnlagIkkeInntekt.isNotEmpty()) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, feilmelding)
        }

        if (virkningstidspunkt == null) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Virkningstidspunkt må settes")
        }

        val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
            erKlageEllerOmgjøring && opprinneligVirkningstidspunkt != null &&
                virkningstidspunkt?.isAfter(opprinneligVirkningstidspunkt) == true
        if (erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Virkningstidspunkt ikke være senere enn opprinnelig virkningstidspunkt")
        }

        if (saksnummer.isEmpty()) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Saksnummer mangler")
        }
    }

    private fun Behandling.vedtakAlleredeFattet(): Nothing =
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er allerede fattet for behandling $id med vedtakId $vedtaksid",
        )
}
