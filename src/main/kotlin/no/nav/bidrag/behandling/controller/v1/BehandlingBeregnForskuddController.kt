package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.dto.beregning.Forskuddsberegningrespons
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.ForskuddService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.client.HttpClientErrorException

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV1
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val forskuddService: ForskuddService,
) {
    @Suppress("unused")
    @PostMapping("/behandling/{behandlingsid}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnForskudd(
        @PathVariable behandlingsid: Long,
    ): Forskuddsberegningrespons {
        LOGGER.info("Beregner forskudd for behandling med id $behandlingsid")
        val behandling = behandlingService.hentBehandlingById(behandlingsid)

        if (behandling.id == null) throw HttpClientErrorException(HttpStatus.NOT_FOUND)

        return forskuddService.beregneForskudd(behandling.id)
    }
}
