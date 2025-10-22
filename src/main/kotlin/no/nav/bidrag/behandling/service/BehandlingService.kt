package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.extensions.BehandlingMetadataDo
import no.nav.bidrag.behandling.database.datamodell.extensions.hentDefaultÅrsak
import no.nav.bidrag.behandling.database.datamodell.json.FattetDelvedtak
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.json.VedtakDetaljer
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerStatus
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v1.behandling.tilType
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.kafka.BehandlingEndringHendelse
import no.nav.bidrag.behandling.kafka.BehandlingOppdatertLytter
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.behandling.tilBehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.transformers.finnEksisterendeVedtakMedOpphør
import no.nav.bidrag.behandling.transformers.kreverGrunnlag
import no.nav.bidrag.behandling.transformers.opprettForsendelse
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.toHusstandsmedlem
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
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
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.hendelse.BehandlingHendelseType
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime

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
    private val behandlingOppdatertLytter: BehandlingOppdatertLytter? = null,
    private val forholdsmessigFordelingService: ForholdsmessigFordelingService? = null,
//    private val applicationEventPublisher: ApplicationEventPublisher? = null,
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

        log.debug { "Logisk sletter behandling $behandlingId" }
        behandlingRepository.logiskSlett(behandling.id!!)
        behandlingOppdatertLytter!!.sendBehandlingOppdatertHendelse(
            behandling.id!!,
            BehandlingHendelseType.AVSLUTTET,
        )
    }

    fun hentEksisteredenBehandling(søknadsid: Long): Behandling? = behandlingRepository.findFirstBySoknadsid(søknadsid)

    fun lagreBehandling(
        behandling: Behandling,
        opprettForsendelse: Boolean = false,
    ): Behandling {
        val oppretterBehandling = behandling.id == null
        val lagretBehandling =
            if (oppretterBehandling) {
                behandlingRepository.save(behandling)
            } else {
                behandling
            }
        behandlingOppdatertLytter!!.sendBehandlingOppdatertHendelse(
            lagretBehandling.id!!,
            if (oppretterBehandling) {
                BehandlingHendelseType.OPPRETTET
            } else {
                BehandlingHendelseType.ENDRET
            },
        )
        if (behandling.vedtakstype.opprettForsendelse() && (oppretterBehandling || opprettForsendelse)) {
            opprettForsendelseForBehandling(lagretBehandling)
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

        behandlingRepository.findFirstBySoknadsid(opprettBehandling.søknadsid)?.let {
            log.info { "Fant eksisterende behandling ${it.id} for søknadsId ${opprettBehandling.søknadsid}. Oppretter ikke ny behandling" }
            return OpprettBehandlingResponse(it.id!!)
        }

        val bp = opprettBehandling.roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
        behandlingRepository.finnHovedbehandlingForBpVedFF(bp!!.ident!!.verdi)?.let { behandling ->
            val bm = opprettBehandling.roller.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
            behandling.roller.addAll(
                HashSet(
                    opprettBehandling.roller.mapNotNull { opprettRolle ->

                        if (behandling.roller.none { it.ident == opprettRolle.ident!!.verdi }) {
                            val rolle = opprettRolle.toRolle(behandling)
                            rolle.forholdsmessigFordeling =
                                ForholdsmessigFordelingRolle(
                                    tilhørerSak = opprettBehandling.saksnummer,
                                    delAvOpprinneligBehandling = true,
                                    bidragsmottaker = bm?.ident?.verdi,
                                    behandlerEnhet = Enhetsnummer(opprettBehandling.behandlerenhet),
                                    erRevurdering = opprettBehandling.vedtakstype == Vedtakstype.REVURDERING,
                                )
                            rolle
                        } else {
                            null
                        }
                    },
                ),
            )
            return OpprettBehandlingResponse(behandling.id!!)
        }
        opprettBehandling.valider()
        validerBehandlingService.validerKanBehandlesINyLøsning(opprettBehandling.tilKanBehandlesINyLøsningRequest())

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils
                .hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        val virkningstidspunkt =
            when (opprettBehandling.tilType()) {
                TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR -> opprettBehandling.søktFomDato
                TypeBehandling.SÆRBIDRAG -> LocalDate.now().withDayOfMonth(1)
            }
        val årsak = hentDefaultÅrsak(opprettBehandling.tilType(), opprettBehandling.vedtakstype)
        val avslag =
            when (opprettBehandling.tilType()) {
                TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR ->
                    if (opprettBehandling.vedtakstype == Vedtakstype.OPPHØR) {
                        if (opprettBehandling.stønadstype == Stønadstype.BIDRAG18AAR) {
                            Resultatkode.AVSLUTTET_SKOLEGANG
                        } else {
                            Resultatkode.IKKE_OMSORG
                        }
                    } else {
                        null
                    }

                TypeBehandling.SÆRBIDRAG -> null
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

        val behandlingDo = opprettBehandlingHvisIkkeEksisterer(behandling)

        if (TypeBehandling.BIDRAG == opprettBehandling.tilType() && opprettBehandling.vedtakstype.kreverGrunnlag()) {
            // Oppretter underholdskostnad for alle barna i behandlingen ved bidrag
            opprettBehandling.roller.filter { Rolletype.BARN == it.rolletype }.forEach {
                behandlingDo.underholdskostnader.add(
                    underholdService.oppretteUnderholdskostnad(behandling, BarnDto(personident = it.ident)),
                )
            }
        }

        grunnlagService.oppdatereGrunnlagForBehandling(behandlingDo)

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
        behandlingRepository.save(behandling)
        log.debug {
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
        resultat: FattetDelvedtak,
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
                            fattetDelvedtak =
                                (eksisterendeDetaljer.fattetDelvedtak + setOf(resultat)).toSet(),
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
                            vedtakstidspunkt = it.vedtakDetaljer?.vedtakstidspunkt ?: LocalDateTime.now(),
                            unikreferanse = unikreferanse,
                            vedtakFattetAv =
                                it.vedtakDetaljer?.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                                    ?: TokenUtils.hentApplikasjonsnavn(),
                        )

                it.vedtaksid = vedtaksid
                it.vedtakFattetAvEnhet = fattetAvEnhet
                it.vedtakstidspunkt = it.vedtakstidspunkt ?: LocalDateTime.now()
                it.vedtakFattetAv = it.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                    ?: TokenUtils.hentApplikasjonsnavn()
                it.søknadsbarn.forEach {
                    it.behandlingstatus = Behandlingstatus.VEDTAK_FATTET
                }
            }

        behandlingOppdatertLytter!!.sendBehandlingOppdatertHendelse(
            behandlingsid,
            BehandlingHendelseType.AVSLUTTET,
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
    fun henteBehandling(behandlingsid: Long): Behandling {
        val behandling = hentBehandlingById(behandlingsid)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        virkningstidspunktService.run {
            behandling.oppdatereVirkningstidspunktSærbidrag()
        }
        return behandling
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingId)
                .orElseThrow { behandlingNotFoundException(behandlingId) }
                .let {
                    if (it.forholdsmessigFordeling != null &&
                        it.forholdsmessigFordeling?.erHovedbehandling == false
                    ) {
                        behandlingRepository.finnHovedbehandlingForBpVedFF(it.bidragspliktig!!.ident!!)
                            ?: behandlingNotFoundException(behandlingId)
                    } else {
                        it
                    }
                }

        tilgangskontrollService.sjekkTilgangBehandling(behandling)
        if (behandling.deleted) behandlingNotFoundException(behandlingId)
        return behandling
    }

    @Transactional
    fun oppdaterRoller(
        behandlingId: Long,
        oppdaterRollerListe: List<OpprettRolleDto>,
    ): OppdaterRollerResponse {
        val behandling = hentBehandlingById(behandlingId)
        if (behandling.erVedtakFattet) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke oppdatere behandling hvor vedtak er fattet",
            )
        }

        secureLogger.debug { "Oppdater roller i behandling $behandlingId: $oppdaterRollerListe" }
        val eksisterendeRoller = behandling.roller
        val oppdaterRollerNyesteIdent =
            oppdaterRollerListe.map { rolle ->
                rolle.copy(
                    ident = oppdaterTilNyesteIdent(rolle.ident?.verdi, behandlingId)?.let { Personident(it) } ?: rolle.ident,
                )
            }

        behandling.oppdaterEksisterendeRoller(oppdaterRollerNyesteIdent)

        val rollerSomLeggesTil =
            oppdaterRollerNyesteIdent
                .filter { !it.erSlettet }
                .filter { !eksisterendeRoller.any { br -> br.ident == it.ident?.verdi } }

        val identerSomSkalLeggesTil = rollerSomLeggesTil.mapNotNull { it.ident?.verdi }
        identerSomSkalLeggesTil.isNotEmpty().ifTrue {
            secureLogger.debug {
                "Legger til søknadsbarn ${
                    identerSomSkalLeggesTil.joinToString(",")
                } til behandling $behandlingId"
            }
        }
        behandling.roller.addAll(rollerSomLeggesTil.map { it.toRolle(behandling) })

        val rollerSomSkalSlettes = oppdaterRollerListe.filter { r -> r.erSlettet }
        val identerSomSkalSlettes = rollerSomSkalSlettes.mapNotNull { it.ident?.verdi }
        identerSomSkalSlettes.isNotEmpty().ifTrue {
            secureLogger.debug { "Sletter søknadsbarn ${identerSomSkalSlettes.joinToString(",")} fra behandling $behandlingId" }
        }
        if (behandling.forholdsmessigFordeling != null) {
            behandling.roller
                .filter { identerSomSkalSlettes.contains(it.ident) }
                .forEach {
                    forholdsmessigFordelingService?.slettBarnFraBehandlingFF(it, behandling)
                }
        } else {
            behandling.roller.removeIf { r ->
                val skalSlettes = identerSomSkalSlettes.contains(r.ident)
                skalSlettes.ifTrue {
                    log.debug { "Sletter rolle ${r.id} fra behandling $behandlingId" }
                }
                skalSlettes
            }
        }

        oppdatereHusstandsmedlemmerForRoller(behandling, rollerSomLeggesTil)
        oppdatereSamværForRoller(behandling, rollerSomLeggesTil)

        // TODO: Underholdskostnad versjon 3: Opprette underholdskostnad for nytt søknadsbarn

        lagreBehandling(behandling)

        if (behandling.søknadsbarn.isEmpty()) {
            log.debug { "Alle barn i behandling $behandlingId er slettet. Sletter behandling" }
            slettBehandling(behandling.id!!)
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

    private fun Behandling.oppdaterEksisterendeRoller(oppdaterRollerListe: List<OpprettRolleDto>) {
        oppdaterRollerListe
            .filter { !it.erSlettet }
            .filter { roller.any { br -> br.ident == it.ident?.verdi } }
            .forEach {
                roller.find { br -> br.ident == it.ident?.verdi }?.let { eksisterendeRolle ->
                    eksisterendeRolle.innbetaltBeløp = it.innbetaltBeløp
                    eksisterendeRolle.harGebyrsøknad = it.harGebyrsøknad
                }
            }
    }

    private fun oppdatereSamværForRoller(
        behandling: Behandling,
        rollerSomLeggesTil: List<OpprettRolleDto>,
    ) {
        if (behandling.tilType() == TypeBehandling.BIDRAG) {
            rollerSomLeggesTil.forEach { rolle ->
                behandling.samvær.add(Samvær(behandling, rolle = behandling.roller.find { it.ident == rolle.ident?.verdi }!!))
            }
        }
    }

    private fun oppdatereHusstandsmedlemmerForRoller(
        behandling: Behandling,
        rollerSomLeggesTil: List<OpprettRolleDto>,
    ) {
        val nyeRollerSomIkkeHarHusstandsmedlemmer =
            rollerSomLeggesTil.filter { nyRolle -> behandling.husstandsmedlem.none { it.ident == nyRolle.ident?.verdi } }
        behandling.husstandsmedlem.addAll(
            nyeRollerSomIkkeHarHusstandsmedlemmer.map {
                secureLogger.debug { "Legger til husstandsmedlem med ident ${it.ident?.verdi} i behandling ${behandling.id}" }
                it.toHusstandsmedlem(behandling)
            },
        )
    }
}
