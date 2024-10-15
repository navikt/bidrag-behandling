package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.database.datamodell.Samværskalkulator
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.service.SamværService
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class SamværController(
    private val samværService: SamværService,
) {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingsid}/samvar")
    @Operation(
        description =
            "Oppdater samvær for en behandling.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterSamvær(
        @PathVariable behandlingsid: Long,
        @Valid
        @RequestBody(required = true)
        request: OppdaterSamværDto,
    ): OppdaterSamværResponsDto = samværService.oppdaterSamvær(behandlingsid, request)

    @Suppress("unused")
    @PostMapping("/samvar/beregn")
    @Operation(
        description =
            "Oppdater samvær for en behandling.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterSamvær(
        @Valid
        @RequestBody(required = true)
        request: Samværskalkulator,
    ): Samværsklasse = samværService.beregnSamværsklasse(request)
}
