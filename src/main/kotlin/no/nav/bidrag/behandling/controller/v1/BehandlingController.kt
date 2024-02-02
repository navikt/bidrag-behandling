package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.tilBehandlingDto
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.behandling.transformers.tilOppdaterBehandlingRequestV2
import no.nav.bidrag.domene.ident.Personident
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV1
class BehandlingController(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
) {
    @Suppress("unused")
    @PostMapping("/behandling")
    @Operation(
        description = "Legge til en ny behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret behandling"),
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
    @PutMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBehandling(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequest,
    ): BehandlingDto {
        val behandlingFørOppdatering = behandlingService.hentBehandlingById(behandlingId)
        val personidentBm =
            behandlingFørOppdatering.bidragsmottaker?.ident?.let { Personident(it) }
                ?: throw IllegalArgumentException("Behandling mangler BM!")

        val behandling =
            behandlingService.oppdaterBehandling(
                behandlingId,
                request.tilOppdaterBehandlingRequestV2(personidentBm),
            )

        return behandling.tilBehandlingDto()
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/roller")
    @Operation(
        description = "Sync fra behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterRoller(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterRollerRequest,
    ) = behandlingService.syncRoller(behandlingId, request.roller)

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}")
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
    fun hentBehandling(
        @PathVariable behandlingId: Long,
    ): BehandlingDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val opplysninger = grunnlagService.hentAlleSistAktiv(behandlingId)
        return behandling.tilBehandlingDtoV2(opplysninger).tilBehandlingDto()
    }
}
