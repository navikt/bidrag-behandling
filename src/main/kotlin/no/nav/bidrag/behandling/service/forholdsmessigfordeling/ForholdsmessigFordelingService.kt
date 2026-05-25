package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagLøpendeBidragFF
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.leggTilGebyr
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.grunnlag.LøpendeBidragGrunnlagForholdsmessigFordeling
import no.nav.bidrag.behandling.dto.grunnlag.PersonStønad
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegning
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.OpprettFFRequest
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningService
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.service.hentPerson
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.behandling.erSamme
import no.nav.bidrag.behandling.transformers.behandling.oppdaterBehandlingEtterOppdatertRoller
import no.nav.bidrag.behandling.transformers.finnPeriodeLøperBidrag
import no.nav.bidrag.behandling.transformers.finnSistePeriodeLøpendePeriodeInnenforSøktFomDato
import no.nav.bidrag.behandling.transformers.finnesLøpendeBidragForRolle
import no.nav.bidrag.behandling.transformers.grunnlagsreferanseSimulert
import no.nav.bidrag.behandling.transformers.harSlåttUtTilForholdsmessigFordeling
import no.nav.bidrag.behandling.transformers.løperPeriodeEtterBeregnTil
import no.nav.bidrag.behandling.transformers.løperPeriodeEtterSøktFomDato
import no.nav.bidrag.behandling.transformers.mapTilBeregnetBidragDto
import no.nav.bidrag.behandling.transformers.maxOfNullable
import no.nav.bidrag.behandling.transformers.tilDato18årsBidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregningsperiode
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragTilFordelingLøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.hendelse.BehandlingStatusType
import no.nav.bidrag.transport.behandling.vedtak.Periode
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.toYearMonth
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Service
class ForholdsmessigFordelingService(
    private val sakConsumer: BidragSakConsumer,
    private val behandlingRepository: BehandlingRepository,
    private val behandlingService: BehandlingService,
    private val beløpshistorikkConsumer: BidragBeløpshistorikkConsumer,
    private val grunnlagService: GrunnlagService,
    private val bbmConsumer: BidragBBMConsumer,
    private val forsendelseService: ForsendelseService,
    private val beregningService: BeregningService,
    private val virkningstidspunktService: VirkningstidspunktService,
    private val underholdService: UnderholdService,
) {
    private val kravhaverService =
        ForholdsmessigFordelingKravhaverService(
            sakConsumer = sakConsumer,
            behandlingRepository = behandlingRepository,
            beløpshistorikkConsumer = beløpshistorikkConsumer,
            bbmConsumer = bbmConsumer,
        )

    private val søknadSyncService =
        ForholdsmessigFordelingSøknadSyncService(
            bbmConsumer = bbmConsumer,
            kravhaverService = kravhaverService,
        )

    private val barnStatusService =
        ForholdsmessigFordelingBarnStatusService(
            søknadSyncService = søknadSyncService,
        )

    private val søknadOpprettService =
        ForholdsmessigFordelingSøknadOpprettService(
            bbmConsumer = bbmConsumer,
            sakConsumer = sakConsumer,
            forsendelseService = forsendelseService,
            kravhaverService = kravhaverService,
            virkningstidspunktService = virkningstidspunktService,
        )

    private val overføringService =
        ForholdsmessigFordelingOverføringService(
            bbmConsumer = bbmConsumer,
            sakConsumer = sakConsumer,
            behandlingService = behandlingService,
            behandlingRepository = behandlingRepository,
            kravhaverService = kravhaverService,
        )

    private val klageService =
        ForholdsmessigFordelingKlageService(
            bbmConsumer = bbmConsumer,
            behandlingService = behandlingService,
            grunnlagService = grunnlagService,
            underholdService = underholdService,
            virkningstidspunktService = virkningstidspunktService,
            kravhaverService = kravhaverService,
            søknadOpprettService = søknadOpprettService,
            overføringService = overføringService,
        )

    private val barnService =
        ForholdsmessigFordelingBarnService(
            bbmConsumer = bbmConsumer,
            behandlingService = behandlingService,
            virkningstidspunktService = virkningstidspunktService,
            underholdService = underholdService,
            kravhaverService = kravhaverService,
            søknadOpprettService = søknadOpprettService,
        )

    @Transactional
    fun slettEllerGjennopprettKlageSøknader(
        behandling: Behandling,
        søknadsidSomSlettes: Long,
    ) = klageService.slettEllerGjennopprettKlageSøknader(behandling, søknadsidSomSlettes)

    @Transactional
    fun opprettSøknaderForKlageEllerOmgjøring(
        behandling: Behandling,
        opprettetEllerOppdaterSøknadsid: Long,
    ) = klageService.opprettSøknaderForKlageEllerOmgjøring(
        behandling,
        opprettetEllerOppdaterSøknadsid,
    )

    @Transactional
    fun oppdaterSøknadStatuserForAlleRoller(behandling: Behandling) {
        søknadSyncService.oppdaterSøknadStatuserForAlleRoller(behandling)
    }

    @Transactional
    fun slettDuplikatForholdsmessigFordelingSøknader(behandling: Behandling) {
        søknadSyncService.slettDuplikatForholdsmessigFordelingSøknader(behandling)
    }

    @Transactional
    fun avsluttForholdsmessigFordeling(
        behandling: Behandling,
        slettBarn: List<Rolle>,
    ) = barnService.avsluttForholdsmessigFordeling(behandling, slettBarn)

    @Transactional
    fun oppdaterBarnEtterInnkrevingsvedtak(
        behandling: Behandling,
        barn: PersonStønad,
    ) = barnService.oppdaterBarnEtterInnkrevingsvedtak(behandling, barn)

    @Transactional
    fun slettRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
    ) = barnService.slettRevurderingsbarn(behandling, rolle)

    @Transactional
    fun oppdaterBarnEtterOpphør(
        behandling: Behandling,
        barnIdent: Personident,
        stønadstype: Stønadstype?,
        periode: Periode,
    ) = barnService.oppdaterBarnEtterOpphør(behandling, barnIdent, stønadstype, periode)

    @Transactional
    fun leggTilEllerSlettBarnFraBehandlingSomErIFF(request: OppdaterBarnFraFFRequest) =
        barnService.leggTilEllerSlettBarnFraBehandlingSomErIFF(request)

    @Transactional
    fun slettBarnEllerBehandling(
        slettBarn: List<Rolle>,
        behandling: Behandling,
        søknadsid: Long,
        søknadBleSlettet: Boolean = false,
    ) {
        barnService.slettBarnEllerBehandling(slettBarn, behandling, søknadsid, søknadBleSlettet)
        if (søknadBleSlettet) {
            søknadSyncService.endreHovedsøknadIFFEtterHovedsøknadBleSlettet(behandling, søknadsid)
        }
    }

    fun finnEnhetForBarnIBehandling(behandling: Behandling): String = kravhaverService.finnEnhetForBarnIBehandling(behandling)

    fun hentAlleRelevanteKravhavere(behandling: Behandling): Set<SakKravhaver> = kravhaverService.hentAlleRelevanteKravhavere(behandling)

    fun hentAlleÅpneEllerLøpendeBidraggsakerForBP(
        behandling: Behandling,
        eksisterendeRelevanteKravhavere: Set<SakKravhaver>? = null,
    ): Set<SakKravhaver> = kravhaverService.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, eksisterendeRelevanteKravhavere)

    @Transactional
    fun overførÅpneBehandlingTilHovedbehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ): List<Long> = overføringService.overførÅpneBehandlingTilHovedbehandling(behandling, relevanteKravhavere)

    fun overførÅpneBisysSøknaderTilBehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ) = overføringService.overførÅpneBisysSøknaderTilBehandling(behandling, relevanteKravhavere)

    // endregion

    // region Synkronisering

    /** Feilhåndtering hvis FF søknad blir slettet manuelt eller ved feil */
    @Transactional
    fun synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling: Behandling) {
        val løpendeBidraggsakerBP =
            kravhaverService.hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!), behandling.finnBeregningsperiode())

        grunnlagService.lagreBeløpshistorikkGrunnlag(behandling)
        grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(behandling)

        val alleSøknaderRelevantForBehandling =
            kravhaverService.hentÅpneSøknader(
                behandling.bidragspliktig!!.ident!!,
                behandling.behandlingstypeForFF,
                behandling.omgjøringsdetaljer,
            )

        behandling.roller.forEach { rolle ->
            val løpendeBidrag = løpendeBidraggsakerBP.find { rolle.erSammeRolle(it.kravhaver.verdi, it.type) }
            rolle.forholdsmessigFordeling!!.løperBidragFra = løpendeBidrag?.periodeFra
            rolle.forholdsmessigFordeling!!.løperBidragTil = løpendeBidrag?.periodeTil
            rolle.forholdsmessigFordeling!!.harLøpendeBidrag =
                løpendeBidrag?.løperBidragEtterDato(behandling.eldsteSøktFomDato.toYearMonth()) ?: false
        }

        oppdaterSøknadStatuserForAlleRoller(behandling)
        slettDuplikatForholdsmessigFordelingSøknader(behandling)
        if (behandling.erKlageEllerOmgjøring) {
            opprettSøknaderForKlageEllerOmgjøring(behandling, behandling.soknadsid!!)
            søknadSyncService.knyttSammenManglendeSøknadsknytningerIBehandling(behandling)
            return
        }

        leggTilRollerFraRelevanteSøknaderSomIkkeErIBehandling(behandling, alleSøknaderRelevantForBehandling)

        behandling.søknadsbarn.forEach { rolle ->
            sjekkOgSynkroniserSøknadsstatusForBarn(rolle, behandling, løpendeBidraggsakerBP, alleSøknaderRelevantForBehandling)
        }

        val hovedsøknad = bbmConsumer.hentSøknad(behandling.soknadsid!!)
        if (hovedsøknad != null && hovedsøknad.søknad.behandlingStatusType == BehandlingStatusType.AVBRUTT) {
            søknadSyncService.endreHovedsøknadIFFEtterHovedsøknadBleSlettet(behandling, hovedsøknad.søknad.søknadsid)
        }

        søknadSyncService.knyttSammenManglendeSøknadsknytningerIBehandling(behandling)
    }

    private fun sjekkOgSynkroniserSøknadsstatusForBarn(
        rolle: Rolle,
        behandling: Behandling,
        løpendeBidraggsakerBP: List<LøpendeBidragSakPeriode>,
        alleSøknaderRelevantForBehandling: List<HentSøknad>,
    ) {
        val ffDetaljer = rolle.forholdsmessigFordeling ?: return
        val beløpshistorikk = løpendeBidraggsakerBP.find { rolle.erSammeRolle(it.kravhaver.verdi, it.type) }
        val løperBidrag =
            if (beløpshistorikk != null) {
                rolle.løperPeriodeEtterBeregnTil(
                    ÅrMånedsperiode(beløpshistorikk.periodeFra, beløpshistorikk.periodeTil),
                )
            } else {
                false
            }

        val eldsteSøknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
        val erMedInnkreving =
            (eldsteSøknad != null && eldsteSøknad.innkreving) || rolle.innkrevingstype == Innkrevingstype.MED_INNKREVING
        if (!erMedInnkreving && løperBidrag) {
            barnService.oppdaterBarnEtterInnkrevingsvedtak(behandling, PersonStønad(rolle.personident, rolle.stønadstype))
        }
        if (erMedInnkreving && !løperBidrag && rolle.erRevurderingsbarn) {
            barnService.oppdaterRevurderingsbarnFraInnkrevingTilUtenInnkreving(behandling, rolle)
            rolle.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
        }

        val lagretSøknader = ffDetaljer.søknader
        val søknaderForBarn = søknadSyncService.finnApneSoknaderForBarn(alleSøknaderRelevantForBehandling, rolle)
        val åpneSøknaderIkkeFF = søknaderForBarn.filter { !it.behandlingstype.erForholdsmessigFordeling }
        val åpneSøknaderFF = søknaderForBarn.filter { it.behandlingstype.erForholdsmessigFordeling }

        barnStatusService.sjekkOgOppdaterStatusPåRevurderingOgSøknadsbarn(
            åpneSøknaderIkkeFF = åpneSøknaderIkkeFF,
            rolle = rolle,
            behandling = behandling,
            åpneSøknaderFF = åpneSøknaderFF,
            lagretSøknader = lagretSøknader,
            leggTilEllerSlettBarnFraBehandlingSomErIFF = barnService::leggTilEllerSlettBarnFraBehandlingSomErIFF,
            feilregistrerBarnFraFFSøknad = barnService::feilregistrerBarnFraFFSøknad,
            slettRevurderingsbarn = barnService::slettRevurderingsbarn,
            opprettEllerOppdaterForholdsmessigFordeling = { behandlingId, reevaluerSøknadsbarn ->
                opprettEllerOppdaterForholdsmessigFordeling(
                    behandlingId = behandlingId,
                    reevaluerSøkndasbarn = reevaluerSøknadsbarn,
                )
            },
            leggTilEllerOpprettSøknadForRevurderingsbarn =
                { behandlingArg, barnIdent, stønadstypeArg, saksnummerArg, søktFomDatoArg, medInnkrevingArg ->
                    søknadOpprettService.leggTilEllerOpprettSøknadForRevurderingsbarn(
                        behandling = behandlingArg,
                        barnIdent = barnIdent,
                        stønadstype = stønadstypeArg,
                        saksnummer = saksnummerArg,
                        søktFomDato = søktFomDatoArg,
                        medInnkreving = medInnkrevingArg,
                    )
                },
        )
    }

    @Transactional
    fun opprettEllerOppdaterForholdsmessigFordeling(
        behandlingId: Long,
        reevaluerSøkndasbarn: Pair<String, Stønadstype?>? = null,
        request: OpprettFFRequest? = null,
    ) {
        try {
            if (!UnleashFeatures.TILGANG_OPPRETTE_FF.isEnabled) {
                LOGGER.info { "Opprettelse av forholdsmessig fordeling er deaktivert" }
                ugyldigForespørsel("Opprettelse av forholdsmessig fordeling er deaktivert")
            }
            val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
            val erOppdateringAvBehandlingSomErIFF = behandling.erIForholdsmessigFordeling
            val nyesteLøpendeBidragGrunnlag = sjekkBeregningKreverForholdsmessigFordeling(behandling).løpendeBidragBarn

            val originalBM = behandling.bidragsmottaker!!.ident

            behandling.alleBidragsmottakere.filter { it.forholdsmessigFordeling == null }.forEach {
                it.forholdsmessigFordeling = behandling.tilFFDetaljerBM()
            }
            val behandlerEnhet = finnEnhetForBarnIBehandling(behandling)
            val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling).toMutableSet()
            val eksisterendeSøknadsbarn = finnEksisterendeSøknadsbarn(behandling, reevaluerSøkndasbarn)

            leggTilManglendeSøknadsbarnIKravhavere(reevaluerSøkndasbarn, relevanteKravhavere, behandling)

            val relevanteKravhavereIkkeSøknadsbarn =
                relevanteKravhavere
                    .filter { !eksisterendeSøknadsbarn.contains(it.distinctKey) }
                    .toSet()

            overføringService.overførÅpneBehandlingTilHovedbehandling(behandling, relevanteKravhavereIkkeSøknadsbarn)
            overføringService.overførÅpneBisysSøknaderTilBehandling(behandling, relevanteKravhavereIkkeSøknadsbarn)

            opprettRevurderingsbarnOgSøknaderForNyeKravhavere(
                behandling,
                relevanteKravhavereIkkeSøknadsbarn,
                behandlerEnhet,
                request,
                erOppdateringAvBehandlingSomErIFF,
            )

            behandling.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    erHovedbehandling = true,
                )

            oppdaterGebyrDetaljerRollerIBehandling(behandling, relevanteKravhavere)
            oppdaterFFDetaljerPåSøknadsbarn(behandling, relevanteKravhavere, eksisterendeSøknadsbarn, originalBM)

            overføringService.giSakTilgangTilEnhet(behandling, behandlerEnhet)
            syncGebyrSøknadReferanse(behandling)
            lagreOgOppdaterGrunnlag(behandling, nyesteLøpendeBidragGrunnlag)
        } catch (e: Exception) {
            secureLogger.error(e) { "Det skjedde en feil ved opprettelse eller oppdatering av FF for behandling $behandlingId" }
            behandlingRepository.markerOpprettelseAvFFFeilet(behandlingId)
        }
    }

    private fun finnEksisterendeSøknadsbarn(
        behandling: Behandling,
        reevaluerSøkndasbarn: Pair<String, Stønadstype?>?,
    ): List<String> =
        behandling.søknadsbarn
            .filter {
                reevaluerSøkndasbarn == null ||
                    (!reevaluerSøkndasbarn.erSamme(it.ident!!, it.stønadstype))
            }.map { it.identStønadstypeNøkkel }

    private fun leggTilManglendeSøknadsbarnIKravhavere(
        reevaluerSøkndasbarn: Pair<String, Stønadstype?>?,
        relevanteKravhavere: MutableSet<SakKravhaver>,
        behandling: Behandling,
    ) {
        if (reevaluerSøkndasbarn != null && relevanteKravhavere.none { reevaluerSøkndasbarn.erSamme(it.kravhaver, it.stønadstype) }) {
            val søknadsbarn = behandling.søknadsbarn.find { it.erSammeRolle(reevaluerSøkndasbarn.first, reevaluerSøkndasbarn.second) }
            relevanteKravhavere.add(
                SakKravhaver(
                    kravhaver = reevaluerSøkndasbarn.first,
                    stønadstype = reevaluerSøkndasbarn.second,
                    saksnummer = søknadsbarn!!.saksnummer,
                    bidragsmottaker = søknadsbarn.bidragsmottaker!!.ident,
                ),
            )
        }
    }

    private fun opprettRevurderingsbarnOgSøknaderForNyeKravhavere(
        behandling: Behandling,
        relevanteKravhavereIkkeSøknadsbarn: Set<SakKravhaver>,
        behandlerEnhet: String,
        request: OpprettFFRequest?,
        erOppdateringAvBehandlingSomErIFF: Boolean,
    ) {
        val bidragssakerBpUtenÅpenBehandling =
            relevanteKravhavereIkkeSøknadsbarn.filter {
                it.åpneSøknader.isEmpty() && it.åpneBehandlinger.isEmpty()
            }

        val revurderingFraDatoDefault = relevanteKravhavereIkkeSøknadsbarn.finnSøktFomRevurderingSøknad(behandling)
        bidragssakerBpUtenÅpenBehandling
            .filter { !it.saksnummer.isNullOrEmpty() }
            .sortedByDescending { it.stønadstype }
            .groupBy { kravhaverSak ->
                val tidligstSøktFomDato =
                    if (kravhaverSak.stønadstype == Stønadstype.BIDRAG18AAR) {
                        val person = hentPerson(kravhaverSak.kravhaver)
                        person?.fødselsdato?.tilDato18årsBidrag()
                    } else {
                        behandling.eldsteSøktFomDato
                    }
                val manueltOverstyrtDato =
                    request?.revurderingFraDato
                        ?: request?.detaljerBarn?.find { it.ident == kravhaverSak.kravhaver }?.manueltOverstyrtRevurderingFraDato
                        ?: revurderingFraDatoDefault
                Triple(kravhaverSak.saksnummer!!, kravhaverSak.stønadstype, maxOfNullable(tidligstSøktFomDato, manueltOverstyrtDato))
            }.forEach { (saksnummerLøpendeBidrag, løpendebidragssaker) ->
                søknadOpprettService.opprettRollerOgRevurderingssøknadForSak(
                    behandling,
                    saksnummerLøpendeBidrag.first,
                    løpendebidragssaker,
                    behandlerEnhet,
                    saksnummerLøpendeBidrag.second,
                    saksnummerLøpendeBidrag.third ?: revurderingFraDatoDefault,
                    erOppdateringAvBehandlingSomErIFF,
                )
            }
    }

    private fun oppdaterFFDetaljerPåSøknadsbarn(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
        eksisterendeSøknadsbarn: List<String>,
        originalBM: String?,
    ) {
        val søknaderSøknadsbarn = relevanteKravhavere.filter { eksisterendeSøknadsbarn.contains(it.distinctKey) }.toSet()

        behandling.søknadsbarn.forEach { barn ->
            if (barn.forholdsmessigFordeling == null) {
                val sakKravhaverSøknadsbarn = søknaderSøknadsbarn.find { it.erSammePerson(barn.ident!!, barn.stønadstype) }

                val søknadsdetaljer =
                    if (sakKravhaverSøknadsbarn != null) {
                        overføringService.overførÅpneBehandlingerOgSøknaderSøknadsbarn(sakKravhaverSøknadsbarn, behandling)
                        val søknader =
                            sakKravhaverSøknadsbarn.åpneSøknader
                                .filter { it.søknadsid != behandling.soknadsid }
                                .map { it.tilForholdsmessigFordelingSøknad() }
                        val behandlinger =
                            sakKravhaverSøknadsbarn.åpneBehandlinger
                                .filter { it.id != behandling.id && it.soknadsid != behandling.soknadsid }
                                .map { it.tilFFBarnDetaljer() }
                        søknader + behandlinger
                    } else {
                        emptyList()
                    } + behandling.tilFFBarnDetaljer()

                val løpendeBidrag = behandling.finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(barn)
                barn.forholdsmessigFordeling =
                    ForholdsmessigFordelingRolle(
                        delAvOpprinneligBehandling = true,
                        tilhørerSak = behandling.saksnummer,
                        behandlingsid = behandling.id,
                        behandlerenhet = behandling.behandlerEnhet,
                        bidragsmottaker = originalBM,
                        erRevurdering = false,
                        søknader = søknadsdetaljer.toMutableSet(),
                        løperBidragFra = løpendeBidrag?.periode?.fom,
                        løperBidragTil = løpendeBidrag?.periode?.til,
                        harLøpendeBidrag = løpendeBidrag?.løperBidragEtterDato(behandling.eldsteSøktFomDato.toYearMonth()) == true,
                    )
            }
        }
    }

    private fun lagreOgOppdaterGrunnlag(
        behandling: Behandling,
        nyesteLøpendeBidragGrunnlag: List<LøpendeBidragGrunnlagForholdsmessigFordeling>,
    ) {
        behandlingService.lagreBehandling(behandling)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        oppdaterBehandlingEtterOppdatertRoller(
            behandling,
            underholdService,
            virkningstidspunktService,
            behandling.søknadsbarn.map { it.tilOpprettRolleDto() },
            emptyList(),
        )
        opprettGrunnlagLøpendeBidrag(behandling, nyesteLøpendeBidragGrunnlag)
    }

    // endregion

    // region Sjekk FF

    @Transactional
    fun skalLeggeTilBarnFraAndreSøknaderEllerBehandlinger(behandlingId: Long): Boolean {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        if (behandling.forholdsmessigFordeling == null || !behandling.forholdsmessigFordeling!!.erHovedbehandling) return false

        return harLøpendeBidragForBarnIkkeIBehandling(behandling)
    }

    @Transactional
    fun sjekkSkalOppretteForholdsmessigFordeling(behandlingId: Long): SjekkForholdmessigFordelingResponse {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        if (behandling.vedtakstype == Vedtakstype.ALDERSJUSTERING) {
            return SjekkForholdmessigFordelingResponse(
                skalBehandlesAvEnhet = "",
                eldsteSøktFraDato = behandling.søktFomDato,
            )
        }
        val finnesLøpendeBidragSomOverlapperMedEldsteVirkning =
            !behandling.erVirkningstidspunktLiktForAlle &&
                behandling.søknadsbarn.any {
                    val periodeLøperBidrag = behandling.finnPeriodeLøperBidrag(it)
                    val periodeBeregning = ÅrMånedsperiode(behandling.eldsteVirkningstidspunkt.toYearMonth(), it.opphørsdato?.toYearMonth())
                    periodeLøperBidrag != null && periodeLøperBidrag.fom < it.virkningstidspunktRolle.toYearMonth() &&
                        periodeLøperBidrag.overlapper(periodeBeregning)
                }
        val behandlesAvEnhet = finnEnhetForBarnIBehandling(behandling)

        val eksisterendeSøknadsbarn = behandling.søknadsbarn.map { it.identStønadstypeNøkkel }
        val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling)
        val relevanteKravhavereIkkeSøknadsbarn = relevanteKravhavere.filter { !eksisterendeSøknadsbarn.contains(it.distinctKey) }
        val alleRelevanteKravhavere = relevanteKravhavereIkkeSøknadsbarn + relevanteKravhavere
        val bpsBarnMedLøpendeBidragEllerPrivatAvtale =
            if (relevanteKravhavereIkkeSøknadsbarn.isEmpty() && finnesLøpendeBidragSomOverlapperMedEldsteVirkning) {
                relevanteKravhavere
            } else {
                relevanteKravhavereIkkeSøknadsbarn
            }.toSet()
                .map { lb ->
                    val sak = lb.saksnummer?.let { sakConsumer.hentSak(it) }
                    lb.mapSakKravhaverTilForholdsmessigFordelingDto(
                        sak,
                        behandling,
                        lb.løperBidragEtterDato(alleRelevanteKravhavere.finnSøktFomRevurderingSøknad(behandling).toYearMonth()),
                    )
                }
        val resultat = sjekkBeregningKreverForholdsmessigFordeling(behandling)
        return SjekkForholdmessigFordelingResponse(
            skalBehandlesAvEnhet = behandlesAvEnhet,
            kanOppretteForholdsmessigFordeling =
                (
                    relevanteKravhavereIkkeSøknadsbarn.isNotEmpty() ||
                        (!behandling.erIForholdsmessigFordeling && finnesLøpendeBidragSomOverlapperMedEldsteVirkning)
                ),
            simulertGrunnlag = resultat.simulertGrunnlag,
            måOppretteForholdsmessigFordeling = resultat.beregningManglerGrunnlag,
            harSlåttUtTilForholdsmessigFordeling = resultat.harSlåttUtTilFF,
            eldsteSøktFraDato = relevanteKravhavere.finnEldsteSøktFomDato(behandling),
            barn = bpsBarnMedLøpendeBidragEllerPrivatAvtale,
            løpendeBidragBarn = resultat.løpendeBidragBarn,
        )
    }

    // endregion

    // region Private hjelpemetoder

    private fun opprettGrunnlagLøpendeBidrag(
        behandling: Behandling,
        nyesteLøpendeBidragGrunnlag: List<LøpendeBidragGrunnlagForholdsmessigFordeling>,
    ) {
        val type = Grunnlagsdatatype.LØPENDE_BIDRAG_OPPRETT_FORHOLDSMESSIG_FORDELING

        val eksisterendeGrunnlag =
            behandling.grunnlag.hentSisteGrunnlagLøpendeBidragFF(behandling) ?: emptyList()
        if (eksisterendeGrunnlag != nyesteLøpendeBidragGrunnlag) {
            secureLogger.debug {
                "Lagrer ny grunnlag løpende bidrag hvor siste aktive grunnlag var $eksisterendeGrunnlag"
            }
            val eksisterendeGrunnlagIdenter = eksisterendeGrunnlag.map { it.gjelderBarnIdent to it.gjelderStønadstype }
            val nyeGrunnlag =
                nyesteLøpendeBidragGrunnlag
                    .filter { !eksisterendeGrunnlagIdenter.contains(it.gjelderBarnIdent to it.gjelderStønadstype) }

            behandling.grunnlag.addAll(
                nyeGrunnlag.map {
                    val rolle = behandling.roller.find { r -> r.erSammeRolle(it.gjelderBarnIdent, it.gjelderStønadstype) }
                    Grunnlag(
                        behandling = behandling,
                        type = type,
                        gjelder = it.gjelderBarnIdent,
                        gjelderBarnRolle = rolle,
                        data = commonObjectmapper.writeValueAsString(it.løpendeBidragPerioder),
                        innhentet = LocalDateTime.now(),
                        aktiv = LocalDateTime.now(),
                        rolle = behandling.bidragspliktig!!,
                        erBearbeidet = false,
                    )
                },
            )
        }
    }

    private fun oppdaterGebyrDetaljerRollerIBehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ) {
        behandling.roller.forEach { rolle ->
            val sakKravhaver =
                relevanteKravhavere.filter {
                    rolle.rolletype == Rolletype.BIDRAGSPLIKTIG ||
                        it.kravhaver == rolle.ident ||
                        it.bidragsmottaker == rolle.ident
                }

            val gebyrDetaljerRolle =
                if (sakKravhaver.isNotEmpty()) {
                    val gebyrFraBehandlinger =
                        sakKravhaver.flatMap {
                            it.åpneBehandlinger
                                .filter { it.id != behandling.id && it.soknadsid != behandling.soknadsid }
                                .flatMap { it.roller.find { it.erSammeRolle(rolle) }?.gebyrSøknader ?: emptySet() }
                        }
                    val gebyrFraSøknader =
                        sakKravhaver.flatMap {
                            it.åpneSøknader
                                .filter { it.søknadsid != behandling.soknadsid }
                                .mapNotNull {
                                    val rolleISøknad =
                                        it.partISøknadListe.find { ps ->
                                            rolle.erSammeRolle(ps.personident!!, it.behandlingstema.tilStønadstype())
                                        }
                                    if (rolleISøknad != null && rolleISøknad.gebyr) {
                                        GebyrRolleSøknad(
                                            søknadsid = it.søknadsid,
                                            saksnummer = it.saksnummer,
                                            referanse = rolleISøknad.referanseGebyr,
                                            manueltOverstyrtGebyr = null,
                                        )
                                    } else {
                                        null
                                    }
                                }
                        }
                    gebyrFraSøknader + gebyrFraBehandlinger
                } else {
                    emptyList()
                }

            if (rolle.rolletype == Rolletype.BIDRAGSPLIKTIG && sakKravhaver.isNotEmpty()) {
                val søknader =
                    sakKravhaver.flatMap { it.åpneBehandlinger.map { it.tilFFBarnDetaljer() } } +
                        sakKravhaver.flatMap { it.åpneSøknader.map { it.tilForholdsmessigFordelingSøknad() } }
                if (rolle.forholdsmessigFordeling == null) {
                    rolle.forholdsmessigFordeling = behandling.tilFFDetaljerBP()
                }
                rolle.forholdsmessigFordeling!!.søknader.addAll(søknader.toMutableSet())
                LOGGER.info { "Legger til $søknader for bidragspliktig" }
            }
            LOGGER.info { "Legger til $gebyrDetaljerRolle for ${rolle.ident} - ${rolle.rolletype}" }

            rolle.leggTilGebyr(gebyrDetaljerRolle)
        }
    }

    private fun syncGebyrSøknadReferanse(behandling: Behandling) {
        behandling.roller.forEach { rolle ->
            rolle.hentEllerOpprettGebyr().gebyrSøknader.forEach { gebyrSøknad ->
                if (gebyrSøknad.referanse.isNullOrEmpty()) {
                    val søknad = bbmConsumer.hentSøknad(gebyrSøknad.søknadsid)
                    gebyrSøknad.referanse =
                        søknad
                            ?.søknad
                            ?.partISøknadListe
                            ?.find { rolle.erSammeRolle(it.personident!!, søknad.søknad.behandlingstema.tilStønadstype()) }
                            ?.referanseGebyr
                }
            }
        }
    }

    private fun leggTilRollerFraRelevanteSøknaderSomIkkeErIBehandling(
        behandling: Behandling,
        alleSøknaderRelevantForBehandling: List<HentSøknad>,
    ) {
        val alleIdenterIBehandling = behandling.roller.map { PersonStønad(it.personident, it.stønadstype) }
        val alleRollerRelevantSomIkkeErIBehandling =
            alleSøknaderRelevantForBehandling
                .flatMap { søknad ->
                    søknad.partISøknadListe
                        .filter { it.rolletype != Rolletype.BIDRAGSPLIKTIG }
                        .map { ps -> PersonStønad(Personident(ps.personident!!), søknad.behandlingstema.tilStønadstype()) }
                }.distinct()
                .filter { !alleIdenterIBehandling.contains(it) }

        alleRollerRelevantSomIkkeErIBehandling.forEach { rolle ->
            val søknad =
                alleSøknaderRelevantForBehandling.find { søknad ->
                    søknad.partISøknadListe.any { ps ->
                        rolle.personident!!.verdi == ps.personident &&
                            (rolle.stønadstype == null || rolle.stønadstype == søknad.behandlingstema.tilStønadstype())
                    }
                }!!
            barnService.leggTilEllerSlettBarnFraBehandlingSomErIFF(
                OppdaterBarnFraFFRequest(
                    behandling = behandling,
                    søknadsid = søknad.søknadsid,
                    saksnummer = søknad.saksnummer,
                    bmIdent = søknad.partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }?.personident,
                    søktFraDato = søknad.søknadFomDato,
                    stønadstype = søknad.behandlingstema.tilStønadstype(),
                    rollerSomSkalLeggesTilDto =
                        søknad.partISøknadListe.map {
                            OpprettRolleDto(
                                rolletype = it.rolletype,
                                fødselsdato = hentPersonFødselsdato(it.personident!!),
                                ident = Personident(it.personident!!),
                            )
                        },
                ),
            )
        }
    }

    private fun harLøpendeBidragForBarnIkkeIBehandling(behandling: Behandling): Boolean {
        val bidraggsakerBP =
            kravhaverService.hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!), behandling.finnBeregningsperiode())
        return bidraggsakerBP.any { lb ->
            val sak = sakConsumer.hentSak(lb.sak.verdi)
            val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
            behandling.roller.none { it.erSammeRolle(lb.kravhaver.verdi, lb.type) } ||
                behandling.roller.none { it.ident == bmFødselsnummer }
        }
    }

    private fun sjekkBeregningKreverForholdsmessigFordeling(behandling: Behandling): FFBeregningResultat =
        try {
            val resultat =
                try {
                    beregningService.beregneBidrag(behandling, true, simulerBeregning = true)
                } catch (e: Exception) {
                    ResultatBidragsberegning(vedtakstype = behandling.vedtakstype)
                }
            val resultatBarn = resultat.resultatBarn
            val lagretLøpendeBidrag = behandling.grunnlag.hentSisteGrunnlagLøpendeBidragFF(behandling) ?: emptyList()
            val grunnlagsliste = resultat.grunnlagsliste.toSet().toList()

            val simulertInntektGrunnlag =
                grunnlagsliste
                    .filter {
                        it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE &&
                            it.referanse.contains(grunnlagsreferanseSimulert)
                    }.map {
                        SimulertInntektGrunnlag(
                            type = it.type,
                            gjelder = grunnlagsliste.hentPersonMedReferanse(it.gjelderReferanse!!)!!.personIdent!!,
                            beløp = it.innholdTilObjekt<InntektsrapporteringPeriode>().beløp,
                            inntektstype = it.innholdTilObjekt<InntektsrapporteringPeriode>().inntektsrapportering,
                        )
                    }

            val løpendeBidrag =
                grunnlagsliste.filtrerOgKonverterBasertPåEgenReferanse<DelberegningBidragTilFordelingLøpendeBidrag>(
                    Grunnlagstype.DELBEREGNING_BIDRAG_TIL_FORDELING_LØPENDE_BIDRAG,
                )
            val lagretLøpendeBidragBarnIdenter = lagretLøpendeBidrag.map { it.gjelderBarnIdent to it.gjelderStønadstype }
            val løpendeBidragBarn =
                grunnlagsliste
                    .mapTilBeregnetBidragDto(løpendeBidrag)
                    .filter { !lagretLøpendeBidragBarnIdenter.contains(it.barn.ident!!.verdi to it.stønadstype) }
                    .groupBy { it.barn.ident!!.verdi to it.stønadstype }
                    .map { (identStønad, løpendeBidrag) ->
                        LøpendeBidragGrunnlagForholdsmessigFordeling(
                            identStønad.first,
                            identStønad.second,
                            løpendeBidrag.mapNotNull { it.beregnetBidrag },
                        )
                    }

            FFBeregningResultat(
                harSlåttUtTilFF = grunnlagsliste.harSlåttUtTilForholdsmessigFordeling(),
                beregningManglerGrunnlag = resultat.alleUgyldigBeregninger.isNotEmpty(),
                simulertGrunnlag = simulertInntektGrunnlag,
                løpendeBidragBarn = løpendeBidragBarn + lagretLøpendeBidrag,
            )
        } catch (e: Exception) {
            // Valideringsfeil
            FFBeregningResultat(false, false)
        }

    // endregion

    // region Dev-verktøy

    /** Brukes bare i dev */
    @Transactional
    fun lukkAllFFSaker(behandlingsid: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        val åpneSaker =
            kravhaverService.hentÅpneSøknader(
                behandling.bidragspliktig!!.ident!!,
                behandling.behandlingstypeForFF,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )
        åpneSaker.filter { it.behandlingstype == behandling.behandlingstypeForFF }.forEach {
            bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(it.søknadsid))
            bbmConsumer.fjernSammeknytning(it.søknadsid)
        }
    }

    // endregion
}
