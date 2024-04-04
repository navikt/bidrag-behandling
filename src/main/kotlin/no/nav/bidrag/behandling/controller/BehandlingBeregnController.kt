package no.nav.bidrag.behandling.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeilList
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.transformers.tilDto
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV1
class BehandlingBeregnController(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
    private val vedtakService: VedtakService,
) {
    @Suppress("unused")
    @PostMapping("/behandling/{behandlingsid}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validering av grunnlag feilet for beregning",
                content = [
                    Content(
                        schema = Schema(implementation = BeregningValideringsfeilList::class),
                    ),
                ],
            ),
        ],
    )
    fun beregnForskudd(
        @PathVariable behandlingsid: Long,
    ): List<ResultatBeregningBarnDto> {
        LOGGER.info { "Beregner forskudd for behandling med id $behandlingsid" }

        val behandling = behandlingService.hentBehandlingById(behandlingsid)

        return beregningService.beregneForskudd(behandling.id!!).tilDto()
    }

    @Suppress("unused")
    @PostMapping("/vedtak/{vedtaksId}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentVedtakBeregningResultat(
        @PathVariable vedtaksId: Long,
    ): List<ResultatBeregningBarnDto> {
        LOGGER.info { "Henter resultat for $vedtaksId" }

        return vedtakService.konverterVedtakTilBeregningResultat(vedtaksId)
    }
}
