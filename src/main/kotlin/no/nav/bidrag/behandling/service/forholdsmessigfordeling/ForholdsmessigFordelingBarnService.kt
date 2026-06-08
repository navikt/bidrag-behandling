package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.dto.grunnlag.PersonStønad
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.transformers.behandling.finnRolle
import no.nav.bidrag.behandling.transformers.behandling.oppdaterBehandlingEtterOppdatertRoller
import no.nav.bidrag.behandling.transformers.finnSistePeriodeLøpendePeriodeInnenforSøktFomDato
import no.nav.bidrag.behandling.transformers.harLøpendeBidragFørOpphørEllerLøpende
import no.nav.bidrag.behandling.transformers.løperBidragFørOpphør
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.hendelse.BehandlingStatusType
import no.nav.bidrag.transport.behandling.vedtak.Periode
import no.nav.bidrag.transport.felles.toLocalDate
import no.nav.bidrag.transport.felles.toYearMonth
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val BARN_LOGGER = KotlinLogging.logger {}

/**
 * Håndterer barn-livssyklus i forholdsmessig fordeling:
 * - Legge til / slette barn fra FF-behandlinger
 * - Status-overganger (søknadsbarn ↔ revurderingsbarn)
 * - Feilregistrering av barn/søknader
 * - Opphør og innkrevingsendringer
 */
