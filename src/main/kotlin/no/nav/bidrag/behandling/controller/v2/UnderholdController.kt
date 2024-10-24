package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderhold
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.transformers.underhold.validere
import no.nav.bidrag.commons.util.secureLogger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class UnderholdController(
    private val behandlingRepository: BehandlingRepository,
    private val underholdService: UnderholdService,
) {
    @DeleteMapping("/behandling/{behandlingsid}/underhold")
    @Operation(
        description = "Oppdatere underholdskostnad for behandling. Returnerer oppdaterte underholdsobjekt. Objektet " +
                " vil være null dersom barn slettes.",
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
    fun sletteFraUnderhold(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: SletteUnderholdselement,
    ): ResponseEntity<UnderholdDto> {
        log.info { "Sletter fra underholdskostnad i behandling $behandlingsid" }
        secureLogger.info { "Sletter fra underholdskostnad i behandling $behandlingsid med forespørsel $request" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val underholdDto = underholdService.sletteFraUnderhold(behandling, request)

        if (Underholdselement.BARN == request.type && underholdDto == null) {
            return ResponseEntity(null, HttpStatus.ACCEPTED)
        }

        return ResponseEntity(underholdDto, HttpStatus.OK)
    }

    @PutMapping("/behandling/{behandlingsid}/underhold")
    @Operation(
        description = "Oppdatere underholdskostnad for behandling. Returnerer oppdaterte behandlingsdetaljer.",
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
    fun oppdatereUnderhold(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereUnderhold,
    ): UnderholdDto? {
        log.info { "Oppdaterer underholdskostnad for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer underholdskostnad for behandling $behandlingsid med forespørsel $request" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        return underholdService.oppdatereUnderhold(behandling, request)
    }
}
