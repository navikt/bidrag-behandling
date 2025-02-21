package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.commons.util.secureLogger
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class VirkningstidspunktController(
    private val virkningstidspunktService: VirkningstidspunktService,
    private val dtomapper: Dtomapper,
) {
    @PutMapping("/behandling/{behandlingsid}/opphorsdato")
    @Operation(
        description = "Oppdatere opphørsdato for behandling.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereOpphørsdato(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdaterOpphørsdatoRequestDto,
    ): BehandlingDtoV2 {
        log.info { "Oppdaterer virkningstidspunkt for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer virkningstidspunkt for behandling $behandlingsid med forespørsel $request" }

        val behandling = virkningstidspunktService.oppdaterOpphørsdato(behandlingsid, request)

        return dtomapper.tilDto(behandling)
    }
}
