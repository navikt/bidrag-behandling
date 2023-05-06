package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.inntekt.UpdateBehandlingInntekterRequest
import no.nav.bidrag.behandling.dto.inntekt.UpdateBehandlingInntekterResponse
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toInntektDto
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class InntekterController(private val behandlingService: BehandlingService) {

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/inntekter")
    @Operation(
        description = "Oppdaterer en behandling inntekter",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret behandling inntekter"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun oppdaterInntekter(
        @PathVariable behandlingId: Long,
        @RequestBody request: UpdateBehandlingInntekterRequest,
    ): UpdateBehandlingInntekterResponse {
        return UpdateBehandlingInntekterResponse(behandlingService.oppdaterInntekter(behandlingId, request.inntekter).toInntektDto())
    }
}
