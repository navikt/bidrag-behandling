package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.json.FattetVedtak
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.json.OpprettParagraf35C
import no.nav.bidrag.behandling.dto.internal.vedtak.BeregningVedtakResultat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.validering.FatteVedtakFeil
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.dto.v2.vedtak.OppdaterParagraf35cDetaljerDto
import no.nav.bidrag.behandling.transformers.behandling.kanFatteVedtak
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
import no.nav.bidrag.behandling.transformers.vedtak.innkrevingstype
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
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
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
                    innkrevingstype = vedtak.vedtak.innkrevingstype,
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
            val behandlingDo = behandlingService.lagreBehandling(konvertertBehandling, true)
            grunnlagService.oppdaterGrunnlagForBehandlingAsync(behandlingDo)
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
                lesemodus = false,
                vedtakType = request.vedtakstype,
                mottattdato = request.mottattdato,
                søktFomDato = request.søktFomDato,
                soknadFra = request.søknadFra,
                søknadRefId = request.søknadsreferanseid,
                søknadId = request.søknadsid,
                enhet = request.behandlerenhet,
                omgjortVedtakVedtakstidspunkt = vedtak.justerVedtakstidspunktVedtak().vedtakstidspunkt,
                søknadstype = request.søknadstype,
                erBisysVedtak = vedtak.kildeapplikasjon == "bisys",
                omgjørVedtaksliste = omgjørVedtakListe,
                innkrevingstype = vedtak.innkrevingstype,
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
        simuler: Boolean = false,
    ): BeregningVedtakResultat {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        behandling.validerKanFatteVedtak()
        return when (behandling.tilType()) {
            TypeBehandling.FORSKUDD -> fatteVedtakForskudd(behandling, request, simuler)

            TypeBehandling.SÆRBIDRAG -> fatteVedtakSærbidrag(behandling, request, simuler)

            TypeBehandling.BIDRAG -> fatteVedtakBidrag(behandling, request, simuler)

            else -> throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Fatte vedtak av behandlingstype ${behandling.tilType()} støttes ikke",
            )
        }
    }

    fun fatteVedtakSærbidrag(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
        simuler: Boolean = false,
    ): BeregningVedtakResultat {
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
        secureLogger.info { "Fatter vedtak for særbidrag behandling $behandlingId med forespørsel $vedtakRequest, simuler=$simuler" }
        val response = fatteVedtak(vedtakRequest, simuler)
        if (!simuler) {
            behandlingService.oppdaterVedtakFattetStatus(
                behandlingId,
                vedtaksid = response.vedtaksid,
                request?.enhet ?: behandling.behandlerEnhet,
            )
            opprettNotat(behandling)
            LOGGER.info {
                "Fattet vedtak for særbidrag behandling $behandlingId med vedtaksid ${response.vedtaksid}"
            }
        }

        return BeregningVedtakResultat(mutableListOf(response.vedtaksid to vedtakRequest), response.vedtaksid)
    }

    fun fatteVedtakForskudd(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
        simuler: Boolean = false,
    ): BeregningVedtakResultat {
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
        secureLogger.info { "Fatter vedtak for behandling ${behandling.id} med forespørsel $fatteVedtakRequest, simulering=$simuler" }
        val response = fatteVedtak(fatteVedtakRequest, simuler)
        if (!simuler) {
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
        }

        return BeregningVedtakResultat(mutableListOf(response.vedtaksid to fatteVedtakRequest), response.vedtaksid)
    }

    fun fatteVedtakBidragOmgjøring(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
        simuler: Boolean = false,
    ): BeregningVedtakResultat {
        if (!UnleashFeatures.FATTE_VEDTAK.isEnabled) {
            ugyldigForespørsel("Kan ikke fatte vedtak for klage")
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run { behandling.validerForBeregningBidrag() }

        val beregning = behandlingTilVedtakMapping.hentBeregningBarnebidrag(behandling)
        beregning.validerManuelAldersjustering(behandling)

        val vedtakRequestDtos: MutableList<Pair<Int, OpprettVedtakRequestDto>> = mutableListOf()

        val requestDelvedtak =
            beregning.copy(
                delvedtak =
                    behandlingTilVedtakMapping.opprettVedtakRequestDelvedtakV2(
                        behandling,
                        beregning.sak,
                        request?.enhet,
                        beregning.beregning,
                        beregning.klagevedtakErEnesteVedtak,
                    ),
            )

        val endeligVedtakOrkestrering =
            if (beregning.klagevedtakErEnesteVedtak) {
                val klagevedtak = requestDelvedtak.delvedtak.find { it.omgjøringsvedtak }!!
                secureLogger.info {
                    "Klagevedtak er eneste vedtak i orkestrering. Fatter bare vedtak for klagevedtak ${klagevedtak.request}"
                }
                val response = fatteVedtak(klagevedtak.request!!, simuler)
                vedtakRequestDtos.add(response.vedtaksid to klagevedtak.request)
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
                        val response = fatteVedtak(opprettRequest, simuler)
                        vedtakRequestDtos.add(response.vedtaksid to opprettRequest)
                        if (!simuler) {
                            behandlingService.oppdaterDelvedtakFattetStatus(
                                behandlingsid = behandling.id!!,
                                fattetAvEnhet = request?.enhet ?: behandling.behandlerEnhet,
                                resultat =
                                    FattetVedtak(
                                        vedtaksid = response.vedtaksid,
                                        vedtakstype = delvedtak.request.type,
                                        referanse = delvedtak.request.unikReferanse ?: "ukjent",
                                    ),
                            )
                        }

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
                val response = fatteVedtak(requestEndeligVedtak, simuler)
                secureLogger.info { "Fattet endelig vedtak med forespørsel $requestEndeligVedtak og vedtaksid ${response.vedtaksid}" }
                vedtakRequestDtos.add(response.vedtaksid to requestEndeligVedtak)
                response to requestEndeligVedtak
            }

        if (behandling.innkrevingstype == Innkrevingstype.UTEN_INNKREVING) {
            fatteInnkrevingsgrunnlagOmgjøring(
                behandling,
                request?.enhet,
                endeligVedtakOrkestrering.first.vedtaksid,
                endeligVedtakOrkestrering.second,
                vedtakRequestDtos,
                simuler,
            )
        }
        if (!simuler) {
            behandlingService.oppdaterVedtakFattetStatus(
                behandling.id!!,
                vedtaksid = endeligVedtakOrkestrering.first.vedtaksid,
                request?.enhet ?: behandling.behandlerEnhet,
            )

            opprettNotat(behandling)

            LOGGER.info {
                "Fattet vedtak for behandling ${behandling.id} med ${
                    behandling.årsak?.let { "årsakstype $it" }
                        ?: "avslagstype ${behandling.avslag}"
                } med vedtaksid ${endeligVedtakOrkestrering.first.vedtaksid}"
            }
        }

        return BeregningVedtakResultat(
            requests = vedtakRequestDtos,
            vedtaksidHovedVedtak = endeligVedtakOrkestrering.first.vedtaksid,
        )
    }

    private fun fatteInnkreving(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
        simuler: Boolean = false,
    ): BeregningVedtakResultat {
        if (!UnleashFeatures.FATTE_VEDTAK.isEnabled) {
            ugyldigForespørsel("Kan ikke fatte vedtak for innkreving")
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        validering.run { behandling.validerForBeregningBidrag() }
        val innkrevingRequest =
            behandlingTilVedtakMapping.byggOpprettVedtakRequestInnkreving(
                behandling,
                request?.enhet,
                request?.skalIndeksreguleres ?: emptyMap(),
            )

        innkrevingRequest.validerGrunnlagsreferanser()
        val responseInnkreving = fatteVedtak(innkrevingRequest, simuler)

        if (!simuler) {
            secureLogger.info {
                "Fattet innkrevingsgrunnlag for vedtak med forespørsel $innkrevingRequest og vedtaksid ${responseInnkreving.vedtaksid}"
            }
            behandlingService.oppdaterVedtakFattetStatus(
                behandlingsid = behandling.id!!,
                vedtaksid = responseInnkreving.vedtaksid,
                fattetAvEnhet = request?.enhet ?: behandling.behandlerEnhet,
                unikreferanse = innkrevingRequest.unikReferanse,
            )
            opprettNotat(behandling)
            LOGGER.info {
                "Fattet vedtak for behandling ${behandling.id} med ${
                    behandling.årsak?.let { "årsakstype $it" }
                        ?: "avslagstype ${behandling.avslag}"
                } med vedtaksid ${responseInnkreving.vedtaksid}"
            }
        }

        return BeregningVedtakResultat(
            requests = mutableListOf(responseInnkreving.vedtaksid to innkrevingRequest),
            vedtaksidHovedVedtak = responseInnkreving.vedtaksid,
        )
    }

    private fun fatteInnkrevingsgrunnlagOmgjøring(
        behandling: Behandling,
        enhet: String?,
        vedtaksidOrkestrering: Int,
        vedtak: OpprettVedtakRequestDto,
        vedtakRequestDtos: MutableList<Pair<Int, OpprettVedtakRequestDto>>,
        simuler: Boolean = false,
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
        val responseInnkreving = fatteVedtak(innkrevingRequest, simuler)
        vedtakRequestDtos.add(responseInnkreving.vedtaksid to innkrevingRequest)
        if (!simuler) {
            secureLogger.info {
                "Fattet innkrevingsgrunnlag for vedtak med forespørsel $innkrevingRequest og vedtaksid ${responseInnkreving.vedtaksid}"
            }
            behandlingService.oppdaterDelvedtakFattetStatus(
                behandlingsid = behandling.id!!,
                fattetAvEnhet = enhet ?: behandling.behandlerEnhet,
                resultat =
                    FattetVedtak(
                        vedtaksid = responseInnkreving.vedtaksid,
                        vedtakstype = innkrevingRequest.type,
                        referanse = innkrevingRequest.unikReferanse ?: "ukjent",
                    ),
            )
        }
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
        simuler: Boolean = false,
    ): BeregningVedtakResultat {
        if (behandling.erKlageEllerOmgjøring) return fatteVedtakBidragOmgjøring(behandling, request, simuler)
        if (behandling.erInnkreving) return fatteInnkreving(behandling, request, simuler)
        if (!behandling.kanFatteVedtak()) {
            ugyldigForespørsel("Kan ikke fatte vedtak for behandling ${behandling.id}")
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())

        val vedtakRequester = opprettFatteVedtakRequestForBidrag(behandling, request)
        val vedtakRequestDtos: MutableList<Pair<Int, OpprettVedtakRequestDto>> = mutableListOf()

        val vedtakResponser =
            vedtakRequester.associate { vedtakRequest ->
                vedtakRequest.validerGrunnlagsreferanser()
                secureLogger.info { "Fatter vedtak for behandling ${behandling.id} med forespørsel $vedtakRequest" }
                val søknadsider =
                    vedtakRequest.behandlingsreferanseListe
                        .filter {
                            it.kilde == BehandlingsrefKilde.BISYS_SØKNAD
                        }.map { it.referanse.toLong() }
                val response = fatteVedtak(vedtakRequest, simuler)
                vedtakRequestDtos.add(response.vedtaksid to vedtakRequest)
                if (!simuler) {
                    behandlingService.oppdaterDelvedtakFattetStatus(
                        behandlingsid = behandling.id!!,
                        fattetAvEnhet = request?.enhet ?: behandling.behandlerEnhet,
                        resultat =
                            FattetVedtak(
                                vedtaksid = response.vedtaksid,
                                vedtakstype = vedtakRequest.type,
                                referanse = vedtakRequest.unikReferanse ?: "ukjent",
                            ),
                    )
                    val aldersjusteringBeregnet =
                        vedtakRequest.type == Vedtakstype.ALDERSJUSTERING &&
                            vedtakRequest.stønadsendringListe.all { it.beslutning == Beslutningstype.ENDRING }
                    if (aldersjusteringBeregnet) {
                        forsendelseService.opprettForsendelseForAldersjustering(behandling)
                    } else if (vedtakRequest.type != Vedtakstype.ALDERSJUSTERING) {
                        opprettNotat(behandling)
                    }
                }

                if (vedtakRequest.type == Vedtakstype.ALDERSJUSTERING && !simuler) {
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

                søknadsider to response.vedtaksid
            }

        // Hent hoved vedtaksiden, dette skal fjernes etterhvert når det migreres over til ny struktur
        val vedtaksid =
            vedtakResponser.filterKeys { it.contains(behandling.soknadsid!!) }.values.firstOrNull() ?: vedtakResponser.values.first()
        if (!simuler) {
            behandlingService.oppdaterVedtakFattetStatus(
                behandling.id!!,
                vedtaksid = vedtakResponser.filterKeys { it.contains(behandling.soknadsid!!) }.values.first(),
                request?.enhet ?: behandling.behandlerEnhet,
            )
        }

        return BeregningVedtakResultat(vedtakRequestDtos, vedtaksid)
    }

    fun opprettFatteVedtakRequestForBidrag(
        behandling: Behandling,
        request: FatteVedtakRequestDto?,
    ): List<OpprettVedtakRequestDto> {
        validering.run { behandling.validerForBeregningBidrag() }

        return behandlingTilVedtakMapping
            .run {
                if (behandling.erAvslagForAlle) {
                    listOf(behandling.byggOpprettVedtakRequestAvslagForBidrag(request?.enhet))
                } else {
                    behandling.byggOpprettVedtakRequestBidragAlle(request?.enhet)
                }
            }.map {
                val erAvvisning = it.stønadsendringListe.all { it.beslutning == Beslutningstype.AVVIST }
                it.copy(
                    innkrevingUtsattTilDato =
                        if (behandling.skalInnkrevingKunneUtsettes() && !erAvvisning) {
                            request?.innkrevingUtsattAntallDager?.let {
                                LocalDate.now().plusDays(it)
                            }
                        } else {
                            null
                        },
                )
            }
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

    private fun fatteVedtak(
        request: OpprettVedtakRequestDto,
        simuler: Boolean = false,
    ): OpprettVedtakResponseDto =
        if (simuler) {
            OpprettVedtakResponseDto(opprettSimulerVedtaksid(), emptyList())
        } else {
            vedtakConsumer!!.fatteVedtak(request)
//            vedtakLocalConsumer!!.fatteVedtak(request)
        }

    private fun opprettSimulerVedtaksid() = Math.random().times(100000).toInt()
}
