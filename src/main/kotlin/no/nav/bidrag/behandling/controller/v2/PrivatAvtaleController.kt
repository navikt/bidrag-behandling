package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleAvtaleDatoRequest
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtalePeriodeDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleResponsDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleSkalIndeksreguleresRequest
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class PrivatAvtaleController(
    private val behandlingRepository: BehandlingRepository,
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
    }

    @PutMapping("/behandling/{behandlingsid}/privatavtale/{privatavtaleid}/avtaledato")
    @Operation(
        description =
            "Oppdatere privat avtale avtaledato. Returnerer oppdatert element.",
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
    fun oppdaterePrivatAvtaleAvtaleDato(
        @PathVariable behandlingsid: Long,
        @PathVariable privatavtaleid: Long,
        @Valid @RequestBody(required = true) request: OppdaterePrivatAvtaleAvtaleDatoRequest,
    ): OppdaterePrivatAvtaleResponsDto {
        log.info { "Oppdaterer privatavtale $privatavtaleid avtaledato i behandling $behandlingsid" }
        return OppdaterePrivatAvtaleResponsDto()
    }

    @PutMapping("/behandling/{behandlingsid}/privatavtale/{privatavtaleid}/skalIndeksreguleres")
    @Operation(
        description =
            "Oppdatere privat avtale om det skal indeksreguleres eller ikke Returnerer oppdatert element.",
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
    fun oppdaterePrivatAvtaleSkalIndeksreguleres(
        @PathVariable behandlingsid: Long,
        @PathVariable privatavtaleid: Long,
        @Valid @RequestBody(required = true) request: OppdaterePrivatAvtaleSkalIndeksreguleresRequest,
    ): OppdaterePrivatAvtaleResponsDto {
        log.info { "Oppdaterer privatavtale $privatavtaleid skal indeksreguleres i behandling $behandlingsid" }
        return OppdaterePrivatAvtaleResponsDto()
    }

    @PutMapping("/behandling/{behandlingsid}/privatavtale/{privatavtaleid}/periode")
    @Operation(
        description =
            "Oppdatere privat avtale periode. Returnerer oppdatert element.",
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
    fun oppdaterePrivatAvtalePeriode(
        @PathVariable behandlingsid: Long,
        @PathVariable privatavtaleid: Long,
        @Valid @RequestBody(required = true) request: OppdaterePrivatAvtalePeriodeDto,
    ): OppdaterePrivatAvtaleResponsDto {
        log.info { "Oppdaterer faktisk tilsynsutgift for behandling $behandlingsid" }
        return OppdaterePrivatAvtaleResponsDto()
    }

    @PutMapping("/behandling/{behandlingsid}/privatavtale/{privatavtaleid}/begrunnelse")
    @Operation(
        description = "Oppdatere begrunnelse for underhold relatert til søknadsbarn eller andre barn.",
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
    fun oppdatereBegrunnelse(
        @PathVariable behandlingsid: Long,
        @PathVariable privatavtaleid: Long,
        @RequestBody(required = true) request: OppdaterePrivatAvtaleBegrunnelseRequest,
    ): OppdaterePrivatAvtaleResponsDto = OppdaterePrivatAvtaleResponsDto()

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
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        return OppdaterePrivatAvtaleResponsDto()
    }
}
