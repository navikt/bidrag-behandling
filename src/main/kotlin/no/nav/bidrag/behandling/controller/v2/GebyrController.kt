package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrResponsDto
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterManueltGebyrDto
import no.nav.bidrag.behandling.service.GebyrService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class GebyrController(
    private val gebyrService: GebyrService,
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
    ): OppdaterGebyrResponsDto = gebyrService.oppdaterManueltGebyr(behandlingsid, request)
}
