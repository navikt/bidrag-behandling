package no.nav.bidrag.behandling.controller.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.service.VedtakService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV1
class VedtakController(
    private val vedtakService: VedtakService,
) {
    @Suppress("unused")
    @PostMapping("/behandling/{behandlingsid}/vedtak")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun fatteVedtak(
        @PathVariable behandlingsid: Long,
    ): Int {
        LOGGER.info { "Beregner forskudd for behandling med id $behandlingsid" }

        val respons = vedtakService.fatteVedtak(behandlingsid)
        return respons
    }
}
