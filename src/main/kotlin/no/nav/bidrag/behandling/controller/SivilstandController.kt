package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.sivilstand.UpdateBehandlingSivilstandRequest
import no.nav.bidrag.behandling.dto.sivilstand.UpdateBehandlingSivilstandResponse
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class SivilstandController(private val behandlingService: BehandlingService) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/sivilstand")
    @Operation(
        description = "Oppdaterer en behandling inntekter",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret behandling sivilstand"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun oppdaterSivilstand(
        @PathVariable behandlingId: Long,
        @RequestBody request: UpdateBehandlingSivilstandRequest,
    ): UpdateBehandlingSivilstandResponse {
        return UpdateBehandlingSivilstandResponse(behandlingService.oppdaterSivilstand(behandlingId, request.sivilstand).toSivilstandDto())
    }
}