class ForholdsmessigFordelingBarnService(
    private val bbmConsumer: BidragBBMConsumer,
    private val behandlingService: BehandlingService,
    private val virkningstidspunktService: VirkningstidspunktService,
    private val underholdService: UnderholdService,
    private val kravhaverService: ForholdsmessigFordelingKravhaverService,
    private val søknadService: ForholdsmessigFordelingSøknadService,
) {
    // ═══════════════════════════════════════════════════════════════════
    // region Legg til / slett barn
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Legger til eller sletter barn fra en behandling som er i FF.
     */
    fun leggTilEllerSlettBarnFraBehandlingSomErIFF(request: OppdaterBarnFraFFRequest) {
        val søknad =
            try {
                bbmConsumer.hentSøknad(request.søknadsid)?.søknad
            } catch (e: Exception) {
                BARN_LOGGER.error(e) { "Det skjedde en feil ved henting av søknad ${request.søknadsid}" }
                return
            }
        // Skal ikke trigge noe endringer for FF søknad
        if (søknad?.behandlingstype?.erForholdsmessigFordeling == true) {
            return
        }
        if (request.behandling.soknadsid != request.søknadsid) {
            bbmConsumer.sammeknyttSøknader(request.behandling.soknadsid!!, request.søknadsid)
        }
        val stønadstypeBeregnet =
            request.stønadstype ?: søknad
                ?.behandlingstema
                ?.tilStønadstype()
        val identerSomSkalSlettes = request.rollerSomSkalSlettes.mapNotNull { it.ident?.verdi }
        if (!request.erRevurdering) {
            feilregistrerRevurderingsbarnFraFFSøknad(request.behandling, request.rollerSomSkalLeggesTilDto, stønadstypeBeregnet)
        }
        val relevanteKravhavere = kravhaverService.hentAlleRelevanteKravhavere(request.behandling)

        val rollerSomSkalLeggesTil = mutableSetOf<Rolle>()
        request.rollerSomSkalLeggesTilDto
            .forEach { nyRolle ->
                val søknadsdetaljerBarn = request.søknadsdetaljer ?: request.behandling.tilFFBarnDetaljer()
                val eksisterendeRolle = request.behandling.finnRolle(nyRolle.ident!!.verdi, stønadstypeBeregnet)
                val ffRolleDetaljer =
                    ForholdsmessigFordelingRolle(
                        tilhørerSak = request.saksnummer,
                        delAvOpprinneligBehandling = true,
                        behandlingsid = request.behandling.id,
                        bidragsmottaker = request.bmIdent ?: request.behandling.bidragsmottakerForSak(request.saksnummer)?.ident,
                        behandlerenhet = request.behandlerenhet,
                        erRevurdering = request.erRevurdering,
                        søknader = mutableSetOf(søknadsdetaljerBarn.copy(søknadsid = request.søknadsid)),
                    )
                val løpendeBidragRolle =
                    relevanteKravhavere.find {
                        it.erLik(nyRolle.ident.verdi, stønadstypeBeregnet)
                    }
                if (eksisterendeRolle == null) {
                    opprettNyRolleForBarn(
                        nyRolle,
                        request,
                        stønadstypeBeregnet,
                        ffRolleDetaljer,
                        løpendeBidragRolle,
                        rollerSomSkalLeggesTil,
                    )
                } else {
                    oppdaterEksisterendeRolleForBarn(
                        eksisterendeRolle,
                        ffRolleDetaljer,
                        nyRolle,
                        request,
                        søknadsdetaljerBarn,
                        stønadstypeBeregnet,
                        rollerSomSkalLeggesTil,
                    )
                }
            }

        request.behandling.roller.addAll(rollerSomSkalLeggesTil)
        oppdaterBehandlingEtterOppdatertRoller(
            request.behandling,
            underholdService,
            virkningstidspunktService,
            request.rollerSomSkalLeggesTilDto,
            emptyList(),
        )
        val rollerSomSkalSlettes =
            request.behandling.roller
                .filter { identerSomSkalSlettes.contains(it.ident) && it.stønadstype == stønadstypeBeregnet }
                .map { it }
        slettBarnEllerBehandling(rollerSomSkalSlettes, request.behandling, request.søknadsid)
    }

    /**
     * Sletter barn fra behandling, eller sletter hele behandlingen hvis alle barn er revurderingsbarn.
     */
    fun slettBarnEllerBehandling(
        slettBarn: List<Rolle>,
        behandling: Behandling,
        søknadsid: Long,
        søknadBleSlettet: Boolean = false,
    ) {
        if (kanBehandlingSlettes(behandling, slettBarn)) {
            avsluttForholdsmessigFordeling(behandling, slettBarn)
            behandlingService.logiskSlettBehandling(behandling)
            bbmConsumer.fjernSammeknytningHovedsøknad(behandling.soknadsid!!)
        } else {
            slettBarn.forEach { slettBarnFraBehandlingFF(it, behandling, søknadsid) }
            behandlingService.sendOppdatertHendelse(behandling.id!!, false)
        }
        if (søknadBleSlettet) {
            bbmConsumer.fjernSammenknytning(søknadsid)
        }
    }

    /**
     * Sletter revurderingsbarn som ikke lenger har løpende bidrag eller privat avtale.
     */
    fun slettRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
    ) {
        val rolleHarLøpendeBidrag = rolle.harLøpendeBidragFørOpphørEllerLøpende()
        if (!rolle.erRevurderingsbarn || rolleHarLøpendeBidrag) return

        val eldsteSøknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
        feilregistrerBarnFraFFSøknad(rolle)
        behandlingService.slettRolleFraBehandling(behandling, rolle)
        behandling.roller.remove(rolle)
        secureLogger.info { "Slettet revurderingsbarn ${rolle.ident} fra behandling ${behandling.id}" }
        behandlingService.sendOppdatertHendelse(behandling.id!!, false)
        søknadService.opprettForsendelseForNySøknad(
            rolle.saksnummer,
            behandling,
            rolle.bidragsmottaker!!.ident!!,
            eldsteSøknad!!,
            listOf(SakKravhaver(kravhaver = rolle.ident!!, saksnummer = rolle.saksnummer, stønadstype = rolle.stønadstype)),
        )
    }

    /**
     * Avslutter forholdsmessig fordeling ved å feilregistrere alle revurderingssøknader.
     */
    fun avsluttForholdsmessigFordeling(
        behandling: Behandling,
        slettBarn: List<Rolle>,
    ) {
        if (behandling.forholdsmessigFordeling == null) return
        if (!behandling.forholdsmessigFordeling!!.erHovedbehandling) return

        if (!kanBehandlingSlettes(behandling, slettBarn)) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke slette behandling fordi den inneholder flere søknader som ikke er revurdering",
            )
        }
        behandling.søknadsbarn
            .filter { it.forholdsmessigFordeling!!.erRevurdering }
            .forEach {
                feilregistrerFFSøknad(it)
            }
    }

    private fun kanBehandlingSlettes(
        behandling: Behandling,
        slettBarn: List<Rolle>,
    ): Boolean {
        val barnIkkeRevurdering =
            behandling.søknadsbarn
                .filter { slettBarn.isEmpty() || !slettBarn.mapNotNull { it.ident }.contains(it.ident) }
                .filter { it.forholdsmessigFordeling == null || !it.forholdsmessigFordeling!!.erRevurdering }
        return barnIkkeRevurdering.isEmpty()
    }

    private fun slettBarnFraBehandlingFF(
        barn: Rolle,
        behandling: Behandling,
        søknadsid: Long,
    ) {
        barn.fjernSøknad(søknadsid)
        barn.fjernGebyr(søknadsid)
        if (barn.forholdsmessigFordeling!!.søknaderUnderBehandling.isNotEmpty()) {
            barn.innkrevingstype = if (barn.harSøknadMedInnkreving) Innkrevingstype.MED_INNKREVING else Innkrevingstype.UTEN_INNKREVING
            BARN_LOGGER.info {
                "Barnet er koblet til flere søknader ${barn.forholdsmessigFordeling!!.søknader}" +
                    " etter den ble slettet fra søknad $søknadsid. Gjør ingen endring. Behandlingid = ${behandling.id}"
            }
            return
        }
        BARN_LOGGER.info { "Sletter barn ${barn.ident} fra behandling ${behandling.id} og lager ny revurderingsøknad" }
        barn.forholdsmessigFordeling!!.erRevurdering = true

        if (!barn.harLøpendeBidragFørOpphørEllerLøpende()) {
            feilregistrerBarnFraFFSøknad(barn)
            behandlingService.slettRolleFraBehandling(behandling, barn)
            behandling.roller.remove(barn)
            secureLogger.info { "Slettet barn ${barn.ident} fra behandling ${behandling.id}" }
        } else {
            val løpendeBidrag = behandling.finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(barn)
            val skalOppretteFFSøknadMedInnkreving =
                løpendeBidrag?.løperBidragEtterDato(behandling.eldsteSøktFomDato.toYearMonth()) == true

            val søktFomDato = LocalDate.now().plusMonths(1).withDayOfMonth(1)

            val søknad =
                søknadService.leggTilEllerOpprettSøknadForRevurderingsbarn(
                    behandling,
                    barn.ident!!,
                    barn.stønadstype,
                    barn.forholdsmessigFordeling!!.tilhørerSak,
                    søktFomDato,
                    skalOppretteFFSøknadMedInnkreving,
                )
            barn.forholdsmessigFordeling!!.søknader.add(søknad)
            barn.årsak = VirkningstidspunktÅrsakstype.REVURDERING_MÅNEDEN_ETTER
            barn.innkrevingstype =
                if (skalOppretteFFSøknadMedInnkreving) Innkrevingstype.MED_INNKREVING else Innkrevingstype.UTEN_INNKREVING
            virkningstidspunktService.oppdaterVirkningstidspunkt(
                barn.id,
                søktFomDato.withDayOfMonth(1),
                behandling,
                forrigeVirkningstidspunkt = behandling.eldsteVirkningstidspunkt,
            )
        }
    }

    private fun opprettNyRolleForBarn(
        nyRolle: OpprettRolleDto,
        request: OppdaterBarnFraFFRequest,
        stønadstypeBeregnet: Stønadstype?,
        ffRolleDetaljer: ForholdsmessigFordelingRolle,
        løpendeBidragRolle: SakKravhaver?,
        rollerSomSkalLeggesTil: MutableSet<Rolle>,
    ) {
        val rolle = nyRolle.toRolle(request.behandling, stønadstypeBeregnet, request.søktFraDato)
        val løperBidrag =
            løpendeBidragRolle?.løperBidragEtterDato(request.søknadsdetaljer!!.søknadFomDato!!.toYearMonth()) == true
        rolle.forholdsmessigFordeling =
            ffRolleDetaljer.copy(
                løperBidragFra = if (løperBidrag) løpendeBidragRolle.løperBidragFra else null,
                løperBidragTil = if (løperBidrag) løpendeBidragRolle.løperBidragTil else null,
            )
        rolle.innkrevingstype =
            if ((request.medInnkreving == null && løperBidrag) || request.medInnkreving == true) {
                Innkrevingstype.MED_INNKREVING
            } else {
                Innkrevingstype.UTEN_INNKREVING
            }
        rolle.opphørsdato = if (løperBidrag) løpendeBidragRolle.løperBidragTil?.toLocalDate() else null
        if (nyRolle.harGebyrsøknad) {
            val gebyr = GebyrRolle()
            gebyr.gebyrSøknader.add(
                GebyrRolleSøknad(
                    gjelder18ÅrSøknad = request.gebyrGjelder18År,
                    saksnummer = request.saksnummer,
                    søknadsid = request.søknadsid,
                    referanse = nyRolle.referanseGebyr,
                ),
            )
            rolle.gebyr = gebyr
            rolle.harGebyrsøknad = true
        }
        rollerSomSkalLeggesTil.add(rolle)
    }

    private fun oppdaterEksisterendeRolleForBarn(
        eksisterendeRolle: Rolle,
        ffRolleDetaljer: ForholdsmessigFordelingRolle,
        nyRolle: OpprettRolleDto,
        request: OppdaterBarnFraFFRequest,
        søknadsdetaljerBarn: ForholdsmessigFordelingSøknadBarn,
        stønadstypeBeregnet: Stønadstype?,
        rollerSomSkalLeggesTil: MutableSet<Rolle>,
    ) {
        if (eksisterendeRolle.forholdsmessigFordeling == null) {
            eksisterendeRolle.forholdsmessigFordeling = ffRolleDetaljer
        } else {
            val varRevurderingsbarn = eksisterendeRolle.forholdsmessigFordeling!!.erRevurdering
            val eksisterendeSøknadsliste = eksisterendeRolle.forholdsmessigFordeling!!.søknader
            eksisterendeRolle.stønadstype = stønadstypeBeregnet ?: eksisterendeRolle.stønadstype
            eksisterendeRolle.forholdsmessigFordeling =
                ffRolleDetaljer.copy(
                    søknader =
                        (
                            eksisterendeSøknadsliste +
                                setOf(søknadsdetaljerBarn.copy(søknadsid = request.søknadsid))
                        ).toMutableSet(),
                )
            eksisterendeRolle.innkrevingstype =
                if (request.medInnkreving == null) {
                    eksisterendeRolle.innkrevingstype
                } else if (request.medInnkreving) {
                    Innkrevingstype.MED_INNKREVING
                } else {
                    Innkrevingstype.UTEN_INNKREVING
                }
            if (varRevurderingsbarn && request.søktFraDato != null) {
                eksisterendeRolle.årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
                virkningstidspunktService.oppdaterVirkningstidspunkt(
                    eksisterendeRolle.id,
                    request.søktFraDato.withDayOfMonth(1),
                    request.behandling,
                )
            }
        }

        if (nyRolle.harGebyrsøknad) {
            val gebyr = eksisterendeRolle.gebyr ?: GebyrRolle()
            gebyr.gebyrSøknader.add(
                GebyrRolleSøknad(
                    gjelder18ÅrSøknad = request.gebyrGjelder18År,
                    saksnummer = request.saksnummer,
                    søknadsid = request.søknadsid,
                    referanse = nyRolle.referanseGebyr,
                ),
            )
            eksisterendeRolle.gebyr = gebyr
            eksisterendeRolle.harGebyrsøknad = true
        }
        rollerSomSkalLeggesTil.add(eksisterendeRolle)
    }

    // endregion

    // ═══════════════════════════════════════════════════════════════════
    // region Oppdatering (innkreving, opphør)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Oppdaterer barn etter det er fattet innkrevingsvedtak.
     * Feilregistrerer gammel søknad og oppretter ny med riktig innkrevingsstatus.
     */
    fun oppdaterBarnEtterInnkrevingsvedtak(
        behandling: Behandling,
        barn: PersonStønad,
    ) {
        val rolle = behandling.søknadsbarn.find { it.erSammeRolle(barn.personident!!.verdi, barn.stønadstype) } ?: return
        val relevanteKravhavere = kravhaverService.hentAlleRelevanteKravhavere(behandling)

        val rollerRevurderingsbarn =
            behandling.søknadsbarn.filter { it.erRevurderingsbarn }.map {
                PersonStønad(
                    it.personident,
                    it.stønadstype,
                )
            }
        if (rolle.erRevurderingsbarn) {
            val søknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
            if (søknad == null || !søknad.innkreving) {
                feilregistrerBarnFraFFSøknad(rolle)
                søknadService.opprettRollerOgRevurderingssøknadForSak(
                    behandling,
                    behandling.saksnummer,
                    relevanteKravhavere.filter { it.erLik(rolle.ident!!, rolle.stønadstype) },
                    behandling.behandlerEnhet,
                    rolle.stønadstype,
                    søknad?.søknadFomDato ?: rolle.forholdsmessigFordeling?.sisteOpprettetSøknad?.søknadFomDato
                        ?: relevanteKravhavere
                            .filter {
                                rollerRevurderingsbarn.contains(PersonStønad(Personident(it.kravhaver), it.stønadstype))
                            }.finnSøktFomRevurderingSøknad(behandling),
                    true,
                )
            }
        }
    }

    /**
     * Oppdaterer revurderingsbarn fra innkrevingssøknad til uten innkreving.
     */
    fun oppdaterRevurderingsbarnFraInnkrevingTilUtenInnkreving(
        behandling: Behandling,
        rolle: Rolle,
    ) {
        if (rolle.erRevurderingsbarn) {
            val relevanteKravhavere = kravhaverService.hentAlleRelevanteKravhavere(behandling)
            val søknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
            if (søknad == null || søknad.innkreving) {
                feilregistrerBarnFraFFSøknad(rolle)
                søknadService.opprettRollerOgRevurderingssøknadForSak(
                    behandling,
                    behandling.saksnummer,
                    relevanteKravhavere.filter { it.erLik(rolle.ident!!, rolle.stønadstype) },
                    behandling.behandlerEnhet,
                    rolle.stønadstype,
                    søknad?.søknadFomDato ?: rolle.forholdsmessigFordeling?.sisteOpprettetSøknad?.søknadFomDato!!,
                    true,
                )
            }
        }
    }

    /**
     * Oppdaterer barn etter opphør av bidrag.
     * Håndterer oppdatering av virkningstidspunkt, opphørsdato, og evt. sletting av revurderingsbarn.
     */
    fun oppdaterBarnEtterOpphør(
        behandling: Behandling,
        barnIdent: Personident,
        stønadstype: Stønadstype?,
        periode: Periode,
    ) {
        val rolle = behandling.søknadsbarn.find { it.erSammeRolle(barnIdent.verdi, stønadstype) } ?: return
        val opphørsdato = periode.periode.fom.toLocalDate()

        if (rolle.virkningstidspunkt != null && rolle.virkningstidspunkt!! > opphørsdato) {
            val nyVirkning = if (opphørsdato > behandling.eldsteVirkningstidspunkt) behandling.eldsteVirkningstidspunkt else opphørsdato
            virkningstidspunktService.oppdaterVirkningstidspunkt(
                rolle.id,
                nyVirkning,
                behandling,
                true,
                rekalkulerOpplysningerVedEndring = false,
            )
        }
        virkningstidspunktService.oppdaterOpphørsdato(
            OppdaterOpphørsdatoRequestDto(
                idRolle = rolle.id,
                opphørsdato = periode.periode.fom.toLocalDate(),
            ),
            behandling,
            tvingEndring = true,
        )

        if (!rolle.løperBidragFørOpphør()) {
            if (rolle.erRevurderingsbarn) {
                secureLogger.info {
                    "Sletter revurderingsbarn ${rolle.personident?.verdi} " +
                        "fra behandling ${behandling.id} etter det er fattet opphør av bidrag før søkt fom dato. " +
                        "Barnet har ingen løpende bidrag lenger og trenger derfor ikke å være revurderingsbarn"
                }
                slettRevurderingsbarn(behandling, rolle)
            } else {
                virkningstidspunktService.oppdaterAvslagÅrsak(
                    behandling,
                    OppdatereVirkningstidspunkt(
                        årsak = null,
                        avslag = Resultatkode.fraKode(periode.resultatkode),
                    ),
                    tvingEndring = true,
                )
                oppdaterRevurderingsbarnFraInnkrevingTilUtenInnkreving(behandling, rolle)
            }
        }
    }

    // endregion

    // ═══════════════════════════════════════════════════════════════════
    // region Feilregistrering
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Feilregistrerer et barn fra en FF-søknad og oppdaterer status på relaterte roller.
     */
    fun feilregistrerBarnFraFFSøknad(rolle: Rolle) {
        val søknader =
            rolle.forholdsmessigFordeling!!.søknaderUnderBehandling.filter {
                it.behandlingstype == rolle.behandling.behandlingstypeForFF
            }
        val søknaderFeilregistrertBarn =
            søknader.mapNotNull { søknad ->
                søknadService.feilregistrerBarnFraSøknad(rolle, søknad.søknadsid!!)
            }

        val søknaderFeilregistrert =
            søknaderFeilregistrertBarn.filter {
                val søknad = bbmConsumer.hentSøknad(it)
                søknad?.søknad?.behandlingStatusType == BehandlingStatusType.AVBRUTT
            }

        rolle.behandling.bidragsmottaker
            ?.forholdsmessigFordeling
            ?.søknaderUnderBehandling
            ?.filter { søknaderFeilregistrert.contains(it.søknadsid!!) }
            ?.forEach {
                it.status = Behandlingstatus.FEILREGISTRERT
            }

        rolle.behandling.bidragspliktig
            ?.forholdsmessigFordeling
            ?.søknaderUnderBehandling
            ?.filter { søknaderFeilregistrert.contains(it.søknadsid!!) }
            ?.forEach {
                it.status = Behandlingstatus.FEILREGISTRERT
            }
    }

    /**
     * Feilregistrerer en hel FF-søknad (brukes for revurderingssøknader).
     */
    fun feilregistrerFFSøknad(rolle: Rolle) {
        if (rolle.forholdsmessigFordeling == null || !rolle.forholdsmessigFordeling!!.erRevurdering) return
        rolle.forholdsmessigFordeling!!.søknaderUnderBehandling.forEach { søknad ->
            val søknadsid = søknad.søknadsid
            BARN_LOGGER.info { "Feilregistrerer søknad $søknadsid i behandling ${rolle.behandling.id}" }
            try {
                bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknadsid!!))
                bbmConsumer.fjernSammenknytning(søknadsid)
                søknad.status = Behandlingstatus.FEILREGISTRERT
                if (rolle.bidragsmottaker != null) {
                    rolle.bidragsmottaker!!
                        .forholdsmessigFordeling!!
                        .søknaderUnderBehandling
                        .find { it.søknadsid == søknad.søknadsid }
                        ?.let {
                            it.status = Behandlingstatus.FEILREGISTRERT
                        }
                }
            } catch (e: Exception) {
                BARN_LOGGER.error(e) { "Feil ved feilregistrering av søknad $søknadsid i behandling ${rolle.behandling.id}" }
            }
        }
    }

    /**
     * Feilregistrerer revurderingsbarn fra FF-søknad for barn som skal legges til på nytt.
     */
    fun feilregistrerRevurderingsbarnFraFFSøknad(
        behandling: Behandling,
        barn: List<OpprettRolleDto>,
        stønadstype: Stønadstype?,
    ) {
        barn
            .filter { it.rolletype == Rolletype.BARN }
            .mapNotNull { nyRolle -> behandling.roller.find { it.erSammeRolle(nyRolle.ident!!.verdi, stønadstype) } }
            .filter { it.forholdsmessigFordeling?.erRevurdering == true }
            .forEach {
                feilregistrerBarnFraFFSøknad(it)
            }
    }

    // endregion
}
