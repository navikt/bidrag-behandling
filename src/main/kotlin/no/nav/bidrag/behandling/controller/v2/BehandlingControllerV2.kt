package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.tilBehandlingDto
import no.nav.bidrag.domene.ident.Personident
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class BehandlingControllerV2(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingsid}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBehandlingV2(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequestV2,
    ): BehandlingDtoV2 {
        val behandlingFørOppdatering = behandlingService.hentBehandlingById(behandlingsid)

        behandlingFørOppdatering.getBidragsmottaker()?.ident?.let { Personident(it) }
            ?: throw IllegalArgumentException("Behandling mangler BM!")

        return behandlingService.oppdaterBehandling(behandlingsid, request)
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingsid}")
    @Operation(
        description = "Hente en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun hentBehandlingV2(
        @PathVariable behandlingsid: Long,
    ): BehandlingDto {
        val respons = behandlingService.henteBehandling(behandlingsid).tilBehandlingDto()
        return respons
    }
}
