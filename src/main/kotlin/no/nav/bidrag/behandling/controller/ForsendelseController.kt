package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.ForsendelseService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class ForsendelseController(private val forsendelseService: ForsendelseService) {

    @Suppress("unused")
    @PostMapping("/forsendelse/init")
    @Operation(
        description = "Oppretter forsendelse for behandling eller vedtak. Skal bare benyttes hvis vedtakId eller behandlingId mangler for behandling (Søknad som behandles gjennom Bisys)",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun opprettForsendelse(
        @Valid
        @RequestBody(required = true)
        request: InitalizeForsendelseRequest,
    ): List<String> {
        return forsendelseService.opprettForsendelse(request)
    }
}
