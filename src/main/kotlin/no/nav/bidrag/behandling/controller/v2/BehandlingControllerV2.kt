package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.domene.ident.Personident
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
class BehandlingControllerV2(private val vedtakService: VedtakService,    private val behandlingService: BehandlingService,
                             private val grunnlagService: GrunnlagService) {
    @Suppress("unused")
    @GetMapping("/behandling/vedtak/{vedtakId}")
    @Operation(
        description = "Omgjør vedtak til en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Vedtak i form av behandling"),
            ApiResponse(
                responseCode = "404",
                description = "Fant ikke vedtak med oppgitt vedtakid",
            ),
        ],
    )
    fun omgjørVedtakTilBehandling(
        @PathVariable vedtakId: Long,
    ): BehandlingDtoV2 {
        val resultat =
            vedtakService.konverterVedtakTilBehandling(vedtakId)
                ?: throw RuntimeException("Fant ikke vedtak for vedtakid $vedtakId")
        return resultat.tilBehandlingDtoV2(resultat.grunnlagListe)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingsid}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Forespørsel oppdatert uten feil",
            ),
            ApiResponse(responseCode = "400", description = "Feil opplysninger oppgitt"),
            ApiResponse(
                responseCode = "401",
                description = "Sikkerhetstoken mangler, er utløpt, eller av andre årsaker ugyldig",
            ),
            ApiResponse(responseCode = "404", description = "Fant ikke forespørsel"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun oppdatereBehandlingV2(
        @PathVariable behandlingsid: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequestV2,
    ): ResponseEntity<BehandlingDtoV2> {
        val behandlingFørOppdatering = behandlingService.hentBehandlingById(behandlingsid)

        behandlingFørOppdatering.bidragsmottaker?.ident?.let { Personident(it) }
            ?: throw IllegalArgumentException("Behandling mangler BM!")

        behandlingService.oppdaterBehandling(behandlingsid, request)

        val behandling = behandlingService.hentBehandlingById(behandlingsid)

        return ResponseEntity(
            behandling.tilBehandlingDtoV2(
                grunnlagService.henteGjeldendeAktiveGrunnlagsdata(behandlingsid),
                grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(behandlingsid, behandling.roller),
            ),
            HttpStatus.CREATED,
        )
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingsid}")
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
        @PathVariable behandlingsid: Long,
    ): BehandlingDtoV2 {
        return behandlingService.henteBehandling(behandlingsid)
    }
}
