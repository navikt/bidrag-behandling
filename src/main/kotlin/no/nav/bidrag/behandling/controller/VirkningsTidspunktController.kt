package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.virkningstidspunkt.UpdateVirkningsTidspunktRequest
import no.nav.bidrag.behandling.dto.virkningstidspunkt.VirkningsTidspunktResponse
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toDate
import no.nav.bidrag.behandling.transformers.toLocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class VirkningsTidspunktController(private val behandlingService: BehandlingService) {

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/virkningstidspunkt")
    @Operation(
        description = "Oppdatere virkningstidspunkt data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun oppdaterVirkningsTidspunkt(
        @PathVariable behandlingId: Long,
        @RequestBody updateVirkningsTidspunktRequest: UpdateVirkningsTidspunktRequest,
    ): VirkningsTidspunktResponse {
        behandlingService.updateVirkningsTidspunkt(
            behandlingId,
            updateVirkningsTidspunktRequest.aarsak,
            updateVirkningsTidspunktRequest.virkningsDato?.toDate(),
            updateVirkningsTidspunktRequest.virkningsTidspunktBegrunnelseKunINotat,
            updateVirkningsTidspunktRequest.virkningsTidspunktBegrunnelseMedIVedtakNotat,
        )

        val behandling = behandlingService.hentBehandlingById(behandlingId)

        return VirkningsTidspunktResponse(
            behandling.virkningsTidspunktBegrunnelseMedIVedtakNotat,
            behandling.virkningsTidspunktBegrunnelseKunINotat,
            behandling.aarsak,
            behandling.virkningsDato?.toLocalDate(),
        )
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}/virkningstidspunkt")
    @Operation(
        description = "Hente virkningstidspunkt data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun hentVirkningsTidspunkt(
        @PathVariable behandlingId: Long,
    ): VirkningsTidspunktResponse {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return VirkningsTidspunktResponse(
            behandling.virkningsTidspunktBegrunnelseMedIVedtakNotat,
            behandling.virkningsTidspunktBegrunnelseKunINotat,
            behandling.aarsak,
            behandling.virkningsDato?.toLocalDate(),
        )
    }
}
