package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import mu.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.OppdaterRollerRequest
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.behandling.RolleDto
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.toHusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.rolle.Rolletype
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
        opprettBehandling: OpprettBehandlingRequest,
    ): OpprettBehandlingResponse {
        Validate.isTrue(
            ingenBarnMedVerkenIdentEllerNavn(opprettBehandling.roller) &&
                ingenVoksneUtenIdent(opprettBehandling.roller),
        )

        Validate.isTrue(
            opprettBehandling.stønadstype != null || opprettBehandling.engangsbeløpstype != null,
            "${OpprettBehandlingRequest::stønadstype.name} " +
                "eller ${OpprettBehandlingRequest::engangsbeløpstype.name} må være satt i forespørselen",
        )

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils.hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        val behandling =
            Behandling(
                vedtakstype = opprettBehandling.vedtakstype,
                datoFom = opprettBehandling.datoFom,
                mottattdato = opprettBehandling.mottattdato,
                saksnummer = opprettBehandling.saksnummer,
                soknadsid = opprettBehandling.søknadsid,
                soknadRefId = opprettBehandling.søknadsreferanseid,
                behandlerEnhet = opprettBehandling.behandlerenhet,
                soknadFra = opprettBehandling.søknadFra,
                stonadstype = opprettBehandling.stønadstype,
                engangsbeloptype = opprettBehandling.engangsbeløpstype,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = TokenUtils.hentApplikasjonsnavn() ?: "ukjent",
            )
        val roller =
            HashSet(
                opprettBehandling.roller.map {
                    it.toRolle(behandling)
                },
            )

        behandling.roller.addAll(roller)

        val behandlingDo = behandlingService.opprettBehandling(behandling)
        LOGGER.info {
            "Opprettet behandling for stønadstype ${opprettBehandling.stønadstype} " +
                "og engangsbeløptype ${opprettBehandling.engangsbeløpstype} " +
                "soknadType ${opprettBehandling.vedtakstype} " +
                "og soknadFra ${opprettBehandling.søknadFra} " +
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
    fun oppdatereBehandling(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: UpdateBehandlingRequest,
    ) {
        behandlingService.updateBehandling(behandlingId, request.grunnlagspakkeId)
    }

    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}/roller")
    @Operation(
        description = "Sync fra behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterRoller(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterRollerRequest,
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
        id = behandlingId,
        vedtakstype = behandling.vedtakstype,
        søknadType = behandling.vedtakstype,
        erVedtakFattet = behandling.vedtaksid != null,
        datoFom = behandling.datoFom,
        datoTom = behandling.datoTom,
        mottattdato = behandling.mottattdato,
        soknadFraType = behandling.soknadFra,
        saksnummer = behandling.saksnummer,
        soknadsid = behandling.soknadsid,
        behandlerenhet = behandling.behandlerEnhet,
        roller =
            behandling.roller.map {
                RolleDto(
                    it.id!!,
                    it.rolletype,
                    it.ident,
                    it.navn ?: hentPersonVisningsnavn(it.ident),
                    it.foedselsdato,
                    it.opprettetDato,
                )
            }.toSet(),
        husstandsbarn = behandling.husstandsbarn.toHusstandsBarnDto(behandling),
        sivilstand = behandling.sivilstand.toSivilstandDto(),
        virkningsdato = behandling.virkningsdato,
        soknadRefId = behandling.soknadRefId,
        grunnlagspakkeid = behandling.grunnlagspakkeid,
        årsak = behandling.aarsak,
        virkningstidspunktsbegrunnelseIVedtakOgNotat = behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat,
        virkningstidspunktsbegrunnelseKunINotat = behandling.virkningstidspunktbegrunnelseKunINotat,
        boforholdsbegrunnelseIVedtakOgNotat = behandling.boforholdsbegrunnelseIVedtakOgNotat,
        boforholdsbegrunnelseKunINotat = behandling.boforholdsbegrunnelseKunINotat,
        inntektsbegrunnelseIVedtakOgNotat = behandling.inntektsbegrunnelseIVedtakOgNotat,
        inntektsbegrunnelseKunINotat = behandling.inntektsbegrunnelseKunINotat,
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

    private fun ingenBarnMedVerkenIdentEllerNavn(roller: Set<OpprettRolleDto>): Boolean {
        return roller.filter { r -> r.rolletype == Rolletype.BARN && r.ident?.verdi.isNullOrBlank() }
            .none { r -> r.navn.isNullOrBlank() }
    }

    private fun ingenVoksneUtenIdent(roller: Set<OpprettRolleDto>): Boolean {
        return roller.none { r -> r.rolletype != Rolletype.BARN && r.ident?.verdi.isNullOrBlank() }
    }
}
