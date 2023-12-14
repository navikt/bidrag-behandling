package no.nav.bidrag.behandling.controller.deprecated

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.deprecated.dto.BoforholdResponse
import no.nav.bidrag.behandling.deprecated.dto.OppdatereBoforholdRequest
import no.nav.bidrag.behandling.deprecated.dto.toHustandsbarndDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toHusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@Deprecated("Bruk endepunktene i BoforholdController /api/v1/boforhold")
@DeprecatedBehandlingRestController
class DeprecatedBoforholdController(private val behandlingService: BehandlingService) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/boforhold")
    @Operation(
        description = "Oppdatere boforhold data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBoforhold(
        @PathVariable behandlingId: Long,
        @RequestBody updateBoforholdRequest: OppdatereBoforholdRequest,
    ): BoforholdResponse {
        behandlingService.updateBoforhold(
            behandlingId,
            updateBoforholdRequest.husstandsBarn.toHustandsbarndDto(),
            updateBoforholdRequest.sivilstand,
            updateBoforholdRequest.boforholdsbegrunnelseKunINotat,
            updateBoforholdRequest.boforholdsbegrunnelseIVedtakOgNotat,
        )

        val updatedBehandling = behandlingService.hentBehandlingById(behandlingId)

        return BoforholdResponse(
            updatedBehandling.husstandsbarn.toHusstandsBarnDto(updatedBehandling),
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
            behandling.husstandsbarn.toHusstandsBarnDto(behandling),
            behandling.sivilstand.toSivilstandDto(),
            behandling.boforholdsbegrunnelseIVedtakOgNotat,
            behandling.boforholdsbegrunnelseKunINotat,
        )
    }
}
