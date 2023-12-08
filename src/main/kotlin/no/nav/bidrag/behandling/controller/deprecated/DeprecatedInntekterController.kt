package no.nav.bidrag.behandling.controller.deprecated

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.deprecated.dto.InntekterResponse
import no.nav.bidrag.behandling.deprecated.dto.UpdateInntekterRequest
import no.nav.bidrag.behandling.deprecated.dto.toDepreactedInntektDto
import no.nav.bidrag.behandling.deprecated.dto.toInntektDto
import no.nav.bidrag.behandling.deprecated.dto.toUtvidetBarnetrygdDto
import no.nav.bidrag.behandling.deprecated.dto.toUtvidetbarnetrygdDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toBarnetilleggDto
import no.nav.bidrag.behandling.transformers.toInntektDto
import no.nav.bidrag.behandling.transformers.toUtvidetBarnetrygdDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@Deprecated("Bruk v1")
@DeprecatedBehandlingRestController
class DeprecatedInntekterController(private val behandlingService: BehandlingService) {
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
        behandlingService.oppdaterInntekter(
            behandlingId,
            request.inntekter.toInntektDto(),
            request.barnetillegg,
            request.utvidetbarnetrygd.toUtvidetBarnetrygdDto(),
            request.inntektBegrunnelseMedIVedtakNotat,
            request.inntektBegrunnelseKunINotat,
        )

        val newBehandling = behandlingService.hentBehandlingById(behandlingId)

        return InntekterResponse(
            newBehandling.inntekter.toInntektDto().toDepreactedInntektDto(),
            newBehandling.barnetillegg.toBarnetilleggDto(),
            newBehandling.utvidetBarnetrygd.toUtvidetBarnetrygdDto().toUtvidetbarnetrygdDto(),
            newBehandling.inntektsbegrunnelseIVedtakOgNotat,
            newBehandling.inntektsbegrunnelseKunINotat,
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
            behandling.inntekter.toInntektDto().toDepreactedInntektDto(),
            behandling.barnetillegg.toBarnetilleggDto(),
            behandling.utvidetBarnetrygd.toUtvidetBarnetrygdDto().toUtvidetbarnetrygdDto(),
            behandling.inntektsbegrunnelseIVedtakOgNotat,
            behandling.inntektsbegrunnelseKunINotat,
        )
    }
}
