package no.nav.bidrag.behandling.controller.deprecated

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.deprecated.dto.OpplysningerDto
import no.nav.bidrag.behandling.deprecated.dto.tilOpplysningerDto
import no.nav.bidrag.behandling.dto.opplysninger.AddOpplysningerRequest
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.toDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Deprecated("Bruk v1")
@DeprecatedBehandlingRestController
class DeprecatedGrunnlagController(val grunnlagService: GrunnlagService) {
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
    fun addOpplysningerData(
        @PathVariable behandlingId: Long,
        @RequestBody(required = true) addOpplysningerRequest: AddOpplysningerRequest,
    ): OpplysningerDto {
        val (_, _, opplysningerType, data, hentetDato) = addOpplysningerRequest
        return grunnlagService.opprett(
            behandlingId,
            opplysningerType,
            data,
            hentetDato.atStartOfDay(),
        )
            .toDto().tilOpplysningerDto()
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}/opplysninger/{grunnlagstype}/aktiv")
    @Operation(
        description = "Hente aktive opplysninger til behandling",
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
    ): OpplysningerDto {
        return grunnlagService.hentSistAktiv(
            behandlingId,
            grunnlagstype,
        )?.toDto()?.tilOpplysningerDto() ?: behandlingNotFoundException(behandlingId)
    }
}
