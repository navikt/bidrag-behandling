package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværskalkulatorBeregningDto
import no.nav.bidrag.behandling.dto.v2.samvær.SletteSamværsperiodeElementDto
import no.nav.bidrag.behandling.service.SamværService
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseResultat
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import org.springframework.web.bind.annotation.DeleteMapping
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
    @DeleteMapping("/behandling/{behandlingsid}/samvar/periode")
    @Operation(
        description =
            "Slett samværsperiode",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun slettSamværsperiode(
        @PathVariable behandlingsid: Long,
        @Valid
        @RequestBody(required = true)
        request: SletteSamværsperiodeElementDto,
    ): OppdaterSamværResponsDto = samværService.slettPeriode(behandlingsid, request)

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingsid}/samvar/periode/beregning")
    @Operation(
        description =
            "Oppdater samværsperiode beregning",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterSamværskalkulatorBeregning(
        @PathVariable behandlingsid: Long,
        @Valid
        @RequestBody(required = true)
        request: OppdaterSamværskalkulatorBeregningDto,
    ): OppdaterSamværResponsDto = samværService.oppdaterSamværsperiodeBeregning(behandlingsid, request)

    @Suppress("unused")
    @PostMapping("/samvar/beregn")
    @Operation(
        description =
            "Oppdater samvær for en behandling.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnSamværsklasse(
        @Valid
        @RequestBody(required = true)
        request: SamværskalkulatorDetaljer,
    ): BeregnSamværsklasseResultat = samværService.beregnSamværsklasse(request)
}
