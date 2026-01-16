package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.SletteSamværsperiodeElementDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.SamværService
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.samvær.tilDto
import no.nav.bidrag.behandling.transformers.samvær.tilOppdaterSamværResponseDto
import no.nav.bidrag.behandling.transformers.sorterPersonEtterEldsteFødselsdato
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class SamværController(
    private val behandlingService: BehandlingService,
    private val samværService: SamværService,
    private val dtomapper: Dtomapper,
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
    ): OppdaterSamværResponsDto {
        val respons = samværService.oppdaterSamvær(behandlingsid, request)
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return OppdaterSamværResponsDto(
            oppdatertSamvær = respons.tilDto(),
            erSammeForAlle = behandling.sammeSamværForAlle,
            samværBarn = dtomapper.run { behandling.tilSamværDto() ?: emptyList() },
        )
    }

    @PostMapping("/behandling/{behandlingsid}/samvar/merge")
    @Operation(
        description = "Bruk samme samvær for alle barna",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun brukSammeSamværForAlleBarna(
        @PathVariable behandlingsid: Long,
    ): BehandlingDtoV2 {
        secureLogger.info { "Sett sammen virkningstidspunkt for alle barne for behandling $behandlingsid" }

        samværService.brukSammeSamværForAlleBarn(behandlingsid)
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return dtomapper.tilDto(behandling)
    }

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
    ): OppdaterSamværResponsDto {
        val respons = samværService.slettPeriode(behandlingsid, request)
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return OppdaterSamværResponsDto(
            oppdatertSamvær = respons.oppdatertSamvær,
            erSammeForAlle = behandling.sammeSamværForAlle,
            samværBarn =
                behandling.samvær
                    .sortedWith(
                        sorterPersonEtterEldsteFødselsdato({ it.rolle.fødselsdato }, { it.rolle.navn }),
                    ).map { it.tilDto() },
        )
    }

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
    ): DelberegningSamværsklasse = samværService.beregnSamværsklasse(request)
}
