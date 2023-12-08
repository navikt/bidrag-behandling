package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import mu.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.behandling.RolleDto
import no.nav.bidrag.behandling.dto.behandling.SyncRollerRequest
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toHusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.toSivilstandDto
<<<<<<< HEAD:src/main/kotlin/no/nav/bidrag/behandling/controller/v1/BehandlingController.kt
import no.nav.bidrag.domene.enums.rolle.Rolletype
=======
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
>>>>>>> main:src/main/kotlin/no/nav/bidrag/behandling/controller/BehandlingController.kt
import org.apache.commons.lang3.Validate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestControllerV1
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
    fun oppretteBehandling(
        @Valid
        @RequestBody(required = true)
        createBehandling: CreateBehandlingRequest,
    ): CreateBehandlingResponse {
        Validate.isTrue(
            ingenBarnMedVerkenIdentEllerNavn(createBehandling.roller) &&
                ingenVoksneUtenIdent(createBehandling.roller),
        )

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils.hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        val behandling =
            Behandling(
                createBehandling.behandlingstype,
                createBehandling.søknadstype,
                createBehandling.datoFom,
                createBehandling.datoTom,
                createBehandling.mottattdato,
                createBehandling.saksnummer,
<<<<<<< HEAD:src/main/kotlin/no/nav/bidrag/behandling/controller/v1/BehandlingController.kt
                createBehandling.søknadsid,
                createBehandling.søknadsreferanseid,
                createBehandling.behandlerenhet,
                createBehandling.søknadFra,
                createBehandling.stønadstype,
                createBehandling.engangsbeløpstype,
=======
                createBehandling.soknadId,
                createBehandling.soknadRefId,
                createBehandling.behandlerEnhet,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = TokenUtils.hentApplikasjonsnavn() ?: "ukjent",
                createBehandling.soknadFra,
                createBehandling.stonadType,
                createBehandling.engangsbelopType,
>>>>>>> main:src/main/kotlin/no/nav/bidrag/behandling/controller/BehandlingController.kt
            )
        val roller =
            HashSet(
                createBehandling.roller.map {
                    it.toRolle(behandling)
                },
            )

        behandling.roller.addAll(roller)

        val behandlingDo = behandlingService.createBehandling(behandling)
        LOGGER.info {
            "Opprettet behandling for behandlingType ${createBehandling.behandlingstype} " +
                "soknadType ${createBehandling.søknadstype} " +
                "og soknadFra ${createBehandling.søknadFra} " +
                "med id ${behandlingDo.id} "
        }
        return CreateBehandlingResponse(behandlingDo.id!!)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBehandling(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: UpdateBehandlingRequest,
    ) {
        behandlingService.updateBehandling(behandlingId, request.grunnlagspakkeId)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/roller/sync")
    @Operation(
        description = "Sync fra behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun synkronisereRoller(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: SyncRollerRequest,
    ) = behandlingService.syncRoller(behandlingId, request.roller)

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
    fun oppdatereVedtaksid(
        @PathVariable behandlingId: Long,
        @PathVariable vedtakId: Long,
    ) {
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
    fun hentBehandling(
        @PathVariable behandlingId: Long,
    ): BehandlingDto {
        return findBehandlingById(behandlingId)
    }

    private fun findBehandlingById(behandlingId: Long): BehandlingDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return behandlingDto(behandlingId, behandling)
    }

    private fun behandlingDto(
        behandlingId: Long,
        behandling: Behandling,
    ) = BehandlingDto(
        behandlingId,
        behandling.behandlingstype,
        behandling.soknadstype,
        behandling.vedtaksid != null,
        behandling.datoFom,
        behandling.datoTom,
        behandling.mottattdato,
        behandling.soknadFra,
        behandling.saksnummer,
        behandling.soknadsid,
        behandling.behandlerEnhet,
        behandling.roller.map {
<<<<<<< HEAD:src/main/kotlin/no/nav/bidrag/behandling/controller/v1/BehandlingController.kt
            RolleDto(it.id!!, it.rolletype, it.ident, it.navn, it.foedselsdato)
=======
            RolleDto(
                it.id!!,
                it.rolleType.toRolleTypeDto(),
                it.ident,
                it.navn,
                it.fodtDato,
                it.opprettetDato,
            )
>>>>>>> main:src/main/kotlin/no/nav/bidrag/behandling/controller/BehandlingController.kt
        }.toSet(),
        behandling.husstandsbarn.toHusstandsBarnDto(),
        behandling.sivilstand.toSivilstandDto(),
        behandling.virkningsdato,
        behandling.soknadRefId,
        behandling.grunnlagspakkeid,
        behandling.aarsak,
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat,
        behandling.virkningstidspunktbegrunnelseKunINotat,
        behandling.boforholdsbegrunnelseIVedtakOgNotat,
        behandling.boforholdsbegrunnelseKunINotat,
        behandling.inntektsbegrunnelseIVedtakOgNotat,
        behandling.inntektsbegrunnelseKunINotat,
    )

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

    private fun ingenBarnMedVerkenIdentEllerNavn(roller: Set<CreateRolleDto>): Boolean {
        return roller.filter { r -> r.rolletype == Rolletype.BARN && r.ident.isNullOrBlank() }
            .none { r -> r.navn.isNullOrBlank() }
    }

    private fun ingenVoksneUtenIdent(roller: Set<CreateRolleDto>): Boolean {
<<<<<<< HEAD:src/main/kotlin/no/nav/bidrag/behandling/controller/v1/BehandlingController.kt
        return roller.filter { r -> r.rolletype != Rolletype.BARN && r.ident.isNullOrBlank() }
=======
        return roller.filter { r -> r.rolleType != CreateRolleRolleType.BARN && r.ident.isNullOrBlank() }
>>>>>>> main:src/main/kotlin/no/nav/bidrag/behandling/controller/BehandlingController.kt
            .none()
    }
}
