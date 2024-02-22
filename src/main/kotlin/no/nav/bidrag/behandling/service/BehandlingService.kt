package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.RolleRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.apache.commons.lang3.Validate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val rolleRepository: RolleRepository,
    private val forsendelseService: ForsendelseService,
    private val grunnlagService: GrunnlagService,
    private val inntektService: InntektService,
    private val entityManager: EntityManager,
) {
    private fun opprettBehandling(behandling: Behandling): Behandling =
        behandlingRepository.save(behandling).let {
            opprettForsendelseForBehandling(it)
            it
        }

    fun opprettBehandling(opprettBehandling: OpprettBehandlingRequest): OpprettBehandlingResponse {
        Validate.isTrue(
            ingenBarnMedVerkenIdentEllerNavn(opprettBehandling.roller) &&
                ingenVoksneUtenIdent(
                    opprettBehandling.roller,
                ),
        )

        Validate.isTrue(
            opprettBehandling.stønadstype != null || opprettBehandling.engangsbeløpstype != null,
            "${OpprettBehandlingRequest::stønadstype.name} eller " +
                "${OpprettBehandlingRequest::engangsbeløpstype.name} må være satt i forespørselen",
        )

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils.hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        val behandling =
            Behandling(
                vedtakstype = opprettBehandling.vedtakstype,
                søktFomDato = opprettBehandling.søktFomDato,
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
        val behandlingDo = opprettBehandling(behandling)

        grunnlagService.oppdatereGrunnlagForBehandling(behandlingDo)

        log.info {
            "Opprettet behandling for stønadstype ${opprettBehandling.stønadstype} og engangsbeløptype " +
                "${opprettBehandling.engangsbeløpstype} vedtakstype ${opprettBehandling.vedtakstype} " +
                "og søknadFra ${opprettBehandling.søknadFra} med id ${behandlingDo.id} "
        }
        return OpprettBehandlingResponse(behandlingDo.id!!)
    }

    private fun opprettForsendelseForBehandling(behandling: Behandling) {
        forsendelseService.slettEllerOpprettForsendelse(
            no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest(
                saksnummer = behandling.saksnummer,
                enhet = behandling.behandlerEnhet,
                roller = behandling.tilForsendelseRolleDto(),
                behandlingInfo =
                    BehandlingInfoDto(
                        behandlingId = behandling.id,
                        soknadId = behandling.soknadsid,
                        soknadFra = behandling.soknadFra,
                        behandlingType = behandling.tilBehandlingstype(),
                        stonadType = behandling.stonadstype,
                        engangsBelopType = behandling.engangsbeloptype,
                        vedtakType = behandling.vedtakstype,
                    ),
            ),
        )
    }

    fun deleteBehandlingById(behandlingId: Long) = behandlingRepository.deleteById(behandlingId)

    @Transactional
    fun oppdaterBehandling(
        behandlingsid: Long,
        request: OppdaterBehandlingRequestV2,
    ) {
        behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                log.info { "Oppdatere behandling $behandlingsid" }
                SECURE_LOGGER.info("Oppdatere behandling $behandlingsid for forespørsel $request")
                it.grunnlagspakkeid = request.grunnlagspakkeId ?: it.grunnlagspakkeid
                it.vedtaksid = request.vedtaksid ?: it.vedtaksid
                request.aktivereGrunnlag.let { grunnlagsider ->
                    if (grunnlagsider.isNotEmpty()) {
                        log.info { "Aktivere nyinnhenta grunnlag for behandling med id $behandlingsid" }
                        grunnlagService.aktivereGrunnlag(grunnlagsider)
                    }
                }
                request.inntekter?.let { inntekter ->
                    log.info { "Oppdatere inntekter for behandling $behandlingsid" }
                    inntektService.oppdatereInntekterManuelt(behandlingsid, request.inntekter)
                    entityManager.refresh(it)
                    it.inntektsbegrunnelseKunINotat =
                        inntekter.notat?.kunINotat ?: it.inntektsbegrunnelseKunINotat
                    it.inntektsbegrunnelseIVedtakOgNotat =
                        inntekter.notat?.medIVedtaket ?: it.inntektsbegrunnelseIVedtakOgNotat
                }
                request.virkningstidspunkt?.let { vt ->
                    log.info { "Oppdatere informasjon om virkningstidspunkt for behandling $behandlingsid" }
                    it.årsak = vt.årsak
                    it.avslag = vt.avslag
                    it.virkningstidspunkt = vt.virkningstidspunkt
                    it.virkningstidspunktbegrunnelseKunINotat =
                        vt.notat?.kunINotat ?: it.virkningstidspunktbegrunnelseKunINotat
                    it.virkningstidspunktsbegrunnelseIVedtakOgNotat =
                        vt.notat?.medIVedtaket
                            ?: it.virkningstidspunktsbegrunnelseIVedtakOgNotat
                }
                request.boforhold?.let { bf ->
                    log.info { "Oppdatere informasjon om boforhold for behandling $behandlingsid" }
                    bf.sivilstand?.run {
                        it.sivilstand.clear()
                        it.sivilstand.addAll(bf.sivilstand.toSivilstandDomain(it))
                    }
                    bf.husstandsbarn?.run {
                        it.husstandsbarn.clear()
                        it.husstandsbarn.addAll(bf.husstandsbarn.toDomain(it))
                    }
                    entityManager.merge(it)
                    it.boforholdsbegrunnelseKunINotat =
                        bf.notat?.kunINotat ?: it.boforholdsbegrunnelseKunINotat
                    it.boforholdsbegrunnelseIVedtakOgNotat =
                        bf.notat?.medIVedtaket ?: it.boforholdsbegrunnelseIVedtakOgNotat
                }
                it
            }
    }

    fun henteBehandling(behandlingsid: Long): BehandlingDtoV2 {
        val behandling = hentBehandlingById(behandlingsid)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)

        val gjeldendeAktiveGrunnlagsdata =
            grunnlagService.henteGjeldendeAktiveGrunnlagsdata(behandlingsid)
        val grunnlagsdataEndretEtterAktivering =
            grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(behandlingsid, behandling.roller)
        return behandling.tilBehandlingDtoV2(gjeldendeAktiveGrunnlagsdata, grunnlagsdataEndretEtterAktivering)
    }

    fun hentBehandlingById(behandlingId: Long): Behandling =
        behandlingRepository.findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }

    @Transactional
    fun syncRoller(
        behandlingId: Long,
        roller: List<OpprettRolleDto>,
    ) {
        val existingRoller = rolleRepository.findRollerByBehandlingId(behandlingId)

        val behandling = behandlingRepository.findById(behandlingId).get()

        val rollerSomLeggesTil =
            roller.filter { r ->
                // not deleted and behandling.roller doesn't contain it yet
                !r.erSlettet && !existingRoller.any { br -> br.ident == r.ident?.verdi }
            }

        behandling.roller.addAll(rollerSomLeggesTil.map { it.toRolle(behandling) })

        val identsSomSkalSlettes = roller.filter { r -> r.erSlettet }.map { it.ident?.verdi }
        behandling.roller.removeIf { r -> identsSomSkalSlettes.contains(r.ident) }

        behandlingRepository.save(behandling)

        if (behandling.roller.none { r -> r.rolletype == Rolletype.BARN }) {
            behandlingRepository.delete(behandling)
        }
    }

    private fun ingenBarnMedVerkenIdentEllerNavn(roller: Set<OpprettRolleDto>): Boolean {
        return roller.filter { r -> r.rolletype == Rolletype.BARN && r.ident?.verdi.isNullOrBlank() }
            .none { r -> r.navn.isNullOrBlank() }
    }

    private fun ingenVoksneUtenIdent(roller: Set<OpprettRolleDto>): Boolean {
        return roller.none { r -> r.rolletype != Rolletype.BARN && r.ident?.verdi.isNullOrBlank() }
    }

    private fun forespørselInneholderBmOgBarn(roller: Set<OpprettRolleDto>): Boolean {
        return roller.filter { r -> r.rolletype == Rolletype.BIDRAGSMOTTAKER }
            .isNotEmpty() && roller.filter { r -> r.rolletype == Rolletype.BARN }.isNotEmpty()
    }
}
