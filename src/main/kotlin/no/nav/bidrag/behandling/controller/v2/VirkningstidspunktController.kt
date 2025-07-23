package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.behandling.ManuellVedtakResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBeregnTilDatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterManuellVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterManuellVedtakResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.commons.util.secureLogger
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class VirkningstidspunktController(
    private val virkningstidspunktService: VirkningstidspunktService,
    private val dtomapper: Dtomapper,
    private val behandlingService: BehandlingService,
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

    @PutMapping("/behandling/{behandlingsid}/beregntildato")
    @Operation(
        description = "Oppdatere opphørsdato for behandling.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBeregnTilDato(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdaterBeregnTilDatoRequestDto,
    ): BehandlingDtoV2 {
        secureLogger.info { "Oppdaterer beregnTilDato for behandling $behandlingsid med forespørsel $request" }

        val behandling = virkningstidspunktService.oppdaterBeregnTilDato(behandlingsid, request)

        return dtomapper.tilDto(behandling)
    }

    @GetMapping("/behandling/{behandlingsid}/manuelleVedtak")
    @Operation(
        description = "Hent manuelle vedtak.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentManuelleVedtak(
        @PathVariable behandlingsid: Long,
    ): ManuellVedtakResponse =
        virkningstidspunktService.hentManuelleVedtakForBehandling(behandlingsid).let {
            secureLogger.info { "Hentet manuelle vedtak for behandling $behandlingsid: $it" }
            ManuellVedtakResponse(it)
        }

    @PostMapping("/behandling/{behandlingsid}/manuelleVedtak")
    @Operation(
        description = "Velg manuelle vedtak for beregning.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterValgtManuellVedtak(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdaterManuellVedtakRequest,
    ): OppdaterManuellVedtakResponse {
        virkningstidspunktService.oppdaterBeregnManuellVedtak(behandlingsid, request)
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val beregning = hentBeregning(behandling)
        behandling.grunnlagslisteFraVedtak = beregning.firstOrNull()?.resultat?.grunnlagListe
        return OppdaterManuellVedtakResponse(
            beregning.all {
                it.resultat.beregnetBarnebidragPeriodeListe.isEmpty()
            },
            dtomapper.tilUnderholdskostnadDto(behandling, beregning),
        )
    }

    private fun hentBeregning(behandling: Behandling) =
        try {
            dtomapper.beregningService!!.beregneBidrag(behandling)
        } catch (e: Exception) {
            emptyList()
        }
}
