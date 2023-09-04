package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import mu.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.CreateRolleRolleType
import no.nav.bidrag.behandling.dto.behandling.RolleDto
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequestExtended
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toHusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
private val LOGGER = KotlinLogging.logger {}

@BehandlingRestController
class BehandlingController(private val behandlingService: BehandlingService) {

    @Suppress("unused")
    @PostMapping("/behandling")
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
    fun createBehandling(
        @Valid
        @RequestBody(required = true)
        createBehandling: CreateBehandlingRequest,
    ): CreateBehandlingResponse {
        val behandling = Behandling(
            createBehandling.behandlingType,
            createBehandling.soknadType,
            createBehandling.datoFom,
            createBehandling.datoTom,
            createBehandling.mottatDato,
            createBehandling.saksnummer,
            createBehandling.soknadId,
            createBehandling.soknadRefId,
            createBehandling.behandlerEnhet,
            createBehandling.soknadFra,
            createBehandling.stonadType,
            createBehandling.engangsbelopType,
        )
        val roller = HashSet(
            createBehandling.roller.map {
                Rolle(
                    behandling,
                    rolleType = when (it.rolleType) {
                        CreateRolleRolleType.BIDRAGS_MOTTAKER -> RolleType.BIDRAGSMOTTAKER
                        CreateRolleRolleType.BIDRAGS_PLIKTIG -> RolleType.BIDRAGSPLIKTIG
                        CreateRolleRolleType.REELL_MOTTAKER -> RolleType.REELLMOTTAKER
                        CreateRolleRolleType.BARN -> RolleType.BARN
                        CreateRolleRolleType.FEILREGISTRERT -> RolleType.FEILREGISTRERT
                    },
                    it.ident,
                    it.fodtDato,
                    it.opprettetDato,
                )
            },
        )

        behandling.roller.addAll(roller)

        val behandlingDo = behandlingService.createBehandling(behandling)
        LOGGER.info {
            "Opprettet behandling for behandlingType ${createBehandling.behandlingType} " +
                "soknadType ${createBehandling.soknadType} " +
                "og soknadFra ${createBehandling.soknadFra} " +
                "med id ${behandlingDo.id} "
        }
        return CreateBehandlingResponse(behandlingDo.id!!)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/vedtak/{vedtakId}")
    @Operation(
        description = "Oppdaterer vedtak id",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lagret behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Oppdaterer behandling med ny vedtak id",
            ),
        ],
    )
    fun oppdaterVedtakId(@PathVariable behandlingId: Long, @PathVariable vedtakId: Long) {
        behandlingService.oppdaterVedtakId(behandlingId, vedtakId)
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Hente en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun hentBehandling(@PathVariable behandlingId: Long): BehandlingDto {
        return findBehandlingById(behandlingId)
    }

    private fun findBehandlingById(behandlingId: Long): BehandlingDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return behandlingDto(behandlingId, behandling)
    }

    private fun behandlingDto(behandlingId: Long, behandling: Behandling) =
        BehandlingDto(
            behandlingId,
            behandling.behandlingType,
            behandling.soknadType,
            behandling.vedtakId != null,
            behandling.datoFom.toLocalDate(),
            behandling.datoTom.toLocalDate(),
            behandling.mottatDato.toLocalDate(),
            behandling.soknadFra,
            behandling.saksnummer,
            behandling.soknadId,
            behandling.behandlerEnhet,
            behandling.roller.map {
                RolleDto(it.id!!, it.rolleType, it.ident, it.fodtDato, it.opprettetDato)
            }.toSet(),
            behandling.husstandsBarn.toHusstandsBarnDto(),
            behandling.sivilstand.toSivilstandDto(),
            behandling.virkningsDato?.toLocalDate(),
            behandling.soknadRefId,
            behandling.aarsak,
            behandling.virkningsTidspunktBegrunnelseMedIVedtakNotat,
            behandling.virkningsTidspunktBegrunnelseKunINotat,
            behandling.boforholdBegrunnelseMedIVedtakNotat,
            behandling.boforholdBegrunnelseKunINotat,
            behandling.inntektBegrunnelseMedIVedtakNotat,
            behandling.inntektBegrunnelseKunINotat,
        )

    @Suppress("unused")
    @PutMapping("/behandling/ext/{behandlingId}")
    @Operation(
        description = "Oppdatere en behandling",
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
    fun oppdaterBehandlingExtended(
        @PathVariable behandlingId: Long,
        @RequestBody updateBehandling: UpdateBehandlingRequestExtended,
    ): BehandlingDto {
        return behandlingDto(behandlingId, behandlingService.oppdaterBehandlingExtended(behandlingId, updateBehandling))
    }

    @Suppress("unused")
    @GetMapping("/behandling")
    @Operation(
        description = "Hente en liste av alle behandlinger",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandlinger"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandlinger"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)",
            ),
        ],
    )
    fun hentBehandlinger(): List<BehandlingDto> {
        return behandlingService.hentBehandlinger().map { behandlingDto(it.id!!, it) }
    }
}
