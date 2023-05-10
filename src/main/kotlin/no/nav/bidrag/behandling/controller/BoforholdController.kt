package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.boforhold.UpdateBoforholdRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class BoforholdController(private val behandlingService: BehandlingService) {

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/boforhold")
    @Operation(
        description = "Oppdaterer en behandling boforhold data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun updateBoforhold(
        @PathVariable behandlingId: Long,
        @RequestBody updateBoforholdRequest: UpdateBoforholdRequest,
    ) {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        behandlingService.updateBoforhold(
            behandlingId,
            updateBoforholdRequest.behandlingBarn.toDomain(behandling),
            updateBoforholdRequest.sivilstand.toSivilstandDomain(behandling),
            updateBoforholdRequest.boforholdBegrunnelseKunINotat,
            updateBoforholdRequest.boforholdBegrunnelseMedIVedtakNotat,
        )
    }
}
