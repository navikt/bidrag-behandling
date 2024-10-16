package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdskostnad
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class UnderholdController {


    @DeleteMapping("/behandling/{behandlingsid}/underhold")
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
    fun sletteFraUnderhold(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: SletteUnderholdselement,
    ) : UnderholdDto{
        log.info { "Sletter fra underholdskostnad i behandling $behandlingsid" }
        secureLogger.info { "Sletter fra underholdskostn i behandling $behandlingsid med forespørsel $request" }

        // TODO: Implement me
        log.warn { "API for å slette fra underholdskostnad er ikke implementert!" }

        return UnderholdDto(
            id = 1L,
            gjelderBarn = PersoninfoDto(),
            underholdskostnand =
            UnderholdskostnadDto(
                periode =
                ÅrMånedsperiode(
                    LocalDate.now(),
                    null,
                ),
            ),
        )
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
        @Valid @RequestBody(required = true) request: OppdatereUnderholdskostnad,
    ): UnderholdDto {
        log.info { "Oppdaterer underholdskostnad for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer underholdskostnad for behandling $behandlingsid med forespørsel $request" }

        // TODO: Implement me
        log.warn { "API for å oppdatere underholdskostnad er ikke implementert!" }

        return UnderholdDto(
            id = 1L,
            gjelderBarn = PersoninfoDto(),
            underholdskostnand =
            UnderholdskostnadDto(
                periode =
                ÅrMånedsperiode(
                    LocalDate.now(),
                    null,
                ),
            ),
        )
    }
}