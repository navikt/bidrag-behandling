package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerStatus
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.toV2
import no.nav.bidrag.behandling.transformers.behandling.tilAktivGrunnlagsdata
import no.nav.bidrag.behandling.transformers.behandling.tilBehandlingDtoV2
import no.nav.bidrag.behandling.transformers.behandling.tilBoforholdV2
import no.nav.bidrag.behandling.transformers.behandling.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toHusstandsbarn
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.behandling.transformers.validerKanOppdatere
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.apache.commons.lang3.Validate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val forsendelseService: ForsendelseService,
    private val boforholdService: BoforholdService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val grunnlagService: GrunnlagService,
    private val inntektService: InntektService,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun slettBehandling(behandlingId: Long) {
        val behandling = hentBehandlingById(behandlingId)
        if (behandling.erVedtakFattet) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke slette behandling hvor vedtak er fattet",
            )
        }

        log.info { "Logisk sletter behandling $behandlingId" }
        behandlingRepository.logiskSlett(behandling.id!!)
    }

    fun opprettBehandling(behandling: Behandling): Behandling =
        behandlingRepository.findFirstBySoknadsid(behandling.soknadsid)?.let {
            log.info { "Fant eksisterende behandling ${it.id} for søknadsId ${behandling.soknadsid}. Oppretter ikke ny behandling" }
            return it
        } ?: run {
            behandlingRepository.save(behandling).let {
                opprettForsendelseForBehandling(it)
                it
            }
        }

    fun opprettBehandling(opprettBehandling: OpprettBehandlingRequest): OpprettBehandlingResponse {
        tilgangskontrollService.sjekkTilgangSak(opprettBehandling.saksnummer)
        behandlingRepository.findFirstBySoknadsid(opprettBehandling.søknadsid)?.let {
            log.info { "Fant eksisterende behandling ${it.id} for søknadsId ${opprettBehandling.søknadsid}. Oppretter ikke ny behandling" }
            return OpprettBehandlingResponse(it.id!!)
        }

        ingenBarnMedVerkenIdentEllerNavn(opprettBehandling.roller)
        ingenVoksneUtenIdent(opprettBehandling.roller)

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
                virkningstidspunkt = opprettBehandling.søktFomDato,
                årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT,
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

    @Transactional
    fun aktiverGrunnlag(
        behandlingsid: Long,
        request: AktivereGrunnlagRequestV2,
    ): AktivereGrunnlagResponseV2 {
        behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                log.info { "Aktiverer grunnlag for $behandlingsid med type ${request.grunnlagstype}" }
                secureLogger.info {
                    "Aktiverer grunnlag for $behandlingsid med type ${request.grunnlagstype} " +
                        "for person ${request.personident}"
                }
                grunnlagService.aktivereGrunnlag(it, request)
                val gjeldendeAktiveGrunnlagsdata = it.grunnlagListe.toSet().hentSisteAktiv()
                val ikkeAktiverteEndringerIGrunnlagsdata =
                    grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(it)
                return AktivereGrunnlagResponseV2(
                    boforhold = it.tilBoforholdV2(),
                    inntekter = it.tilInntektDtoV2(gjeldendeAktiveGrunnlagsdata),
                    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.tilAktivGrunnlagsdata(),
                    ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata,
                )
            }
    }

    @Transactional
    fun oppdatereVirkningstidspunkt(
        behandlingsid: Long,
        request: OppdatereVirkningstidspunkt,
    ): Behandling {
        return behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                log.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid" }
                secureLogger.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid, forespørsel=$request" }
                request.valider(it)
                it.årsak = if (request.avslag != null) null else request.årsak ?: it.årsak
                it.avslag = if (request.årsak != null) null else request.avslag ?: it.avslag
                it.virkningstidspunktbegrunnelseKunINotat =
                    request.notat?.kunINotat ?: it.virkningstidspunktbegrunnelseKunINotat
                oppdaterVirkningstidspunkt(request, it)
                it
            }
    }

    @Transactional
    fun oppdaterVirkningstidspunkt(
        request: OppdatereVirkningstidspunkt,
        behandling: Behandling,
    ) {
        val erVirkningstidspunktEndret = request.virkningstidspunkt != behandling.virkningstidspunkt
        if (erVirkningstidspunktEndret) {
            behandling.virkningstidspunkt = request.virkningstidspunkt ?: behandling.virkningstidspunkt
            log.info { "Virkningstidspunkt er endret. Beregner husstandsmedlem perioder på nytt for behandling ${behandling.id}" }
            // Bearbeida boforhold per husstandsmedlem vil påvirkes av endringer i virkningsdato
            grunnlagService.oppdatereBearbeidaBoforhold(behandling)
            grunnlagService.aktivereBearbeidaBoforholdEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling.id!!)

            log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling.id!!)
        }
    }

    @Transactional
    fun oppdaterBehandling(
        behandlingsid: Long,
        request: OppdaterBehandlingRequestV2,
    ): Behandling {
        return behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                it.validerKanOppdatere()
                log.info { "Oppdatere behandling $behandlingsid" }
                SECURE_LOGGER.info("Oppdatere behandling $behandlingsid for forespørsel $request")
                request.aktivereGrunnlagForPerson.let { aktivereGrunnlagRequest ->
                    if (aktivereGrunnlagRequest != null) {
                        log.info { "Aktivere nyinnhenta grunnlag for behandling med id $behandlingsid" }
                        grunnlagService.aktivereGrunnlag(it, aktivereGrunnlagRequest.toV2())
                    }
                }
                request.virkningstidspunkt?.let { vt ->
                    log.info { "Oppdatere informasjon om virkningstidspunkt for behandling $behandlingsid" }
                    vt.valider(it)
                    val erVirkningstidspunktEndret = vt.virkningstidspunkt != it.virkningstidspunkt
                    it.årsak = vt.årsak
                    it.avslag = vt.avslag
                    it.virkningstidspunkt = vt.virkningstidspunkt
                    it.virkningstidspunktbegrunnelseKunINotat =
                        vt.notat?.kunINotat ?: it.virkningstidspunktbegrunnelseKunINotat

                    if (erVirkningstidspunktEndret) {
                        log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på inntekter behandling $behandlingsid" }
                        inntektService.rekalkulerPerioderInntekter(behandlingsid)
                    }
                }
                // TODO: Fjerne når boforhold v2-migrering er fullført
                request.inntekter?.let { inntekter ->
                    log.info { "Bakoverkompatibilitet - Oppdatere inntekter for behandling $behandlingsid" }
                    inntektService.oppdatereInntekterManuelt(behandlingsid, request.inntekter)
                    entityManager.refresh(it)
                    it.inntektsbegrunnelseKunINotat =
                        inntekter.notat?.kunINotat ?: it.inntektsbegrunnelseKunINotat
                }
                // TODO: Fjerne når boforhold v2-migrering er fullført
                request.boforhold?.let { bf ->
                    log.info { "Bakoverkompatibilitet - Oppdatere informasjon om boforhold for behandling $behandlingsid" }
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
                }
                it
            }
    }

    @Transactional
    fun oppdaterVedtakFattetStatus(
        behandlingsid: Long,
        vedtaksid: Long,
    ) {
        behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                log.info { "Oppdaterer vedtaksid til $vedtaksid for behandling $behandlingsid" }
                it.vedtaksid = vedtaksid
                it.vedtakstidspunkt = it.vedtakstidspunkt ?: LocalDateTime.now()
                it.vedtakFattetAv = it.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                    ?: TokenUtils.hentApplikasjonsnavn()
            }
    }

    fun henteBehandling(behandlingsid: Long): BehandlingDtoV2 {
        val behandling = hentBehandlingById(behandlingsid)
        tilgangskontrollService.sjekkTilgangBehandling(behandling)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)

        val grunnlagsdataEndretEtterAktivering =
            grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(behandling)

        return behandling.tilBehandlingDtoV2(
            behandling.grunnlagListe.toSet().hentSisteAktiv(),
            grunnlagsdataEndretEtterAktivering,
        )
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingId)
                .orElseThrow { behandlingNotFoundException(behandlingId) }
        tilgangskontrollService.sjekkTilgangBehandling(behandling)
        if (behandling.deleted) behandlingNotFoundException(behandlingId)
        return behandling
    }

    @Transactional
    fun oppdaterRoller(
        behandlingId: Long,
        oppdaterRollerListe: List<OpprettRolleDto>,
    ): OppdaterRollerResponse {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        tilgangskontrollService.sjekkTilgangBehandling(behandling)
        if (behandling.erVedtakFattet) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke oppdatere behandling hvor vedtak er fattet",
            )
        }

        log.info { "Oppdater roller i behandling $behandlingId" }
        secureLogger.info { "Oppdater roller i behandling $behandlingId: $oppdaterRollerListe" }
        val eksisterendeRoller = behandling.roller
        val rollerSomLeggesTil =
            oppdaterRollerListe
                .filter { !it.erSlettet }
                .filter { !eksisterendeRoller.any { br -> br.ident == it.ident?.verdi } }

        val identerSomSkalLeggesTil = rollerSomLeggesTil.mapNotNull { it.ident?.verdi }
        identerSomSkalLeggesTil.isNotEmpty().ifTrue {
            secureLogger.info {
                "Legger til søknadsbarn ${
                    identerSomSkalLeggesTil.joinToString(",")
                } til behandling $behandlingId"
            }
        }
        behandling.roller.addAll(rollerSomLeggesTil.map { it.toRolle(behandling) })

        val rollerSomSkalSlettes = oppdaterRollerListe.filter { r -> r.erSlettet }
        val identerSomSkalSlettes = rollerSomSkalSlettes.mapNotNull { it.ident?.verdi }
        identerSomSkalSlettes.isNotEmpty().ifTrue {
            secureLogger.info { "Sletter søknadsbarn ${identerSomSkalSlettes.joinToString(",")} fra behandling $behandlingId" }
        }
        behandling.roller.removeIf { r ->
            val skalSlettes = identerSomSkalSlettes.contains(r.ident)
            skalSlettes.ifTrue {
                log.info { "Sletter rolle ${r.id} fra behandling $behandlingId" }
            }
            skalSlettes
        }

        oppdaterHusstandsbarnForRoller(behandling, rollerSomLeggesTil)

        behandlingRepository.save(behandling)

        if (behandling.søknadsbarn.isEmpty()) {
            log.info { "Alle barn i behandling $behandlingId er slettet. Sletter behandling" }
            behandlingRepository.logiskSlett(behandling.id!!)
            return OppdaterRollerResponse(OppdaterRollerStatus.BEHANDLING_SLETTET)
        }

        return OppdaterRollerResponse(OppdaterRollerStatus.ROLLER_OPPDATERT)
    }

    private fun oppdaterHusstandsbarnForRoller(
        behandling: Behandling,
        rollerSomLeggesTil: List<OpprettRolleDto>,
    ) {
        val nyeRollerSomIkkeHarHusstandsbarn =
            rollerSomLeggesTil.filter { nyRolle -> behandling.husstandsbarn.none { it.ident == nyRolle.ident?.verdi } }
        behandling.husstandsbarn.addAll(
            nyeRollerSomIkkeHarHusstandsbarn.map {
                secureLogger.info { "Legger til husstandsbarn med ident ${it.ident?.verdi} i behandling ${behandling.id}" }
                it.toHusstandsbarn(
                    behandling,
                )
            },
        )
    }

    private fun ingenBarnMedVerkenIdentEllerNavn(roller: Set<OpprettRolleDto>) {
        roller.filter { r -> r.rolletype == Rolletype.BARN }
            .forEach { Validate.isTrue(!it.ident?.verdi.isNullOrBlank() || !it.navn.isNullOrBlank()) }
    }

    private fun ingenVoksneUtenIdent(roller: Set<OpprettRolleDto>) {
        roller.filter { r -> r.rolletype != Rolletype.BARN }
            .forEach { Validate.isTrue(!it.ident?.verdi.isNullOrBlank()) }
    }
}
