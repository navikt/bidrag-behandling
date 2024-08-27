package no.nav.bidrag.behandling.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.transformers.tilDto
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.client.HttpClientErrorException

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV1
class BehandlingBeregnController(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
    private val vedtakService: VedtakService,
) {
    @Suppress("unused")
    @PostMapping("/behandling/{behandlingsid}/beregn", "/behandling/{behandlingsid}/beregn/forskudd")
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
                        schema = Schema(implementation = BeregningValideringsfeil::class),
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
        if (behandling.stonadstype != Stønadstype.FORSKUDD) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Behandling $behandlingsid er ikke en forskudd behandling",
            )
        }
        return beregningService.beregneForskudd(behandling.id!!).tilDto(behandling.vedtakstype)
    }

    @Suppress("unused")
    @PostMapping("/behandling/{behandlingsid}/beregn/sarbidrag")
    @Operation(
        description = "Beregn særbidrag",
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
                        schema = Schema(implementation = BeregningValideringsfeil::class),
                    ),
                ],
            ),
        ],
    )
    fun beregnSærbidrag(
        @PathVariable behandlingsid: Long,
    ): ResultatSærbidragsberegningDto {
        LOGGER.info { "Beregner særbidrag for behandling med id $behandlingsid" }

        val behandling = behandlingService.hentBehandlingById(behandlingsid)

        if (behandling.engangsbeloptype != Engangsbeløptype.SÆRBIDRAG) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Behandling $behandlingsid er ikke en særbidrag behandling",
            )
        }

        return beregningService.beregneSærbidrag(behandling.id!!).tilDto(behandling)
    }

    @Suppress("unused")
    @PostMapping("/vedtak/{vedtaksId}/beregn", "/vedtak/{vedtaksId}/beregn/forskudd")
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

    @Suppress("unused")
    @PostMapping("/vedtak/{vedtaksId}/beregn/sarbidrag")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentVedtakBeregningResultatSærbidrag(
        @PathVariable vedtaksId: Long,
    ): ResultatSærbidragsberegningDto? {
        LOGGER.info { "Henter resultat for $vedtaksId" }

        return vedtakService.konverterVedtakTilBeregningResultatSærbidrag(vedtaksId)
    }
}
