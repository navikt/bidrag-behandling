package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.tilBehandlingDto
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDate

@BehandlingRestController
class BehandlingController(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
) {
    @Suppress("unused")
    @PostMapping("/v1/behandling")
    @Operation(
        description = "Legge til en ny behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun oppretteBehandling(
        @Valid
        @RequestBody(required = true)
        opprettBehandling: OpprettBehandlingRequest,
    ): OpprettBehandlingResponse = behandlingService.opprettBehandling(opprettBehandling)

    @Suppress("unused")
    @PutMapping("/v1/behandling/{behandlingId}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBehandling(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequest,
    ): BehandlingDto {
        val behandling = behandlingService.oppdaterBehandling(behandlingId, request)
        return behandling
    }

    @Suppress("unused")
    @PutMapping("/v2/behandling/{behandlingId}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBehandlingV2(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequestV2,
    ): BehandlingDtoV2 {
        // TODO: implementere
        return responsstubbe()
    }

    @Suppress("unused")
    @PutMapping("/v1/behandling/{behandlingId}/roller")
    @Operation(
        description = "Sync fra behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterRoller(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest,
    ) = behandlingService.syncRoller(behandlingId, request.roller)

    @Suppress("unused")
    @GetMapping("/v2/behandling/{behandlingId}")
    @Operation(
        description = "Hente en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun hentBehandlingV2(
        @PathVariable behandlingId: Long,
    ): BehandlingDtoV2 {
        // TODO: implementere
        return responsstubbe()
    }

    @Suppress("unused")
    @GetMapping("/v1/behandling/{behandlingId}")
    @Operation(
        description = "Hente en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun hentBehandling(
        @PathVariable behandlingId: Long,
    ): BehandlingDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val opplysninger = grunnlagService.hentAlleSistAktiv(behandlingId)
        return behandling.tilBehandlingDto(opplysninger)
    }

    fun responsstubbe(): BehandlingDtoV2 {
        val nå = LocalDate.now()
        return BehandlingDtoV2(
            0L,
            vedtakstype = Vedtakstype.FASTSETTELSE,
            erVedtakFattet = false,
            søktFomDato = nå,
            mottattdato = nå,
            søktAv = SøktAvType.VERGE,
            saksnummer = "1234567",
            søknadsid = 1,
            behandlerenhet = "4806",
            roller = emptySet(),
            virkningstidspunkt = VirkningstidspunktDto(notat = BehandlingNotatDto()),
            inntekter = InntekterDtoV2(emptySet(), BehandlingNotatDto()),
            boforhold = BoforholdDto(emptySet(), sivilstand = emptySet(), notat = BehandlingNotatDto()),
            opplysninger = emptyList(),
        )
    }
}
