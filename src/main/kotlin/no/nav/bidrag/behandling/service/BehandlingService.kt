package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerStatus
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v1.behandling.tilType
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.behandling.tilBehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.toHusstandsmedlem
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val forsendelseService: ForsendelseService,
    private val boforholdService: BoforholdService,
    private val notatService: NotatService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val grunnlagService: GrunnlagService,
    private val inntektService: InntektService,
    private val samværService: SamværService,
    private val mapper: Dtomapper,
    private val validerBehandlingService: ValiderBehandlingService,
    private val underholdService: UnderholdService,
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

        opprettBehandling.valider()
        validerBehandlingService.validerKanBehandlesINyLøsning(opprettBehandling.tilKanBehandlesINyLøsningRequest())

        val opprettetAv =
            TokenUtils.hentSaksbehandlerIdent() ?: TokenUtils.hentApplikasjonsnavn() ?: "ukjent"
        val opprettetAvNavn =
            TokenUtils
                .hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        val behandling =
            Behandling(
                vedtakstype = opprettBehandling.vedtakstype,
                søktFomDato = opprettBehandling.søktFomDato,
                innkrevingstype =
                    when (opprettBehandling.tilType()) {
                        TypeBehandling.FORSKUDD -> Innkrevingstype.MED_INNKREVING
                        else -> opprettBehandling.innkrevingstype
                    },
                virkningstidspunkt =
                    when (opprettBehandling.tilType()) {
                        TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG -> opprettBehandling.søktFomDato
                        TypeBehandling.SÆRBIDRAG -> LocalDate.now().withDayOfMonth(1)
                    },
                årsak =
                    when (opprettBehandling.tilType()) {
                        TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG ->
                            if (opprettBehandling.vedtakstype !=
                                Vedtakstype.OPPHØR
                            ) {
                                VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
                            } else {
                                null
                            }

                        TypeBehandling.SÆRBIDRAG -> null
                    },
                avslag =
                    when (opprettBehandling.tilType()) {
                        TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG ->
                            if (opprettBehandling.vedtakstype ==
                                Vedtakstype.OPPHØR
                            ) {
                                Resultatkode.IKKE_OMSORG
                            } else {
                                null
                            }

                        TypeBehandling.SÆRBIDRAG -> null
                    },
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
                kategori = opprettBehandling.kategori?.kategori,
                kategoriBeskrivelse = opprettBehandling.kategori?.beskrivelse,
            )

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
        }

        val behandlingDo = opprettBehandling(behandling)

        if (TypeBehandling.BIDRAG == opprettBehandling.tilType()) {
            // Oppretter underholdskostnad for alle barna i behandlingen ved bidrag
            opprettBehandling.roller.filter { Rolletype.BARN == it.rolletype }.forEach {
                behandlingDo.underholdskostnader.add(
                    underholdService.oppretteUnderholdskostnad(behandling, BarnDto(personident = it.ident)),
                )
            }
        }

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
    fun aktivereGrunnlag(
        behandlingsid: Long,
        request: AktivereGrunnlagRequestV2,
    ): AktivereGrunnlagResponseV2 {
        behandlingRepository
            .findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }
            .let {
                log.info { "Aktiverer grunnlag for $behandlingsid med type ${request.grunnlagstype}" }
                secureLogger.info {
                    "Aktiverer grunnlag for $behandlingsid med type ${request.grunnlagstype} " +
                        "for person ${request.personident}: $request"
                }
                grunnlagService.aktivereGrunnlag(it, request)
                return mapper.tilAktivereGrunnlagResponseV2(it)
            }
    }

    @Transactional
    fun oppdatereVirkningstidspunkt(
        behandlingsid: Long,
        request: OppdatereVirkningstidspunkt,
    ): Behandling =
        behandlingRepository
            .findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }
            .let {
                log.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid" }
                secureLogger.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid, forespørsel=$request" }
                request.valider(it)
                it.årsak = if (request.avslag != null) null else request.årsak ?: it.årsak
                it.avslag = if (request.årsak != null) null else request.avslag ?: it.avslag
                request.henteOppdatereNotat()?.let { n ->
                    notatService.oppdatereNotat(
                        it,
                        NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                        n.henteNyttNotat() ?: "",
                        it.bidragsmottaker!!,
                    )
                }
                oppdaterVirkningstidspunkt(request, it)
                it
            }

    @Transactional
    fun oppdaterVirkningstidspunkt(
        request: OppdatereVirkningstidspunkt,
        behandling: Behandling,
    ) {
        val erVirkningstidspunktEndret = request.virkningstidspunkt != behandling.virkningstidspunkt

        fun oppdaterBoforhold() {
            log.info { "Virkningstidspunkt er endret. Beregner husstandsmedlemsperioder på ny for behandling ${behandling.id}" }
            grunnlagService.oppdaterAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling.id!!)
            grunnlagService.aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling)
        }

        fun oppdaterSivilstand() {
            log.info { "Virkningstidspunkt er endret. Bygger sivilstandshistorikk på ny for behandling ${behandling.id}" }
            grunnlagService.oppdatereAktivSivilstandEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdatereIkkeAktivSivilstandEtterEndretVirkningsdato(behandling)
            boforholdService.oppdatereSivilstandshistorikk(behandling)
            grunnlagService.aktivereSivilstandHvisEndringIkkeKreverGodkjenning(behandling)
        }

        fun oppdaterSamvær() {
            log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på samvær for behandling ${behandling.id}" }
            samværService.rekalkulerPerioderSamvær(behandling.id!!)
        }

        fun oppdaterInntekter() {
            log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling.id!!)
        }

        fun oppdaterAndreVoksneIHusstanden() {
            log.info { "Virkningstidspunkt er endret. Beregner andre voksne i husstanden perioder på nytt for behandling ${behandling.id}" }
            grunnlagService.oppdatereAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdatereIkkeAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandling.id!!)
            grunnlagService.aktivereGrunnlagForBoforholdAndreVoksneIHusstandenHvisIngenEndringerMåAksepteres(behandling)
        }

        if (erVirkningstidspunktEndret) {
            behandling.virkningstidspunkt = request.virkningstidspunkt ?: behandling.virkningstidspunkt

            when (behandling.tilType()) {
                TypeBehandling.FORSKUDD -> {
                    oppdaterBoforhold()
                    oppdaterSivilstand()
                    oppdaterInntekter()
                }

                TypeBehandling.SÆRBIDRAG -> {
                    oppdaterBoforhold()
                    oppdaterAndreVoksneIHusstanden()
                    oppdaterInntekter()
                }

                TypeBehandling.BIDRAG -> {
                    oppdaterBoforhold()
                    oppdaterAndreVoksneIHusstanden()
                    oppdaterInntekter()
                    oppdaterSamvær()
                    // TODO Underholdskostnad
                }
            }
        }
    }

    @Transactional
    fun oppdaterVedtakFattetStatus(
        behandlingsid: Long,
        vedtaksid: Long,
    ) {
        behandlingRepository
            .findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }
            .let {
                log.info { "Oppdaterer vedtaksid til $vedtaksid for behandling $behandlingsid" }
                it.vedtaksid = vedtaksid
                it.vedtakstidspunkt = it.vedtakstidspunkt ?: LocalDateTime.now()
                it.vedtakFattetAv = it.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent()
                    ?: TokenUtils.hentApplikasjonsnavn()
            }
    }

    fun henteBehandlingDetaljer(behandlingsid: Long): BehandlingDetaljerDtoV2 {
        val behandling = hentBehandlingById(behandlingsid)
        tilgangskontrollService.sjekkTilgangBehandling(behandling)
        return behandling.tilBehandlingDetaljerDtoV2()
    }

    fun henteBehandlingDetaljerForSøknadsid(søknadsid: Long): BehandlingDetaljerDtoV2 {
        log.info { "Henter behandling for søknadsId $søknadsid." }
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
        inkluderHistoriskeInntekter: Boolean = false,
    ): Behandling {
        val behandling = hentBehandlingById(behandlingsid)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        behandling.oppdatereVirkningstidspunktSærbidrag()
        return behandling
    }

    @Transactional
    fun Behandling.oppdatereVirkningstidspunktSærbidrag() {
        if (tilType() != TypeBehandling.SÆRBIDRAG) return
        val nyVirkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        // Virkningstidspunkt skal alltid være lik det som var i opprinnelig vedtaket.
        // Oppdaterer derfor ikke virkningstidspunkt hvis behandlingen er klage eller omgjøring
        if (virkningstidspunkt != nyVirkningstidspunkt && !erKlageEllerOmgjøring) {
            log.info {
                "Virkningstidspunkt $virkningstidspunkt på særbidrag er ikke riktig som følge av ny kalendermåned." +
                    " Endrer virkningstidspunkt til starten av nåværende kalendermåned $nyVirkningstidspunkt"
            }
            oppdaterVirkningstidspunkt(OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningstidspunkt), this)
        }
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingId)
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

        behandling.oppdaterEksisterendeRoller(oppdaterRollerListe)

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

        oppdatereHusstandsmedlemmerForRoller(behandling, rollerSomLeggesTil)
        oppdatereSamværForRoller(behandling, rollerSomLeggesTil)

        behandlingRepository.save(behandling)

        if (behandling.søknadsbarn.isEmpty()) {
            log.info { "Alle barn i behandling $behandlingId er slettet. Sletter behandling" }
            behandlingRepository.logiskSlett(behandling.id!!)
            return OppdaterRollerResponse(OppdaterRollerStatus.BEHANDLING_SLETTET)
        }

        return OppdaterRollerResponse(OppdaterRollerStatus.ROLLER_OPPDATERT)
    }

    private fun Behandling.oppdaterEksisterendeRoller(oppdaterRollerListe: List<OpprettRolleDto>) {
        oppdaterRollerListe
            .filter { !it.erSlettet }
            .filter { roller.any { br -> br.ident == it.ident?.verdi } }
            .forEach {
                roller.find { br -> br.ident == it.ident?.verdi }?.let { eksisterendeRolle ->
                    eksisterendeRolle.innbetaltBeløp = it.innbetaltBeløp
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
                secureLogger.info { "Legger til husstandsmedlem med ident ${it.ident?.verdi} i behandling ${behandling.id}" }
                it.toHusstandsmedlem(behandling)
            },
        )
    }
}
