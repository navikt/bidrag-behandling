package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.boforhold.BoforholdResponse
import no.nav.bidrag.behandling.dto.boforhold.OppdatereBoforholdRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toHusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV1
class BoforholdController(private val behandlingService: BehandlingService) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/boforhold")
    @Operation(
        description = "Oppdatere boforhold data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBoforhold(
        @PathVariable behandlingId: Long,
        @RequestBody oppdatereBoforholdRequest: OppdatereBoforholdRequest,
    ): BoforholdResponse {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        behandlingService.updateBoforhold(
            behandlingId,
            oppdatereBoforholdRequest.husstandsbarn.toDomain(behandling),
            oppdatereBoforholdRequest.sivilstand.toSivilstandDomain(behandling),
            oppdatereBoforholdRequest.boforholdsbegrunnelseKunINotat,
            oppdatereBoforholdRequest.boforholdsbegrunnelseIVedtakOgNotat,
        )

        val updatedBehandling = behandlingService.hentBehandlingById(behandlingId)

        return BoforholdResponse(
            updatedBehandling.husstandsbarn.toHusstandsBarnDto(),
            updatedBehandling.sivilstand.toSivilstandDto(),
            updatedBehandling.boforholdsbegrunnelseIVedtakOgNotat,
            updatedBehandling.boforholdsbegrunnelseKunINotat,
        )
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}/boforhold")
    @Operation(
        description = "Hente boforhold data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentBoforhold(
        @PathVariable behandlingId: Long,
    ): BoforholdResponse {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        return BoforholdResponse(
            behandling.husstandsbarn.toHusstandsBarnDto(),
            behandling.sivilstand.toSivilstandDto(),
            behandling.boforholdsbegrunnelseIVedtakOgNotat,
            behandling.boforholdsbegrunnelseKunINotat,
        )
    }
}
