package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.`404`
import no.nav.bidrag.behandling.dto.opplysninger.AddOpplysningerRequest
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto
import no.nav.bidrag.behandling.service.OpplysningerService
import no.nav.bidrag.behandling.transformers.toDate
import no.nav.bidrag.behandling.transformers.toDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class OpplysningerController(val opplysningerService: OpplysningerService) {

    @Suppress("unused")
    @PostMapping("/behandling/{behandlingId}/opplysninger")
    @Operation(
        description = "Legger til nye opplysninger til behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret opplysninger"),
            ApiResponse(responseCode = "404", description = "Fant ikke opplysninger"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun addOpplysningerData(@RequestBody(required = true) addOpplysningerRequest: AddOpplysningerRequest): OpplysningerDto {
        val (behandlingId, aktiv, opplysningerType, data, hentetDato) = addOpplysningerRequest
        return opplysningerService.opprett(behandlingId, aktiv, opplysningerType, data, hentetDato.toDate())
            .toDto()
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}/opplysninger/aktiv")
    @Operation(
        description = "Henter aktive opplysninger til behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun hentAktiv(@PathVariable behandlingId: Long): OpplysningerDto {
        return opplysningerService.hentSistAktiv(behandlingId).orElseThrow { `404`(behandlingId) }.toDto()
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/opplysninger/{id}/aktiv")
    @Operation(
        description = "Sette opplysninger som aktive",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret endringer"),
            ApiResponse(responseCode = "404", description = "Fant ikke opplysninger"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun setAktiv(@PathVariable behandlingId: Long, @PathVariable id: Long) {
        opplysningerService.settAktiv(id, behandlingId)
    }
}
