package no.nav.bidrag.behandling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.BehandlingDto
import no.nav.bidrag.behandling.dto.CreateBehandlingRequest
import no.nav.bidrag.behandling.dto.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.RolleDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.domain.ident.PersonIdent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestController
class BehandlingController(val behandlingService: BehandlingService, val bidragPersonConsumer: BidragPersonConsumer) {

    @PostMapping("/behandling")
    @Operation(
        description = "Legger til en ny behandling",
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
    fun createBehandling(@RequestBody createBehandling: CreateBehandlingRequest): CreateBehandlingResponse {
        val behandling = Behandling(
            createBehandling.behandlingType,
            createBehandling.soknadType,
            createBehandling.datoFom,
            createBehandling.datoTom,
            createBehandling.saksnummer,
            createBehandling.behandlerEnhet,
        )
        val roller = HashSet(
            createBehandling.roller.map {
                Rolle(
                    behandling,
                    it.rolleType,
                    it.ident,
                    it.opprettetDato,
                )
            },
        )

        behandling.roller.addAll(roller)

        return CreateBehandlingResponse(behandlingService.createBehandling(behandling).id!!)
    }

    @GetMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Henter en behandling",
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
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return BehandlingDto(
            behandlingId,
            behandling.behandlingType,
            behandling.soknadType,
            behandling.datoFom,
            behandling.datoTom,
            behandling.saksnummer,
            behandling.behandlerEnhet,
            behandling.roller.map {
                val person = bidragPersonConsumer.hentPerson(PersonIdent(it.ident))
                RolleDto(it.id!!, it.rolleType, it.ident, it.opprettetDato, person.navn)
            }.toSet(),
            behandling.virkningsDato,
            behandling.aarsak,
            behandling.avslag,
            behandling.begrunnelseMedIVedtakNotat,
            behandling.begrunnelseKunINotat,
        )
    }

    @GetMapping("/behandling")
    @Operation(
        description = "Henter en behandlinger",
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
    fun hentBehandlinger(): List<Behandling> {
        return behandlingService.hentBehandlinger()
    }
}
