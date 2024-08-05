package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@BehandlingRestControllerV2
class VedtakSimuleringController(
    private val vedtakService: VedtakService,
) {
    @Suppress("unused")
    @GetMapping("/simulervedtak/{behandlingId}")
    @Operation(
        description = "Simuler vedtakstruktur for en behandling. Brukes for testing av grunnlagsstruktur uten å faktisk fatte vedtak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun behandlingTilVedtak(
        @PathVariable behandlingId: Long,
    ): VedtakDto = vedtakService.behandlingTilVedtakDto(behandlingId)
}
