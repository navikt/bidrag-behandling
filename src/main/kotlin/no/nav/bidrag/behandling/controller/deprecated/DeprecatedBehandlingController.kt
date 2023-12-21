package no.nav.bidrag.behandling.controller.deprecated

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import mu.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.deprecated.dto.BehandlingDto
import no.nav.bidrag.behandling.deprecated.dto.CreateBehandlingRequest
import no.nav.bidrag.behandling.deprecated.dto.RolleDto
import no.nav.bidrag.behandling.deprecated.dto.RolleTypeDto
import no.nav.bidrag.behandling.deprecated.dto.SyncRollerRequest
import no.nav.bidrag.behandling.deprecated.dto.toOpprettRolleDto
import no.nav.bidrag.behandling.deprecated.dto.toRolle
import no.nav.bidrag.behandling.deprecated.modell.SoknadType
import no.nav.bidrag.behandling.dto.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.toDate
import no.nav.bidrag.behandling.transformers.toHusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.apache.commons.lang3.Validate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val LOGGER = KotlinLogging.logger {}

@Deprecated("Bruk endepunktene i BehandlingController /api/v1/behandling")
@DeprecatedBehandlingRestController
class DeprecatedBehandlingController(private val behandlingService: BehandlingService) {
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
    ): OpprettBehandlingResponse {
        ingenBarnMedVerkenIdentEllerNavn(createBehandling.roller.toOpprettRolleDto())

        Validate.isTrue(
            ingenBarnMedVerkenIdentEllerNavn(createBehandling.roller.toOpprettRolleDto()) &&
                    ingenVoksneUtenIdent(
                        createBehandling.roller.toOpprettRolleDto(),
                    ),
        )

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils.hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }

        val behandling =
            Behandling(
                vedtakstype = Vedtakstype.valueOf(createBehandling.soknadType.name),
                søktFomDato = createBehandling.datoFom.toLocalDate(),
                datoTom = createBehandling.datoTom.toLocalDate(),
                mottattdato = createBehandling.mottatDato.toLocalDate(),
                saksnummer = createBehandling.saksnummer,
                soknadsid = createBehandling.soknadId,
                soknadRefId = createBehandling.soknadRefId,
                behandlerEnhet = createBehandling.behandlerEnhet,
                soknadFra = createBehandling.soknadFra,
                stonadstype = createBehandling.stonadType,
                engangsbeloptype = createBehandling.engangsbelopType,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = TokenUtils.hentApplikasjonsnavn() ?: "ukjent",
            )
        val roller =
            HashSet(
                createBehandling.roller.map {
                    it.toRolle(behandling)
                },
            )

        behandling.roller.addAll(roller)

        val behandlingDo = behandlingService.opprettBehandling(behandling)
        LOGGER.info {
            "Opprettet behandling for behandlingType ${createBehandling.behandlingType} " +
                    "soknadType ${createBehandling.soknadType} " +
                    "og soknadFra ${createBehandling.soknadFra} " +
                    "med id ${behandlingDo.id} "
        }
        return OpprettBehandlingResponse(behandlingDo.id!!)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun updateBehandling(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequest,
    ) {
        behandlingService.oppdaterGrunnlagspakkeid(behandlingId, request.grunnlagspakkeId)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/roller/sync")
    @Operation(
        description = "Sync fra behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun syncRoller(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: SyncRollerRequest,
    ) = behandlingService.syncRoller(
        behandlingId,
        request.roller.toSet().toOpprettRolleDto().toList(),
    )

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
    fun oppdaterVedtakId(
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
        behandling.toBehandlingstype(),
        SoknadType.valueOf(behandling.vedtakstype.name),
        behandling.vedtaksid != null,
        behandling.søktFomDato,
        behandling.datoTom,
        behandling.mottattdato,
        behandling.soknadFra,
        behandling.saksnummer,
        behandling.soknadsid,
        behandling.behandlerEnhet,
        behandling.roller.map {
            RolleDto(
                it.id!!,
                RolleTypeDto.valueOf(it.rolletype.name),
                it.ident,
                it.navn ?: hentPersonVisningsnavn(it.ident),
                it.foedselsdato.toDate(),
                it.opprettetDato?.toDate(),
            )
        }.toSet(),
        behandling.husstandsbarn.toHusstandsBarnDto(behandling),
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

    private fun ingenBarnMedVerkenIdentEllerNavn(roller: Set<OpprettRolleDto>): Boolean {
        return roller.filter { r -> r.rolletype == Rolletype.BARN && r.ident?.verdi.isNullOrBlank() }
            .none { r -> r.navn.isNullOrBlank() }
    }

    private fun ingenVoksneUtenIdent(roller: Set<OpprettRolleDto>): Boolean {
        return roller.none { r -> r.rolletype != Rolletype.BARN && r.ident?.verdi.isNullOrBlank() }
    }
}

fun Behandling.toBehandlingstype(): Behandlingstype =
    (stonadstype?.name ?: engangsbeloptype?.name)?.let { Behandlingstype.valueOf(it) }
        ?: Behandlingstype.FORSKUDD
