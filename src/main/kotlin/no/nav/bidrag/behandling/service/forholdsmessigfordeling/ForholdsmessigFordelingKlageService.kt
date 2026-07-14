package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.dto.FinnSammenknytningerHovedsøknadResponse
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.dto.grunnlag.LøpendeBidragGrunnlagForholdsmessigFordeling
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.OpprettFFRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.transformers.behandling.oppdaterBehandlingEtterOppdatertRoller
import no.nav.bidrag.behandling.transformers.erOverEllerLik18År
import no.nav.bidrag.behandling.transformers.maxOfNullable
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregningsperiode
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.SøknadsknytningStatus
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.Barn
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.behandling.hendelse.BehandlingStatusType
import no.nav.bidrag.transport.felles.toYearMonth

private val KLAGE_LOGGER = KotlinLogging.logger {}

class ForholdsmessigFordelingKlageService(
    private val bbmConsumer: BidragBBMConsumer,
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
    private val underholdService: UnderholdService,
    private val virkningstidspunktService: VirkningstidspunktService,
    private val kravhaverService: ForholdsmessigFordelingKravhaverService,
    private val søknadService: ForholdsmessigFordelingSøknadService,
    private val overføringService: ForholdsmessigFordelingOverføringService,
) {
    // Feilhåndtering - slett eller gjenopprett klagesøknader basert på påklaget søknad
    // Hvis hovedsøknad slettes så slettes alle relaterte søknader
    // Hvis en annen søknad slettes så gjenopprettes klagesøknaden
    fun slettEllerGjennopprettKlageSøknader(
        behandling: Behandling,
        søknadsidSomSlettes: Long,
    ) {
        if (behandling.soknadsid == søknadsidSomSlettes) {
            val tilknyttedeSøknader =
                bbmConsumer.finnSammenknytningerHovedsøknad(
                    søknadsidSomSlettes,
                    SøknadsknytningStatus.Aktiv,
                )
            val annenSøknadForSammePåklagetSøknad =
                tilknyttedeSøknader.søknader.find {
                    it.søknadsid != behandling.soknadsid &&
                        it.refSøknadsid == behandling.omgjøringsdetaljer?.soknadRefId
                }
            if (annenSøknadForSammePåklagetSøknad != null) {
                behandling.soknadsid = annenSøknadForSammePåklagetSøknad.søknadsid
                bbmConsumer.fjernSammeknytningHovedsøknad(søknadsidSomSlettes, annenSøknadForSammePåklagetSøknad.søknadsid)
            } else {
                tilknyttedeSøknader.søknader.forEach { søknad ->
                    bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknad.søknadsid))
                    behandling.roller.filter { it.harSøknad(søknad.søknadsid) }.forEach { rolle ->
                        val lagretSøknad = rolle.finnSøknad(søknad.søknadsid)!!
                        lagretSøknad.status = Behandlingstatus.FEILREGISTRERT
                    }
                }
                bbmConsumer.fjernSammeknytningHovedsøknad(søknadsidSomSlettes)
                behandlingService.logiskSlettBehandling(behandling)
            }
        } else {
            val søknadSomSlettes = bbmConsumer.hentSøknad(søknadsidSomSlettes)!!.søknad
            if (søknadSomSlettes.refSøknadsid != behandling.soknadsid) {
                // Var ikke hovedsøknad som ble slettet. Gjennopprett klagesøknad slik at samme struktur beholdes som i påklaget søknad
                opprettKlageSøknad(søknadSomSlettes, behandling, emptyList(), behandling.soknadsid)
                bbmConsumer.fjernSammenknytning(søknadsidSomSlettes)
            }
        }

        // Gjør dette helt til slutt da det sjekkes om barn var feilregistrert i original søknad ved gjennopprettelse
        behandling.roller.filter { it.harSøknad(søknadsidSomSlettes) }.forEach {
            val søknad = it.finnSøknad(søknadsidSomSlettes)!!
            søknad.status = Behandlingstatus.FEILREGISTRERT
        }
    }

    fun opprettSøknaderForKlageEllerOmgjøring(
        behandling: Behandling,
        opprettetEllerOppdaterSøknadsid: Long,
        request: OpprettFFRequest? = null,
        nyesteLøpendeBidragGrunnlag: List<LøpendeBidragGrunnlagForholdsmessigFordeling> = emptyList(),
    ) {
        if (TokenUtils.hentBruker() != null && !UnleashFeatures.TILGANG_OPPRETTE_FF.isEnabled) {
            KLAGE_LOGGER.info { "Opprettelse av forholdsmessig fordeling er deaktivert" }
            ugyldigForespørsel("Opprettelse av forholdsmessig fordeling er deaktivert")
        }

        val relevanteKravhavere = kravhaverService.hentAlleRelevanteKravhavere(behandling).toMutableSet()
        val bmOgBidragspliktiIdenter = listOfNotNull(behandling.bidragspliktig?.ident, behandling.bidragsmottaker?.ident)
        var hovedsøknadsid = behandling.soknadsid!!
        val behandlerEnhet = kravhaverService.finnEnhetForBarnIBehandling(behandling)
        val åpneSøknaderForVedtaksid = hentÅpneSøknaderForVedtak(behandling)

        sammeknyttSøknadHvisNødvendig(hovedsøknadsid, opprettetEllerOppdaterSøknadsid)

        val opprettetSøknad = bbmConsumer.hentSøknad(opprettetEllerOppdaterSøknadsid)!!.søknad
        hovedsøknadsid =
            håndterSlettetHovedsøknad(
                opprettetSøknad,
                behandling,
                åpneSøknaderForVedtaksid,
                hovedsøknadsid,
                opprettetEllerOppdaterSøknadsid,
            )

        oppdaterRollerMedSøknadDetaljer(behandling, opprettetSøknad, bmOgBidragspliktiIdenter, opprettetEllerOppdaterSøknadsid)

        val søknadsbarnOrdinæreSøknader =
            opprettKlagesøknaderForTilknyttedeSøknader(
                behandling,
                opprettetSøknad,
                relevanteKravhavere,
                åpneSøknaderForVedtaksid,
                hovedsøknadsid,
            )

        fjernSøknaderSomIkkeErDelAvKlagebehandlingen(behandling)

        val gjenværendeKravhavere =
            relevanteKravhavere
                .filter { rk -> søknadsbarnOrdinæreSøknader.none { it.first == rk.kravhaver && it.second == rk.stønadstype } }
                .toSet()
        opprettRevurderingssøknaderForGjenværendeKravhavere(
            behandling,
            gjenværendeKravhavere,
            behandlerEnhet,
            request,
        )

        behandling.forholdsmessigFordeling =
            ForholdsmessigFordeling(
                erHovedbehandling = true,
            )

        overføringService.giSakTilgangTilEnhet(behandling, behandlerEnhet)
        kravhaverService.opprettGrunnlagLøpendeBidrag(behandling, nyesteLøpendeBidragGrunnlag)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        oppdaterBehandlingEtterOppdatertRoller(
            behandling,
            underholdService,
            virkningstidspunktService,
            behandling.søknadsbarn.map { it.tilOpprettRolleDto() },
            emptyList(),
        )

        opprettVarselForsendelserForKlage(behandling, hovedsøknadsid)
    }

    private fun opprettVarselForsendelserForKlage(
        behandling: Behandling,
        hovedsøknadsid: Long,
    ) {
        behandling.søknadsbarn
            .groupBy {
                it.saksnummer
            }.forEach { (saksnummer, roller) ->
                val hovedsøknad = behandling.hentSøknad(hovedsøknadsid)!!
                val barnUnder18År = roller.filter { !it.fødselsdato.erOverEllerLik18År() }
                søknadService.opprettForsendelseForNySøknad(
                    saksnummer,
                    behandling,
                    "",
                    hovedsøknad,
                    barn = barnUnder18År.map { SakKravhaver(saksnummer, kravhaver = it.ident!!, stønadstype = it.stønadstype) },
                )

                val barnOver18År = roller.filter { it.fødselsdato.erOverEllerLik18År() }
                if (barnOver18År.isNotEmpty()) {
                    barnOver18År.forEach {
                        søknadService.opprettForsendelseForNySøknad(
                            saksnummer,
                            behandling,
                            "",
                            hovedsøknad,
                            barn = listOf(SakKravhaver(saksnummer, kravhaver = it.ident!!, stønadstype = it.stønadstype)),
                        )
                    }
                }
            }
    }

    private fun hentÅpneSøknaderForVedtak(behandling: Behandling): List<HentSøknad> =
        bbmConsumer
            .hentÅpneSøknaderForBp(behandling.bidragspliktig!!.ident!!)
            .åpneSøknader
            .filter { it.refVedtaksid == behandling.omgjøringsdetaljer?.omgjørVedtakId }

    private fun sammeknyttSøknadHvisNødvendig(
        hovedsøknadsid: Long,
        opprettetEllerOppdaterSøknadsid: Long,
    ) {
        val tilknyttedeSøknaderBehandling =
            bbmConsumer.finnSammenknytningerHovedsøknad(
                hovedsøknadsid,
                SøknadsknytningStatus.Aktiv,
            )
        if (tilknyttedeSøknaderBehandling.søknader.none { it.søknadsid == opprettetEllerOppdaterSøknadsid }) {
            bbmConsumer.sammeknyttSøknader(hovedsøknadsid, opprettetEllerOppdaterSøknadsid)
        }
    }

    /**
     * Hvis den opprettede søknaden er avbrutt, opprett en ny klagesøknad.
     * Returnerer oppdatert hovedsøknadsid.
     */
    private fun håndterSlettetHovedsøknad(
        opprettetSøknad: HentSøknad,
        behandling: Behandling,
        åpneSøknaderForVedtaksid: List<HentSøknad>,
        gjeldeneHovedsøknadsid: Long,
        opprettetEllerOppdaterSøknadsid: Long,
    ): Long {
        if (opprettetSøknad.behandlingStatusType != BehandlingStatusType.AVBRUTT) return gjeldeneHovedsøknadsid

        val originalSøknad = bbmConsumer.hentSøknad(behandling.omgjøringsdetaljer!!.soknadRefId!!)!!.søknad
        val varHovedsøknad = opprettetEllerOppdaterSøknadsid == gjeldeneHovedsøknadsid
        val nySøknadsid =
            opprettKlageSøknad(
                originalSøknad,
                behandling,
                åpneSøknaderForVedtaksid,
                if (varHovedsøknad) null else gjeldeneHovedsøknadsid,
            )
        if (varHovedsøknad) {
            bbmConsumer.fjernSammeknytningHovedsøknad(gjeldeneHovedsøknadsid, nySøknadsid)
            behandling.soknadsid = nySøknadsid
            return nySøknadsid
        }
        return gjeldeneHovedsøknadsid
    }

    /** Legger til søknadsinfo på roller som tilhører den opprettede søknaden */
    private fun oppdaterRollerMedSøknadDetaljer(
        behandling: Behandling,
        opprettetSøknad: HentSøknad,
        bmOgBidragspliktiIdenter: List<String>,
        opprettetEllerOppdaterSøknadsid: Long,
    ) {
        val opprettetSøknadRoller = opprettetSøknad.partISøknadListe.map { it.personident!! } + bmOgBidragspliktiIdenter
        behandling.roller
            .filter {
                opprettetSøknadRoller.contains(it.ident) &&
                    it.stønadstype == opprettetSøknad.behandlingstema.tilStønadstype() &&
                    !it.harSøknad(opprettetEllerOppdaterSøknadsid)
            }.forEach {
                it.forholdsmessigFordeling!!.søknader.add(
                    ForholdsmessigFordelingSøknadBarn(
                        søknadsid = opprettetEllerOppdaterSøknadsid,
                        behandlingstema = opprettetSøknad.behandlingstema,
                        behandlingstype = opprettetSøknad.behandlingstype,
                        omgjørSøknadsid = opprettetSøknad.refSøknadsid ?: behandling.omgjøringsdetaljer?.soknadRefId,
                        omgjørVedtaksid = opprettetSøknad.refVedtaksid ?: behandling.omgjøringsdetaljer?.omgjørVedtakId,
                        innkreving = opprettetSøknad.innkreving,
                        mottattDato = opprettetSøknad.søknadMottattDato,
                        søktAvType = opprettetSøknad.søktAvType,
                        søknadFomDato = opprettetSøknad.søknadFomDato,
                    ),
                )
            }
    }

    /**
     * Finner tilknyttede søknader fra påklaget vedtak og oppretter klagesøknader for dem.
     * Returnerer liste over søknadsbarn (ident + stønadstype) som ble håndtert.
     */
    private fun opprettKlagesøknaderForTilknyttedeSøknader(
        behandling: Behandling,
        opprettetSøknad: HentSøknad,
        relevanteKravhavere: Set<SakKravhaver>,
        åpneSøknaderForVedtaksid: List<HentSøknad>,
        hovedsøknadsid: Long,
    ): List<Pair<String?, Stønadstype?>> {
        val tilknyttedeSøknaderOmgjortSøknad =
            bbmConsumer.finnSammenknytningerHovedsøknad(
                behandling.omgjøringsdetaljer!!.soknadRefId!!,
                SøknadsknytningStatus.Deaktiv,
            )

        val tilknyttetSøknaderIkkeHovedsøknad =
            filtrerTilknyttedeSøknaderForKlage(tilknyttedeSøknaderOmgjortSøknad, relevanteKravhavere, behandling.søknadsbarn)

        val søknadsbarnOpprettetSøknad =
            opprettetSøknad.parterUnderBehandling.map {
                it.personident to opprettetSøknad.behandlingstema.tilStønadstype()
            }
        val søknadsbarnAndreSøknader =
            tilknyttetSøknaderIkkeHovedsøknad.flatMap { tilknyttetSøknad ->
                opprettKlageSøknad(
                    tilknyttetSøknad,
                    behandling,
                    åpneSøknaderForVedtaksid,
                    hovedsøknadsid,
                )
                tilknyttetSøknad.parterVedtakFattet
                    .map {
                        it.personident to tilknyttetSøknad.behandlingstema.tilStønadstype()
                    }
            }

        return søknadsbarnAndreSøknader + søknadsbarnOpprettetSøknad
    }

    /** Filtrerer tilknyttede søknader til kun de som er relevante for klagebehandling */
    private fun filtrerTilknyttedeSøknaderForKlage(
        tilknyttedeSøknaderOmgjortSøknad: FinnSammenknytningerHovedsøknadResponse,
        relevanteKravhavere: Set<SakKravhaver>,
        søknadsbarn: List<Rolle>,
    ): List<HentSøknad> {
        val hovedsøknadPåklagetVedtak = tilknyttedeSøknaderOmgjortSøknad.hovedsøknadsid

        return tilknyttedeSøknaderOmgjortSøknad.søknader
            .filter { !it.behandlingstype.erForholdsmessigFordeling }
            .filter { it.behandlingStatusType == BehandlingStatusType.VEDTAK_FATTET }
            .filter { søknad ->
                val parterISøknad = søknad.parterVedtakFattet.map { it.personident!! }
                val søknadHarIngenRelevanteKravhavere =
                    relevanteKravhavere.none {
                        parterISøknad.contains(it.kravhaver) &&
                            it.stønadstype == søknad.behandlingstema.tilStønadstype()
                    }
                val søknadsbarnErDelAvSøknad =
                    søknadsbarn.any { s ->
                        parterISøknad.contains(s.ident) &&
                            s.stønadstype == søknad.behandlingstema.tilStønadstype()
                    }
                (søknad.søknadsid != hovedsøknadPåklagetVedtak && søknadsbarnErDelAvSøknad) || søknadHarIngenRelevanteKravhavere
            }
    }

    /** Fjerner søknader uten omgjøringsreferanse fra alle roller */
    private fun fjernSøknaderSomIkkeErDelAvKlagebehandlingen(behandling: Behandling) {
        behandling.roller.forEach {
            it.forholdsmessigFordeling?.søknader?.removeIf { søknad -> søknad.omgjørSøknadsid == null }
        }
    }

    /** Oppretter revurderingssøknader for kravhavere som ikke allerede er håndtert via ordinære søknader */
    private fun opprettRevurderingssøknaderForGjenværendeKravhavere(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
        behandlerEnhet: String,
        request: OpprettFFRequest?,
    ) {
        val eldsteSøktFomDato = behandling.eldsteSøktFomDatoSøknadsbarn

        val tilknyttedeSøknaderOmgjortSøknad =
            bbmConsumer
                .finnSammenknytningerHovedsøknad(
                    behandling.omgjøringsdetaljer!!.soknadRefId!!,
                    SøknadsknytningStatus.Deaktiv,
                ).søknader
                .filter { it.behandlingStatusType == BehandlingStatusType.VEDTAK_FATTET }

        relevanteKravhavere
            .sortedByDescending { it.stønadstype }
            .groupBy { kravhaver ->
                val søktFomDato =
                    finnSøktFomDatoForKravhaver(
                        relevanteKravhavere,
                        kravhaver,
                        behandling,
                        request,
                    )
                val tilknyttetSøknad =
                    tilknyttedeSøknaderOmgjortSøknad
                        .filter {
                            it.behandlingstema.tilStønadstype() == kravhaver.stønadstype &&
                                it.behandlingstype.erForholdsmessigFordeling
                        }.find { it.parterVedtakFattet.finnBarn(kravhaver.kravhaver) != null }
                Triple(
                    kravhaver.saksnummer!!,
                    kravhaver.stønadstype,
                    maxOfNullable(eldsteSøktFomDato, tilknyttetSøknad?.søknadFomDato ?: søktFomDato)!!,
                )
            }.forEach { (saksnummerLøpendeBidrag, løpendebidragssaker) ->
                val saksnummer = saksnummerLøpendeBidrag.first
                val søktFomDato = saksnummerLøpendeBidrag.third
                søknadService.opprettRollerOgRevurderingssøknadForSak(
                    behandling,
                    saksnummer,
                    løpendebidragssaker,
                    behandlerEnhet,
                    saksnummerLøpendeBidrag.second,
                    søktFomDato,
                    true,
                )
            }
    }

    fun opprettKlageSøknad(
        originalSøknad: HentSøknad,
        behandling: Behandling,
        åpneSøknaderForVedtaksid: List<HentSøknad>,
        hovedsøknadsid: Long?,
    ): Long {
        val behandlingstype =
            if (originalSøknad.behandlingstype.erForholdsmessigFordeling) {
                Behandlingstype.FORHOLDSMESSIG_FORDELING_KLAGE
            } else {
                behandling.søknadstype!!
            }
        val barnIOriginalSøknad =
            behandling.roller.filter { it.harSøknad(originalSøknad.søknadsid) }.filter {
                val søknad = it.finnSøknad(originalSøknad.søknadsid)!!
                søknad.status?.lukketStatus == false
            }
        val barnISøknad =
            originalSøknad.partISøknadListe
                .filter { it.rolletype == Rolletype.BARN }
                .filter { barn ->
                    if (barnIOriginalSøknad.isEmpty()) {
                        !barn.behandlingstatus!!.erFeilregistrert
                    } else {
                        barnIOriginalSøknad.any { it.erSammeRolle(barn.personident!!, originalSøknad.behandlingstema.tilStønadstype()) }
                    }
                }

        val barnISøknadIdenter = barnISøknad.map { it.personident }.toSet()
        val løpendeBidraggsakerBP =
            kravhaverService.hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!), behandling.finnBeregningsperiode())
        val åpenFFSøknad =
            åpneSøknaderForVedtaksid.find { søknad ->
                søknad.refSøknadsid == originalSøknad.søknadsid && søknad.behandlingstema == originalSøknad.behandlingstema
            }

        val nySøknadId =
            åpenFFSøknad?.søknadsid ?: bbmConsumer
                .opprettSøknader(
                    OpprettSøknadRequest(
                        saksnummer = originalSøknad.saksnummer,
                        behandlingsid = behandling.id,
                        refVedtaksid = behandling.omgjøringsdetaljer?.omgjørVedtakId,
                        refSøknadsid = originalSøknad.søknadsid,
                        behandlingstype = behandlingstype,
                        behandlerenhet = originalSøknad.behandlerenhet ?: behandling.behandlerEnhet,
                        hovedsøknadsid = hovedsøknadsid,
                        søktAv = originalSøknad.søktAvType,
                        søknadMottattDato = behandling.mottattdato,
                        behandlingstema = originalSøknad.behandlingstema,
                        søknadFomDato = originalSøknad.søknadFomDato!!,
                        innkreving = originalSøknad.innkreving,
                        barnListe = barnISøknad.map { Barn(it.personident!!, it.innbetaltBeløp) },
                    ),
                ).søknadsid
        val forholdsmessigFordelingSøknad =
            ForholdsmessigFordelingSøknadBarn(
                søknadsid = nySøknadId,
                mottattDato = behandling.mottattdato,
                søknadFomDato = originalSøknad.søknadFomDato,
                søktAvType = originalSøknad.søktAvType,
                behandlingstype = originalSøknad.behandlingstype,
                behandlingstema = originalSøknad.behandlingstema,
                innkreving = originalSøknad.innkreving,
                saksnummer = originalSøknad.saksnummer,
                enhet = originalSøknad.behandlerenhet ?: behandling.behandlerEnhet,
                omgjørSøknadsid = originalSøknad.søknadsid,
                omgjørVedtaksid = behandling.omgjøringsdetaljer?.omgjørVedtakId,
                status = Behandlingstatus.UNDER_BEHANDLING,
            )
        behandling.søknadsbarn
            .filter {
                barnISøknadIdenter.contains(it.ident) && it.stønadstype == originalSøknad.behandlingstema.tilStønadstype() &&
                    !it.harSøknad(nySøknadId)
            }.forEach {
                val løpendeBidrag =
                    løpendeBidraggsakerBP.hentBidragSakForKravhaver(it.ident!!, it.stønadstype)
                it.forholdsmessigFordeling!!.let {
                    it.søknader.add(forholdsmessigFordelingSøknad)
                    it.løperBidragFra = løpendeBidrag?.periodeFra
                    it.løperBidragTil = løpendeBidrag?.periodeTil
                    it.harLøpendeBidrag =
                        løpendeBidrag?.løperBidragEtterDato(behandling.finnBeregnTilDato().toYearMonth()) == true
                }

                it.innkrevingstype = if (originalSøknad.innkreving) Innkrevingstype.MED_INNKREVING else Innkrevingstype.UTEN_INNKREVING
                it.bidragsmottaker!!
                    .forholdsmessigFordeling!!
                    .søknader
                    .add(forholdsmessigFordelingSøknad)
            }
        if (!behandling.bidragspliktig!!.harSøknad(nySøknadId)) {
            behandling.bidragspliktig!!
                .forholdsmessigFordeling!!
                .søknader
                .add(forholdsmessigFordelingSøknad)
        }
        return nySøknadId
    }
}
