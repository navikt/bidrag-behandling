package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.virkningstidspunkt.UpdateVirkningsTidspunktRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toDate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class VirkningsTidspunktController(private val behandlingService: BehandlingService) {

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/virkningstidspunkt")
    @Operation(
        description = "Oppdaterer en behandling virkningstidspunkt data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun updateVirkningsTidspunkt(
        @PathVariable behandlingId: Long,
        @RequestBody updateVirkningsTidspunktRequest: UpdateVirkningsTidspunktRequest,
    ) {
        behandlingService.updateVirkningsTidspunkt(
            behandlingId,
            updateVirkningsTidspunktRequest.aarsak,
            updateVirkningsTidspunktRequest.avslag,
            updateVirkningsTidspunktRequest.virkningsDato?.toDate(),
            updateVirkningsTidspunktRequest.virkningsTidspunktBegrunnelseKunINotat,
            updateVirkningsTidspunktRequest.virkningsTidspunktBegrunnelseMedIVedtakNotat,
        )
    }
}
