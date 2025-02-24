package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleRequest
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleResponsDto
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.PrivatAvtaleService
import no.nav.bidrag.behandling.transformers.Dtomapper
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class PrivatAvtaleController(
    private val behandlingService: BehandlingService,
    private val privatAvtaleService: PrivatAvtaleService,
    private val dtomapper: Dtomapper,
) {
    @DeleteMapping("/behandling/{behandlingsid}/privatavtale/{privatavtaleid}")
    @Operation(
        description =
            "Sletter privat avtale.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    fun slettePrivatAvtale(
        @PathVariable behandlingsid: Long,
        @PathVariable privatavtaleid: Long,
    ) {
        log.info { "Sletter fra privat avtale $privatavtaleid i behandling $behandlingsid" }
        privatAvtaleService.slettPrivatAvtale(behandlingsid, privatavtaleid)
    }

    @PutMapping("/behandling/{behandlingsid}/privatavtale/{privatavtaleid}")
    @Operation(
        description =
            "Oppdatere privat avtale. Returnerer oppdatert element.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    fun oppdaterPrivatAvtale(
        @PathVariable behandlingsid: Long,
        @PathVariable privatavtaleid: Long,
        @Valid @RequestBody(required = true) request: OppdaterePrivatAvtaleRequest,
    ): OppdaterePrivatAvtaleResponsDto {
        privatAvtaleService.oppdaterPrivatAvtale(behandlingsid, privatavtaleid, request)
        return tilPrivatAvtaleResponsDto(behandlingsid, privatavtaleid)
    }

    @PostMapping("/behandling/{behandlingsid}/privatavtale/opprette")
    @Operation(
        description = "Oppretter privat avtale",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    fun opprettePrivatAvtale(
        @PathVariable behandlingsid: Long,
        @RequestBody(required = true) gjelderBarn: BarnDto,
    ): OppdaterePrivatAvtaleResponsDto {
        log.info { "Oppretter privat avtale for barn ${gjelderBarn.id} i behandling $behandlingsid" }
        val privatAvtale = privatAvtaleService.opprettPrivatAvtale(behandlingsid, gjelderBarn)
        return tilPrivatAvtaleResponsDto(behandlingsid, privatAvtale.id!!)
    }

    private fun tilPrivatAvtaleResponsDto(
        behandlingsid: Long,
        privatavtaleid: Long,
    ): OppdaterePrivatAvtaleResponsDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale = behandling.privatAvtale.find { it.id == privatavtaleid }!!
        return OppdaterePrivatAvtaleResponsDto(
            oppdatertPrivatAvtale =
                dtomapper.run {
                    privatAvtale.tilDto()
                },
        )
    }
}
