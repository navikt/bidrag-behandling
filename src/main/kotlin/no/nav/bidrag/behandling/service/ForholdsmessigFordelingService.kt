package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.datamodell.leggTilGebyr
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.behandling.transformers.barn
import no.nav.bidrag.behandling.transformers.filtrerSakerHvorPersonErBP
import no.nav.bidrag.behandling.transformers.finnPeriodeLøperBidrag
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.finnEldsteSøktFomDato
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.finnSøktFomRevurderingSøknad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.fjernSøknad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.hentForKravhaver
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierGrunnlag
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierInntekt
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierSamvær
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierUnderholdskostnad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.mapSakKravhaverTilForholdsmessigFordelingDto
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.opprettRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.opprettSamværOgUnderholdForBarn
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilFFBarnDetaljer
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilFFDetaljerBM
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilFFDetaljerBP
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilForholdsmessigFordelingSøknad
import no.nav.bidrag.behandling.transformers.grunnlagsreferanseSimulert
import no.nav.bidrag.behandling.transformers.harSlåttUtTilForholdsmessigFordeling
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.tilBehandlingstema
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.beregning.felles.Barn
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadsBarnRequest
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlerenhetRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.ÅpenSøknadDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toYearMonth
import no.nav.bidrag.transport.sak.OpprettMidlertidligTilgangRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.collections.plus

private val LOGGER = KotlinLogging.logger {}
val ÅpenSøknadDto.bidragsmottaker get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
val ÅpenSøknadDto.bidragspliktig get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
val ÅpenSøknadDto.barn get() = partISøknadListe.filter { it.rolletype == Rolletype.BARN }
val behandlingstyperSomIkkeSkalInkluderesIFF =
    listOf(
        Behandlingstype.ALDERSJUSTERING,
        Behandlingstype.INNKREVINGSGRUNNLAG,
        Behandlingstype.PRIVAT_AVTALE,
        Behandlingstype.OPPHØR,
        Behandlingstype.INDEKSREGULERING,
    )

data class FFBeregningResultat(
    val harSlåttUtTilFF: Boolean,
    val beregningManglerGrunnlag: Boolean,
    val simulertGrunnlag: List<SimulertInntektGrunnlag> = emptyList(),
) {
    data class SimulertInntektGrunnlag(
        val type: Grunnlagstype,
        val gjelder: String,
        val beløp: BigDecimal,
        val inntektstype: Inntektsrapportering,
    )
}

data class SakKravhaver(
    val saksnummer: String?,
    val kravhaver: String,
    val bidragsmottaker: String? = null,
    val eierfogd: String? = null,
    val løperBidragFra: YearMonth? = null,
    val stønadstype: Stønadstype? = null,
    val åpneSøknader: MutableSet<ÅpenSøknadDto> = mutableSetOf(),
    val åpneBehandlinger: MutableSet<Behandling> = mutableSetOf(),
    val privatAvtale: PrivatAvtale? = null,
)

