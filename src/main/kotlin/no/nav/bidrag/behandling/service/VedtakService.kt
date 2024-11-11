package no.nav.bidrag.behandling.service

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.toggleFatteVedtakName
import no.nav.bidrag.behandling.transformers.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.tilBeregningResultat
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.validerGrunnlagsreferanser
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
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
    private val notatOpplysningerService: NotatOpplysningerService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val unleashInstance: Unleash,
    private val validering: ValiderBeregning,
    private val vedtakTilBehandlingMapping: VedtakTilBehandlingMapping,
    private val behandlingTilVedtakMapping: BehandlingTilVedtakMapping,
    private val vedtakValiderBehandlingService: ValiderBehandlingService,
) {
    fun konverterVedtakTilBehandlingForLesemodus(vedtakId: Long): Behandling? {
        try {
            LOGGER.info { "Konverterer vedtak $vedtakId for lesemodus" }
            val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null
            tilgangskontrollService.sjekkTilgangVedtak(vedtak)

            secureLogger.info { "Konverterer vedtak $vedtakId for lesemodus med innhold $vedtak" }
            return vedtakTilBehandlingMapping.run { vedtak.tilBehandling(vedtakId, lesemodus = true) }
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved konvertering av vedtak $vedtakId for lesemodus" }
            throw e
        }
    }

    private fun hentOpprinneligVedtakstype(vedtak: VedtakDto): Vedtakstype {
        val vedtaksiderStønadsendring = vedtak.stønadsendringListe.mapNotNull { it.omgjørVedtakId }
        val vedtaksiderEngangsbeløp = vedtak.engangsbeløpListe.mapNotNull { it.omgjørVedtakId }
        val refererTilVedtakId = (vedtaksiderEngangsbeløp + vedtaksiderStønadsendring).toSet()
        if (refererTilVedtakId.isNotEmpty()) {
            val opprinneligVedtak = vedtakConsumer.hentVedtak(refererTilVedtakId.first().toLong())!!
            return hentOpprinneligVedtakstype(opprinneligVedtak)
        }
        return vedtak.type
    }

    private fun hentOpprinneligVedtakstidspunkt(vedtak: VedtakDto): Set<LocalDateTime> {
        val vedtaksiderStønadsendring = vedtak.stønadsendringListe.mapNotNull { it.omgjørVedtakId }
        val vedtaksiderEngangsbeløp = vedtak.engangsbeløpListe.mapNotNull { it.omgjørVedtakId }
        val refererTilVedtakId = (vedtaksiderEngangsbeløp + vedtaksiderStønadsendring).toSet()
        if (refererTilVedtakId.isNotEmpty()) {
            return refererTilVedtakId
                .flatMap { vedtaksid ->
                    val opprinneligVedtak = vedtakConsumer.hentVedtak(vedtaksid.toLong())!!
                    hentOpprinneligVedtakstidspunkt(opprinneligVedtak)
                }.toSet() + setOf(vedtak.vedtakstidspunkt)
        }
        return setOf(vedtak.vedtakstidspunkt)
    }

    @Transactional
    fun opprettBehandlingFraVedtak(
        request: OpprettBehandlingFraVedtakRequest,
        refVedtaksid: Long,
    ): OpprettBehandlingResponse {
        try {
            LOGGER.info {
                "Oppretter behandling fra vedtak $refVedtaksid med søktAv ${request.søknadFra}, " +
                    "søktFomDato ${request.søktFomDato}, mottatDato ${request.mottattdato}, søknadId ${request.søknadsid}: $request"
            }
            val konvertertBehandling =
                konverterVedtakTilBehandling(request, refVedtaksid)
                    ?: throw RuntimeException("Fant ikke vedtak for vedtakid $refVedtaksid")

            tilgangskontrollService.sjekkTilgangBehandling(konvertertBehandling)
            val behandlingDo = behandlingService.opprettBehandling(konvertertBehandling)
            grunnlagService.oppdatereGrunnlagForBehandling(behandlingDo)

            LOGGER.info {
                "Opprettet behandling ${behandlingDo.id} fra vedtak $refVedtaksid med søktAv ${request.søknadFra}, " +
                    "søktFomDato ${request.søktFomDato}, mottatDato ${request.mottattdato}, søknadId ${request.søknadsid}: $request"
            }
            return OpprettBehandlingResponse(behandlingDo.id!!)
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved opprettelse av behandling fra vedtak $refVedtaksid: ${e.message}" }
            throw e
        }
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
        return vedtakTilBehandlingMapping.run {
            vedtak.tilBehandling(
                vedtakId = refVedtaksid,
                søktFomDato = request.søktFomDato,
                mottattdato = request.mottattdato,
                soknadFra = request.søknadFra,
                vedtakType = request.vedtakstype,
                søknadRefId = request.søknadsreferanseid,
                enhet = request.behandlerenhet,
                søknadId = request.søknadsid,
                lesemodus = false,
                opprinneligVedtakstidspunkt = hentOpprinneligVedtakstidspunkt(vedtak).toSet(),
                opprinneligVedtakstype = hentOpprinneligVedtakstype(vedtak),
            )
        }
    }

    fun konverterVedtakTilBeregningResultat(vedtakId: Long): List<ResultatBeregningBarnDto> {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return emptyList()
        return vedtak.tilBeregningResultat()
    }

    fun konverterVedtakTilBeregningResultatSærbidrag(vedtakId: Long): ResultatSærbidragsberegningDto? {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null
        return vedtakTilBehandlingMapping.run { vedtak.tilBeregningResultatSærbidrag() }
    }

    fun fatteVedtak(behandlingId: Long): Int {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        behandling.validerKanFatteVedtak()
        return when (behandling.tilType()) {
            TypeBehandling.FORSKUDD -> fatteVedtakForskudd(behandling)
            TypeBehandling.SÆRBIDRAG -> fatteVedtakSærbidrag(behandling)
            TypeBehandling.BIDRAG -> fatteVedtakBidrag(behandling)
            else -> throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Fatte vedtak av behandlingstype ${behandling.tilType()} støttes ikke",
            )
        }
    }

    fun fatteVedtakSærbidrag(behandling: Behandling): Int {
        val behandlingId = behandling.id!!
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run {
            behandling.validerTekniskForBeregningAvSærbidrag()
            behandling.validerForBeregningSærbidrag()
        }

        val request =
            validering.run {
                behandlingTilVedtakMapping.run {
                    if (behandling.erDirekteAvslagUtenBeregning()) {
                        behandling.byggOpprettVedtakRequestAvslagForSærbidrag()
                    } else {
                        behandling.byggOpprettVedtakRequestSærbidrag()
                    }
                }
            }
        request.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for særbidrag behandling $behandlingId med forespørsel $request" }
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterVedtakFattetStatus(
            behandlingId,
            vedtaksid = response.vedtaksid.toLong(),
        )
        opprettNotat(behandling)
        LOGGER.info {
            "Fattet vedtak for særbidrag behandling $behandlingId med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun fatteVedtakForskudd(behandling: Behandling): Int {
        validering.run { behandling.validerForBeregning() }

        val request =
            behandlingTilVedtakMapping.run {
                if (behandling.avslag != null) {
                    behandling.byggOpprettVedtakRequestAvslagForForskudd()
                } else {
                    behandling.byggOpprettVedtakRequestForskudd()
                }
            }

        request.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for behandling ${behandling.id} med forespørsel $request" }
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterVedtakFattetStatus(
            behandling.id!!,
            vedtaksid = response.vedtaksid.toLong(),
        )
        opprettNotat(behandling)
        LOGGER.info {
            "Fattet vedtak for behandling ${behandling.id} med ${
                behandling.årsak?.let { "årsakstype $it" }
                    ?: "avslagstype ${behandling.avslag}"
            } med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun fatteVedtakBidrag(behandling: Behandling): Int {
        val isEnabled = unleashInstance.isEnabled(toggleFatteVedtakName, false)
        if (isEnabled.not()) {
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Fattevedtak er ikke aktivert",
            )
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run { behandling.validerForBeregning() }
        val request =
            behandlingTilVedtakMapping.run {
                if (behandling.avslag != null) {
                    behandling.byggOpprettVedtakRequestAvslagForBidrag()
                } else {
                    behandling.byggOpprettVedtakRequestBidrag()
                }
            }

        request.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for behandling ${behandling.id} med forespørsel $request" }
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterVedtakFattetStatus(
            behandling.id!!,
            vedtaksid = response.vedtaksid.toLong(),
        )
        opprettNotat(behandling)
        LOGGER.info {
            "Fattet vedtak for behandling ${behandling.id} med ${
                behandling.årsak?.let { "årsakstype $it" }
                    ?: "avslagstype ${behandling.avslag}"
            } med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun behandlingTilVedtakDto(behandlingId: Long): VedtakDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return behandlingTilVedtakMapping.run {
            behandling.mapBehandlingTilVedtakDto()
        }
    }

    private fun Behandling.validerKanFatteVedtak() {
        if (erVedtakFattet) vedtakAlleredeFattet()
        if (virkningstidspunkt == null) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Virkningstidspunkt må settes")
        }

        val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
            erKlageEllerOmgjøring &&
                opprinneligVirkningstidspunkt != null &&
                virkningstidspunkt?.isAfter(opprinneligVirkningstidspunkt) == true
        if (erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Virkningstidspunkt ikke være senere enn opprinnelig virkningstidspunkt",
            )
        }

        if (saksnummer.isEmpty()) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Saksnummer mangler")
        }
    }

    private fun opprettNotat(behandling: Behandling) {
        try {
            notatOpplysningerService.opprettNotat(behandling.id!!)
        } catch (e: Exception) {
            LOGGER.error(
                e,
            ) { "Det skjedde en feil ved opprettelse av notat for behandling ${behandling.id} og vedtaksid ${behandling.vedtaksid}" }
        }
    }

    private fun Behandling.vedtakAlleredeFattet(): Nothing =
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er allerede fattet for behandling $id med vedtakId $vedtaksid",
        )
}
