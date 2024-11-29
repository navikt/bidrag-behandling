package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrResponsDto
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterManueltGebyrDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GebyrService
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.gebyr.tilDto
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class GebyrController(
    private val gebyrService: GebyrService,
    private val behandlingService: BehandlingService,
) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingsid}/gebyr")
    @Operation(
        description =
            "Oppdater manuelt overstyr gebyr for en behandling.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterManueltOverstyrtGebyr(
        @PathVariable behandlingsid: Long,
        @Valid
        @RequestBody(required = true)
        request: OppdaterManueltGebyrDto,
    ): OppdaterGebyrResponsDto {
        gebyrService.oppdaterManueltOverstyrtGebyr(behandlingService.hentBehandlingById(behandlingsid), request)
        return tilRespons(behandlingsid, request.rolleId)
    }

    private fun tilRespons(
        behandlingsId: Long,
        rolleId: Long,
    ): OppdaterGebyrResponsDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsId)
        return behandling.roller.find { it.id == rolleId }!!.let { rolle ->
            OppdaterGebyrResponsDto(
                rolle.tilDto(),
                rolle.manueltOverstyrtGebyr?.tilDto(),
            )
        }
    }
}