data class LøpendeBidragSakPeriode(
    val sak: Saksnummer,
    val type: Stønadstype,
    val kravhaver: Personident,
    val valutakode: String,
    val periodeFra: YearMonth,
)

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
) {
    @Transactional
    fun lukkAllFFSaker(behandlingsid: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        val åpneSaker = hentÅpneSøknader(behandling.bidragspliktig!!.ident!!)
        åpneSaker.filter { it.behandlingstype == Behandlingstype.FORHOLDSMESSIG_FORDELING }.forEach {
            bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(it.søknadsid))
        }
    }

    @Transactional
    fun opprettEllerOppdaterForholdsmessigFordeling(behandlingId: Long) {
        if (!UnleashFeatures.TILGANG_OPPRETTE_FF.isEnabled) {
            LOGGER.info { "Opprettelse av forholdsmessig fordeling er deaktivert" }
            ugyldigForespørsel("Opprettelse av forholdsmessig fordeling er deaktivert")
        }
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()

        val originalBM = behandling.bidragsmottaker!!.ident
        behandling.alleBidragsmottakere.filter { it.forholdsmessigFordeling == null }.forEach {
            it.forholdsmessigFordeling = behandling.tilFFDetaljerBM()
        }
        val behandlerEnhet = finnEnhetForBarnIBehandling(behandling)
        val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling)
        val eksisterendeSøknadsbarn = behandling.søknadsbarn.map { it.ident }
        val relevanteKravhavereIkkeSøknadsbarn = relevanteKravhavere.filter { !eksisterendeSøknadsbarn.contains(it.kravhaver) }.toSet()
        overførÅpneBehandlingTilHovedbehandling(behandling, relevanteKravhavereIkkeSøknadsbarn)
        overførÅpneBisysSøknaderTilBehandling(behandling, relevanteKravhavereIkkeSøknadsbarn)
        val bidragssakerBpUtenÅpenBehandling =
            relevanteKravhavereIkkeSøknadsbarn.filter {
                it.åpneSøknader.isEmpty() &&
                    it.åpneBehandlinger.isEmpty()
            }

        bidragssakerBpUtenÅpenBehandling
            .filter { !it.saksnummer.isNullOrEmpty() }
            .groupBy {
                Pair(it.saksnummer!!, it.stønadstype)
            }.forEach { (saksnummerLøpendeBidrag, løpendebidragssaker) ->
                val saksnummer = saksnummerLøpendeBidrag.first
                opprettRollerOgRevurderingssøknadForSak(
                    behandling,
                    saksnummer,
                    løpendebidragssaker,
                    behandlerEnhet,
                    saksnummerLøpendeBidrag.second,
                    relevanteKravhavereIkkeSøknadsbarn.finnSøktFomRevurderingSøknad(behandling),
                )
            }
        behandling.forholdsmessigFordeling =
            ForholdsmessigFordeling(
                erHovedbehandling = true,
            )

        oppdaterGebyrDetaljerRollerIBehandling(behandling, relevanteKravhavere)

        val søknaderSøknadsbarn = relevanteKravhavere.filter { eksisterendeSøknadsbarn.contains(it.kravhaver) }.toSet()

        behandling.søknadsbarn.forEach { barn ->
            if (barn.forholdsmessigFordeling == null) {
                val sakKravhaverSøknadsbarn = søknaderSøknadsbarn.find { it.kravhaver == barn.ident }

                val søknadsdetaljer =
                    if (sakKravhaverSøknadsbarn != null) {
                        overførÅpneBehandlingerOgSøknaderSøknadsbarn(sakKravhaverSøknadsbarn, behandling)
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
                barn.forholdsmessigFordeling =
                    ForholdsmessigFordelingRolle(
                        delAvOpprinneligBehandling = true,
                        tilhørerSak = behandling.saksnummer,
                        behandlingsid = behandling.id,
                        behandlerenhet = behandling.behandlerEnhet,
                        bidragsmottaker = originalBM,
                        erRevurdering = false,
                        søknader = søknadsdetaljer.toMutableSet(),
                        harLøpendeBidrag =
                            bidragssakerBpUtenÅpenBehandling.any { bs ->
                                bs.kravhaver == barn.ident &&
                                    barn.innkrevesFraDato != null
                            },
                    )
            }
        }

        giSakTilgangTilEnhet(behandling, behandlerEnhet)
        opprettSamværOgUnderholdForBarn(behandling)
        behandling.syncGebyrSøknadReferanse()
        behandlingService.lagreBehandling(behandling)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
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
                                .flatMap { it.roller.find { it.ident == rolle.ident }?.gebyrSøknader ?: emptySet() }
                        }
                    val gebyrFraSøknader =
                        sakKravhaver.flatMap {
                            it.åpneSøknader
                                .filter { it.søknadsid != behandling.soknadsid }
                                .mapNotNull {
                                    val rolleISøknad = it.partISøknadListe.find { it.personident == rolle.ident }
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

    private fun overførÅpneBehandlingerOgSøknaderSøknadsbarn(
        sakKravhaver: SakKravhaver,
        behandling: Behandling,
    ) {
        sakKravhaver.åpneBehandlinger.filter { it.id != behandling.id }.forEach { behandlingOverført ->
            behandlingOverført.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    behandlesAvBehandling = behandling.id,
                )
            bbmConsumer.lagreBehandlingsid(
                OppdaterBehandlingsidRequest(behandlingOverført.soknadsid!!, behandlingOverført.id, behandling.id!!),
            )
            giSakTilgangTilEnhet(behandlingOverført, behandling.behandlerEnhet)
            behandlingService.lagreBehandling(behandlingOverført)
        }

        sakKravhaver.åpneSøknader.filter { it.søknadsid != behandling.soknadsid }.forEach { åpenSøknad ->
            if (åpenSøknad.behandlingsid != null) {
                LOGGER.warn {
                    "Overfører søknad ${åpenSøknad.søknadsid} i sak ${åpenSøknad.saksnummer} og behandlingsid " +
                        "${åpenSøknad.behandlingsid} til behandling ${behandling.id} etter opprettelse av FF. " +
                        "Behandlingen burde blitt overført i forrige steg når alle åpne behandlinger for BP ble hentet og behandlet"
                }
                val behandlingOverført = behandlingRepository.findBehandlingById(åpenSøknad.behandlingsid!!)
                if (behandlingOverført.isPresent) {
                    behandlingOverført.get().forholdsmessigFordeling =
                        ForholdsmessigFordeling(
                            behandlesAvBehandling = behandling.id,
                        )
                }
            }
            bbmConsumer.lagreBehandlingsid(
                OppdaterBehandlingsidRequest(åpenSøknad.søknadsid, åpenSøknad.behandlingsid, behandling.id!!),
            )
            if (behandling.behandlerEnhet != åpenSøknad.behandlerenhet) {
                oppdaterSakOgSøknadBehandlerEnhet(åpenSøknad.saksnummer, åpenSøknad.søknadsid, behandling.behandlerEnhet)
            }
        }
    }

    private fun giSakTilgangTilEnhet(
        behandling: Behandling,
        behandlerEnhet: String,
    ) {
        if (behandlerEnhet == behandling.behandlerEnhet) return
        behandling.behandlerEnhet = behandlerEnhet
        oppdaterSakOgSøknadBehandlerEnhet(behandling.saksnummer, behandling.soknadsid!!, behandlerEnhet)
    }

    private fun oppdaterSakOgSøknadBehandlerEnhet(
        saksnummer: String,
        søknadsid: Long,
        tilgangTilEnhet: String,
    ) {
        sakConsumer.opprettMidlertidligTilgang(OpprettMidlertidligTilgangRequest(saksnummer, tilgangTilEnhet))
        bbmConsumer.lagreBehandlerEnhet(OppdaterBehandlerenhetRequest(søknadsid, tilgangTilEnhet))
    }

    @Transactional
    fun avsluttForholdsmessigFordeling(
        behandling: Behandling,
        slettBarn: List<Rolle>,
        søknadsidSomSlettes: Long,
    ) {
        if (behandling.forholdsmessigFordeling == null) return
        if (!behandling.forholdsmessigFordeling!!.erHovedbehandling) return

        if (!kanBehandlingSlettes(behandling, slettBarn, søknadsidSomSlettes)) {
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

    fun feilregistrerBarnFraFFSøknad(rolle: Rolle) {
        val søknader =
            rolle.forholdsmessigFordeling!!.søknaderUnderBehandling.filter {
                it.behandlingstype ==
                    Behandlingstype.FORHOLDSMESSIG_FORDELING
            }
        val søknaderFeilregistrert =
            søknader.mapNotNull { søknad ->
                val søknadsid = søknad.søknadsid!!
                val personidentBarn = rolle.ident!!
                LOGGER.info { "Feilregistrerer barn $personidentBarn fra søknad $søknadsid" }
                try {
                    bbmConsumer.feilregistrerSøknadsbarn(FeilregistrerSøknadsBarnRequest(søknadsid, personidentBarn))
                    søknadsid
                } catch (e: Exception) {
                    LOGGER.error(e) { "Feil ved feilregistrering av søknad $søknadsid" }
                    null
                }
            }
        rolle.forholdsmessigFordeling!!.søknader =
            rolle.forholdsmessigFordeling!!
                .søknader
                .map {
                    if (søknaderFeilregistrert.contains(it.søknadsid!!)) {
                        it.status = Behandlingstatus.FEILREGISTRERT
                    }
                    it
                }.toMutableSet()
    }

    fun feilregistrerFFSøknad(rolle: Rolle) {
        if (rolle.forholdsmessigFordeling == null || !rolle.forholdsmessigFordeling!!.erRevurdering) return
        rolle.forholdsmessigFordeling!!.søknaderUnderBehandling.forEach { søknad ->
            val søknadsid = søknad.søknadsid
            LOGGER.info { "Feilregistrerer søknad $søknadsid i behandling ${rolle.behandling.id}" }
            try {
                bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknadsid!!))
            } catch (e: Exception) {
                LOGGER.error(e) { "Feil ved feilregistrering av søknad $søknadsid i behandling ${rolle.behandling.id}" }
            }
        }
    }

    fun kanBehandlingSlettes(
        behandling: Behandling,
        slettBarn: List<Rolle>,
        søknadsidSomSlettes: Long,
    ): Boolean {
        val barnIkkeRevurdering =
            behandling.søknadsbarn
                .filter { slettBarn.isEmpty() || !slettBarn.mapNotNull { it.ident }.contains(it.ident) }
                .filter { !it.forholdsmessigFordeling!!.erRevurdering }
//                .flatMap {
//                    it.forholdsmessigFordeling!!
//                        .søknaderUnderBehandling
//                        .map { it.søknadsid }
//                }
        return barnIkkeRevurdering.isEmpty()
        // || slettBarn.isNotEmpty() && barnIkkeRevurdering.size == 1 && barnIkkeRevurdering.contains(søknadsidSomSlettes)
    }

    @Transactional
    fun leggTilEllerSlettBarnFraBehandlingSomErIFF(
        rollerSomSkalLeggesTilDto: List<OpprettRolleDto>,
        rollerSomSkalSlettes: List<OpprettRolleDto>,
        behandling: Behandling,
        søknadsid: Long,
        saksnummer: String,
        bmIdent: String? = null,
        behandlerenhet: String = behandling.behandlerEnhet,
        erRevurdering: Boolean = false,
        søknadsdetaljer: ForholdsmessigFordelingSøknadBarn? = null,
    ) {
        val identerSomSkalSlettes = rollerSomSkalSlettes.mapNotNull { it.ident?.verdi }
        rollerSomSkalLeggesTilDto
            .filter { it.rolletype == Rolletype.BARN }
            .mapNotNull { nyRolle -> behandling.roller.find { it.ident == nyRolle.ident!!.verdi } }
            .filter { it.forholdsmessigFordeling?.erRevurdering == true }
            .forEach {
                feilregistrerBarnFraFFSøknad(it)
            }
        val rollerSomSkalLeggesTil =
            rollerSomSkalLeggesTilDto
                .map { nyRolle ->
                    val søknadsdetaljerBarn = søknadsdetaljer ?: behandling.tilFFBarnDetaljer()
                    val eksisterendeRolle = behandling.roller.find { it.ident == nyRolle.ident!!.verdi }
                    val ffRolleDetaljer =
                        ForholdsmessigFordelingRolle(
                            tilhørerSak = saksnummer,
                            delAvOpprinneligBehandling = true,
                            behandlingsid = behandling.id,
                            bidragsmottaker = bmIdent ?: behandling.bidragsmottakerForSak(saksnummer)?.ident,
                            behandlerenhet = behandlerenhet,
                            erRevurdering = erRevurdering,
                            søknader = mutableSetOf(søknadsdetaljerBarn.copy(søknadsid = søknadsid)),
                        )
                    if (eksisterendeRolle == null) {
                        val rolle = nyRolle.toRolle(behandling)
                        rolle.forholdsmessigFordeling = ffRolleDetaljer
                        rolle
                    } else {
                        if (eksisterendeRolle.forholdsmessigFordeling == null) {
                            eksisterendeRolle.forholdsmessigFordeling = ffRolleDetaljer
                        } else {
                            val eksisterendeSøknadsliste = eksisterendeRolle.forholdsmessigFordeling!!.søknader
                            eksisterendeRolle.forholdsmessigFordeling =
                                ffRolleDetaljer.copy(
                                    søknader =
                                        (
                                            eksisterendeSøknadsliste +
                                                setOf(søknadsdetaljerBarn.copy(søknadsid = søknadsid))
                                        ).toMutableSet(),
                                )
                        }
                        val gebyr = eksisterendeRolle.hentEllerOpprettGebyr()
                        if (nyRolle.harGebyrsøknad) {
                            gebyr.gebyrSøknader.add(
                                GebyrRolleSøknad(
                                    saksnummer = saksnummer,
                                    søknadsid = søknadsid,
                                    referanse = nyRolle.referanseGebyr,
                                ),
                            )
                            eksisterendeRolle.gebyr = gebyr
                            eksisterendeRolle.harGebyrsøknad = true
                        }
                        eksisterendeRolle
                    }
                }

        behandling.roller.addAll(rollerSomSkalLeggesTil)
        opprettSamværOgUnderholdForBarn(behandling)
        val rollerSomSkalSlettes =
            behandling.roller
                .filter { identerSomSkalSlettes.contains(it.ident) }
                .map { it }
        slettBarnEllerBehandling(rollerSomSkalSlettes, behandling, søknadsid)
    }

    @Transactional
    fun slettBarnEllerBehandling(
        slettBarn: List<Rolle>,
        behandling: Behandling,
        søknadsid: Long,
    ) {
        if (kanBehandlingSlettes(behandling, slettBarn, søknadsid)) {
            avsluttForholdsmessigFordeling(behandling, slettBarn, søknadsid)
            behandlingService.logiskSlettBehandling(behandling)
        } else {
            slettBarn.forEach { slettBarnFraBehandlingFF(it, behandling, søknadsid) }
            behandlingService.sendOppdatertHendelse(behandling.id!!, false)
        }
    }

    fun slettBarnFraBehandlingFF(
        barn: Rolle,
        behandling: Behandling,
        søknadsid: Long,
    ) {
        barn.fjernSøknad(søknadsid)
        barn.fjernGebyr(søknadsid)
        if (barn.forholdsmessigFordeling!!.søknaderUnderBehandling.isNotEmpty()) {
            LOGGER.info {
                "Barnet er koblet til flere søknader ${barn.forholdsmessigFordeling!!.søknader}" +
                    " etter den ble slettet fra søknad $søknadsid. Gjør ingen endring. Behandlingid = ${behandling.id}"
            }
            return
        }
        LOGGER.info { "Sletter barn ${barn.ident} fra behandling ${behandling.id} og lager ny revurderingsøknad" }
        barn.forholdsmessigFordeling!!.erRevurdering = true
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = hentÅpneSøknader(bidragspliktigFnr)
        val søktFomDato = LocalDate.now().plusMonths(1).withDayOfMonth(1)

        val åpenFFBehandling =
            åpneSøknader.filter { it.behandlingstype == Behandlingstype.FORHOLDSMESSIG_FORDELING }.find {
                it.saksnummer == barn.forholdsmessigFordeling?.tilhørerSak &&
                    it.søknadFomDato == søktFomDato && it.behandlingstema.tilStønadstype() == barn.stønadstype
            }
        if (åpenFFBehandling != null) {
            if (åpenFFBehandling.barn.none { it.personident == barn.ident }) {
                bbmConsumer.leggTilBarnISøknad(
                    LeggTilBarnIFFSøknadRequest(
                        åpenFFBehandling.søknadsid,
                        barn.ident!!,
                    ),
                )
            }
            barn.forholdsmessigFordeling!!.søknader.add(
                åpenFFBehandling.tilForholdsmessigFordelingSøknad().copy(
                    søktAvType = SøktAvType.NAV_BIDRAG,
                    behandlingstype = Behandlingstype.FORHOLDSMESSIG_FORDELING,
                    behandlingstema = Behandlingstema.BIDRAG,
                ),
            )
        } else {
            val søknad =
                bbmConsumer.opprettSøknader(
                    OpprettSøknadRequest(
                        saksnummer = barn.forholdsmessigFordeling!!.tilhørerSak,
                        behandlingsid = behandling.id,
                        behandlingstype = Behandlingstype.FORHOLDSMESSIG_FORDELING,
                        behandlerenhet = behandling.behandlerEnhet,
                        behandlingstema =
                            if (barn.stønadstype ==
                                Stønadstype.BIDRAG18AAR
                            ) {
                                Behandlingstema.BIDRAG_18_ÅR
                            } else {
                                Behandlingstema.BIDRAG
                            },
                        søknadFomDato = søktFomDato,
                        innkreving = barn.forholdsmessigFordeling!!.harLøpendeBidrag,
                        barnListe = listOf(Barn(personident = barn.ident!!)),
                    ),
                )
            barn.forholdsmessigFordeling!!.søknader.add(
                ForholdsmessigFordelingSøknadBarn(
                    søktAvType = SøktAvType.NAV_BIDRAG,
                    behandlingstype = Behandlingstype.FORHOLDSMESSIG_FORDELING,
                    behandlingstema = Behandlingstema.BIDRAG,
                    mottattDato = LocalDate.now(),
                    søknadFomDato = søktFomDato,
                    søknadsid = søknad.søknadsid,
                    enhet = behandling.behandlerEnhet,
                ),
            )
        }
    }

    fun finnEnhetForBarnIBehandling(behandling: Behandling): String {
        val sakerBp = hentSakerBp(behandling.bidragspliktig!!.ident!!)
        val relevantSaker = sakerBp.filter { it.eierfogd.verdi in listOf("4883", "2103") }
        return relevantSaker.find { it.eierfogd.verdi == "2103" }?.eierfogd?.verdi
            ?: relevantSaker.firstOrNull()?.eierfogd?.verdi
            ?: behandling.behandlerEnhet
    }

    private fun hentSakerBp(bpIdent: String) = sakConsumer.hentSakerPerson(bpIdent).filtrerSakerHvorPersonErBP(bpIdent)

    @Transactional
    fun skalLeggeTilBarnFraAndreSøknaderEllerBehandlinger(behandlingId: Long): Boolean {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        if (behandling.forholdsmessigFordeling == null || !behandling.forholdsmessigFordeling!!.erHovedbehandling) return false

        return harLøpendeBidragForBarnIkkeIBehandling(behandling)
    }

    private fun harLøpendeBidragForBarnIkkeIBehandling(behandling: Behandling): Boolean {
        val bidraggsakerBP = hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!))
        return bidraggsakerBP.any { lb ->
            val sak = sakConsumer.hentSak(lb.sak.verdi)
            val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
            behandling.roller.none { it.ident == lb.kravhaver.verdi } || behandling.roller.none { it.ident == bmFødselsnummer }
        }
    }

    private fun sjekkBeregningKreverForholdsmessigFordeling(behandling: Behandling): FFBeregningResultat =
        try {
            val resultat = beregningService.beregneBidrag(behandling, true, simulerBeregning = true).resultatBarn
            val grunnlagsliste = resultat.flatMap { it.resultat.grunnlagListe }.toSet().toList()
            val simulertSamværGrunnlag =
                grunnlagsliste
                    .filter {
                        it.type == Grunnlagstype.SAMVÆRSPERIODE &&
                            it.referanse.contains(grunnlagsreferanseSimulert)
                    }
            val simulertInntektGrunnlag =
                grunnlagsliste
                    .filter {
                        it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE &&
                            it.referanse.contains(grunnlagsreferanseSimulert)
                    }.map {
                        FFBeregningResultat.SimulertInntektGrunnlag(
                            type = it.type,
                            gjelder = grunnlagsliste.hentPersonMedReferanse(it.gjelderReferanse!!)!!.personIdent!!,
                            beløp = it.innholdTilObjekt<InntektsrapporteringPeriode>().beløp,
                            inntektstype = it.innholdTilObjekt<InntektsrapporteringPeriode>().inntektsrapportering,
                        )
                    }

            FFBeregningResultat(
                grunnlagsliste.harSlåttUtTilForholdsmessigFordeling(),
                resultat.any { it.ugyldigBeregning != null },
                simulertGrunnlag = simulertInntektGrunnlag,
            )
        } catch (e: Exception) {
            // Valideringsfeil
            FFBeregningResultat(false, false)
        }

    @Transactional
    fun sjekkSkalOppretteForholdsmessigFordeling(behandlingId: Long): SjekkForholdmessigFordelingResponse {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        val finnesLøpendeBidragSomOverlapperMedEldsteVirkning =
            !behandling.erVirkningstidspunktLiktForAlle &&
                behandling.søknadsbarn.any {
                    val periodeLøperBidrag = behandling.finnPeriodeLøperBidrag(it)
                    val periodeBeregning = ÅrMånedsperiode(behandling.eldsteVirkningstidspunkt.toYearMonth(), it.opphørsdato?.toYearMonth())
                    periodeLøperBidrag != null && periodeLøperBidrag.fom < it.virkningstidspunktRolle.toYearMonth() &&
                        periodeLøperBidrag.overlapper(periodeBeregning)
                }
        val behandlesAvEnhet = finnEnhetForBarnIBehandling(behandling)

        val eksisterendeSøknadsbarn = behandling.søknadsbarn.map { it.ident }
        val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling)
        val relevanteKravhavereIkkeSøknadsbarn = relevanteKravhavere.filter { !eksisterendeSøknadsbarn.contains(it.kravhaver) }
        val bpsBarnMedLøpendeBidragEllerPrivatAvtale =
            if (relevanteKravhavereIkkeSøknadsbarn.isEmpty() && `finnesLøpendeBidragSomOverlapperMedEldsteVirkning`) {
                relevanteKravhavere
            } else {
                relevanteKravhavereIkkeSøknadsbarn
            }.toSet()
                .map { lb ->
                    val sak = lb.saksnummer?.let { sakConsumer.hentSak(it) }
                    lb.mapSakKravhaverTilForholdsmessigFordelingDto(sak, behandling, lb.løperBidragFra != null)
                }
        val resultat = sjekkBeregningKreverForholdsmessigFordeling(behandling)
        return SjekkForholdmessigFordelingResponse(
            skalBehandlesAvEnhet = behandlesAvEnhet,
            kanOppretteForholdsmessigFordeling =
                !behandling.erIForholdsmessigFordeling &&
                    (
                        bpsBarnMedLøpendeBidragEllerPrivatAvtale.isNotEmpty() ||
                            finnesLøpendeBidragSomOverlapperMedEldsteVirkning
                    ),
            måOppretteForholdsmessigFordeling = resultat.beregningManglerGrunnlag,
            harSlåttUtTilForholdsmessigFordeling = resultat.harSlåttUtTilFF,
            eldsteSøktFraDato = relevanteKravhavere.finnEldsteSøktFomDato(behandling),
            barn = bpsBarnMedLøpendeBidragEllerPrivatAvtale,
        )
    }

    private fun hentAlleRelevanteKravhavere(behandling: Behandling): Set<SakKravhaver> {
        val åpneEllerLøpendeSakerBp = hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val sakerUtenLøpendeBidrag =
            hentBarnUtenLøpendeBidrag(behandling, åpneEllerLøpendeSakerBp)

        return åpneEllerLøpendeSakerBp + sakerUtenLøpendeBidrag
    }

    private fun hentBarnUtenLøpendeBidrag(
        behandling: Behandling,
        sakerMedLøpendeBidrag: Set<SakKravhaver>? = null,
    ): List<SakKravhaver> {
        val sakerMedLøpendeBidrag = sakerMedLøpendeBidrag ?: hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val søktFomDatoRevurdering = sakerMedLøpendeBidrag.finnSøktFomRevurderingSøknad(behandling)

        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val søknadsbarnIdenter =
            sakerMedLøpendeBidrag.map { it.kravhaver } +
                behandling.søknadsbarn.mapNotNull { it.ident }

        val sakerBp = hentSakerBp(bidragspliktigFnr)
        val barneSomHarBidragssak = sakerBp.flatMap { it.barn.map { it.fødselsnummer!!.verdi } }
        val privatAvtalerUtenBidragssak =
            behandling.privatAvtale
                .filter {
                    it.rolle == null && !barneSomHarBidragssak.contains(it.personIdent!!)
                }.map {
                    val barnFødselsdato = hentPersonFødselsdato(it.personIdent!!)
                    val dato18ÅrsBidrag = barnFødselsdato!!.plusYears(18).plusMonths(1).withDayOfMonth(1)
                    val er18EtterSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                    SakKravhaver(
                        kravhaver = it.personIdent!!,
                        saksnummer = null,
                        løperBidragFra = null,
                        stønadstype = if (er18EtterSøktFom) Stønadstype.BIDRAG18AAR else Stønadstype.BIDRAG,
                        eierfogd = null,
                        bidragsmottaker = null,
                        privatAvtale = it,
                    )
                }
        val barnMedBidragssakSomHarPrivatAvtale =
            sakerBp
                .flatMap { sak ->
                    val barn =
                        sak.roller
                            .filter { it.type == Rolletype.BARN }
                            .filter { it.fødselsnummer != null && !søknadsbarnIdenter.contains(it.fødselsnummer!!.verdi) }
                    barn.map {
                        val barnFødselsdato = hentPersonFødselsdato(it.fødselsnummer!!.verdi)
                        val dato18ÅrsBidrag = barnFødselsdato!!.plusYears(18).plusMonths(1).withDayOfMonth(1)
                        val er18EtterrSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                        val privatAvtale =
                            behandling.privatAvtale.find { pa ->
                                pa.rolle == null &&
                                    pa.personIdent == it.fødselsnummer?.verdi
                            }
                        SakKravhaver(
                            kravhaver = it.fødselsnummer!!.verdi,
                            saksnummer = sak.saksnummer.verdi,
                            løperBidragFra = null,
                            stønadstype = if (er18EtterrSøktFom) Stønadstype.BIDRAG18AAR else Stønadstype.BIDRAG,
                            eierfogd = sak.eierfogd.verdi,
                            bidragsmottaker = sak.bidragsmottaker?.fødselsnummer?.verdi,
                            privatAvtale = privatAvtale,
                        )
                    }
                }.filter { barn -> barn.privatAvtale != null }

        return privatAvtalerUtenBidragssak + barnMedBidragssakSomHarPrivatAvtale
    }

    fun overførÅpneBisysSøknaderTilBehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = relevanteKravhavere.flatMap { it.åpneSøknader }
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr))
        åpneSøknader
            .forEach { åpenSøknad ->
                if (åpenSøknad.behandlingsid != null) {
                    LOGGER.warn {
                        "Overfører søknad ${åpenSøknad.søknadsid} i sak ${åpenSøknad.saksnummer} og behandlingsid " +
                            "${åpenSøknad.behandlingsid} til behandling ${behandling.id} etter opprettelse av FF. " +
                            "Behandlingen burde blitt overført i forrige steg når alle åpne behandlinger for BP ble hentet og behandlet"
                    }
                    val behandlingOverført = behandlingRepository.findBehandlingById(åpenSøknad.behandlingsid!!)
                    if (behandlingOverført.isPresent) {
                        behandlingOverført.get().forholdsmessigFordeling =
                            ForholdsmessigFordeling(
                                behandlesAvBehandling = behandling.id,
                            )
                    }
                }

                LOGGER.info {
                    "Overfører søknad ${åpenSøknad.søknadsid} i sak ${åpenSøknad.saksnummer} til behandling ${behandling.id} etter opprettelse av FF"
                }
                val sak = sakConsumer.hentSak(åpenSøknad.saksnummer)
                val ffDetaljer =
                    ForholdsmessigFordelingRolle(
                        delAvOpprinneligBehandling = false,
                        tilhørerSak = åpenSøknad.saksnummer,
                        behandlerenhet = sak.eierfogd.verdi,
                        bidragsmottaker = åpenSøknad.bidragsmottaker?.personident,
                        erRevurdering = åpenSøknad.behandlingstype == Behandlingstype.FORHOLDSMESSIG_FORDELING,
                        søknader =
                            mutableSetOf(
                                åpenSøknad.tilForholdsmessigFordelingSøknad(),
                            ),
                    )
                åpenSøknad.bidragsmottaker?.let { bm ->
                    val åpneSøknaderRolle = åpneSøknader.filter { it.barn.any { it.personident == bm.personident } }
                    opprettRolle(
                        behandling,
                        Rolletype.BIDRAGSMOTTAKER,
                        bm.personident!!,
                        harGebyrSøknad =
                            bm.gebyr.ifTrue {
                                GebyrRolleSøknad(
                                    saksnummer = åpenSøknad.saksnummer,
                                    søknadsid = åpenSøknad.søknadsid,
                                )
                            },
                        ffDetaljer =
                            ffDetaljer.copy(
                                søknader = åpneSøknaderRolle.map { it.tilForholdsmessigFordelingSøknad() }.toMutableSet(),
                            ),
                    )
                }
                åpenSøknad.barn.forEach { barn ->
                    val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barn.personident }
                    val åpneSøknaderRolle = åpneSøknader.filter { it.barn.any { it.personident == barn.personident } }
                    opprettRolle(
                        behandling,
                        Rolletype.BARN,
                        barn.personident!!,
                        stønadstype = åpenSøknad.behandlingstema.tilStønadstype() ?: Stønadstype.BIDRAG,
                        harGebyrSøknad =
                            barn.gebyr.ifTrue {
                                GebyrRolleSøknad(
                                    saksnummer = åpenSøknad.saksnummer,
                                    søknadsid = åpenSøknad.søknadsid,
                                )
                            },
                        innbetaltBeløp = barn.innbetaltBeløp,
                        ffDetaljer =
                            ffDetaljer.copy(
                                søknader = åpneSøknaderRolle.map { it.tilForholdsmessigFordelingSøknad() }.toMutableSet(),
                            ),
                        medInnkreving = åpenSøknad.innkreving,
                        innkrevesFraDato = if (åpenSøknad.innkreving) løpendeBidrag?.periodeFra else null,
                    )
                }
                bbmConsumer.lagreBehandlingsid(
                    OppdaterBehandlingsidRequest(åpenSøknad.søknadsid, åpenSøknad.behandlingsid, behandling.id!!),
                )
                if (behandling.behandlerEnhet != åpenSøknad.behandlerenhet) {
                    oppdaterSakOgSøknadBehandlerEnhet(åpenSøknad.saksnummer, åpenSøknad.søknadsid, behandling.behandlerEnhet)
                }
            }
    }

    private fun hentÅpneSøknader(bidragspliktigFnr: String) =
        bbmConsumer
            .hentÅpneSøknaderForBp(bidragspliktigFnr)
            .åpneSøknader
            .filter { !behandlingstyperSomIkkeSkalInkluderesIFF.contains(it.behandlingstype) }
            .sortedWith(
                compareByDescending<ÅpenSøknadDto> { it.behandlingstype == Behandlingstype.FORHOLDSMESSIG_FORDELING }
                    .thenBy { it.søknadFomDato },
            )

    private fun Behandling.syncGebyrSøknadReferanse() {
        roller.forEach { rolle ->
            rolle.hentEllerOpprettGebyr().gebyrSøknader.forEach { gebyrSøknad ->
                if (gebyrSøknad.referanse.isNullOrEmpty()) {
                    val søknad = bbmConsumer.hentSøknad(gebyrSøknad.søknadsid)
                    gebyrSøknad.referanse =
                        søknad.søknad.partISøknadListe
                            .find { rolle.ident == it.personident }
                            ?.referanseGebyr
                }
            }
        }
    }

    @Transactional
    fun overførÅpneBehandlingTilHovedbehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ): List<Long> {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val eksisterendeBMIdenter = behandling.alleBidragsmottakere.map { it.ident }
        val åpneBehandlinger = relevanteKravhavere.flatMap { it.åpneBehandlinger }
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr))
        åpneBehandlinger.forEach { behandlingOverført ->
            if (behandlingOverført.forholdsmessigFordeling?.behandlesAvBehandling == behandling.id) return@forEach
            LOGGER.info {
                "Overfører behandling ${behandlingOverført.id} til behandling ${behandling.id} etter FF ble opprettet for behandlingen"
            }
            behandlingOverført.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    behandlesAvBehandling = behandling.id,
                )
            behandlingOverført.bidragsmottaker?.let { rolle ->
                val eksisterendeRolle = behandling.roller.find { barn -> barn.ident == rolle.ident }
                if (eksisterendeRolle == null) {
                    val behandlingerRolle = åpneBehandlinger.filter { it.søknadsbarn.any { it.ident == rolle.ident } }
                    behandling.roller.add(
                        rolle.kopierRolle(behandling, null, åpneBehandlinger = behandlingerRolle),
                    )
                } else if (eksisterendeRolle.harGebyrsøknad || eksisterendeRolle.gebyr != null) {
                    eksisterendeRolle.leggTilGebyr(rolle)
                }
            }
            val bm = behandlingOverført.bidragsmottaker?.ident
            behandlingOverført.søknadsbarn.forEach { rolle ->
                val eksisterendeRolle = behandling.søknadsbarn.find { barn -> barn.ident == rolle.ident }
                if (eksisterendeRolle == null) {
                    val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == rolle.ident }
                    val innkrevesFra = if (behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING) løpendeBidrag?.periodeFra else null
                    val behandlingerRolle = åpneBehandlinger.filter { it.søknadsbarn.any { it.ident == rolle.ident } }
                    behandling.roller.add(
                        rolle.kopierRolle(
                            behandling,
                            bm,
                            innkrevesFra,
                            behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                            behandlingerRolle,
                        ),
                    )
                } else if (eksisterendeRolle.harGebyrsøknad || eksisterendeRolle.gebyr != null) {
                    eksisterendeRolle.leggTilGebyr(rolle)
                }
            }
            behandlingOverført.samvær.forEach { samværOverført ->
                if (behandling.samvær.none { s -> s.rolle.ident == samværOverført.rolle.ident }) {
                    kopierSamvær(behandling, samværOverført)
                }
            }
            behandlingOverført.underholdskostnader.forEach { underholdskostnadOverført ->
                if (behandling.underholdskostnader.none { s ->
                        !underholdskostnadOverført.gjelderAndreBarn && s.rolle != null &&
                            s.rolle!!.ident == underholdskostnadOverført.rolle!!.ident ||
                            underholdskostnadOverført.person != null && s.person != null &&
                            s.person!!.ident == underholdskostnadOverført.person!!.ident
                    }
                ) {
                    underholdskostnadOverført.kopierUnderholdskostnad(behandling)
                }
            }
            behandlingOverført.inntekter
                .filter { !eksisterendeBMIdenter.contains(behandlingOverført.bidragsmottaker!!.ident) }
                .filter {
                    it.ident == behandlingOverført.bidragsmottaker?.ident ||
                        behandlingOverført.søknadsbarn.map { it.ident }.contains(it.ident)
                }.forEach { inntektOverført ->
                    kopierInntekt(behandling, inntektOverført)
                }

            behandlingOverført.inntekter
                .filter { bidragspliktigFnr == behandlingOverført.bidragspliktig!!.ident }
                .filter { it.taMed && it.type.kanLeggesInnManuelt }
                .forEach { inntektOverført ->
                    kopierInntekt(behandling, inntektOverført)
                }
            behandlingOverført.grunnlag
                .filter { it.rolle.ident != bidragspliktigFnr && behandling.roller.any { r -> r.ident == it.rolle.ident } }
                .forEach {
                    behandling.grunnlag.add(
                        it.kopierGrunnlag(behandling),
                    )
                }

            bbmConsumer.lagreBehandlingsid(
                OppdaterBehandlingsidRequest(behandlingOverført.soknadsid!!, behandlingOverført.id, behandling.id!!),
            )
            giSakTilgangTilEnhet(behandlingOverført, behandling.behandlerEnhet)
            behandlingService.lagreBehandling(behandlingOverført)
        }
        return åpneBehandlinger.map { it.id!! }
    }

    private fun opprettRollerOgRevurderingssøknadForSak(
        behandling: Behandling,
        saksnummer: String,
        løpendeBidragssak: List<SakKravhaver>,
        behandlerEnhet: String,
        stønadstype: Stønadstype? = null,
        søktFomDato: LocalDate,
    ) {
        val sak = sakConsumer.hentSak(saksnummer)

        val barnUtenSøknader = løpendeBidragssak.filter { ls -> behandling.søknadsbarn.none { it.ident == ls.kravhaver } }
        if (barnUtenSøknader.isEmpty()) return

        val bmFødselsnummer = hentNyesteIdent(sak.bidragsmottaker?.fødselsnummer?.verdi)?.verdi

        val barnMedInnkrevingSenereEnnFomDato =
            barnUtenSøknader
                .filter {
                    it.løperBidragFra == null ||
                        it.løperBidragFra > søktFomDato.toYearMonth()
                }.groupBy { it.løperBidragFra }
                .map { (_, barn) ->
                    val søknadsid =
                        opprettSøknad(barn, saksnummer, behandling, behandlerEnhet, stønadstype, søktFomDato, false, bmFødselsnummer!!)
                    Pair(søknadsid, barn.map { it.kravhaver })
                }

        val barnMedInnkreving = barnUtenSøknader.filter { it.løperBidragFra != null }
        val søknadsid =
            if (barnMedInnkreving.isNotEmpty()) {
                opprettSøknad(barnMedInnkreving, saksnummer, behandling, behandlerEnhet, stønadstype, søktFomDato, true, bmFødselsnummer!!)
            } else {
                null
            }
        val ffDetaljerBarn =
            ForholdsmessigFordelingSøknadBarn(
                søknadsid = søknadsid,
                mottattDato = LocalDate.now(),
                søknadFomDato = søktFomDato,
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstema = stønadstype?.tilBehandlingstema() ?: behandling.behandlingstema,
                behandlingstype = Behandlingstype.FORHOLDSMESSIG_FORDELING,
                enhet = behandlerEnhet,
            )
        val søknadMedInnkreving =
            søknadsid?.let {
                ffDetaljerBarn.copy(søknadsid = it)
            }

        val alleSøknader =
            setOfNotNull(
                søknadsid?.let {
                    ffDetaljerBarn.copy(søknadsid = it)
                },
            ).toMutableSet()
        val ffDetaljer =
            ForholdsmessigFordelingRolle(
                delAvOpprinneligBehandling = false,
                erRevurdering = true,
                tilhørerSak = saksnummer,
                behandlerenhet = sak.eierfogd.verdi,
                bidragsmottaker = bmFødselsnummer,
                søknader = alleSøknader,
            )

        barnUtenSøknader.forEach { søknad ->
            val søknadsIdUtenInnkreving =
                barnMedInnkrevingSenereEnnFomDato
                    .find { b ->
                        b.second.any { it == søknad.kravhaver }
                    }?.first
            val skalInnkreves = barnUtenSøknader.find { it.kravhaver == søknad.kravhaver }?.løperBidragFra != null
            val søknader =
                setOfNotNull(
                    søknadMedInnkreving,
                    søknadsIdUtenInnkreving?.let {
                        ffDetaljerBarn.copy(
                            søknadsid = it,
                            innkreving = false,
                        )
                    },
                )
            alleSøknader.addAll(søknader)
            val rolle =
                opprettRolle(
                    behandling,
                    Rolletype.BARN,
                    søknad.kravhaver,
                    stønadstype = stønadstype ?: Stønadstype.BIDRAG,
                    innkrevesFraDato = søknad.løperBidragFra,
                    medInnkreving = skalInnkreves,
                    ffDetaljer =
                        ffDetaljer.copy(
                            løperBidragFra = søknad.løperBidragFra,
                            søknader = søknader.toMutableSet(),
                        ),
                )

            if (søknad.privatAvtale != null) {
                søknad.privatAvtale.rolle = rolle
                søknad.privatAvtale.person = null
            }
        }
        if (bmFødselsnummer != null && behandling.roller.none { it.ident == bmFødselsnummer }) {
            opprettRolle(
                behandling,
                Rolletype.BIDRAGSMOTTAKER,
                bmFødselsnummer,
                ffDetaljer =
                    ffDetaljer.copy(
                        søknader = alleSøknader.toMutableSet(),
                    ),
            )
        } else {
            behandling.roller.find { hentNyesteIdent(it.ident)?.verdi == bmFødselsnummer }?.let {
                if (it.forholdsmessigFordeling == null) {
                    it.forholdsmessigFordeling = behandling.tilFFDetaljerBM()
                }
                it.forholdsmessigFordeling!!.søknader.addAll(alleSøknader.toMutableSet())
            }
        }

        behandling.bidragspliktig?.let {
            if (it.forholdsmessigFordeling == null) {
                it.forholdsmessigFordeling = behandling.tilFFDetaljerBP()
            }
            it.forholdsmessigFordeling!!.søknader.addAll(alleSøknader.toMutableSet())
        }
    }

    private fun opprettSøknad(
        barnUtenSøknader: List<SakKravhaver>,
        saksnummer: String,
        behandling: Behandling,
        behandlerEnhet: String,
        stønadstype: Stønadstype?,
        søktFomDato: LocalDate,
        medInnkreving: Boolean,
        bmFødselsnummer: String,
    ): Long {
        val opprettSøknader =
            barnUtenSøknader.map {
                Barn(
                    personident = it.kravhaver,
                )
            }
        val response =
            bbmConsumer.opprettSøknader(
                OpprettSøknadRequest(
                    saksnummer = saksnummer,
                    behandlingsid = behandling.id,
                    behandlerenhet = behandlerEnhet,
                    behandlingstema = stønadstype?.tilBehandlingstema() ?: Behandlingstema.BIDRAG,
                    søknadFomDato = søktFomDato,
                    barnListe = opprettSøknader,
                    innkreving = medInnkreving,
                    behandlingstype = Behandlingstype.FORHOLDSMESSIG_FORDELING,
                ),
            )

        val søknadsid = response.søknadsid

        opprettForsendelseForNySøknad(saksnummer, behandling, bmFødselsnummer, søknadsid.toString())
        return søknadsid
    }

    private fun opprettForsendelseForNySøknad(
        saksnummer: String,
        behandling: Behandling,
        bmFødselsnummer: String,
        søknadsid: String,
    ) {
        forsendelseService.slettEllerOpprettForsendelse(
            no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest(
                saksnummer = saksnummer,
                enhet = behandling.behandlerEnhet,
                roller =
                    listOf(
                        ForsendelseRolleDto(
                            fødselsnummer = Personident(bmFødselsnummer),
                            type = Rolletype.BIDRAGSMOTTAKER,
                        ),
                        ForsendelseRolleDto(
                            fødselsnummer = Personident(behandling.bidragspliktig!!.ident!!),
                            type = Rolletype.BIDRAGSPLIKTIG,
                        ),
                    ),
                behandlingInfo =
                    BehandlingInfoDto(
                        behandlingId = behandling.id?.toString(),
                        soknadId = søknadsid,
                        soknadFra = SøktAvType.NAV_BIDRAG,
                        behandlingType = behandling.tilBehandlingstype(),
                        stonadType = behandling.stonadstype,
                        engangsBelopType = behandling.engangsbeloptype,
                        vedtakType = behandling.vedtakstype,
                    ),
            ),
        )
    }

    private fun hentSisteLøpendeStønader(bpIdent: Personident): List<LøpendeBidragSakPeriode> =
        beløpshistorikkConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bpIdent)).bidragssakerListe.map { sak ->
            beløpshistorikkConsumer
                .hentLøpendeStønad(
                    HentStønadRequest(
                        skyldner = bpIdent,
                        kravhaver = sak.kravhaver,
                        sak = sak.sak,
                        type = sak.type,
                    ),
                )!!
                .let {
                    LøpendeBidragSakPeriode(
                        sak = it.sak,
                        kravhaver = it.kravhaver,
                        type = it.type,
                        valutakode = sak.valutakode,
                        periodeFra = it.periodeListe.minOf { it.periode.fom },
                    )
                }
        }

    fun hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling: Behandling): Set<SakKravhaver> {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr))
        val åpneBehandlinger =
            behandlingRepository
                .finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
                .filter {
                    it.søknadstype != null && !behandlingstyperSomIkkeSkalInkluderesIFF.contains(it.søknadstype)
                }
        val åpneSøknader =
            hentÅpneSøknader(bidragspliktigFnr).filter {
                it.behandlingstype == Behandlingstype.KLAGE && behandling.erKlageEllerOmgjøring ||
                    it.behandlingstype != Behandlingstype.KLAGE
            }

        val sakKravhaverListe = mutableSetOf<SakKravhaver>()

        åpneBehandlinger.forEach { behandling ->
            behandling.søknadsbarn.forEach { barn ->
                val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barn.ident }
                val eksisterende = sakKravhaverListe.hentForKravhaver(barn.ident!!)
                if (eksisterende != null) {
                    eksisterende.åpneBehandlinger.add(behandling)
                } else {
                    sakKravhaverListe.add(
                        SakKravhaver(
                            saksnummer = behandling.saksnummer,
                            kravhaver = barn.ident!!,
                            stønadstype = barn.stønadstype ?: behandling.stonadstype,
                            åpneBehandlinger = mutableSetOf(behandling),
                            eierfogd = behandling.behandlerEnhet,
                            løperBidragFra = løpendeBidrag?.periodeFra,
                        ),
                    )
                }
            }
        }

        åpneSøknader
            .filter { søknad ->
                søknad.behandlingsid == null ||
                    sakKravhaverListe.none {
                        it.åpneBehandlinger.any { it.id == søknad.behandlingsid || it.soknadsid == søknad.søknadsid }
                    } &&
                    !behandlingRepository.erIForholdsmessigFordeling(søknad.behandlingsid!!)
            }.forEach { åpenSøknad ->
                åpenSøknad.partISøknadListe
                    .filter { it.rolletype == Rolletype.BARN }
                    .forEach { barnFnr ->
                        val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barnFnr.personident }
                        val eksisterende = sakKravhaverListe.hentForKravhaver(barnFnr.personident!!)
                        if (eksisterende != null) {
                            eksisterende.åpneSøknader.add(åpenSøknad)
                        } else {
                            sakKravhaverListe.add(
                                SakKravhaver(
                                    åpenSøknad.saksnummer,
                                    kravhaver = barnFnr.personident!!,
                                    eierfogd = åpenSøknad.behandlerenhet,
                                    løperBidragFra = løpendeBidrag?.periodeFra,
                                    åpneSøknader = mutableSetOf(åpenSøknad),
                                ),
                            )
                        }
                    }
            }

        val krahaverFraÅpneSaker = sakKravhaverListe.map { it.kravhaver }

        val løpendeBidragsaker =
            løpendeBidraggsakerBP.filter { !krahaverFraÅpneSaker.contains(it.kravhaver.verdi) }.map {
                SakKravhaver(
                    saksnummer = it.sak.verdi,
                    kravhaver = it.kravhaver.verdi,
                    stønadstype = it.type,
                    løperBidragFra = it.periodeFra,
                )
            }
        val bidragsaker = løpendeBidragsaker + sakKravhaverListe
        return bidragsaker
            .sortedWith { a, b ->
                val aHasOpen = a.åpneSøknader.isNotEmpty() || a.åpneBehandlinger.isNotEmpty()
                val bHasOpen = b.åpneSøknader.isNotEmpty() || b.åpneBehandlinger.isNotEmpty()
                when {
                    aHasOpen && !bHasOpen -> -1
                    !aHasOpen && bHasOpen -> 1
                    else -> 0
                }
            }.distinctBy { it.saksnummer to it.kravhaver }
            .toSet()
    }
}
