package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDetaljerDto
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GebyrService
import no.nav.bidrag.behandling.transformers.tilDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class GebyrController(
    private val gebyrService: GebyrService,
    private val behandlingService: BehandlingService,
    private val vedtakGrunnlagMapper: VedtakGrunnlagMapper,
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
        request: OppdaterGebyrDto,
    ): GebyrDetaljerDto {
        gebyrService.oppdaterManueltOverstyrtGebyr(behandlingService.hentBehandlingById(behandlingsid), request)
        return tilRespons(behandlingsid, request)
    }

    private fun tilRespons(
        behandlingsId: Long,
        request: OppdaterGebyrDto,
    ): GebyrDetaljerDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsId)
        return behandling.roller.find { it.id == request.rolleId }!!.let { rolle ->
            vedtakGrunnlagMapper
                .beregnGebyr(behandling, rolle)
                .tilDto(rolle, request.søknadsid ?: behandling.soknadsid!!)
        }
    }
}
