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
import no.nav.bidrag.behandling.dto.UpdateBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.domain.ident.PersonIdent
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDate
import java.time.ZoneId

@BehandlingRestController
class BehandlingController(val behandlingService: BehandlingService, val bidragPersonConsumer: BidragPersonConsumer) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suppress("unused")
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
            createBehandling.mottatDato,
            createBehandling.saksnummer,
            createBehandling.behandlerEnhet,
            createBehandling.soknadFra,
        )
        val roller = HashSet(
            createBehandling.roller.map {
                Rolle(
                    behandling,
                    it.rolleType,
                    it.ident ?: "UKJENT",
                    it.opprettetDato,
                )
            },
        )

        behandling.roller.addAll(roller)

        return CreateBehandlingResponse(behandlingService.createBehandling(behandling).id!!)
    }

    @Suppress("unused")
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
            LocalDate.ofInstant(behandling.datoFom.toInstant(), ZoneId.systemDefault()),
            LocalDate.ofInstant(behandling.datoTom.toInstant(), ZoneId.systemDefault()),
            LocalDate.ofInstant(behandling.mottatDato.toInstant(), ZoneId.systemDefault()),
            behandling.soknadFra,
            behandling.saksnummer,
            behandling.behandlerEnhet,
            behandling.roller.map {
                val navn = try {
                    val person = bidragPersonConsumer.hentPerson(PersonIdent(it.ident))
                    person.navn
                } catch (e: Exception) {
                    logger.info("Kunne ikke hente data for en person ", e)
                    "UKJENT"
                }
                RolleDto(it.id!!, it.rolleType, it.ident, it.opprettetDato, navn)
            }.toSet(),
            if (behandling.virkningsDato != null) {
                LocalDate.ofInstant(behandling.virkningsDato.toInstant(), ZoneId.systemDefault())
            } else {
                null
            },
            behandling.aarsak,
            behandling.avslag,
            behandling.begrunnelseMedIVedtakNotat,
            behandling.begrunnelseKunINotat,
        )

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Oppdaterer en behandling",
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
    fun oppdaterBehandling(@PathVariable behandlingId: Long, @RequestBody updateBehandling: UpdateBehandlingRequest): BehandlingDto {
        return behandlingDto(
            behandlingId,
            behandlingService.oppdaterBehandling(
                behandlingId,
                updateBehandling.begrunnelseKunINotat,
                updateBehandling.begrunnelseMedIVedtakNotat,
                // updateBehandling.virkningsDato,
            ),
        )
    }

    @Suppress("unused")
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
    fun hentBehandlinger(): List<BehandlingDto> {
        return behandlingService.hentBehandlinger().map { behandlingDto(it.id!!, it) }
    }
}
