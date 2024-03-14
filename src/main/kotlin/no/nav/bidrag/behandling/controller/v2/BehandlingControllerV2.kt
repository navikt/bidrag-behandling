package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.domene.ident.Personident
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class BehandlingControllerV2(
    private val vedtakService: VedtakService,
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
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
    ): BehandlingDtoV2 {
        val resultat =
            vedtakService.konverterVedtakTilBehandlingForLesemodus(vedtakId)
                ?: throw RuntimeException("Fant ikke vedtak for vedtakid $vedtakId")
        return resultat.tilBehandlingDtoV2(resultat.grunnlagListe, emptySet())
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingsid}")
    @Operation(
        description = "Oppdatere behandling",
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
            ApiResponse(responseCode = "404", description = "Fant ikke forespørsel"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun oppdatereBehandlingV2(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequestV2,
    ): ResponseEntity<BehandlingDtoV2> {
        val behandlingFørOppdatering = behandlingService.hentBehandlingById(behandlingsid)

        behandlingFørOppdatering.bidragsmottaker?.ident?.let { Personident(it) }
            ?: throw IllegalArgumentException("Behandling mangler BM!")

        behandlingService.oppdaterBehandling(behandlingsid, request)

        val behandling = behandlingService.hentBehandlingById(behandlingsid)

        return ResponseEntity(
            behandling.tilBehandlingDtoV2(
                grunnlagService.henteGjeldendeAktiveGrunnlagsdata(behandling),
                grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(
                    behandlingsid,
                    behandling.roller,
                ),
            ),
            HttpStatus.CREATED,
        )
    }

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
    ): BehandlingDtoV2 {
        return behandlingService.henteBehandling(behandlingsid)
    }

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
    ): OpprettBehandlingResponse = behandlingService.opprettBehandling(opprettBehandling)

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
}
