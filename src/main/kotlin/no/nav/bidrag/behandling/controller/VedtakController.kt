package no.nav.bidrag.behandling.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.controller.v2.BehandlingRestControllerV2
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.service.VedtakService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV2
class VedtakController(
    private val vedtakService: VedtakService,
) {
    @Suppress("unused")
    @PostMapping("/behandling/fattevedtak/{behandlingsid}")
    @Operation(
        description = "Fatte vedtak for behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun fatteVedtak(
        @PathVariable behandlingsid: Long,
        @RequestBody(required = false) request: FatteVedtakRequestDto? = null,
    ): Int {
        LOGGER.info { "Fatter vedtak for $behandlingsid" }
        return vedtakService.fatteVedtak(behandlingsid, request)
    }
}
