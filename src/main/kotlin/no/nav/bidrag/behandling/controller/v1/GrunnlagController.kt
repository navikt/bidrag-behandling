package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.dto.opplysninger.GrunnlagDto
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.toDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@BehandlingRestControllerV1
class GrunnlagController(val grunnlagService: GrunnlagService) {

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}/grunnlag/{grunnlagstype}/aktiv")
    @Operation(
        description = "Hente grunnlag til behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun hentAktiv(
        @PathVariable behandlingId: Long,
        @PathVariable grunnlagstype: Grunnlagstype,
    ): GrunnlagDto {
        return grunnlagService.hentSistAktiv(
            behandlingId,
            grunnlagstype,
        )?.toDto() ?: behandlingNotFoundException(behandlingId)
    }
}
