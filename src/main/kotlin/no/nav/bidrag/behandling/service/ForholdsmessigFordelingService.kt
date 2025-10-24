package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingBarnDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingÅpenBehandlingDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierGrunnlag
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierInntekt
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierSamvær
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierUnderholdskostnad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.opprettRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.opprettSamværOgUnderholdForBarn
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.beregning.felles.Barn
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlerenhetRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.ÅpenSøknadDto
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.toYearMonth
import no.nav.bidrag.transport.sak.OpprettMidlertidligTilgangRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.collections.plus

private val LOGGER = KotlinLogging.logger {}
val ÅpenSøknadDto.bidragsmottaker get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
val ÅpenSøknadDto.bidragspliktig get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
val ÅpenSøknadDto.barn get() = partISøknadListe.filter { it.rolletype == Rolletype.BARN }

data class SakKravhaver(
    val saksnummer: String,
    val kravhaver: String,
    val bidragsmottaker: String? = null,
    val eierfogd: String? = null,
    val løperBidragFra: YearMonth? = null,
    val stønadstype: Stønadstype? = null,
    val åpenSøknad: ÅpenSøknadDto? = null,
    val åpenBehandling: Behandling? = null,
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
) {
    @Transactional
    fun opprettForholdsmessigFordeling(behandlingId: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
//        behandlingRepository.finnHovedbehandlingForBpVedFF(behandling.bidragspliktig!!.ident!!)?.apply {
//            ugyldigForespørsel(
//                "Det finnes allerede en åpen behandling med forholdsmessig fordeling for bidragspliktig ${behandling.bidragspliktig!!.ident}",
//            )
//        }
        val originalBM = behandling.bidragsmottaker!!.ident
        val behandlerEnhet = finnEnhetForBarnIBehandling(behandling)
        giSakTilgangTilEnhet(behandling, behandlerEnhet)
        overførÅpneBehandlingTilHovedbehandling(behandling)
        overførÅpneBisysSøknaderTilBehandling(behandling)
        val bidragssakerBpUtenLøpendeBidrag = hentBarnUtenLøpendeBidrag(behandling)

        val bidraggsakerBPMedLøpendeBidrag =
            hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!))
                .map { SakKravhaver(saksnummer = it.sak.verdi, kravhaver = it.kravhaver.verdi, stønadstype = it.type) }

        val bidragssakerBp = bidragssakerBpUtenLøpendeBidrag + bidraggsakerBPMedLøpendeBidrag

        bidragssakerBp
            .groupBy {
                Pair(it.saksnummer, it.stønadstype)
            }.forEach { (saksnummerLøpendeBidrag, løpendebidragssaker) ->
                val saksnummer = saksnummerLøpendeBidrag.first
                opprettRollerForSak(
                    behandling,
                    saksnummer,
                    løpendebidragssaker,
                    behandlerEnhet,
                    saksnummerLøpendeBidrag.second,
                )
            }
        behandling.forholdsmessigFordeling =
            ForholdsmessigFordeling(
                erHovedbehandling = true,
            )

        behandling.søknadsbarn.forEach {
            if (it.forholdsmessigFordeling == null) {
                it.forholdsmessigFordeling =
                    ForholdsmessigFordelingRolle(
                        delAvOpprinneligBehandling = true,
                        tilhørerSak = behandling.saksnummer,
                        søknadsid = behandling.soknadsid,
                        behandlingsid = behandling.id,
                        eierfogd = Enhetsnummer(behandling.behandlerEnhet),
                        bidragsmottaker = originalBM,
                        erRevurdering = false,
                        harLøpendeBidrag = bidraggsakerBPMedLøpendeBidrag.any { bs -> bs.kravhaver == it.ident },
                    )
            }
        }

        opprettSamværOgUnderholdForBarn(behandling)
        behandlingService.lagreBehandling(behandling)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
    }

    private fun giSakTilgangTilEnhet(
        behandling: Behandling,
        behandlerEnhet: String,
    ) {
        if (behandlerEnhet == behandling.behandlerEnhet) return
        behandling.behandlerEnhet = behandlerEnhet
        oppdaterSakOgSøknadBehandlerEnhet(behandling.saksnummer, behandling.soknadsid.toString(), behandlerEnhet)
    }

    private fun oppdaterSakOgSøknadBehandlerEnhet(
        saksnummer: String,
        søknadsid: String,
        tilgangTilEnhet: String,
    ) {
        sakConsumer.opprettMidlertidligTilgang(OpprettMidlertidligTilgangRequest(saksnummer, tilgangTilEnhet))
        bbmConsumer.lagreBehandlerEnhet(OppdaterBehandlerenhetRequest(tilgangTilEnhet, søknadsid))
    }

    @Transactional
    fun avsluttForholdsmessigFordeling(
        behandling: Behandling,
        slettBarn: List<Rolle>,
    ) {
        if (behandling.forholdsmessigFordeling == null) return
        if (!behandling.forholdsmessigFordeling!!.erHovedbehandling) return

        if (!kanBehandlingSlettes(behandling, slettBarn)) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke slette behandling som ",
            )
        }
        behandling.søknadsbarn
            .filter { it.forholdsmessigFordeling!!.erRevurdering }
            .map { it.forholdsmessigFordeling!!.søknadsid }
            .distinct()
            .forEach {
                LOGGER.info { "Feilregistrerer søknad $it i behandling ${behandling.id}" }
                bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(it.toString()))
            }
    }

    fun kanBehandlingSlettes(
        behandling: Behandling,
        slettBarn: List<Rolle>,
    ): Boolean =
        if (slettBarn.isEmpty()) {
            behandling.søknadsbarn
                .filter { !it.forholdsmessigFordeling!!.erRevurdering }
                .map { it.forholdsmessigFordeling!!.søknadsid }
                .distinct()
                .size == 1
        } else {
            behandling.søknadsbarn.none { !it.forholdsmessigFordeling!!.erRevurdering && !slettBarn.contains(it) }
        }

    @Transactional
    fun slettBarnFraBehandlingFF(
        slettBarn: List<Rolle>,
        behandling: Behandling,
    ) {
        if (kanBehandlingSlettes(behandling, slettBarn)) {
            behandlingService.slettBehandling(behandling, slettBarn)
        } else {
            slettBarn.forEach { slettBarnFraBehandlingFF(it, behandling) }
        }
    }

    fun slettBarnFraBehandlingFF(
        barn: Rolle,
        behandling: Behandling,
    ) {
        barn.forholdsmessigFordeling!!.erRevurdering = true
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = hentÅpneSøknader(bidragspliktigFnr)
        val søktFomDato = LocalDate.now().plusMonths(1).withDayOfMonth(1)

        val åpenFFBehandling =
            åpneSøknader.filter { it.behandlingstype == Behandlingstype.FORHOLDSMESSIG_FORDELING }.find {
                it.saksnummer == barn.forholdsmessigFordeling?.tilhørerSak &&
                    it.søknadFomDato == søktFomDato
            }
        if (åpenFFBehandling != null) {
            barn.forholdsmessigFordeling!!.søknadFomDato = åpenFFBehandling.søknadFomDato
            barn.forholdsmessigFordeling!!.søknadsid = åpenFFBehandling.søknadsid.toLong()
            bbmConsumer.leggTilBarnISøknad(
                LeggTilBarnIFFSøknadRequest(
                    åpenFFBehandling.søknadsid,
                    barn.ident!!,
                    true,
                ),
            )
        } else {
            val søknad =
                bbmConsumer.opprettSøknader(
                    OpprettSøknadRequest(
                        saksnummer = barn.forholdsmessigFordeling!!.tilhørerSak,
                        behandlingsid = behandling.id.toString(),
                        enhet = behandling.behandlerEnhet,
                        stønadstype = Stønadstype.BIDRAG,
                        søknadFomDato = søktFomDato,
                        innkreving = true,
                        barnListe = listOf(Barn(personident = barn.ident!!)),
                    ),
                )
            barn.forholdsmessigFordeling!!.søknadFomDato = søktFomDato
            barn.forholdsmessigFordeling!!.søknadsid = søknad.søknadsid.toLong()
        }
    }

    fun finnEnhetForBarnIBehandling(behandling: Behandling): String {
        val sakerBp = sakConsumer.hentSakerPerson(behandling.bidragspliktig!!.ident!!)
        val relevantSaker = sakerBp.filter { it.eierfogd.verdi in listOf("4883", "2103") }
        return relevantSaker.find { it.eierfogd.verdi == "2103" }?.eierfogd?.verdi
            ?: relevantSaker.firstOrNull()?.eierfogd?.verdi
            ?: behandling.behandlerEnhet
    }

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

    @Transactional
    fun sjekkSkalOppretteForholdsmessigFordeling(behandlingId: Long): SjekkForholdmessigFordelingResponse {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        val behandlesAvEnhet = finnEnhetForBarnIBehandling(behandling)

//        if (behandling.forholdsmessigFordeling != null) {
//            return SjekkForholdmessigFordelingResponse(behandlesAvEnhet, false)
//        }

        val bpHarLøpendeBidragForAndreBarn = harLøpendeBidragForBarnIkkeIBehandling(behandling)

        val åpneEllerLøpendeSakerBp = hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val sakerUtenLøpendeBidrag =
            hentBarnUtenLøpendeBidrag(behandling, åpneEllerLøpendeSakerBp)
                .map { sakKravhaver ->
                    val barnIdent = sakKravhaver.kravhaver
                    ForholdsmessigFordelingBarnDto(
                        ident = barnIdent,
                        navn = hentPersonVisningsnavn(barnIdent) ?: "Ukjent",
                        fødselsdato = hentPersonFødselsdato(barnIdent),
                        saksnr = sakKravhaver.saksnummer,
                        sammeSakSomBehandling = sakKravhaver.saksnummer == behandling.saksnummer,
                        erRevurdering = true,
                        enhet = sakKravhaver.eierfogd!!,
                        åpenBehandling = null,
                        harLøpendeBidrag = false,
                        innkrevesFraDato = null,
                        stønadstype = sakKravhaver.stønadstype,
                        bidragsmottaker =
                            sakKravhaver.bidragsmottaker?.let {
                                RolleDto(
                                    id = -1,
                                    ident = it,
                                    rolletype = Rolletype.BIDRAGSMOTTAKER,
                                    navn = hentPersonVisningsnavn(it) ?: "Ukjent",
                                    fødselsdato = hentPersonFødselsdato(it),
                                    delAvOpprinneligBehandling = false,
                                    erRevurdering = false,
                                )
                            },
                    )
                }
        val bidragsaker =
            åpneEllerLøpendeSakerBp
                .toSet()
                .map { lb ->
                    val sak = sakConsumer.hentSak(lb.saksnummer)
                    val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
                    val barnFødselsnummer = lb.kravhaver
                    ForholdsmessigFordelingBarnDto(
                        ident = lb.kravhaver,
                        navn = hentPersonVisningsnavn(barnFødselsnummer) ?: "Ukjent",
                        fødselsdato = hentPersonFødselsdato(barnFødselsnummer),
                        saksnr = lb.saksnummer,
                        sammeSakSomBehandling = behandling.saksnummer == lb.saksnummer,
                        erRevurdering = false,
                        enhet = sak.eierfogd.verdi,
                        harLøpendeBidrag = true,
                        stønadstype = lb.stønadstype,
                        innkrevesFraDato =
                            if (lb.løperBidragFra != null &&
                                lb.løperBidragFra > behandling.globalVirkningstidspunkt.toYearMonth()
                            ) {
                                lb.løperBidragFra
                            } else {
                                null
                            },
                        åpenBehandling =
                            if (lb.åpenBehandling != null) {
                                ForholdsmessigFordelingÅpenBehandlingDto(
                                    søktFraDato = lb.åpenBehandling.søktFomDato,
                                    mottattDato = lb.åpenBehandling.mottattdato,
                                    stønadstype = lb.åpenBehandling.stonadstype!!,
                                    behandlerEnhet = lb.åpenBehandling.behandlerEnhet,
                                    behandlingId = lb.åpenBehandling.id,
                                    medInnkreving = lb.åpenBehandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                                    søknadsid = null,
                                )
                            } else if (lb.åpenSøknad != null) {
                                ForholdsmessigFordelingÅpenBehandlingDto(
                                    stønadstype = lb.åpenSøknad.stønadstype,
                                    behandlerEnhet = sak.eierfogd.verdi,
                                    søktFraDato = LocalDate.now(),
                                    mottattDato = LocalDate.now(),
                                    behandlingId = null,
                                    medInnkreving = lb.åpenSøknad.innkreving,
                                    søknadsid = lb.åpenSøknad.søknadsid.toLong(),
                                )
                            } else {
                                null
                            },
                        bidragsmottaker =
                            RolleDto(
                                id = -1,
                                ident = bmFødselsnummer,
                                rolletype = Rolletype.BIDRAGSMOTTAKER,
                                navn = hentPersonVisningsnavn(bmFødselsnummer) ?: "Ukjent",
                                fødselsdato = hentPersonFødselsdato(bmFødselsnummer),
                                delAvOpprinneligBehandling = false,
                                erRevurdering = false,
                            ),
                    )
                }
        return SjekkForholdmessigFordelingResponse(
            behandlesAvEnhet,
            bidragsaker.isNotEmpty() || sakerUtenLøpendeBidrag.isNotEmpty(),
            false,
            bidragsaker + sakerUtenLøpendeBidrag,
        )
    }

    private fun hentBarnUtenLøpendeBidrag(
        behandling: Behandling,
        sakerMedLøpendeBidrag: Set<SakKravhaver>? = null,
    ): List<SakKravhaver> {
        val sakerMedLøpendeBidrag = sakerMedLøpendeBidrag ?: hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)

        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val søknadsbarnIdenter =
            sakerMedLøpendeBidrag.map { it.kravhaver } +
                behandling.søknadsbarn.mapNotNull { it.ident }

        val sakerBp = sakConsumer.hentSakerPerson(bidragspliktigFnr)
        return sakerBp
            .flatMap { sak ->
                val barn =
                    sak.roller
                        .filter { it.type == Rolletype.BARN }
                        .filter { it.fødselsnummer != null && !søknadsbarnIdenter.contains(it.fødselsnummer!!.verdi) }
                barn.map {
                    val barnFødselsdato = hentPersonFødselsdato(it.fødselsnummer!!.verdi)
                    val er18EtterrSøktFom = barnFødselsdato!!.plusYears(18).plusMonths(1).withDayOfMonth(1) > behandling.søktFomDato
                    SakKravhaver(
                        kravhaver = it.fødselsnummer!!.verdi,
                        saksnummer = sak.saksnummer.verdi,
                        løperBidragFra = null,
                        stønadstype = if (er18EtterrSøktFom) Stønadstype.BIDRAG18AAR else Stønadstype.BIDRAG,
                        eierfogd = sak.eierfogd.verdi,
                        bidragsmottaker = sak.bidragsmottaker?.fødselsnummer?.verdi,
                    )
                }
            }.filter { barn -> behandling.privatAvtale.any { it.personIdent == barn.kravhaver } }
    }

    fun overførÅpneBisysSøknaderTilBehandling(behandling: Behandling) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = hentÅpneSøknader(bidragspliktigFnr)
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr))
        åpneSøknader.forEach { åpenSøknad ->

            val sak = sakConsumer.hentSak(åpenSøknad.saksnummer)
            val ffDetaljer =
                ForholdsmessigFordelingRolle(
                    delAvOpprinneligBehandling = false,
                    tilhørerSak = åpenSøknad.saksnummer,
                    søknadsid = åpenSøknad.søknadsid.toLong(),
                    eierfogd = sak.eierfogd,
                    mottattDato = åpenSøknad.søknadMottattDato,
                    søknadFomDato = åpenSøknad.søknadFomDato,
                    søktAvType = åpenSøknad.søktAvType,
                    erRevurdering = false,
                )
            åpenSøknad.bidragsmottaker?.let {
                opprettRolle(
                    behandling,
                    Rolletype.BIDRAGSMOTTAKER,
                    it.personident!!,
                    harGebyrSøknad = it.gebyr,
                    ffDetaljer = ffDetaljer,
                )
            }
            åpenSøknad.barn.forEach { barn ->
                val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barn.personident }
                opprettRolle(
                    behandling,
                    Rolletype.BARN,
                    barn.personident!!,
                    stønadstype = åpenSøknad.stønadstype,
                    harGebyrSøknad = barn.gebyr,
                    innbetaltBeløp = barn.innbetaltBeløp,
                    ffDetaljer = ffDetaljer,
                    medInnkreving = åpenSøknad.innkreving,
                    innkrevesFraDato = if (åpenSøknad.innkreving) løpendeBidrag?.periodeFra else null,
                )
            }
            bbmConsumer.lagreBehandlingsid(
                OppdaterBehandlingsidRequest(åpenSøknad.behandlingsid, behandling.id!!.toString(), åpenSøknad.søknadsid),
            )
            if (sak.eierfogd.verdi != behandling.behandlerEnhet) {
                oppdaterSakOgSøknadBehandlerEnhet(åpenSøknad.saksnummer, åpenSøknad.søknadsid, behandling.behandlerEnhet)
            }
        }
    }

    private fun hentÅpneSøknader(bidragspliktigFnr: String) =
        bbmConsumer
            .hentÅpneSøknaderForBp(bidragspliktigFnr)
            .åpneSøknader
            .sortedWith(
                compareByDescending<ÅpenSøknadDto> { it.behandlingstype == Behandlingstype.FORHOLDSMESSIG_FORDELING }
                    .thenBy { it.søknadFomDato },
            ).distinctBy { it.saksnummer }

    @Transactional
    fun overførÅpneBehandlingTilHovedbehandling(behandling: Behandling) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val eksisterendeBMIdenter = behandling.alleBidragsmottakere.map { it.ident }
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
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
                if (behandling.roller.none { it.ident == rolle.ident }) {
                    behandling.roller.add(
                        rolle.kopierRolle(behandling, null),
                    )
                }
            }
            val bm = behandlingOverført.bidragsmottaker?.ident
            behandlingOverført.søknadsbarn.forEach { rolle ->
                if (behandling.søknadsbarn.none { barn -> barn.ident == rolle.ident }) {
                    val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == rolle.ident }
                    val innkrevesFra = if (behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING) løpendeBidrag?.periodeFra else null
                    behandling.roller.add(
                        rolle.kopierRolle(behandling, bm, innkrevesFra, behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING),
                    )
                }
            }
            behandlingOverført.samvær.forEach { samværOverført ->
                if (behandling.samvær.none { s -> s.rolle.ident == samværOverført.rolle.ident }) {
                    kopierSamvær(behandling, samværOverført)
                }
            }
            behandlingOverført.underholdskostnader.forEach { underholdskostnadOverført ->
                if (behandling.underholdskostnader.none { s ->
                        underholdskostnadOverført.rolle != null && s.rolle != null &&
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
            behandlingOverført.grunnlag
                .filter { it.rolle.ident != bidragspliktigFnr && behandling.roller.any { r -> r.ident == it.rolle.ident } }
                .forEach {
                    behandling.grunnlag.add(
                        it.kopierGrunnlag(behandling),
                    )
                }

            giSakTilgangTilEnhet(behandlingOverført, behandling.behandlerEnhet)
            behandlingService.lagreBehandling(behandlingOverført)
        }
    }

    private fun opprettRollerForSak(
        behandling: Behandling,
        saksnummer: String,
        løpendeBidragssak: List<SakKravhaver>,
        behandlerEnhet: String,
        stønadstype: Stønadstype? = null,
    ) {
        val sak = sakConsumer.hentSak(saksnummer)

        val barnUtenSøknader = løpendeBidragssak.filter { ls -> behandling.søknadsbarn.none { it.ident == ls.kravhaver } }
        if (barnUtenSøknader.isEmpty()) return

        val søktFomDato = LocalDate.now().plusMonths(1).withDayOfMonth(1)
        val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi

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
        val ffDetaljer =
            ForholdsmessigFordelingRolle(
                delAvOpprinneligBehandling = false,
                erRevurdering = true,
                tilhørerSak = saksnummer,
                søknadsid = søknadsid,
                eierfogd = sak.eierfogd,
                mottattDato = LocalDate.now(),
                søknadFomDato = søktFomDato,
                søktAvType = SøktAvType.NAV_BIDRAG,
            )
        if (bmFødselsnummer != null && behandling.roller.none { it.ident == bmFødselsnummer }) {
            opprettRolle(
                behandling,
                Rolletype.BIDRAGSMOTTAKER,
                bmFødselsnummer,
                ffDetaljer = ffDetaljer,
            )
        }
        barnUtenSøknader.forEach { søknad ->
            val søknadsIdUtenInnkreving =
                barnMedInnkrevingSenereEnnFomDato
                    .find { b ->
                        b.second.any { it == søknad.kravhaver }
                    }?.first
            val skalInnkreves = barnUtenSøknader.find { it.kravhaver == søknad.kravhaver }?.løperBidragFra != null
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
                        søknadsid = ffDetaljer.søknadsid ?: søknadsIdUtenInnkreving,
                        søknadsidUtenInnkreving = søknadsIdUtenInnkreving,
                    ),
            )
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
                    behandlingsid = behandling.id.toString(),
                    enhet = behandlerEnhet,
                    stønadstype = stønadstype ?: Stønadstype.BIDRAG,
                    søknadFomDato = søktFomDato,
                    barnListe = opprettSøknader,
                    innkreving = medInnkreving,
                ),
            )

        val søknadsid = response.søknadsid.toLong()

        opprettForsendelseForNySøknad(saksnummer, behandling, bmFødselsnummer!!, søknadsid.toString())
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
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
        val åpneSøknader = hentÅpneSøknader(bidragspliktigFnr)

        val eksisterendeSøknadsbarn = behandling.søknadsbarn.map { it.ident }

        val åpneSøknaderSakKravhaver =
            åpneSøknader.flatMap { åpenSøknad ->
                åpenSøknad.partISøknadListe.filter { it.rolletype == Rolletype.BARN }.map { barnFnr ->
                    val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barnFnr.personident }
                    SakKravhaver(
                        åpenSøknad.saksnummer,
                        kravhaver = barnFnr.personident!!,
                        løperBidragFra = løpendeBidrag?.periodeFra,
                        åpenSøknad = åpenSøknad,
                    )
                }
            }
        val åpneBehandlingSakKravhaver =
            åpneBehandlinger.flatMap { behandling ->
                behandling.søknadsbarn.map { barn ->
                    val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barn.ident }
                    SakKravhaver(
                        saksnummer = behandling.saksnummer,
                        kravhaver = barn.ident!!,
                        stønadstype = barn.stønadstype ?: behandling.stonadstype,
                        åpenBehandling = behandling,
                        eierfogd = behandling.behandlerEnhet,
                        løperBidragFra = løpendeBidrag?.periodeFra,
                    )
                }
            }
        val krahaverFraÅpneSaker = åpneSøknaderSakKravhaver.map { it.kravhaver } + åpneBehandlingSakKravhaver.map { it.kravhaver }
        val løpendeBidragsaker =
            løpendeBidraggsakerBP.filter { !krahaverFraÅpneSaker.contains(it.kravhaver.verdi) }.map {
                SakKravhaver(
                    saksnummer = it.sak.verdi,
                    kravhaver = it.kravhaver.verdi,
                    stønadstype = it.type,
                    løperBidragFra = it.periodeFra,
                )
            }
        val bidragsaker = løpendeBidragsaker + åpneSøknaderSakKravhaver + åpneBehandlingSakKravhaver
        return bidragsaker
            .filter { !eksisterendeSøknadsbarn.contains(it.kravhaver) }
            .sortedWith { a, b ->
                val aHasOpen = a.åpenSøknad != null || a.åpenBehandling != null
                val bHasOpen = b.åpenSøknad != null || b.åpenBehandling != null
                when {
                    aHasOpen && !bHasOpen -> -1
                    !aHasOpen && bHasOpen -> 1
                    else -> 0
                }
            }.distinctBy { it.saksnummer to it.kravhaver }
            .toSet()
    }
}
