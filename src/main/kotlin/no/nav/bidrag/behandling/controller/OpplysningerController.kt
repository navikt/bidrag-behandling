package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.controller.BehandlingRestControllerV1
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.deprecated.dto.AddOpplysningerRequest
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataDto
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.toDto
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Deprecated("Unødvendig å kalle ettersom backend foretar innhenting av grunnlag")
@BehandlingRestControllerV1
class OpplysningerController(val grunnlagService: GrunnlagService) {
    @Suppress("unused")
    @PostMapping("/behandling/{behandlingId}/opplysninger")
    @Operation(
        description = "Legge til nye opplysninger til behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret opplysninger"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun leggTilOpplysninger(
        @PathVariable behandlingId: Long,
        @RequestBody(required = true) leggTilGrunnlagRequest: AddOpplysningerRequest,
    ): GrunnlagsdataDto {
        val (_, _, opplysningerType, data, hentetDato) = leggTilGrunnlagRequest
        return grunnlagService.opprett(
            behandlingId,
            Grunnlagsdatatype.valueOf(opplysningerType.name),
            data,
            hentetDato.atStartOfDay(),
        ).toDto()
    }
}
