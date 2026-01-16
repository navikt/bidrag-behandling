package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.async.BestillAsyncJobService
import no.nav.bidrag.behandling.async.dto.BehandlingHendelseBestilling
import no.nav.bidrag.behandling.async.dto.BehandlingOppdateringBestilling
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.extensions.BehandlingMetadataDo
import no.nav.bidrag.behandling.database.datamodell.extensions.hentDefaultÅrsak
import no.nav.bidrag.behandling.database.datamodell.json.FattetVedtak
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.json.VedtakDetaljer
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerStatus
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.erBidrag
import no.nav.bidrag.behandling.dto.v1.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v1.behandling.tilType
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.behandling.oppdaterBehandlingEtterOppdatertRoller
import no.nav.bidrag.behandling.transformers.behandling.tilBehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.transformers.finnEksisterendeVedtakMedOpphør
import no.nav.bidrag.behandling.transformers.kreverGrunnlag
import no.nav.bidrag.behandling.transformers.opprettForsendelse
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.EnhetProvider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.behandling.ÅpenBehandling
import no.nav.bidrag.transport.behandling.behandling.ÅpenBehandlingBarn
import no.nav.bidrag.transport.behandling.behandling.ÅpenBehandlingBarnSøknad
import no.nav.bidrag.transport.behandling.hendelse.BehandlingHendelseType
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val forsendelseService: ForsendelseService,
    private val virkningstidspunktService: VirkningstidspunktService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val grunnlagService: GrunnlagService,
    private val mapper: Dtomapper,
    private val validerBehandlingService: ValiderBehandlingService,
    private val underholdService: UnderholdService,
    private val bestillAsyncJobService: BestillAsyncJobService? = null,
    @Lazy
    private val forholdsmessigFordelingService: ForholdsmessigFordelingService? = null,
) {
    @Transactional
    fun slettBehandling(
        behandlingId: Long,
        søknadsid: Long? = null,
    ) {
        val behandling = hentBehandlingById(behandlingId)
        if (behandling.erVedtakFattet) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke slette behandling hvor vedtak er fattet",
            )
        }

        slettBehandling(behandling, søknadsid)
    }

    fun slettBehandling(
        behandling: Behandling,
        søknadsid: Long? = null,
    ) {
        if (behandling.erIForholdsmessigFordeling && UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled) {
            if (søknadsid == null) {
                forholdsmessigFordelingService!!.avsluttForholdsmessigFordeling(
                    behandling,
                    behandling.søknadsbarnForSøknad(behandling.soknadsid!!),
                    behandling.soknadsid!!,
                )
                logiskSlettBehandling(behandling)
            } else {
                val barnSomSkalSlettes =
                    behandling.søknadsbarn
                        .filter { it.forholdsmessigFordeling!!.søknaderUnderBehandling.any { it.søknadsid == søknadsid } }
                forholdsmessigFordelingService!!.slettBarnEllerBehandling(barnSomSkalSlettes, behandling, søknadsid)
                behandling.bidragspliktig?.fjernGebyr(søknadsid)
            }
        } else {
            logiskSlettBehandling(behandling)
        }
    }

    fun sendOppdatertHendelse(
        behandlingId: Long,
        slettet: Boolean,
    ) {
        bestillAsyncJobService!!.bestillHendelse(
            BehandlingHendelseBestilling(
                behandlingId,
                if (slettet) BehandlingHendelseType.AVSLUTTET else BehandlingHendelseType.ENDRET,
            ),
        )
    }

    fun logiskSlettBehandling(behandling: Behandling) {
        log.debug { "Logisk sletter behandling ${behandling.id}" }
        behandlingRepository.logiskSlett(behandling.id!!)
        if (behandling.erIForholdsmessigFordeling) {
            val søknaderToUpdate =
                behandling.roller
                    .filter { !it.erRevurderingsbarn }
                    .flatMap { it.forholdsmessigFordeling!!.søknaderUnderBehandling }
                    .filter { it.søknadsid == behandling.soknadsid }

            søknaderToUpdate.forEach { it.status = Behandlingstatus.FEILREGISTRERT }
        }

        sendOppdatertHendelse(behandling.id!!, true)
    }

    fun hentEksisteredenBehandling(søknadsid: Long): Behandling? = behandlingRepository.findFirstBySoknadsid(søknadsid)

    fun lagreBehandling(
        behandling: Behandling,
        opprettForsendelse: Boolean = false,
        forceSave: Boolean = false,
    ): Behandling {
        val oppretterBehandling = behandling.id == null
        val lagretBehandling =
            if (oppretterBehandling || forceSave) {
                behandlingRepository.save(behandling)
            } else {
                behandling
            }
        bestillAsyncJobService!!.bestillHendelse(
            BehandlingHendelseBestilling(
                lagretBehandling.id!!,
                if (oppretterBehandling) {
                    BehandlingHendelseType.OPPRETTET
                } else {
                    BehandlingHendelseType.ENDRET
                },
            ),
        )
        if (behandling.vedtakstype.opprettForsendelse() && (oppretterBehandling || opprettForsendelse)) {
            opprettForsendelseForBehandling(behandling.id!!)
        }
        return lagretBehandling
    }

    fun opprettBehandlingHvisIkkeEksisterer(behandling: Behandling) =
        hentEksisteredenBehandling(behandling.soknadsid!!)?.let {
            log.debug { "Fant eksisterende behandling ${it.id} for søknadsId ${behandling.soknadsid}. Oppretter ikke ny behandling" }
            return it
        } ?: run {
            lagreBehandling(behandling)
        }

    @Transactional
    fun opprettBehandling(opprettBehandling: OpprettBehandlingRequest): OpprettBehandlingResponse {
        opprettBehandling.roller.forEach { rolle ->
            rolle.ident?.let {
                tilgangskontrollService.sjekkTilgangPersonISak(
                    it,
                    Saksnummer(opprettBehandling.saksnummer),
                )
            }
        }
        val søknadsid = opprettBehandling.søknadsid

        if (opprettBehandling.erBidrag() && UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled &&
            opprettBehandling.behandlingstype != null &&
            !behandlingstyperSomIkkeSkalInkluderesIFF.contains(opprettBehandling.behandlingstype)
        ) {
            val bp = opprettBehandling.roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
            behandlingRepository.finnHovedbehandlingForBpVedFF(bp!!.ident!!.verdi)?.let { behandling ->
                val bm = opprettBehandling.roller.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
                val søknadsdetaljer =
                    ForholdsmessigFordelingSøknadBarn(
                        søknadsid = søknadsid,
                        søknadFomDato = opprettBehandling.søktFomDato,
                        søktAvType = opprettBehandling.søknadFra,
                        innkreving = opprettBehandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                        mottattDato = opprettBehandling.mottattdato,
                        behandlingstema = opprettBehandling.behandlingstema,
                        behandlingstype = opprettBehandling.behandlingstype,
                        enhet = opprettBehandling.behandlerenhet,
                    )
                forholdsmessigFordelingService!!.leggTilEllerSlettBarnFraBehandlingSomErIFF(
                    opprettBehandling.roller.toList(),
                    emptyList(),
                    behandling,
                    søknadsid,
                    opprettBehandling.saksnummer,
                    bm?.ident?.verdi,
                    behandlerenhet = opprettBehandling.behandlerenhet,
                    erRevurdering = opprettBehandling.vedtakstype == Vedtakstype.REVURDERING,
                    medInnkreving = opprettBehandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                    søknadsdetaljer = søknadsdetaljer,
                    søktFraDato = opprettBehandling.søktFomDato,
                    gebyrGjelder18År = opprettBehandling.gebyrGjelder18År,
                    stønadstype = opprettBehandling.stønadstype!!,
                )
                return OpprettBehandlingResponse(behandling.id!!)
            }
        }
        behandlingRepository.findFirstBySoknadsid(søknadsid)?.let {
            log.info { "Fant eksisterende behandling ${it.id} for søknadsId $søknadsid. Oppretter ikke ny behandling" }
            return OpprettBehandlingResponse(it.id!!)
        }

        opprettBehandling.valider()
        validerBehandlingService.validerKanBehandlesINyLøsning(opprettBehandling.tilKanBehandlesINyLøsningRequest())

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils
                .hentSaksbehandlerIdent()
                ?.let { EnhetProvider.hentSaksbehandlernavn(it) }
        val virkningstidspunkt =
            when (opprettBehandling.tilType()) {
                TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR -> opprettBehandling.søktFomDato
                TypeBehandling.SÆRBIDRAG -> LocalDate.now().withDayOfMonth(1)
            }
        val årsak = hentDefaultÅrsak(opprettBehandling.tilType(), opprettBehandling.vedtakstype)
        val avslag =
            when (opprettBehandling.tilType()) {
                TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR -> {
                    if (opprettBehandling.vedtakstype == Vedtakstype.OPPHØR) {
                        if (opprettBehandling.stønadstype == Stønadstype.BIDRAG18AAR) {
                            Resultatkode.AVSLUTTET_SKOLEGANG
                        } else if (opprettBehandling.stønadstype == Stønadstype.BIDRAG) {
                            Resultatkode.IKKE_OMSORG_FOR_BARNET
                        } else {
                            Resultatkode.IKKE_OMSORG
                        }
                    } else {
                        null
                    }
                }

                TypeBehandling.SÆRBIDRAG -> {
                    null
                }
            }
        val behandling =
            Behandling(
                søknadstype = opprettBehandling.søknadstype ?: opprettBehandling.behandlingstype,
                behandlingstema = opprettBehandling.behandlingstema,
                vedtakstype = opprettBehandling.vedtakstype,
                søktFomDato = opprettBehandling.søktFomDato,
                innkrevingstype =
                    when (opprettBehandling.tilType()) {
                        TypeBehandling.FORSKUDD -> Innkrevingstype.MED_INNKREVING
                        else -> opprettBehandling.innkrevingstype
                    },
                virkningstidspunkt = virkningstidspunkt,
                årsak = årsak,
                avslag = avslag,
                mottattdato = opprettBehandling.mottattdato,
                saksnummer = opprettBehandling.saksnummer,
                soknadsid = opprettBehandling.søknadsid,
                behandlerEnhet = opprettBehandling.behandlerenhet,
                soknadFra = opprettBehandling.søknadFra,
                stonadstype = opprettBehandling.stønadstype,
                engangsbeloptype = opprettBehandling.engangsbeløpstype,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = TokenUtils.hentApplikasjonsnavn() ?: "ukjent",
                kategori = opprettBehandling.kategori?.kategori,
                kategoriBeskrivelse = opprettBehandling.kategori?.beskrivelse,
            )

        opprettBehandling.søknadsreferanseid?.let {
            behandling.omgjøringsdetaljer =
                Omgjøringsdetaljer(
                    soknadRefId = it,
                )
        }

        if (opprettBehandling.vedtakstype == Vedtakstype.ALDERSJUSTERING) {
            val metadata = BehandlingMetadataDo()
            metadata.setFølgerAutomatiskVedtak(opprettBehandling.vedtaksid)
            behandling.metadata = metadata
        }
        behandling.roller.addAll(
            HashSet(
                opprettBehandling.roller.map {
                    it.toRolle(behandling)
                },
            ),
        )

        if (opprettBehandling.tilType() == TypeBehandling.SÆRBIDRAG) {
            behandling.utgift = Utgift(behandling = behandling)
        }

        if (opprettBehandling.tilType() == TypeBehandling.BIDRAG) {
            behandling.samvær = behandling.søknadsbarn.map { Samvær(behandling, rolle = it) }.toMutableSet()
            if (opprettBehandling.vedtakstype == Vedtakstype.INNKREVING) {
                behandling.søknadsbarn.forEach {
                    val privatAvtale = PrivatAvtale(rolle = it, behandling = behandling, avtaleType = PrivatAvtaleType.PRIVAT_AVTALE)
                    behandling.privatAvtale.add(privatAvtale)
                }
            }
        }
        if (TypeBehandling.BIDRAG == opprettBehandling.tilType() && opprettBehandling.vedtakstype.kreverGrunnlag()) {
            // Oppretter underholdskostnad for alle barna i behandlingen ved bidrag
            opprettBehandling.roller.filter { Rolletype.BARN == it.rolletype }.forEach {
                behandling.underholdskostnader.add(
                    underholdService.oppretteUnderholdskostnad(behandling, BarnDto(personident = it.ident)),
                )
            }
        }

        val behandlingDo = opprettBehandlingHvisIkkeEksisterer(behandling)

        grunnlagService.oppdaterGrunnlagForBehandlingAsync(behandlingDo)

        behandling.søknadsbarn.forEach { rolle ->
            behandling.finnEksisterendeVedtakMedOpphør(rolle)?.let {
                val opphørsdato = if (it.opphørsdato.isAfter(behandling.virkningstidspunkt!!)) it.opphørsdato else null
                if (opphørsdato != null) {
                    virkningstidspunktService.oppdaterOpphørsdato(
                        behandling.id!!,
                        OppdaterOpphørsdatoRequestDto(
                            rolle.id!!,
                            opphørsdato,
                        ),
                    )
                }
            }
        }
        log.debug {
            "Opprettet behandling for stønadstype ${opprettBehandling.stønadstype} og engangsbeløptype " +
                "${opprettBehandling.engangsbeløpstype} vedtakstype ${opprettBehandling.vedtakstype} " +
                "og søknadFra ${opprettBehandling.søknadFra} med id ${behandlingDo.id} "
        }
        return OpprettBehandlingResponse(behandlingDo.id!!)
    }

    fun opprettForsendelseForBehandling(behandlingId: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        forsendelseService.slettEllerOpprettForsendelse(
            no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest(
                saksnummer = behandling.saksnummer,
                enhet = behandling.behandlerEnhet,
                roller = behandling.tilForsendelseRolleDto(behandling.saksnummer),
                behandlingInfo =
                    BehandlingInfoDto(
                        behandlingId = behandling.id?.toString(),
                        soknadId = behandling.soknadsid?.toString(),
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
    fun aktivereGrunnlag(
        behandlingsid: Long,
        request: AktivereGrunnlagRequestV2,
    ): AktivereGrunnlagResponseV2 {
        hentBehandlingById(behandlingsid)
            .let {
                log.info { "Aktiverer grunnlag for $behandlingsid med type ${request.grunnlagstype}" }
                secureLogger.debug {
                    "Aktiverer grunnlag for $behandlingsid med type ${request.grunnlagstype} " +
                        "for person ${request.personident}: $request"
                }
                grunnlagService.aktivereGrunnlag(it, request)
                return mapper.tilAktivereGrunnlagResponseV2(it)
            }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun oppdaterDelvedtakFattetStatus(
        behandlingsid: Long,
        fattetAvEnhet: String,
        resultat: FattetVedtak,
    ) {
        behandlingRepository
            .finnAlleRelaterteBehandlinger(behandlingsid)
            .forEach {
                log.info {
                    "Oppdaterer behandling $behandlingsid med fattet delvedtak ${resultat.vedtaksid} - $resultat"
                }

                val eksisterendeDetaljer = it.vedtakDetaljer ?: VedtakDetaljer()
                it.vedtakDetaljer =
                    eksisterendeDetaljer
                        .copy(
                            vedtakFattetAvEnhet = fattetAvEnhet,
                            vedtakstidspunkt = LocalDateTime.now(),
                            vedtakFattetAv =
                                eksisterendeDetaljer.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                                    ?: TokenUtils.hentApplikasjonsnavn(),
                            fattetVedtak =
                                (eksisterendeDetaljer.fattetVedtak + setOf(resultat)).toSet(),
                        )
            }
    }

    @Transactional
    fun oppdaterVedtakFattetStatus(
        behandlingsid: Long,
        vedtaksid: Int,
        fattetAvEnhet: String,
        unikreferanse: String? = null,
    ) {
        behandlingRepository
            .finnAlleRelaterteBehandlinger(behandlingsid)
            .forEach {
                log.info { "Oppdaterer vedtaksid til $vedtaksid for behandling $behandlingsid" }

                val eksisterendeDetaljer = it.vedtakDetaljer ?: VedtakDetaljer()
                it.vedtakDetaljer =
                    eksisterendeDetaljer
                        .copy(
                            vedtaksid = vedtaksid,
                            vedtakFattetAvEnhet = fattetAvEnhet,
                            vedtakstidspunkt = eksisterendeDetaljer.vedtakstidspunkt ?: LocalDateTime.now(),
                            unikreferanse = unikreferanse,
                            vedtakFattetAv =
                                eksisterendeDetaljer.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                                    ?: TokenUtils.hentApplikasjonsnavn(),
                        )

                // TODO: Fjern disse verdiene når migreringen er over
                it.vedtaksid = vedtaksid
                it.vedtakFattetAvEnhet = fattetAvEnhet
                it.vedtakstidspunkt = it.vedtakstidspunkt ?: LocalDateTime.now()
                it.vedtakFattetAv = it.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                    ?: TokenUtils.hentApplikasjonsnavn()
                it.søknadsbarn.forEach {
                    it.behandlingstatus = Behandlingstatus.VEDTAK_FATTET
                }
            }

        bestillAsyncJobService!!.bestillHendelse(
            BehandlingHendelseBestilling(
                behandlingsid,
                BehandlingHendelseType.AVSLUTTET,
            ),
        )
    }

    fun henteBehandlingDetaljer(behandlingsid: Long): BehandlingDetaljerDtoV2 {
        val behandling = hentBehandlingById(behandlingsid)
        tilgangskontrollService.sjekkTilgangBehandling(behandling)
        return behandling.tilBehandlingDetaljerDtoV2()
    }

    fun henteBehandlingDetaljerForSøknadsid(søknadsid: Long): BehandlingDetaljerDtoV2 {
        log.debug { "Henter behandling for søknadsId $søknadsid." }
        return behandlingRepository.findFirstBySoknadsid(søknadsid)?.let {
            log.info { "Fant behandling ${it.id} for søknadsId $søknadsid med type ${it.tilType()}." }
            tilgangskontrollService.sjekkTilgangBehandling(it)
            it.tilBehandlingDetaljerDtoV2()
        } ?: run {
            log.info { "Fant ingen behandling for søknadsId $søknadsid." }
            behandlingNotFoundException(søknadsid)
        }
    }

    @Transactional
    fun henteBehandling(
        behandlingsid: Long,
        ikkeHentGrunnlag: Boolean = false,
    ): Behandling {
        val behandling = hentBehandlingById(behandlingsid)
        if (!ikkeHentGrunnlag) {
            grunnlagService.oppdaterGrunnlagForBehandlingAsync(behandling)
        }
        virkningstidspunktService.run {
            behandling.oppdatereVirkningstidspunktSærbidrag()
        }
        return behandling
    }

    @Transactional(readOnly = true)
    fun hentÅpneBehandlingerMedFF(bpIdent: String): List<ÅpenBehandling> =
        behandlingRepository
            .finnÅpneBidragsbehandlingerForBpMedFF(bpIdent)
            .filter { it.stonadstype != null }
            .map {
                ÅpenBehandling(
                    it.stonadstype!!,
                    it.id!!,
                    it.søknadsbarn.map {
                        ÅpenBehandlingBarn(
                            saksnummer = it.forholdsmessigFordeling!!.tilhørerSak,
                            ident = it.ident!!,
                            bidragsmottakerIdent = it.bidragsmottaker?.ident!!,
                            søknader =
                                it.forholdsmessigFordeling!!.søknaderUnderBehandling.map {
                                    ÅpenBehandlingBarnSøknad(
                                        mottattDato = it.mottattDato,
                                        søknadsid = it.søknadsid!!,
                                        søktAvType = it.søktAvType,
                                        søktFraDato = it.søknadFomDato,
                                        behandlingstema = it.behandlingstema,
                                        behandlingstype = it.behandlingstype,
                                    )
                                },
                        )
                    },
                )
            }

    @Transactional(readOnly = true)
    fun hentÅpneBehandlinger(barnIdent: String): List<ÅpenBehandling> =
        behandlingRepository
            .finnÅpneBidragsbehandlingerForBarn(barnIdent)
            .filter { it.stonadstype != null }
            .map { ÅpenBehandling(it.stonadstype!!, it.id!!, emptyList()) }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingId)
                .orElseThrow { behandlingNotFoundException(behandlingId) }
                .let {
                    if (it.forholdsmessigFordeling != null &&
                        it.forholdsmessigFordeling?.erHovedbehandling == false
                    ) {
                        behandlingRepository.findBehandlingById(it.forholdsmessigFordeling!!.behandlesAvBehandling!!).getOrNull()
                            ?: behandlingNotFoundException(behandlingId)
                    } else {
                        it
                    }
                }

        tilgangskontrollService.sjekkTilgangBehandling(behandling)
        if (behandling.deleted) behandlingNotFoundException(behandlingId)
        return behandling
    }

    @Transactional(readOnly = true)
    fun oppdaterRollerAsync(
        behandlingId: Long,
        request: OppdaterRollerRequest,
    ) {
        bestillAsyncJobService!!.bestillOppdateringAvRoller(
            BehandlingOppdateringBestilling(
                behandlingId = behandlingId,
                request = request,
            ),
        )
    }

    @Transactional
    fun oppdaterRoller(
        behandlingId: Long,
        request: OppdaterRollerRequest,
    ): OppdaterRollerResponse {
        val oppdaterRollerListe = request.roller
        val behandling =
            behandlingRepository.findBehandlingById(behandlingId).get().let {
                if (it.erIForholdsmessigFordeling && UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled) {
                    behandlingRepository.finnHovedbehandlingForBpVedFF(it.bidragspliktig!!.ident!!)!!
                } else {
                    it
                }
            }

        if (behandling.erVedtakFattet) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke oppdatere behandling hvor vedtak er fattet",
            )
        }
        val oppdaterRollerNyesteIdent =
            oppdaterRollerListe.map { rolle ->
                rolle.copy(
                    ident = oppdaterTilNyesteIdent(rolle.ident?.verdi, behandlingId)?.let { Personident(it) } ?: rolle.ident,
                )
            }

        secureLogger.debug { "Oppdater roller i behandling $behandlingId: $oppdaterRollerListe" }

        val eksisterendeRoller = behandling.roller

        if (request.søknadsid != null) {
            behandling.oppdaterEksisterendeRoller(request.søknadsid, request.saksnummer ?: behandling.saksnummer, oppdaterRollerNyesteIdent)
        }

        val rollerSomLeggesTil =
            oppdaterRollerNyesteIdent
                .filter { !it.erSlettet }
                .filter { !eksisterendeRoller.any { br -> br.ident == it.ident?.verdi } }
                .distinct()

        val identerSomSkalLeggesTil = rollerSomLeggesTil.mapNotNull { it.ident?.verdi }.distinct()
        identerSomSkalLeggesTil.isNotEmpty().ifTrue {
            secureLogger.debug {
                "Legger til søknadsbarn ${
                    identerSomSkalLeggesTil.joinToString(",")
                } til behandling $behandlingId"
            }
        }

        val rollerSomSkalSlettes = oppdaterRollerListe.filter { r -> r.erSlettet }.distinct()
        val identerSomSkalSlettes = rollerSomSkalSlettes.mapNotNull { it.ident?.verdi }.distinct()
        identerSomSkalSlettes.isNotEmpty().ifTrue {
            secureLogger.debug { "Sletter søknadsbarn ${identerSomSkalSlettes.joinToString(",")} fra behandling $behandlingId" }
        }
        if (behandling.erIForholdsmessigFordeling && UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled) {
            val revurderingsbarnSomLeggesTil =
                oppdaterRollerListe
                    .filter { r -> !r.erSlettet }
                    .filter { oppdatertRolle ->
                        val rolle = behandling.roller.find { it.ident == oppdatertRolle.ident?.verdi }
                        rolle != null && rolle.erRevurderingsbarn
                    }
            forholdsmessigFordelingService!!.leggTilEllerSlettBarnFraBehandlingSomErIFF(
                (rollerSomLeggesTil + revurderingsbarnSomLeggesTil).distinct(),
                rollerSomSkalSlettes,
                behandling,
                request.søknadsid ?: behandling.soknadsid!!,
                request.saksnummer ?: behandling.saksnummer,
            )
        } else {
            behandling.roller.addAll(rollerSomLeggesTil.map { it.toRolle(behandling) })
            oppdaterBehandlingEtterOppdatertRoller(
                behandling,
                underholdService,
                virkningstidspunktService,
                rollerSomLeggesTil,
                rollerSomSkalSlettes,
            )
            behandling.roller.removeIf { r ->
                if (identerSomSkalSlettes.contains(r.ident)) {
                    log.debug { "Sletter rolle ${r.id} fra behandling $behandlingId" }
                    true
                } else {
                    false
                }
            }
        }

        lagreBehandling(behandling, forceSave = true)

        if (behandling.søknadsbarn.isEmpty()) {
            log.debug { "Alle barn i behandling $behandlingId er slettet. Sletter behandling" }
            logiskSlettBehandling(behandling)
            return OppdaterRollerResponse(OppdaterRollerStatus.BEHANDLING_SLETTET)
        }

        return OppdaterRollerResponse(OppdaterRollerStatus.ROLLER_OPPDATERT)
    }

    private fun oppdaterTilNyesteIdent(
        ident: String?,
        behandlingId: Long,
    ): String? {
        if (ident == null) return null
        val nyIdent = hentNyesteIdent(ident)?.verdi
        if (nyIdent != ident) {
            secureLogger.info { "Oppdaterer ident fra $ident til $nyIdent i behandling $behandlingId " }
        }
        return nyIdent
    }

    private fun Behandling.oppdaterEksisterendeRoller(
        søknadsid: Long,
        saksnummer: String,
        oppdaterRollerListe: List<OpprettRolleDto>,
    ) {
        oppdaterRollerListe
            .filter { !it.erSlettet }
            .filter { roller.any { br -> br.ident == it.ident?.verdi } }
            .forEach {
                roller.find { br -> br.ident == it.ident?.verdi }?.let { eksisterendeRolle ->
                    eksisterendeRolle.innbetaltBeløp = it.innbetaltBeløp
                    // Skal ikke være mulig å fjerne gebyrsøknad fra rolle
                    eksisterendeRolle.harGebyrsøknad = if (eksisterendeRolle.harGebyrsøknad) true else it.harGebyrsøknad
                    if (it.harGebyrsøknad || !it.referanseGebyr.isNullOrEmpty()) {
                        val gebyrDetaljer = eksisterendeRolle.hentEllerOpprettGebyr()
                        val gebyr = gebyrDetaljer.finnEllerOpprettGebyrForSøknad(søknadsid, saksnummer)
                        gebyr.referanse = it.referanseGebyr ?: gebyr.referanse
                        gebyrDetaljer.leggTilGebyr(gebyr)
                    }
                }
            }
    }
}
