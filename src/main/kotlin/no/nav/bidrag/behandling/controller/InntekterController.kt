package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.inntekt.InntekterResponse
import no.nav.bidrag.behandling.dto.inntekt.UpdateInntekterRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toBarnetilleggDomain
import no.nav.bidrag.behandling.transformers.toBarnetilleggDto
import no.nav.bidrag.behandling.transformers.toInntektDomain
import no.nav.bidrag.behandling.transformers.toInntektDto
import no.nav.bidrag.behandling.transformers.toUtvidetbarnetrygdDomain
import no.nav.bidrag.behandling.transformers.toUtvidetbarnetrygdDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class InntekterController(private val behandlingService: BehandlingService) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/inntekter")
    @Operation(
        description = "Oppdatere inntekter data",
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
    fun oppdaterInntekter(
        @PathVariable behandlingId: Long,
        @RequestBody request: UpdateInntekterRequest,
    ): InntekterResponse {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        behandlingService.oppdaterInntekter(
            behandlingId,
            request.inntekter.toInntektDomain(behandling),
            request.barnetillegg.toBarnetilleggDomain(behandling),
            request.utvidetbarnetrygd.toUtvidetbarnetrygdDomain(behandling),
            request.inntektBegrunnelseMedIVedtakNotat,
            request.inntektBegrunnelseKunINotat,
        )

        val newBehandling = behandlingService.hentBehandlingById(behandlingId)

        return InntekterResponse(
            newBehandling.inntekter.toInntektDto(),
            newBehandling.barnetillegg.toBarnetilleggDto(),
            newBehandling.utvidetbarnetrygd.toUtvidetbarnetrygdDto(),
            newBehandling.inntektBegrunnelseMedIVedtakNotat,
            newBehandling.inntektBegrunnelseKunINotat,
        )
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}/inntekter")
    @Operation(
        description = "Hente inntekter data",
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
    fun hentInntekter(
        @PathVariable behandlingId: Long,
    ): InntekterResponse {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        return InntekterResponse(
            behandling.inntekter.toInntektDto(),
            behandling.barnetillegg.toBarnetilleggDto(),
            behandling.utvidetbarnetrygd.toUtvidetbarnetrygdDto(),
            behandling.inntektBegrunnelseMedIVedtakNotat,
            behandling.inntektBegrunnelseKunINotat,
        )
    }
}
