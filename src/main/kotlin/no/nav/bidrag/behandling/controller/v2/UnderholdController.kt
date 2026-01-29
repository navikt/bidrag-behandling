package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdResponse
import no.nav.bidrag.behandling.dto.v2.underhold.OpprettUnderholdskostnadBarnResponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.underhold.henteOgValidereUnderholdskostnad
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDtos
import no.nav.bidrag.behandling.transformers.underhold.valider
import no.nav.bidrag.commons.util.secureLogger
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class UnderholdController(
    private val behandlingRepository: BehandlingRepository,
    private val underholdService: UnderholdService,
    private val dtomapper: Dtomapper,
) {
    @DeleteMapping("/behandling/{behandlingsid}/underhold")
    @Operation(
        description =
            "Sletter fra underholdskostnad i behandling. Returnerer oppdaterte underholdsobjekt. Objektet " +
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
    ): OppdatereUnderholdResponse {
        log.info { "Sletter fra underholdskostnad i behandling $behandlingsid" }
        secureLogger.info { "Sletter fra underholdskostnad i behandling $behandlingsid med forespørsel $request" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val underholdskostnad = henteOgValidereUnderholdskostnad(behandling!!, request.idUnderhold)

        underholdService.sletteFraUnderhold(behandling, request)

        return if (request.type == Underholdselement.BARN) {
            OppdatereUnderholdResponse(
                beregnetUnderholdskostnader = dtomapper.run { behandling.tilBeregnetUnderholdskostnad() },
                valideringsfeil = behandling.underholdskostnader.valider(),
            )
        } else {
            underholdskostnad.tilRespons()
        }
    }

    @PutMapping("/behandling/{behandlingsid}/underhold/{underholdsid}/barnetilsyn")
    @Operation(
        description =
            "Oppdatere stønad til barnetilsyn for underholdskostnad i behandling. Returnerer oppdatert element.",
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
    fun oppdatereStønadTilBarnetilsyn(
        @PathVariable behandlingsid: Long,
        @PathVariable underholdsid: Long,
        @Valid @RequestBody(required = true) request: StønadTilBarnetilsynDto,
    ): OppdatereUnderholdResponse? {
        log.info { "Oppdaterer stønad til barnetilsyn for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer stønad til barnetilsyn for behandling $behandlingsid med forespørsel $request" }

        val behandling =
            try {
                behandlingRepository
                    .findBehandlingById(behandlingsid)
                    .orElseThrow { behandlingNotFoundException(behandlingsid) }
            } catch (exception: Exception) {
                exception.printStackTrace()
                null
            }

        val underholdskostnad = henteOgValidereUnderholdskostnad(behandling!!, underholdsid)

        underholdService.oppdatereStønadTilBarnetilsynManuelt(underholdskostnad, request)
        return underholdskostnad.tilRespons()
    }

    @PutMapping("/behandling/{behandlingsid}/underhold/{underholdsid}/faktisk_tilsynsutgift")
    @Operation(
        description =
            "Oppdatere faktisk tilsynsutgift for underholdskostnad i behandling. Returnerer oppdatert " +
                "element.",
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
    fun oppdatereFaktiskTilsynsutgift(
        @PathVariable behandlingsid: Long,
        @PathVariable underholdsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereFaktiskTilsynsutgiftRequest,
    ): OppdatereUnderholdResponse {
        log.info { "Oppdaterer faktisk tilsynsutgift for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer faktisk tilsynsutgift  for behandling $behandlingsid med forespørsel $request" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val underholdskostnad = henteOgValidereUnderholdskostnad(behandling, underholdsid)

        underholdService.oppdatereFaktiskeTilsynsutgifter(underholdskostnad, request)
        return underholdskostnad.tilRespons()
    }

    @PutMapping("/behandling/{behandlingsid}/underhold/{underholdsid}/tilleggsstonad")
    @Operation(
        description =
            "Oppdatere tilleggsstønad for underholdskostnad i behandling. Returnerer oppdatert element.",
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
    fun oppdatereTilleggsstønad(
        @PathVariable behandlingsid: Long,
        @PathVariable underholdsid: Long,
        @Valid @RequestBody(required = true) request: OppdatereTilleggsstønadRequest,
    ): OppdatereUnderholdResponse {
        log.info { "Oppdaterer tilleggsstønad for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer tilleggsstønad for behandling $behandlingsid med forespørsel $request" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val underholdskostnad = henteOgValidereUnderholdskostnad(behandling, underholdsid)

        underholdService.oppdatereTilleggsstønad(underholdskostnad, request)
        return underholdskostnad.tilRespons()
    }

    @PutMapping("/behandling/{behandlingsid}/underhold/begrunnelse")
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
        @RequestBody(required = true) request: OppdatereBegrunnelseRequest,
    ) {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        underholdService.oppdatereBegrunnelse(behandling, request)

        return dtomapper.run { tilUnderholdskostnadDto(behandling, emptyList(), false) }
    }

    @PutMapping("/behandling/{behandlingsid}/underhold/{underholdsid}/tilsynsordning")
    @Operation(
        description = "Angir om søknadsbarn har tilsynsordning.",
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
    fun oppdatereTilsynsordning(
        @PathVariable behandlingsid: Long,
        @PathVariable underholdsid: Long,
        @RequestParam(required = true) harTilsynsordning: Boolean,
    ) {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val underholdskostnad = henteOgValidereUnderholdskostnad(behandling, underholdsid)

        underholdService.oppdatereTilsynsordning(underholdskostnad, harTilsynsordning)
    }

    @PostMapping("/behandling/{behandlingsid}/underhold/opprette")
    @Operation(
        description = "Oppretter underholdselement med faktiske utgifter for BMs andre barn. Legges manuelt inn av saksbehandler.",
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
    fun oppretteUnderholdForBarn(
        @PathVariable behandlingsid: Long,
        @RequestBody(required = true) gjelderBarn: BarnDto,
    ): OpprettUnderholdskostnadBarnResponse {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        return OpprettUnderholdskostnadBarnResponse(
            underholdskostnad = dtomapper.tilUnderholdDto(underholdService.oppretteUnderholdskostnad(behandling, gjelderBarn)),
            beregnetUnderholdskostnader = dtomapper.run { behandling.tilBeregnetUnderholdskostnad() },
            valideringsfeil = behandling.underholdskostnader.valider(),
        )
    }

    private fun Underholdskostnad.tilRespons() =
        dtomapper.run {
            OppdatereUnderholdResponse(
                faktiskTilsynsutgift = faktiskeTilsynsutgifter.tilFaktiskeTilsynsutgiftDtos(),
                stønadTilBarnetilsyn = barnetilsyn.tilStønadTilBarnetilsynDtos(),
                tilleggsstønad = tilleggsstønad.tilTilleggsstønadDtos(),
                beregnetUnderholdskostnader = dtomapper.run { behandling.tilBeregnetUnderholdskostnad() },
                valideringsfeil = behandling.underholdskostnader.valider(),
            )
        }
}
