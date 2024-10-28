package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.Ressurstype
import no.nav.bidrag.behandling.dto.v1.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettKategoriRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.SjekkRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.tilType
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektResponse
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.requestManglerDataException
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BoforholdService
import no.nav.bidrag.behandling.service.InntektService
import no.nav.bidrag.behandling.service.UtgiftService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.ident.Personident
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class BehandlingControllerV2(
    private val vedtakService: VedtakService,
    private val behandlingService: BehandlingService,
    private val boforholdService: BoforholdService,
    private val inntektService: InntektService,
    private val utgiftService: UtgiftService,
    private val validerBehandlingService: ValiderBehandlingService,
    private val dtomapper: Dtomapper,
) {
    @Suppress("unused")
    @GetMapping("/behandling/vedtak/{vedtakId}")
    @Operation(
        description = "Hent vedtak som behandling for lesemodus. Vedtak vil bli konvertert til behandling uten lagring",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Vedtak i form av behandling"),
            ApiResponse(
                responseCode = "404",
                description = "Fant ikke vedtak med oppgitt vedtaksid",
            ),
        ],
    )
    fun vedtakLesemodus(
        @PathVariable vedtakId: Long,
        @RequestParam("inkluderHistoriskeInntekter") inkluderHistoriskeInntekter: Boolean = false,
    ): BehandlingDtoV2 {
        val resultat =
            vedtakService.konverterVedtakTilBehandlingForLesemodus(vedtakId)
                ?: throw RuntimeException("Fant ikke vedtak for vedtakid $vedtakId")
        return dtomapper.tilDto(resultat, inkluderHistoriskeInntekter)
    }

    @PutMapping("/behandling/{behandlingsid}/inntekt")
    @Operation(
        description = "Oppdatere inntekt for behandling. Returnerer inntekt som ble endret, opprettet, eller slettet.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Forespørsel oppdatert uten feil",
            ),
            ApiResponse(responseCode = "400", description = "Feil opplysninger oppgitt"),
            ApiResponse(
                responseCode = "401",
                description = "Sikkerhetstoken mangler, er utløpt, eller av andre årsaker ugyldig",
            ),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun oppdatereInntekt(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereInntektRequest,
    ): OppdatereInntektResponse {
        log.info { "Oppdatere inntekter for behandling $behandlingsid" }
        return inntektService.oppdatereInntektManuelt(behandlingsid, request)
    }

    @PutMapping("/behandling/{behandlingsid}/utgift")
    @Operation(
        description = "Oppdatere utgift for behandling. Returnerer oppdatert behandling detaljer.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    fun oppdatereUtgift(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereUtgiftRequest,
    ): OppdatereUtgiftResponse {
        log.info { "Oppdaterer utgift for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer utgift for behandling $behandlingsid med forespørsel $request" }

        return utgiftService.oppdatereUtgift(behandlingsid, request)
    }

    @PutMapping("/behandling/{behandlingsid}/virkningstidspunkt")
    @Operation(
        description = "Oppdatere virkningstidspunkt for behandling. Returnerer oppdatert virkningstidspunkt",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Forespørsel oppdatert uten feil",
            ),
            ApiResponse(responseCode = "400", description = "Feil opplysninger oppgitt"),
            ApiResponse(
                responseCode = "401",
                description = "Sikkerhetstoken mangler, er utløpt, eller av andre årsaker ugyldig",
            ),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun oppdatereVirkningstidspunktV2(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereVirkningstidspunkt,
    ): BehandlingDtoV2 {
        log.info { "Oppdaterer virkningstidspunkt for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer virkningstidspunkt for behandling $behandlingsid med forespørsel $request" }

        val behandling = behandlingService.oppdatereVirkningstidspunkt(behandlingsid, request)

        return dtomapper.tilDto(behandling)
    }

    @PutMapping("/behandling/{behandlingsid}/boforhold")
    @Operation(
        description = "Oppdatere boforhold for behandling. Returnerer boforhold som ble endret, opprettet, eller slettet.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Forespørsel oppdatert uten feil",
            ),
            ApiResponse(responseCode = "400", description = "Feil opplysninger oppgitt"),
            ApiResponse(
                responseCode = "401",
                description = "Sikkerhetstoken mangler, er utløpt, eller av andre årsaker ugyldig",
            ),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun oppdatereBoforhold(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereBoforholdRequestV2,
    ): OppdatereBoforholdResponse {
        log.info { "Oppdaterer boforhold for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer boforhold for behandling $behandlingsid med forespørsel $request" }

        request.oppdatereHusstandsmedlem?.let {
            return boforholdService.oppdatereHusstandsmedlemManuelt(behandlingsid, it)
        }

        request.oppdaterePeriodeMedAndreVoksneIHusstand?.let {
            return boforholdService.oppdatereAndreVoksneIHusstandenManuelt(behandlingsid, it)
        }

        request.oppdatereSivilstand?.let {
            return boforholdService.oppdatereSivilstandManuelt(behandlingsid, it)!!
        }

        request.henteOppdatereBegrunnelse?.let {
            return boforholdService.oppdatereNotat(behandlingsid, it)
        }

        requestManglerDataException(behandlingsid, Ressurstype.BOFORHOLD)
    }

    @Suppress("unused")
    @GetMapping("/behandling/detaljer/soknad/{søknadsid}")
    @Operation(
        description = "Hente behandling detaljer for søknadsid bruk i Bisys",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Fant behandling for søknadsid"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun henteBehandlingDetaljerForSøknadsid(
        @PathVariable søknadsid: Long,
    ): BehandlingDetaljerDtoV2 = behandlingService.henteBehandlingDetaljerForSøknadsid(søknadsid)

    @Suppress("unused")
    @GetMapping("/behandling/detaljer/{behandlingsid}")
    @Operation(
        description = "Hente behandling detaljer for bruk i Bisys",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun henteBehandlingDetaljer(
        @PathVariable behandlingsid: Long,
    ): BehandlingDetaljerDtoV2 = behandlingService.henteBehandlingDetaljer(behandlingsid)

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingsid}")
    @Operation(
        description = "Hente en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun henteBehandlingV2(
        @PathVariable behandlingsid: Long,
        @RequestParam("inkluderHistoriskeInntekter") inkluderHistoriskeInntekter: Boolean = false,
    ): BehandlingDtoV2 = behandlingService.henteBehandling(behandlingsid, inkluderHistoriskeInntekter)

    @Suppress("unused")
    @DeleteMapping("/behandling/{behandlingsid}")
    @Operation(
        description = "Logisk slett en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Slettet behandling"),
            ApiResponse(responseCode = "400", description = "Kan ikke slette behandling"),
        ],
    )
    fun slettBehandling(
        @PathVariable behandlingsid: Long,
    ) = behandlingService.slettBehandling(behandlingsid)

    @Suppress("unused")
    @PostMapping("/behandling/vedtak/{refVedtaksId}")
    @Operation(
        description = "Opprett behandling fra vedtak. Brukes når det skal opprettes klagebehanling fra vedtak.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Opprettet behandling fra vedtak",
            ),
        ],
    )
    fun opprettBehandlingForVedtak(
        @Valid
        @RequestBody(required = true)
        opprettBehandling: OpprettBehandlingFraVedtakRequest,
        @PathVariable refVedtaksId: Long,
    ): OpprettBehandlingResponse = vedtakService.opprettBehandlingFraVedtak(opprettBehandling, refVedtaksId)

    @Suppress("unused")
    @PostMapping("/behandling")
    @Operation(
        description = "Opprett ny behandling",
        summary = """
            Oppretter ny behandlding. 
            Hvis det finnes en behandling fra før med samme søknadsid i forespørsel 
            vil id for den behandlingen returneres istedenfor at det opprettes ny
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Opprettet ny behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun oppretteBehandling(
        @Valid
        @RequestBody(required = true)
        opprettBehandling: OpprettBehandlingRequest,
    ): OpprettBehandlingResponse =
        behandlingService.opprettBehandling(
            // TODO: Dette er midlertidlig ved testing av særbidrag i prod hvor kategori ikke er satt. Skal fjernes før vi skrur på for alle
            opprettBehandling.copy(
                kategori =
                    if (opprettBehandling.tilType() == TypeBehandling.SÆRBIDRAG && opprettBehandling.kategori == null) {
                        OpprettKategoriRequestDto(kategori = Særbidragskategori.ANNET.name, "Testing av særbidrag")
                    } else {
                        opprettBehandling.kategori
                    },
            ),
        )

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/roller")
    @Operation(
        description = "Oppdater roller i behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterRoller(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterRollerRequest,
    ) = behandlingService.oppdaterRoller(behandlingId, request.roller)

    @PutMapping("/behandling/{behandlingsid}/aktivere")
    @Operation(
        description = "Aktivere grunnlag for behandling. Returnerer grunnlag som ble aktivert.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Forespørsel oppdatert uten feil",
            ),
            ApiResponse(responseCode = "400", description = "Feil opplysninger oppgitt"),
            ApiResponse(
                responseCode = "401",
                description = "Sikkerhetstoken mangler, er utløpt, eller av andre årsaker ugyldig",
            ),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun aktivereGrunnlag(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: AktivereGrunnlagRequestV2,
    ): AktivereGrunnlagResponseV2 = behandlingService.aktivereGrunnlag(behandlingsid, request)

    @PostMapping("/behandling/kanBehandles")
    @Operation(
        description = "Sjekk om behandling kan behandles i ny løsning",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    fun kanBehandlesINyLøsning(
        @Valid @RequestBody(required = true) request: KanBehandlesINyLøsningRequest,
    ): ResponseEntity<Void> {
        validerBehandlingService.validerKanBehandlesINyLøsning(request)
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/behandling/kanBehandles/{behandlingsid}")
    @Operation(
        description = "Sjekk om behandling kan behandles i ny løsning",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    fun kanBehandlingBehandlesINyLøsning(
        @PathVariable behandlingsid: Long,
    ): ResponseEntity<Void> {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        validerBehandlingService.validerKanBehandlesINyLøsning(
            KanBehandlesINyLøsningRequest(
                engangsbeløpstype = behandling.engangsbeloptype,
                stønadstype = behandling.stonadstype,
                saksnummer = behandling.saksnummer,
                roller =
                    behandling.roller.map {
                        SjekkRolleDto(
                            rolletype = it.rolletype,
                            ident = Personident(it.ident!!),
                        )
                    },
            ),
        )
        return ResponseEntity.accepted().build()
    }
}
