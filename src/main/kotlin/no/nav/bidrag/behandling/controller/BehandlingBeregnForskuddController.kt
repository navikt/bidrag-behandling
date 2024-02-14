package no.nav.bidrag.behandling.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV1
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
) {
    @Suppress("unused")
    @PostMapping("/behandling/{behandlingsid}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnForskudd(
        @PathVariable behandlingsid: Long,
    ): ResultatForskuddsberegning {
        LOGGER.info { "Beregner forskudd for behandling med id $behandlingsid" }
        val behandling = behandlingService.hentBehandlingById(behandlingsid)

        return beregningService.beregneForskudd(behandling.id!!)
    }
}
