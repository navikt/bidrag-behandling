package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.json.FattetDelvedtak
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.json.OpprettParagraf35C
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.validering.FatteVedtakFeil
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.dto.v2.vedtak.OppdaterParagraf35cDetaljerDto
import no.nav.bidrag.behandling.transformers.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.dto.OrkestrertVedtak
import no.nav.bidrag.behandling.transformers.dto.PåklagetVedtak
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.finnAldersjusteringDetaljerGrunnlag
import no.nav.bidrag.behandling.transformers.finnEksisterendeVedtakMedOpphør
import no.nav.bidrag.behandling.transformers.skalInnkrevingKunneUtsettes
import no.nav.bidrag.behandling.transformers.tilStønadsid
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.tilBeregningResultatBidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.tilBeregningResultatForskudd
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.ResultatadBeregningOrkestrering
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnInnkrevesFraDato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.leggTilVedtaksidPåAldersjusteringGrunnlag
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.behandling.transformers.vedtak.validerGrunnlagsreferanser
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.beregn.core.util.justerVedtakstidspunktVedtak
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.behandlingId
import no.nav.bidrag.transport.behandling.vedtak.response.erDelvedtak
import no.nav.bidrag.transport.behandling.vedtak.response.erOrkestrertVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.erVedtaksforslag
import no.nav.bidrag.transport.behandling.vedtak.response.finnVirkningstidspunkt
import no.nav.bidrag.transport.behandling.vedtak.response.finnVirkningstidspunktForStønad
import no.nav.bidrag.transport.behandling.vedtak.response.harResultatFraAnnenVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.referertVedtaksid
import no.nav.bidrag.transport.behandling.vedtak.response.virkningstidspunkt
import no.nav.bidrag.transport.felles.toYearMonth
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

@Service
class VedtakService(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
    private val notatOpplysningerService: NotatOpplysningerService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val validering: ValiderBeregning,
    private val vedtakTilBehandlingMapping: VedtakTilBehandlingMapping,
    private val behandlingTilVedtakMapping: BehandlingTilVedtakMapping,
    private val vedtakValiderBehandlingService: ValiderBehandlingService,
    private val forsendelseService: ForsendelseService,
    private val virkningstidspunktService: VirkningstidspunktService,
//    private val vedtakLocalConsumer: BidragVedtakConsumerLocal? = null,
) {
    fun konverterVedtakTilBehandlingForLesemodus(vedtakId: Int): Behandling? {
        try {
            LOGGER.info { "Konverterer vedtak $vedtakId for lesemodus" }
            val vedtak =
                hentOrkestrertVedtak(vedtakId) ?: return null

            tilgangskontrollService.sjekkTilgangVedtak(vedtak.opprinneligVedtak)
            val påklagetVedtakListe = hentOmgjortVedtaksliste(vedtak.vedtak)

            secureLogger.info { "Konverterer vedtak $vedtakId for lesemodus med innhold $vedtak" }
            return vedtakTilBehandlingMapping.run {
                vedtak.opprinneligVedtak.tilBehandling(
                    vedtakId,
                    lesemodus = true,
                    omgjørVedtaksliste = påklagetVedtakListe,
                    erOrkestrertVedtak = vedtak.erOrkestrertVedtak,
                )
            }
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved konvertering av vedtak $vedtakId for lesemodus" }
            throw e
        }
    }

    private fun hentOrkestrertVedtak(vedtakId: Int): OrkestrertVedtak? {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null
        val erOrkestrertVedtak = vedtak.erOrkestrertVedtak && vedtak.type != Vedtakstype.INNKREVING
        val referertVedtak =
            if (vedtak.harResultatFraAnnenVedtak && (vedtak.erOrkestrertVedtak || vedtak.erDelvedtak) &&
                vedtak.type != Vedtakstype.INNKREVING
            ) {
                vedtakConsumer.hentVedtak(vedtak.referertVedtaksid!!)
            } else {
                null
            }
        return OrkestrertVedtak(
            vedtak,
            erOrkestrertVedtak,
            referertVedtak,
        )
    }

    private fun hentOpprinneligVedtakstype(vedtak: VedtakDto): Vedtakstype {
        val vedtaksiderStønadsendring = vedtak.stønadsendringListe.mapNotNull { it.omgjørVedtakId }
        val vedtaksiderEngangsbeløp = vedtak.engangsbeløpListe.mapNotNull { it.omgjørVedtakId }
        val refererTilVedtakId = (vedtaksiderEngangsbeløp + vedtaksiderStønadsendring).toSet()
        if (refererTilVedtakId.isNotEmpty()) {
            val opprinneligVedtak = vedtakConsumer.hentVedtak(refererTilVedtakId.first())!!
            return hentOpprinneligVedtakstype(opprinneligVedtak)
        }
        return vedtak.type
    }

    private fun hentOmgjortVedtaksliste(vedtak: VedtakDto): Set<PåklagetVedtak> {
        if (vedtak.erVedtaksforslag()) return emptySet()
        val vedtaksiderStønadsendring = vedtak.stønadsendringListe.mapNotNull { it.omgjørVedtakId }
        val vedtaksiderEngangsbeløp = vedtak.engangsbeløpListe.mapNotNull { it.omgjørVedtakId }
        val refererTilVedtakId = (vedtaksiderEngangsbeløp + vedtaksiderStønadsendring).toSet()
        val virkningstidspunkt =
            if (vedtak.stønadsendringListe.isNotEmpty()) {
                vedtak.stønadsendringListe
                    .filter { it.periodeListe.isNotEmpty() }
                    .mapNotNull {
                        vedtak.finnVirkningstidspunktForStønad(
                            it.tilStønadsid(),
                        )
                    }.minOrNull()
            } else {
                vedtak.virkningstidspunkt?.toYearMonth()
            }

        fun VedtakDto.tilPåklagetVedtaksliste() =
            if (stønadsendringListe.isEmpty()) {
                val kravhaver = engangsbeløpListe.first().kravhaver
                setOf(
                    PåklagetVedtak(
                        vedtaksid,
                        kravhaver,
                        justerVedtakstidspunktVedtak().vedtakstidspunkt!!,
                        virkningstidspunkt,
                        type,
                        BeregnTil.INNEVÆRENDE_MÅNED,
                    ),
                )
            } else {
                stønadsendringListe
                    .map { se ->
                        val virkningstidspunktGrunnlag = vedtak.finnVirkningstidspunkt(se)
                        PåklagetVedtak(
                            vedtak.vedtaksid,
                            se.kravhaver,
                            justerVedtakstidspunktVedtak().vedtakstidspunkt!!,
                            se.periodeListe.takeIfNotNullOrEmpty {
                                finnVirkningstidspunktForStønad(
                                    se.tilStønadsid(),
                                )
                            },
                            type,
                            virkningstidspunktGrunnlag?.innhold?.beregnTil ?: BeregnTil.INNEVÆRENDE_MÅNED,
                        )
                    }.toSet()
            }

        if (refererTilVedtakId.isNotEmpty()) {
            return refererTilVedtakId
                .flatMap { vedtaksid ->
                    val opprinneligVedtak = vedtakConsumer.hentVedtak(vedtaksid)!!

                    hentOmgjortVedtaksliste(opprinneligVedtak)
                }.toSet() + vedtak.tilPåklagetVedtaksliste()
        }
        return vedtak.tilPåklagetVedtaksliste()
    }

    @Transactional
    fun opprettBehandlingFraVedtak(
        request: OpprettBehandlingFraVedtakRequest,
        refVedtaksid: Int,
    ): OpprettBehandlingResponse {
        try {
            LOGGER.info {
                "Oppretter behandling fra vedtak $refVedtaksid med søktAv ${request.søknadFra}, " +
                    "søktFomDato ${request.søktFomDato}, mottatDato ${request.mottattdato}, søknadId ${request.søknadsid}: $request"
            }

            behandlingService.hentEksisteredenBehandling(request.søknadsid)?.let {
                secureLogger.warn {
                    "Fant eksisterende behandling ${it.id} for søknadsId ${request.søknadsid}. Oppretter ikke ny behandling"
                }
                return OpprettBehandlingResponse(it.id!!)
            }

            val konvertertBehandling =
                konverterVedtakTilBehandling(request, refVedtaksid)
                    ?: throw RuntimeException("Fant ikke vedtak for vedtakid $refVedtaksid")

            tilgangskontrollService.sjekkTilgangBehandling(konvertertBehandling)
            val behandlingDo = behandlingService.lagreBehandling(konvertertBehandling)
            grunnlagService.oppdatereGrunnlagForBehandling(behandlingDo)
            if (behandlingDo.erBidrag()) {
                behandlingDo.søknadsbarn.forEach { rolle ->
                    val opphørsdato = rolle.opphørsdato ?: behandlingDo.finnEksisterendeVedtakMedOpphør(rolle)?.opphørsdato
                    opphørsdato?.let {
                        val opphørsdato = if (it.isAfter(behandlingDo.virkningstidspunkt!!)) it else null
                        if (opphørsdato != null) {
                            virkningstidspunktService.oppdaterOpphørsdato(
                                behandlingDo.id!!,
                                OppdaterOpphørsdatoRequestDto(
                                    rolle.id!!,
                                    opphørsdato,
                                    true,
                                ),
                            )
                        }
                    }
                }
            }
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
        omgjørVedtakId: Int,
    ): Behandling? {
        // TODO: Sjekk tilganger
        val vedtak =
            vedtakConsumer.hentVedtak(omgjørVedtakId)?.let {
                if (it.erOrkestrertVedtak) {
                    vedtakConsumer.hentVedtak(it.referertVedtaksid!!)
                } else {
                    it
                }
            } ?: return null
        if (vedtak.behandlingId == null && vedtak.grunnlagListe.isEmpty()) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Vedtak $omgjørVedtakId er ikke fattet gjennom ny løsning og kan derfor ikke konverteres til behandling",
            )
        }

        val omgjørVedtakListe = hentOmgjortVedtaksliste(vedtak)
        return vedtakTilBehandlingMapping.run {
            vedtak.tilBehandling(
                omgjørVedtakId = omgjørVedtakId,
                søktFomDato = request.søktFomDato,
                mottattdato = request.mottattdato,
                soknadFra = request.søknadFra,
                vedtakType = request.vedtakstype,
                søknadRefId = request.søknadsreferanseid,
                enhet = request.behandlerenhet,
                søknadId = request.søknadsid,
                søknadstype = request.søknadstype,
                lesemodus = false,
                omgjørVedtaksliste = omgjørVedtakListe,
                omgjortVedtakVedtakstidspunkt = vedtak.justerVedtakstidspunktVedtak().vedtakstidspunkt,
                erBisysVedtak = vedtak.kildeapplikasjon == "bisys",
            )
        }
    }

    @Transactional
    fun oppdaterParagrafP35c(
        behandlingId: Long,
        request: OppdaterParagraf35cDetaljerDto,
    ) {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val rolle = behandling.søknadsbarn.find { it.ident == request.ident }!!
        val omgjøringsdetaljer = behandling.omgjøringsdetaljer ?: Omgjøringsdetaljer()
        val paragraf35c =
            omgjøringsdetaljer.paragraf35c.find { it.vedtaksid == request.vedtaksid }?.copy(
                opprettParagraf35c = request.opprettP35c,
                vedtaksid = request.vedtaksid,
                rolleid = rolle.id!!,
            ) ?: OpprettParagraf35C(rolle.id!!, request.vedtaksid, request.opprettP35c)
        behandling.omgjøringsdetaljer =
            omgjøringsdetaljer.copy(
                paragraf35c = omgjøringsdetaljer.paragraf35c.filter { it.vedtaksid != request.vedtaksid } + paragraf35c,
            )
    }

    fun konverterVedtakTilBeregningResultatBidrag(vedtakId: Long): ResultatBidragberegningDto? {
        val vedtak =
            hentOrkestrertVedtak(vedtakId.toInt()) ?: return null
        return vedtak.vedtak.tilBeregningResultatBidrag(vedtak.opprinneligVedtak)
    }

    fun konverterVedtakTilBeregningResultatForskudd(vedtakId: Long): List<ResultatBeregningBarnDto> {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId.toInt()) ?: return emptyList()
        return vedtak.tilBeregningResultatForskudd()
    }

    fun konverterVedtakTilBeregningResultatSærbidrag(vedtakId: Long): ResultatSærbidragsberegningDto? {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId.toInt()) ?: return null
        return vedtakTilBehandlingMapping.run { vedtak.tilBeregningResultatSærbidrag() }
    }

    fun fatteVedtak(
        behandlingId: Long,
        request: FatteVedtakRequestDto? = null,
    ): Int {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        behandling.validerKanFatteVedtak()
        return when (behandling.tilType()) {
            TypeBehandling.FORSKUDD -> fatteVedtakForskudd(behandling, request)
            TypeBehandling.SÆRBIDRAG -> fatteVedtakSærbidrag(behandling, request)
            TypeBehandling.BIDRAG -> fatteVedtakBidrag(behandling, request)
            else -> throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Fatte vedtak av behandlingstype ${behandling.tilType()} støttes ikke",
            )
        }
    }

    fun fatteVedtakSærbidrag(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
    ): Int {
        val behandlingId = behandling.id!!
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run {
            behandling.validerTekniskForBeregningAvSærbidrag()
            behandling.validerForBeregningSærbidrag()
        }

        val vedtakRequest =
            validering.run {
                behandlingTilVedtakMapping.run {
                    if (behandling.erDirekteAvslagUtenBeregning()) {
                        behandling.byggOpprettVedtakRequestAvslagForSærbidrag(request?.enhet)
                    } else {
                        behandling.byggOpprettVedtakRequestSærbidrag(request?.enhet)
                    }
                }
            }
        vedtakRequest.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for særbidrag behandling $behandlingId med forespørsel $vedtakRequest" }
        val response = fatteVedtak(vedtakRequest)
        behandlingService.oppdaterVedtakFattetStatus(
            behandlingId,
            vedtaksid = response.vedtaksid,
            request?.enhet ?: behandling.behandlerEnhet,
        )
        opprettNotat(behandling)
        LOGGER.info {
            "Fattet vedtak for særbidrag behandling $behandlingId med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun fatteVedtakForskudd(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
    ): Int {
        validering.run { behandling.validerForBeregningForskudd() }

        val fatteVedtakRequest =
            behandlingTilVedtakMapping.run {
                if (behandling.avslag != null) {
                    behandling.byggOpprettVedtakRequestAvslagForForskudd(request?.enhet)
                } else {
                    behandling.byggOpprettVedtakRequestForskudd(request?.enhet)
                }
            }

        fatteVedtakRequest.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for behandling ${behandling.id} med forespørsel $fatteVedtakRequest" }
        val response = fatteVedtak(fatteVedtakRequest)
        behandlingService.oppdaterVedtakFattetStatus(
            behandling.id!!,
            vedtaksid = response.vedtaksid,
            request?.enhet ?: behandling.behandlerEnhet,
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

    fun fatteVedtakBidragOmgjøring(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
    ): Int {
        if (!UnleashFeatures.FATTE_VEDTAK.isEnabled) {
            ugyldigForespørsel("Kan ikke fatte vedtak for klage")
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run { behandling.validerForBeregningBidrag() }

        val beregning = behandlingTilVedtakMapping.hentBeregningBarnebidrag(behandling)
        beregning.validerManuelAldersjustering(behandling)

        val requestDelvedtak =
            beregning.copy(
                delvedtak =
                    behandlingTilVedtakMapping.opprettVedtakRequestDelvedtak(
                        behandling,
                        beregning.sak,
                        request?.enhet,
                        beregning.beregning.first(),
                        beregning.klagevedtakErEnesteVedtak,
                    ),
            )

        val response =
            if (beregning.klagevedtakErEnesteVedtak) {
                val klagevedtak = requestDelvedtak.delvedtak.find { it.omgjøringsvedtak }!!
                secureLogger.info {
                    "Klagevedtak er eneste vedtak i orkestrering. Fatter bare vedtak for klagevedtak ${klagevedtak.request}"
                }
                val response = fatteVedtak(klagevedtak.request!!)
                response to klagevedtak.request
            } else {
                var klagevedtakId: Int? = null
                val oppdatertDelvedtak =
                    requestDelvedtak.delvedtak.map { delvedtak ->
                        // Ikke fatte vedtak for delvedtak som ikke er beregnet
                        if (!delvedtak.beregnet) return@map delvedtak
                        val opprettRequest =
                            if (delvedtak.type == Vedtakstype.ALDERSJUSTERING) {
                                requireNotNull(klagevedtakId) { "Klagevedtaksid er null. Kunne ikke oppdatere aldersjustering grunnlag" }
                                delvedtak.request!!.leggTilVedtaksidPåAldersjusteringGrunnlag(klagevedtakId)
                            } else {
                                delvedtak.request!!
                            }

                        delvedtak.request.validerGrunnlagsreferanser()
                        secureLogger.info { "Fatter vedtak for delvedtak ${opprettRequest.type} med forespørsel ${delvedtak.request}" }
                        val response = fatteVedtak(opprettRequest)
                        behandlingService.oppdaterDelvedtakFattetStatus(
                            behandlingsid = behandling.id!!,
                            fattetAvEnhet = request?.enhet ?: behandling.behandlerEnhet,
                            resultat =
                                FattetDelvedtak(
                                    vedtaksid = response.vedtaksid,
                                    vedtakstype = delvedtak.request.type,
                                    referanse = delvedtak.request.unikReferanse ?: "ukjent",
                                ),
                        )
                        if (delvedtak.omgjøringsvedtak) {
                            klagevedtakId = response.vedtaksid
                        }
                        delvedtak.copy(
                            vedtaksid = response.vedtaksid,
                        )
                    }

                val requestEndeligVedtak =
                    behandlingTilVedtakMapping.byggOpprettVedtakRequestBidragEndeligKlage(
                        behandling,
                        request?.enhet,
                        requestDelvedtak.copy(delvedtak = oppdatertDelvedtak),
                    )

                requestEndeligVedtak.validerGrunnlagsreferanser()
                val response = fatteVedtak(requestEndeligVedtak)
                secureLogger.info { "Fattet endelig vedtak med forespørsel $requestEndeligVedtak og vedtaksid ${response.vedtaksid}" }
                response to requestEndeligVedtak
            }

        if (behandling.innkrevingstype == Innkrevingstype.UTEN_INNKREVING) {
            fatteInnkrevingsgrunnlagOmgjøring(behandling, request?.enhet, response.first.vedtaksid, response.second)
        }
        behandlingService.oppdaterVedtakFattetStatus(
            behandling.id!!,
            vedtaksid = response.first.vedtaksid,
            request?.enhet ?: behandling.behandlerEnhet,
        )

        opprettNotat(behandling)

        LOGGER.info {
            "Fattet vedtak for behandling ${behandling.id} med ${
                behandling.årsak?.let { "årsakstype $it" }
                    ?: "avslagstype ${behandling.avslag}"
            } med vedtaksid ${response.first.vedtaksid}"
        }
        return response.first.vedtaksid
    }

    private fun fatteInnkreving(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
    ): Int {
        if (!UnleashFeatures.FATTE_VEDTAK.isEnabled) {
            ugyldigForespørsel("Kan ikke fatte vedtak for klage")
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run { behandling.validerForBeregningBidrag() }
        val innkrevingRequest =
            behandlingTilVedtakMapping.byggOpprettVedtakRequestInnkreving(
                behandling,
                request?.enhet,
            )

        innkrevingRequest.validerGrunnlagsreferanser()
        val responseInnkreving = fatteVedtak(innkrevingRequest)
        secureLogger.info {
            "Fattet innkrevingsgrunnlag for vedtak med forespørsel $innkrevingRequest og vedtaksid ${responseInnkreving.vedtaksid}"
        }
        behandlingService.oppdaterDelvedtakFattetStatus(
            behandlingsid = behandling.id!!,
            fattetAvEnhet = request?.enhet ?: behandling.behandlerEnhet,
            resultat =
                FattetDelvedtak(
                    vedtaksid = responseInnkreving.vedtaksid,
                    vedtakstype = innkrevingRequest.type,
                    referanse = innkrevingRequest.unikReferanse ?: "ukjent",
                ),
        )
        return responseInnkreving.vedtaksid
    }

    private fun fatteInnkrevingsgrunnlagOmgjøring(
        behandling: Behandling,
        enhet: String?,
        vedtaksidOrkestrering: Int,
        vedtak: OpprettVedtakRequestDto,
    ) {
        val erUtenInnkreving = behandling.søknadsbarn.all { behandling.finnInnkrevesFraDato(it) == null }
        if (erUtenInnkreving) {
            secureLogger.info { "Sak ${behandling.saksnummer} er uten innkreving. Fatter ikke innkrevingsgrunnlag" }
            return
        }

        val innkrevingRequest =
            behandlingTilVedtakMapping.byggOpprettVedtakRequestInnkrevingAvOmgjøring(
                behandling,
                enhet,
                vedtaksidOrkestrering,
                vedtak,
            )
        innkrevingRequest.validerGrunnlagsreferanser()
        val responseInnkreving = fatteVedtak(innkrevingRequest)
        secureLogger.info {
            "Fattet innkrevingsgrunnlag for vedtak med forespørsel $innkrevingRequest og vedtaksid ${responseInnkreving.vedtaksid}"
        }
        behandlingService.oppdaterDelvedtakFattetStatus(
            behandlingsid = behandling.id!!,
            fattetAvEnhet = enhet ?: behandling.behandlerEnhet,
            resultat =
                FattetDelvedtak(
                    vedtaksid = responseInnkreving.vedtaksid,
                    vedtakstype = innkrevingRequest.type,
                    referanse = innkrevingRequest.unikReferanse ?: "ukjent",
                ),
        )
    }

    fun ResultatadBeregningOrkestrering.validerManuelAldersjustering(behandling: Behandling) {
        val beregning = this.beregning.first()
        val manuellAldersjusteringSomMåVelges =
            beregning
                .resultatVedtak!!
                .resultatVedtakListe
                .filter { it.vedtakstype == Vedtakstype.ALDERSJUSTERING }
                .filter { delberegning ->
                    val søknadsbarn = behandling.søknadsbarn.find { it.ident == beregning.barn.ident!!.verdi }!!
                    val aldersjusteringDetaljer =
                        delberegning.resultat.grunnlagListe.finnAldersjusteringDetaljerGrunnlag() ?: return@filter false
                    val barnVedtak =
                        søknadsbarn.grunnlagFraVedtakListe.find {
                            it.aldersjusteringForÅr ==
                                aldersjusteringDetaljer.periode.fom.year
                        }
                    aldersjusteringDetaljer.aldersjusteresManuelt && barnVedtak?.vedtak == null
                }
        if (manuellAldersjusteringSomMåVelges.isNotEmpty()) {
            FatteVedtakFeil(
                "Et eller flere aldersjusteringer må behandles manuelt",
                manuellAldersjusteringSomMåVelges.map {
                    it.resultat.beregnetBarnebidragPeriodeListe
                        .first()
                        .periode
                },
            ).kastFeil()
        }
    }

    fun fatteVedtakBidrag(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
    ): Int {
        if (behandling.erKlageEllerOmgjøring) return fatteVedtakBidragOmgjøring(behandling, request)
        if (behandling.erInnkreving) return fatteInnkreving(behandling, request)
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run { behandling.validerForBeregningBidrag() }

        val vedtakRequest =
            behandlingTilVedtakMapping
                .run {
                    if (behandling.avslag != null) {
                        behandling.byggOpprettVedtakRequestAvslagForBidrag(request?.enhet)
                    } else {
                        behandling.byggOpprettVedtakRequestBidragAlle(request?.enhet)
                    }
                }.copy(
                    innkrevingUtsattTilDato =
                        if (behandling.skalInnkrevingKunneUtsettes()) {
                            request?.innkrevingUtsattAntallDager?.let {
                                LocalDate.now().plusDays(it)
                            }
                        } else {
                            null
                        },
                )

        vedtakRequest.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for behandling ${behandling.id} med forespørsel $vedtakRequest" }
        val response = fatteVedtak(vedtakRequest)
        behandlingService.oppdaterVedtakFattetStatus(
            behandling.id!!,
            vedtaksid = response.vedtaksid,
            request?.enhet ?: behandling.behandlerEnhet,
        )

        val aldersjusteringBeregnet =
            vedtakRequest.type == Vedtakstype.ALDERSJUSTERING &&
                vedtakRequest.stønadsendringListe.all { it.beslutning == Beslutningstype.ENDRING }
        if (aldersjusteringBeregnet) {
            forsendelseService.opprettForsendelseForAldersjustering(behandling)
        } else if (vedtakRequest.type != Vedtakstype.ALDERSJUSTERING) {
            opprettNotat(behandling)
        }

        if (vedtakRequest.type == Vedtakstype.ALDERSJUSTERING) {
            try {
                // Venter i 2 sekunder for å sikre at vedtaksbro har lest inn vedtaket og har oppdatert saksloggen
                Thread.sleep(2000)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                LOGGER.warn(ie) { "Tråd avbrutt under venting" }
            }
        }

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
            erKlageEllerOmgjøring && !erBidrag() && omgjøringsdetaljer?.opprinneligVirkningstidspunkt != null &&
                virkningstidspunkt!! > omgjøringsdetaljer!!.opprinneligVirkningstidspunkt
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

    private fun fatteVedtak(request: OpprettVedtakRequestDto): OpprettVedtakResponseDto = vedtakConsumer.fatteVedtak(request)
//
//    private fun fatteVedtak(request: OpprettVedtakRequestDto): OpprettVedtakResponseDto = vedtakLocalConsumer!!.fatteVedtak(request)
}
