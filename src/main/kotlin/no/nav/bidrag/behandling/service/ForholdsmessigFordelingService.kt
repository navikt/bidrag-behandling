package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.HentetGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagLøpendeBidragFF
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.leggTilGebyr
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.grunnlag.LøpendeBidragGrunnlagForholdsmessigFordeling
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.OpprettFFRequest
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.transformers.barn
import no.nav.bidrag.behandling.transformers.behandling.erSamme
import no.nav.bidrag.behandling.transformers.behandling.erSammePerson
import no.nav.bidrag.behandling.transformers.behandling.finnRolle
import no.nav.bidrag.behandling.transformers.behandling.finnes
import no.nav.bidrag.behandling.transformers.behandling.oppdaterBehandlingEtterOppdatertRoller
import no.nav.bidrag.behandling.transformers.filtrerSakerHvorPersonErBP
import no.nav.bidrag.behandling.transformers.finnPeriodeLøperBidrag
import no.nav.bidrag.behandling.transformers.finnesLøpendeBidragForRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.OppdaterBarnFraFFRequest
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.erFeilregistrert
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.erForholdsmessigFordeling
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.erLik
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.finnEldsteSøktFomDato
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.finnSøktFomRevurderingSøknad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.fjernSøknad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.hentBidragSakForKravhaver
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.hentForKravhaver
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierGrunnlag
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierHusstandsmedlem
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierInntekt
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierOverBegrunnelseForBehandling
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierOverInntekterForRolleFraBehandling
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierPrivatAvtale
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierSamvær
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.kopierUnderholdskostnad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.`løperBidragEtterDato`
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.mapSakKravhaverTilForholdsmessigFordelingDto
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.opprettRolle
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilFFBarnDetaljer
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilFFDetaljerBM
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilFFDetaljerBP
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilForholdsmessigFordelingSøknad
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.`tilIdentStønadstypeNøkkel`
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.tilOpprettRolleDto
import no.nav.bidrag.behandling.transformers.grunnlagsreferanseSimulert
import no.nav.bidrag.behandling.transformers.harLøpendeBidragFørOpphørEllerLøpende
import no.nav.bidrag.behandling.transformers.harSlåttUtTilForholdsmessigFordeling
import no.nav.bidrag.behandling.transformers.løperBidragFørOpphør
import no.nav.bidrag.behandling.transformers.løperPeriodeEtterSøktFomDato
import no.nav.bidrag.behandling.transformers.mapTilBeregnetBidragDto
import no.nav.bidrag.behandling.transformers.tilDato18årsBidrag
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregningsperiode
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.commons.util.RequestContextAsyncContext
import no.nav.bidrag.commons.util.SecurityCoroutineContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.tilBehandlingstema
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.samhandler.Valutakode
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragPeriodeRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidrag
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragPeriodeResponse
import no.nav.bidrag.transport.behandling.beregning.felles.Barn
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadsBarnRequest
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlerenhetRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.ÅpenSøknadDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragTilFordelingLøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.hendelse.BehandlingStatusType
import no.nav.bidrag.transport.behandling.vedtak.Periode
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toLocalDate
import no.nav.bidrag.transport.felles.toYearMonth
import no.nav.bidrag.transport.sak.OpprettMidlertidligTilgangRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.collections.plus

private val LOGGER = KotlinLogging.logger {}
val ÅpenSøknadDto.bidragsmottaker get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
val ÅpenSøknadDto.bidragspliktig get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
val ÅpenSøknadDto.barn get() =
    partISøknadListe.filter {
        it.rolletype == Rolletype.BARN &&
            it.behandlingstatus == Behandlingstatus.UNDER_BEHANDLING
    }

fun ÅpenSøknadDto.parterForRolle(rolletype: Rolletype) = partISøknadListe.filter { it.rolletype == rolletype }

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
    val løpendeBidragBarn: List<LøpendeBidragGrunnlagForholdsmessigFordeling> = emptyList(),
)

data class SimulertInntektGrunnlag(
    val type: Grunnlagstype,
    val gjelder: String,
    val beløp: BigDecimal,
    val inntektstype: Inntektsrapportering,
)

data class SakKravhaver(
    val saksnummer: String?,
    val kravhaver: String,
    val bidragsmottaker: String? = null,
    val eierfogd: String? = null,
    val løperBidragFra: YearMonth? = null,
    val løperBidragTil: YearMonth? = null,
    val stønadstype: Stønadstype? = null,
    val opphørsdato: YearMonth? = null,
    val åpneSøknader: MutableSet<ÅpenSøknadDto> = mutableSetOf(),
    val åpneBehandlinger: MutableSet<Behandling> = mutableSetOf(),
    val privatAvtale: PrivatAvtale? = null,
) {
    fun erSammePerson(
        ident: String,
        stønadstype1: Stønadstype?,
    ) = erSammePerson(ident, stønadstype1, kravhaver, stønadstype)

    val distinctKey get() = "${kravhaver}_${stønadstype ?: "null"}"
}

data class LøpendeBidragSakPeriode(
    val sak: Saksnummer,
    val type: Stønadstype,
    val kravhaver: Personident,
    val valutakode: String,
    val periodeFra: YearMonth,
    val periodeTil: YearMonth?,
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
    private val virkningstidspunktService: VirkningstidspunktService,
    private val underholdService: UnderholdService,
) {
    @Transactional
    fun lukkAllFFSaker(behandlingsid: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        val åpneSaker =
            hentÅpneSøknader(
                behandling.bidragspliktig!!.ident!!,
                behandling.behandlingstypeForFF,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )
        åpneSaker.filter { it.behandlingstype == behandling.behandlingstypeForFF }.forEach {
            bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(it.søknadsid))
        }
    }

    @Transactional
    fun opprettRevurderingssøknaderForKlageEllerOmgjøring(behandling: Behandling) {
        if (!UnleashFeatures.TILGANG_OPPRETTE_FF.isEnabled) {
            LOGGER.info { "Opprettelse av forholdsmessig fordeling er deaktivert" }
            ugyldigForespørsel("Opprettelse av forholdsmessig fordeling er deaktivert")
        }

        val behandlerEnhet = finnEnhetForBarnIBehandling(behandling)
        val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling)
        val revurderingsbarn = behandling.søknadsbarn.filter { it.erRevurderingsbarn }.map { it.ident to it.stønadstype }
        behandling.søknadsbarn.filter { !it.erRevurderingsbarn }.forEach {
            it.forholdsmessigFordeling!!.søknader.add(
                ForholdsmessigFordelingSøknadBarn(
                    søknadsid = behandling.soknadsid,
                    behandlingstema = behandling.behandlingstema,
                    behandlingstype = behandling.søknadstype,
                    omgjørSøknadsid = behandling.omgjøringsdetaljer?.soknadRefId,
                    omgjørVedtaksid = behandling.omgjøringsdetaljer?.omgjørVedtakId,
                    innkreving = behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                    mottattDato = behandling.mottattdato,
                    søktAvType = behandling.soknadFra,
                    søknadFomDato = behandling.søktFomDato,
                ),
            )
        }
        val relevanteKravhavereRevurderingsbarnMedLøpendeEllerPrivatAvtale =
            relevanteKravhavere
                .filter { rk ->
                    revurderingsbarn.any {
                        it.first == rk.kravhaver && it.second == rk.stønadstype
                    }
                }.toSet()
        val relevanteKravhavereRevurderisnbarnUtenPrivatAvtale =
            revurderingsbarn
                .filter { rb ->
                    relevanteKravhavereRevurderingsbarnMedLøpendeEllerPrivatAvtale.none {
                        it.kravhaver == rb.first &&
                            it.stønadstype == rb.second
                    }
                }.map { rb ->
                    val revurderingsbarnRolle = behandling.roller.find { it.erSammeRolle(rb.first!!, rb.second) }!!
                    // Caser hvor revurderingsbarn ikke har privat avtale eller løpende bidrag
                    SakKravhaver(
                        saksnummer = revurderingsbarnRolle.saksnummer,
                        stønadstype = revurderingsbarnRolle.stønadstype,
                        kravhaver = revurderingsbarnRolle.ident!!,
                        eierfogd = sakConsumer.hentSak(revurderingsbarnRolle.saksnummer).eierfogd.verdi,
                        bidragsmottaker = revurderingsbarnRolle.bidragsmottaker!!.ident,
                    )
                }

        val relevanteKravhavereRevurderingsbarn =
            relevanteKravhavereRevurderingsbarnMedLøpendeEllerPrivatAvtale + relevanteKravhavereRevurderisnbarnUtenPrivatAvtale
        relevanteKravhavereRevurderingsbarn
            .filter { !it.saksnummer.isNullOrEmpty() }
            // 18 års bidrag først
            .sortedByDescending { it.stønadstype }
            .groupBy {
                Pair(it.saksnummer!!, it.stønadstype)
            }.forEach { (saksnummerLøpendeBidrag, løpendebidragssaker) ->
                val saksnummer = saksnummerLøpendeBidrag.first
                val revurderingsbarnRoller =
                    behandling.søknadsbarn.filter {
                        it.erRevurderingsbarn &&
                            løpendebidragssaker.any { lb -> lb.erSammePerson(it.ident!!, it.stønadstype) }
                    }

                val søktFomDatoOpprinneligRevurderingssøknad =
                    revurderingsbarnRoller
                        .first()
                        .forholdsmessigFordeling
                        ?.søknader
                        ?.find { it.behandlingstype?.erForholdsmessigFordeling == true }
                        ?.søknadFomDato ?: relevanteKravhavereRevurderingsbarn.finnSøktFomRevurderingSøknad(behandling)

                revurderingsbarnRoller.forEach {
                    // Slett forholdsmessig fordeling info. Dette er fra påklaget vedtak. Oppretter nye søknader
                    it.forholdsmessigFordeling!!.søknader.clear()
                }
                opprettRollerOgRevurderingssøknadForSak(
                    behandling,
                    saksnummer,
                    løpendebidragssaker,
                    behandlerEnhet,
                    saksnummerLøpendeBidrag.second,
                    søktFomDatoOpprinneligRevurderingssøknad,
                    true,
                )
            }
        val søknadsdetaljer = behandling.tilFFBarnDetaljer()
        behandling.søknadsbarn
            .filter { !it.erRevurderingsbarn }
            .forEach {
                // Fjern søknadsreferanser som ikke gjelder klagesøknad
                it.forholdsmessigFordeling!!.søknader.removeIf { it.omgjørSøknadsid == null }
                it.forholdsmessigFordeling!!.søknader.add(søknadsdetaljer)
            }
        behandling.forholdsmessigFordeling =
            ForholdsmessigFordeling(
                erHovedbehandling = true,
            )

        giSakTilgangTilEnhet(behandling, behandlerEnhet)
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
            opprettGrunnlagLøpendeBidrag(behandling)

            val originalBM = behandling.bidragsmottaker!!.ident

            behandling.alleBidragsmottakere.filter { it.forholdsmessigFordeling == null }.forEach {
                it.forholdsmessigFordeling = behandling.tilFFDetaljerBM()
            }
            val behandlerEnhet = finnEnhetForBarnIBehandling(behandling)
            val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling).toMutableSet()
            val eksisterendeSøknadsbarn =
                behandling.søknadsbarn
                    .filter {
                        reevaluerSøkndasbarn == null ||
                            (!reevaluerSøkndasbarn.erSamme(it.ident!!, it.stønadstype))
                    }.map { it.identStønadstypeNøkkel }
            if (reevaluerSøkndasbarn != null && relevanteKravhavere.none { reevaluerSøkndasbarn.erSamme(it.kravhaver, it.stønadstype) }) {
                val søknadsbarn = behandling.søknadsbarn.find { it.erSammeRolle(reevaluerSøkndasbarn.first, reevaluerSøkndasbarn.second) }
                // Antar barn har ingen løpende bidrag eller privat avtale
                relevanteKravhavere.add(
                    SakKravhaver(
                        kravhaver = reevaluerSøkndasbarn.first,
                        stønadstype = reevaluerSøkndasbarn.second,
                        saksnummer = søknadsbarn!!.saksnummer,
                        bidragsmottaker = søknadsbarn.bidragsmottaker!!.ident,
                    ),
                )
            }
            val relevanteKravhavereIkkeSøknadsbarn =
                relevanteKravhavere
                    .filter {
                        !eksisterendeSøknadsbarn.contains(
                            it.distinctKey,
                        )
                    }.toSet()
            overførÅpneBehandlingTilHovedbehandling(behandling, relevanteKravhavereIkkeSøknadsbarn)
            overførÅpneBisysSøknaderTilBehandling(behandling, relevanteKravhavereIkkeSøknadsbarn)
            val bidragssakerBpUtenÅpenBehandling =
                relevanteKravhavereIkkeSøknadsbarn.filter {
                    it.åpneSøknader.isEmpty() &&
                        it.åpneBehandlinger.isEmpty()
                }

            bidragssakerBpUtenÅpenBehandling
                .filter { !it.saksnummer.isNullOrEmpty() }
                // 18 års bidrag først
                .sortedByDescending { it.stønadstype }
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
                        request?.revurderingFraDato
                            ?: relevanteKravhavereIkkeSøknadsbarn.finnSøktFomRevurderingSøknad(behandling),
                        erOppdateringAvBehandlingSomErIFF,
                    )
                }
            behandling.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    erHovedbehandling = true,
                )

            oppdaterGebyrDetaljerRollerIBehandling(behandling, relevanteKravhavere)

            val søknaderSøknadsbarn = relevanteKravhavere.filter { eksisterendeSøknadsbarn.contains(it.distinctKey) }.toSet()

            behandling.søknadsbarn.forEach { barn ->
                if (barn.forholdsmessigFordeling == null) {
                    val sakKravhaverSøknadsbarn = søknaderSøknadsbarn.find { it.erSammePerson(barn.ident!!, barn.stønadstype) }

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

                    val løpendeBidrag = bidragssakerBpUtenÅpenBehandling.find { bs -> bs.erSammePerson(barn.ident!!, barn.stønadstype) }
                    barn.forholdsmessigFordeling =
                        ForholdsmessigFordelingRolle(
                            delAvOpprinneligBehandling = true,
                            tilhørerSak = behandling.saksnummer,
                            behandlingsid = behandling.id,
                            behandlerenhet = behandling.behandlerEnhet,
                            bidragsmottaker = originalBM,
                            erRevurdering = false,
                            søknader = søknadsdetaljer.toMutableSet(),
                            løperBidragFra = løpendeBidrag?.løperBidragFra,
                            løperBidragTil = løpendeBidrag?.løperBidragTil,
                            harLøpendeBidrag = løpendeBidrag?.løperBidragEtterDato(behandling.eldsteSøktFomDato.toYearMonth()) == true,
                        )
                }
            }

            giSakTilgangTilEnhet(behandling, behandlerEnhet)

            behandling.syncGebyrSøknadReferanse()
            behandlingService.lagreBehandling(behandling)
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)
            oppdaterBehandlingEtterOppdatertRoller(
                behandling,
                underholdService,
                virkningstidspunktService,
                behandling.søknadsbarn.map {
                    it.tilOpprettRolleDto()
                },
                emptyList(),
            )
        } catch (e: Exception) {
            secureLogger.error(e) { "Det skjedde en feil ved opprettelse eller oppdatering av FF for behandling $behandlingId" }
            behandlingRepository.markerOpprettelseAvFFFeilet(behandlingId)
        }
    }

    private fun opprettGrunnlagLøpendeBidrag(behandling: Behandling) {
        val type = Grunnlagsdatatype.LØPENDE_BIDRAG_OPPRETT_FORHOLDSMESSIG_FORDELING
        val nyesteLøpendeBidragGrunnlag = sjekkBeregningKreverForholdsmessigFordeling(behandling).løpendeBidragBarn
        val eksisterendeGrunnlag =
            behandling.grunnlag.hentSisteGrunnlagLøpendeBidragFF(behandling) ?: emptyList()
        if (eksisterendeGrunnlag != nyesteLøpendeBidragGrunnlag) {
            secureLogger.debug {
                "Lagrer ny grunnlag løpende bidrag hvor siste aktive grunnlag var $eksisterendeGrunnlag"
            }
            // Hver gang det lagres så legges det til ny barn. Dette er grunnlag når FF opprettes.
            // Det betyr det saksbehandler baserte valget på for når FF skulle opprettes. Derfor bør ikke eksisterende data endres
            // Når nye grunnlag lagres
            val eksisterendeGrunnlagIdenter = eksisterendeGrunnlag.map { it.gjelderBarnIdent }
            val nyeGrunnlag =
                nyesteLøpendeBidragGrunnlag
                    .filter { !eksisterendeGrunnlagIdenter.contains(it.gjelderBarnIdent) }

            behandling.grunnlag.addAll(
                nyeGrunnlag.map {
                    Grunnlag(
                        behandling = behandling,
                        type = type,
                        gjelder = it.gjelderBarnIdent,
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

    fun feilregistrerBarnFraFFSøknad(rolle: Rolle) {
        val søknader =
            rolle.forholdsmessigFordeling!!.søknaderUnderBehandling.filter {
                it.behandlingstype == rolle.behandling.behandlingstypeForFF
            }
        val søknaderFeilregistrertBarn =
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
        rolle.forholdsmessigFordeling!!
            .søknaderUnderBehandling
            .filter { `søknaderFeilregistrertBarn`.contains(it.søknadsid!!) }
            .forEach {
                it.status = Behandlingstatus.FEILREGISTRERT
            }

        val søknaderFeilregistrert =
            søknaderFeilregistrertBarn.filter {
                val søknad = bbmConsumer.hentSøknad(it)
                søknad?.søknad?.behandlingStatusType == BehandlingStatusType.AVBRUTT
            }

        rolle.behandling.bidragsmottaker
            ?.forholdsmessigFordeling
            ?.søknaderUnderBehandling
            ?.filter { `søknaderFeilregistrert`.contains(it.søknadsid!!) }
            ?.forEach {
                it.status = Behandlingstatus.FEILREGISTRERT
            }

        rolle.behandling.bidragspliktig
            ?.forholdsmessigFordeling
            ?.søknaderUnderBehandling
            ?.filter { `søknaderFeilregistrert`.contains(it.søknadsid!!) }
            ?.forEach {
                it.status = Behandlingstatus.FEILREGISTRERT
            }
    }

    fun feilregistrerFFSøknad(rolle: Rolle) {
        if (rolle.forholdsmessigFordeling == null || !rolle.forholdsmessigFordeling!!.erRevurdering) return
        rolle.forholdsmessigFordeling!!.søknaderUnderBehandling.forEach { søknad ->
            val søknadsid = søknad.søknadsid
            LOGGER.info { "Feilregistrerer søknad $søknadsid i behandling ${rolle.behandling.id}" }
            try {
                bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknadsid!!))
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
                LOGGER.error(e) { "Feil ved feilregistrering av søknad $søknadsid i behandling ${rolle.behandling.id}" }
            }
        }
    }

    fun kanBehandlingSlettes(
        behandling: Behandling,
        slettBarn: List<Rolle>,
    ): Boolean {
        val barnIkkeRevurdering =
            behandling.søknadsbarn
                .filter { slettBarn.isEmpty() || !slettBarn.mapNotNull { it.ident }.contains(it.ident) }
                .filter { it.forholdsmessigFordeling == null || !it.forholdsmessigFordeling!!.erRevurdering }
        return barnIkkeRevurdering.isEmpty()
    }

    @Transactional
    fun oppdaterBarnEtterInnkrevingsvedtak(
        behandling: Behandling,
        barnIdent: Personident,
    ) {
        val rolle = behandling.søknadsbarn.find { it.ident == barnIdent.verdi } ?: return
        val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling)

        if (rolle.erRevurderingsbarn) {
            val søknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
            if (søknad == null || søknad?.innkreving == false) {
                feilregistrerBarnFraFFSøknad(rolle)
                opprettRollerOgRevurderingssøknadForSak(
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

    // Case 1. Slett revurderingsbarn hvis barnet har ingen bidrag som innkreves og privat avtalen slettes
    // Case 2: Slett revurderingsbarn hvis bidraget opphøres før søkt fom dato på behandlingen.
    // Da er det ikke behov for å revurdere barnet fordi barnet har ingen løpende bidrag eller privat avtale
    @Transactional
    fun slettRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
    ) {
        val rolleHarLøpendeBidrag = rolle.harLøpendeBidragFørOpphørEllerLøpende()
        if (!rolle.erRevurderingsbarn || rolleHarLøpendeBidrag) return

        feilregistrerBarnFraFFSøknad(rolle)
        behandlingService.slettRolleFraBehandling(behandling, rolle)
        behandling.roller.remove(rolle)
        secureLogger.info { "Slettet revurderingsbarn ${rolle.ident} fra behandling ${behandling.id}" }
        behandlingService.sendOppdatertHendelse(behandling.id!!, false)
    }

    @Transactional
    fun oppdaterRevurderingsbarnFraInnkrevingTilUtenInnkreving(
        behandling: Behandling,
        rolle: Rolle,
    ) {
        // Flytt revurderingsbarn fra innkrevingssøknad til uten innkreving
        if (rolle.erRevurderingsbarn) {
            val relevanteKravhavere = hentAlleRelevanteKravhavere(behandling)
            val søknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
            if (søknad == null || søknad.innkreving) {
                feilregistrerBarnFraFFSøknad(rolle)
                opprettRollerOgRevurderingssøknadForSak(
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

    @Transactional
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
                // Hvis det løper bidrag før opphørsdatoen i FF behandling så må saksbehandler ta stilling til evt endring før opphørsdatoen
                // Når det er valgt avslag så fungerer virkningstidspunkt som en opphørsdato og perioder før endres ikke. Derfor må det velges en årsak med virkning og opphørsdato hvis det løper bidrag før opphørsdatoen
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

    // Feilhåndtering hvis FF søknad blir slettet manuelt eller ved feil
    @Transactional
    fun synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling: Behandling) {
        val løpendeBidraggsakerBP =
            hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!), behandling.finnBeregningsperiode())

        grunnlagService.lagreBeløpshistorikkGrunnlag(behandling)
        grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(behandling)

        val alleSøknaderRelevantForBehandling =
            hentÅpneSøknader(behandling.bidragspliktig!!.ident!!, behandling.behandlingstypeForFF, behandling.omgjøringsdetaljer)

        leggTilRollerFraRelevanteSøknaderSomIkkeErIBehandling(behandling, alleSøknaderRelevantForBehandling)

        behandling.roller
            .filter { !it.erBarn }
            .forEach { rolle ->
                val ffDetaljer = rolle.forholdsmessigFordeling ?: return@forEach
                ffDetaljer.søknader =
                    oppdaterLagredeSoknadsstatuserFraBbm(
                        ffDetaljer.søknader,
                        alleSøknaderRelevantForBehandling.filter {
                            it.saksnummer == rolle.saksnummer
                        },
                        rolle,
                    )
            }

        behandling.søknadsbarn.forEach { rolle ->
            val ffDetaljer = rolle.forholdsmessigFordeling ?: return@forEach
            val beløpshistorikk = løpendeBidraggsakerBP.find { it.kravhaver.verdi == rolle.ident }
            val løperBidrag =
                if (beløpshistorikk != null) {
                    rolle.løperPeriodeEtterSøktFomDato(
                        ÅrMånedsperiode(beløpshistorikk.periodeFra, beløpshistorikk.periodeTil),
                    )
                } else {
                    false
                }
            ffDetaljer.søknader = oppdaterLagredeSoknadsstatuserFraBbm(ffDetaljer.søknader, alleSøknaderRelevantForBehandling, rolle)

            val eldsteSøknad = rolle.forholdsmessigFordeling!!.eldsteSøknad
            val erMedInnkreving =
                (eldsteSøknad != null && eldsteSøknad.innkreving) || rolle.innkrevingstype == Innkrevingstype.MED_INNKREVING
            if (!erMedInnkreving && løperBidrag) {
                oppdaterBarnEtterInnkrevingsvedtak(behandling, Personident(rolle.ident!!))
            }
            if (erMedInnkreving && !løperBidrag && rolle.erRevurderingsbarn) {
                oppdaterRevurderingsbarnFraInnkrevingTilUtenInnkreving(behandling, rolle)
                rolle.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
            }

            val lagretSøknader = ffDetaljer.søknader
            val søknaderForBarn = finnApneSoknaderForBarn(alleSøknaderRelevantForBehandling, rolle)
            val åpneSøknaderIkkeFF = søknaderForBarn.filter { !it.behandlingstype.erForholdsmessigFordeling }
            val åpneSøknaderFF = søknaderForBarn.filter { it.behandlingstype.erForholdsmessigFordeling }

            if (åpneSøknaderIkkeFF.isNotEmpty() && rolle.erRevurderingsbarn) {
                håndterBarnSomSkalVæreSøknadsbarn(behandling, rolle, åpneSøknaderIkkeFF.first())
            } else if (åpneSøknaderFF.isNotEmpty() && !rolle.erRevurderingsbarn) {
                feilregistrerBarnFraFFSøknad(rolle)
            } else if (!rolle.erRevurderingsbarn && åpneSøknaderIkkeFF.isEmpty()) {
                LOGGER.info {
                    "Barn ${rolle.ident} i ${behandling.id} er ikke markert som revurderingsbarn men har ingen åpne søknader." +
                        "Oppretter eller legger til i eksisterende FF søknad og endrer barnet til revurderingsbarn"
                }
                // Er markert som søknadsbarn men har ingen åpne søknader. Endre til revurderingsbarn
                håndterBarnSomSkalVæreRevurderingsbarn(behandling, rolle, lagretSøknader)
            } else if (rolle.erRevurderingsbarn && åpneSøknaderFF.isEmpty() && åpneSøknaderIkkeFF.isEmpty()) {
                LOGGER.info {
                    "Barn ${rolle.ident} i ${behandling.id} er markert som revurderingsbarn men har ingen åpne FF søknader." +
                        "Oppretter eller legger til i eksisterende FF søknad"
                }
                // Er markert som revurderingsbarn og har ingen åpne FF søknadaer. Opprett FF søknad
                håndterBarnSomSkalVæreRevurderingsbarn(behandling, rolle, lagretSøknader)
            } else if (rolle.erRevurderingsbarn && !rolle.harLøpendeBidragFørOpphørEllerLøpende()) {
                secureLogger.info {
                    "Sletter revurderingsbarn ${rolle.personident?.verdi} " +
                        "fra behandling ${behandling.id} etter da det ikke løper bidrag etter søkt fom dato. " +
                        "Barnet har ingen løpende bidrag lenger og trenger derfor ikke å være revurderingsbarn"
                }
                slettRevurderingsbarn(behandling, rolle)
            }
        }

        val hovedsøknad = bbmConsumer.hentSøknad(behandling.soknadsid!!)
        if (hovedsøknad != null && hovedsøknad.søknad.behandlingStatusType == BehandlingStatusType.AVBRUTT) {
            endreHovedsøknadIFFEtterHovedsøknadBleSlettet(behandling, hovedsøknad.søknad.søknadsid)
        }
    }

    private fun leggTilRollerFraRelevanteSøknaderSomIkkeErIBehandling(
        behandling: Behandling,
        alleSøknaderRelevantForBehandling: List<ÅpenSøknadDto>,
    ) {
        val alleIdenterIBehandling = behandling.roller.map { it.ident!! }
        val alleRollerRelevantSomIkkeErIBehandling =
            alleSøknaderRelevantForBehandling
                .flatMap {
                    it.partISøknadListe
                        .filter { it.rolletype != Rolletype.BIDRAGSPLIKTIG }
                        .map { it.personident!! }
                }.distinct()
                .filter { !alleIdenterIBehandling.contains(it) }

        alleRollerRelevantSomIkkeErIBehandling.forEach { rolle ->
            val søknad = alleSøknaderRelevantForBehandling.find { it.partISøknadListe.any { it.personident == rolle } }!!
            leggTilEllerSlettBarnFraBehandlingSomErIFF(
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

    private fun håndterBarnSomSkalVæreSøknadsbarn(
        behandling: Behandling,
        rolle: Rolle,
        førsteSøknad: ÅpenSøknadDto,
    ) {
        leggTilEllerSlettBarnFraBehandlingSomErIFF(
            OppdaterBarnFraFFRequest(
                rollerSomSkalLeggesTilDto = listOf(rolle.tilOpprettRolleDto()),
                behandling = behandling,
                medInnkreving = førsteSøknad.innkreving,
                søktFraDato = førsteSøknad.søknadFomDato,
                stønadstype = førsteSøknad.behandlingstema.tilStønadstype(),
                behandlerenhet = førsteSøknad.behandlerenhet!!,
                søknadsid = førsteSøknad.søknadsid,
                saksnummer = førsteSøknad.saksnummer,
            ),
        )
    }

    private fun håndterBarnSomSkalVæreRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
    ) {
        val sisteFfSøknad = finnSisteFFSøknad(lagretSøknader)
        val aktivFfSøknad = finnAktivFFSøknad(lagretSøknader)

        val søknadSomSkalBeholdes =
            when {
                aktivFfSøknad != null -> {
                    rolle.forholdsmessigFordeling?.erRevurdering = true
                    aktivFfSøknad
                }

                sisteFfSøknad == null -> {
                    opprettEllerOppdaterForholdsmessigFordeling(
                        behandling.id!!,
                        reevaluerSøkndasbarn = Pair(rolle.ident!!, rolle.stønadstype),
                    )
                    return
                }

                else -> {
                    opprettEllerGjenopprettFfSøknadForRevurderingsbarn(behandling, rolle, lagretSøknader, sisteFfSøknad)
                }
            }

        feilregistrerAndreSøknaderTrygt(lagretSøknader, søknadSomSkalBeholdes)
    }

    private fun opprettEllerGjenopprettFfSøknadForRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        referanseSøknad: ForholdsmessigFordelingSøknadBarn,
    ): ForholdsmessigFordelingSøknadBarn {
        val nyEllerEksisterendeSøknad =
            leggTilEllerOpprettSøknadForRevurderingsbarn(
                barnIdent = rolle.ident!!,
                saksnummer = referanseSøknad.saksnummer ?: rolle.forholdsmessigFordeling!!.tilhørerSak,
                behandling = behandling,
                stønadstype = rolle.stønadstype,
                søktFomDato = referanseSøknad.søknadFomDato ?: LocalDate.now().plusMonths(1).withDayOfMonth(1),
                medInnkreving = rolle.innkrevingstype == Innkrevingstype.MED_INNKREVING,
            )

        val eksisterendeSøknad = lagretSøknader.find { it.søknadsid == nyEllerEksisterendeSøknad.søknadsid }
        val søknadSomSkalBeholdes =
            if (eksisterendeSøknad != null) {
                eksisterendeSøknad.status = Behandlingstatus.UNDER_BEHANDLING
                eksisterendeSøknad
            } else {
                lagretSøknader.add(nyEllerEksisterendeSøknad)
                nyEllerEksisterendeSøknad
            }
        rolle.forholdsmessigFordeling?.erRevurdering = true
        return søknadSomSkalBeholdes
    }

    private fun feilregistrerAndreSøknaderTrygt(
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        søknadSomSkalBeholdes: ForholdsmessigFordelingSøknadBarn,
    ) {
        lagretSøknader
            .filter { it.søknadsid != søknadSomSkalBeholdes.søknadsid }
            // Bare feilregistrer de som har samme innkrevingstype men ikke er samme søknadsid
            .filter { it.innkreving == søknadSomSkalBeholdes.innkreving }
            .forEach { søknad ->
                val søknadsid = søknad.søknadsid ?: return@forEach
                if (feilregistrerSøknadTrygt(søknadsid)) {
                    søknad.status = Behandlingstatus.FEILREGISTRERT
                }
            }
    }

    private fun feilregistrerSøknadTrygt(søknadsid: Long): Boolean =
        try {
            bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknadsid))
            true
        } catch (e: Exception) {
            LOGGER.warn(e) { "Kunne ikke feilregistrere søknad $søknadsid i BBM" }
            false
        }

    private fun finnAktivFFSøknad(lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>) =
        lagretSøknader
            .filter { it.behandlingstype?.erForholdsmessigFordeling == true }
            .filter { it.status == Behandlingstatus.UNDER_BEHANDLING || it.status == null }
            .maxByOrNull { it.søknadsid ?: Long.MIN_VALUE }

    private fun finnSisteFFSøknad(lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>) =
        lagretSøknader
            .filter { it.behandlingstype?.erForholdsmessigFordeling == true }
            .maxByOrNull { it.søknadsid ?: Long.MIN_VALUE }

    private fun finnApneSoknaderForBarn(
        alleSøknaderRelevantForBehandling: List<ÅpenSøknadDto>,
        rolle: Rolle,
    ) = alleSøknaderRelevantForBehandling
        .filter { it.behandlingstema.tilStønadstype() == rolle.stønadstype }
        .filter { søknad ->
            søknad.partISøknadListe
                .filter { it.rolletype == Rolletype.BARN }
                .filter { it.behandlingstatus?.erFeilregistrert != true }
                .any { it.personident == rolle.ident }
        }

    private fun oppdaterLagredeSoknadsstatuserFraBbm(
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        alleSøknaderRelevantForBehandling: List<ÅpenSøknadDto>,
        rolle: Rolle,
    ): MutableSet<ForholdsmessigFordelingSøknadBarn> {
        val eksisterendeSøknaderOppdatert =
            lagretSøknader
                .mapNotNull { lagretSøknad ->
                    val søknad = `alleSøknaderRelevantForBehandling`.find { it.søknadsid == lagretSøknad.søknadsid }
                    var oppslagMotBbmFeilet = false

                    val partISøknad =
                        if (søknad == null) {
                            try {
                                val søknad =
                                    bbmConsumer
                                        .hentSøknad(lagretSøknad.søknadsid!!)
                                        ?.søknad
                                        ?.takeIf {
                                            (rolle.stønadstype == null || it.behandlingstema.tilStønadstype() == rolle.stønadstype)
                                        }
                                søknad?.partISøknadListe?.find { it.personident == rolle.ident }
                            } catch (e: Exception) {
                                oppslagMotBbmFeilet = true
                                LOGGER.warn(e) { "Kunne ikke hente søknad ${lagretSøknad.søknadsid} fra BBM" }
                                null
                            }
                        } else {
                            søknad.partISøknadListe.find { it.personident == rolle.ident }
                        }

                    if (partISøknad == null && !oppslagMotBbmFeilet) {
                        return@mapNotNull null
                    }

                    if (partISøknad != null) {
                        lagretSøknad.status = partISøknad.behandlingstatus ?: lagretSøknad.status
                    } else if (!oppslagMotBbmFeilet) {
                        lagretSøknad.status = Behandlingstatus.FEILREGISTRERT
                    }
                    lagretSøknad.søknadFomDato = søknad?.søknadFomDato ?: lagretSøknad.søknadFomDato
                    lagretSøknad.behandlingstema = søknad?.behandlingstema ?: lagretSøknad.behandlingstema
                    lagretSøknad.søktAvType = søknad?.søktAvType ?: lagretSøknad.søktAvType
                    lagretSøknad.behandlingstype = søknad?.behandlingstype ?: lagretSøknad.behandlingstype
                    lagretSøknad.mottattDato = søknad?.søknadMottattDato ?: lagretSøknad.mottattDato
                    lagretSøknad.innkreving = søknad?.innkreving ?: lagretSøknad.innkreving
                    lagretSøknad
                }.toMutableSet()

        val eksisterendeSøknader = eksisterendeSøknaderOppdatert.map { it.søknadsid }
        val nyeSøknader =
            alleSøknaderRelevantForBehandling
                .asSequence()
                .filter { !eksisterendeSøknader.contains(it.søknadsid) }
                .filter { it.behandlingstema.tilStønadstype() == rolle.stønadstype }
                .filter { it.parterForRolle(rolle.rolletype).any { it.personident == rolle.ident } }
                .map {
                    val partBarn = it.parterForRolle(rolle.rolletype).find { it.personident == rolle.ident }
                    val status =
                        if (rolle.rolletype != Rolletype.BARN) {
                            null
                        } else {
                            partBarn?.behandlingstatus ?: Behandlingstatus.UNDER_BEHANDLING
                        }
                    it.tilForholdsmessigFordelingSøknad().copy(
                        status = status,
                    )
                }.toMutableSet()

        return (eksisterendeSøknaderOppdatert + nyeSøknader).toMutableSet()
    }

    @Transactional
    fun leggTilEllerSlettBarnFraBehandlingSomErIFF(request: OppdaterBarnFraFFRequest) {
        val søknad =
            try {
                bbmConsumer.hentSøknad(request.søknadsid)?.søknad
            } catch (e: Exception) {
                LOGGER.error(e) { "Det skjedde en feil ved henting av søknad ${request.søknadsid}" }
                return
            }
        // Skal ikke trigge noe endringer for FF søknad
        if (søknad?.behandlingstype?.erForholdsmessigFordeling == true) {
            return
        }
        val identerSomSkalSlettes = request.rollerSomSkalSlettes.mapNotNull { it.ident?.verdi }
        feilregistrerRevurderingsbarnFraFFSøknad(request.behandling, request.rollerSomSkalLeggesTilDto)
        val relevanteKravhavere = hentAlleRelevanteKravhavere(request.behandling)
        val stønadstypeBeregnet =
            request.stønadstype ?: søknad
                ?.behandlingstema
                ?.tilStønadstype()

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
                } else {
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

    private fun feilregistrerRevurderingsbarnFraFFSøknad(
        behandling: Behandling,
        barn: List<OpprettRolleDto>,
    ) {
        barn
            .filter { it.rolletype == Rolletype.BARN }
            .mapNotNull { nyRolle -> behandling.roller.find { it.ident == nyRolle.ident!!.verdi } }
            .filter { it.forholdsmessigFordeling?.erRevurdering == true }
            .forEach {
                feilregistrerBarnFraFFSøknad(it)
            }
    }

    @Transactional
    fun slettBarnEllerBehandling(
        slettBarn: List<Rolle>,
        behandling: Behandling,
        søknadsid: Long,
    ) {
        if (kanBehandlingSlettes(behandling, slettBarn)) {
            avsluttForholdsmessigFordeling(behandling, slettBarn, søknadsid)
            behandlingService.logiskSlettBehandling(behandling)
        } else {
            slettBarn.forEach { slettBarnFraBehandlingFF(it, behandling, søknadsid) }
            endreHovedsøknadIFFEtterHovedsøknadBleSlettet(behandling, søknadsid)
            behandlingService.sendOppdatertHendelse(behandling.id!!, false)
        }
    }

    private fun endreHovedsøknadIFFEtterHovedsøknadBleSlettet(
        behandling: Behandling,
        søknadSomBleSlettet: Long,
    ) {
        if (!behandling.erIForholdsmessigFordeling ||
            behandling.forholdsmessigFordeling == null || !behandling.forholdsmessigFordeling!!.erHovedbehandling
        ) {
            return
        }

        if (behandling.soknadsid == søknadSomBleSlettet) {
            val søknadsbarnIkkeRevurdering =
                behandling.søknadsbarn
                    .filter { !it.erRevurderingsbarn && it.forholdsmessigFordeling?.søknadsid != null }
            val søknadsidMedFlestBarn =
                søknadsbarnIkkeRevurdering
                    .groupBy { it.forholdsmessigFordeling!!.søknadsid!! }
                    .maxByOrNull { (_, barn) -> barn.size }
                    ?.key
            behandling.soknadsid = søknadsidMedFlestBarn ?: behandling.soknadsid
            LOGGER.info { "Oppdaterer hovedsøknad i behandling ${behandling.id} fra $søknadSomBleSlettet til $søknadsidMedFlestBarn" }
            bbmConsumer.oppdaterHoveddsøknad(behandling.id!!, behandling.soknadsid!!)
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
            barn.innkrevingstype = if (barn.harSøknadMedInnkreving) Innkrevingstype.MED_INNKREVING else Innkrevingstype.UTEN_INNKREVING
            LOGGER.info {
                "Barnet er koblet til flere søknader ${barn.forholdsmessigFordeling!!.søknader}" +
                    " etter den ble slettet fra søknad $søknadsid. Gjør ingen endring. Behandlingid = ${behandling.id}"
            }
            return
        }
        LOGGER.info { "Sletter barn ${barn.ident} fra behandling ${behandling.id} og lager ny revurderingsøknad" }
        barn.forholdsmessigFordeling!!.erRevurdering = true

        val skalOppretteFFSøknadMedInnkreving =
            barn.forholdsmessigFordeling!!.løperBidragEtterDato(
                behandling.finnBeregnTilDato().toYearMonth(),
            )
        val søktFomDato = LocalDate.now().plusMonths(1).withDayOfMonth(1)

        val søknad =
            leggTilEllerOpprettSøknadForRevurderingsbarn(
                behandling,
                barn.ident!!,
                barn.stønadstype,
                barn.forholdsmessigFordeling!!.tilhørerSak,
                søktFomDato,
                skalOppretteFFSøknadMedInnkreving,
            )
        barn.forholdsmessigFordeling!!.søknader.add(søknad)
        // Oppdater virkning og årsak slik at det matcher med revurderingsøknaden
        barn.årsak = VirkningstidspunktÅrsakstype.REVURDERING_MÅNEDEN_ETTER
        barn.innkrevingstype = if (skalOppretteFFSøknadMedInnkreving) Innkrevingstype.MED_INNKREVING else Innkrevingstype.UTEN_INNKREVING
        virkningstidspunktService.oppdaterVirkningstidspunkt(
            barn.id,
            søktFomDato.withDayOfMonth(1),
            behandling,
            forrigeVirkningstidspunkt = behandling.eldsteVirkningstidspunkt,
        )
    }

    private fun leggTilEllerOpprettSøknadForRevurderingsbarn(
        behandling: Behandling,
        barnIdent: String,
        stønadstype: Stønadstype?,
        saksnummer: String,
        søktFomDato: LocalDate,
        medInnkreving: Boolean,
    ): ForholdsmessigFordelingSøknadBarn {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!

        val åpenFFSøknad =
            hentÅpenSøknadFor(
                bidragspliktigFnr,
                behandlingstype = behandling.behandlingstypeForFF,
                medInnkreving = medInnkreving,
                behandlingsid = behandling.id!!,
                saksnummer = saksnummer,
                søktFomDato = søktFomDato,
                stønadstype = stønadstype,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )

        if (åpenFFSøknad != null) {
            if (åpenFFSøknad.barn.none { it.personident == barnIdent }) {
                bbmConsumer.leggTilBarnISøknad(
                    LeggTilBarnIFFSøknadRequest(
                        `åpenFFSøknad`.søknadsid,
                        barnIdent,
                    ),
                )
            }

            return åpenFFSøknad.tilForholdsmessigFordelingSøknad().copy(
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstype = behandling.behandlingstypeForFF,
                behandlingstema = Behandlingstema.BIDRAG,
            )
        } else {
            val søknad =
                bbmConsumer.opprettSøknader(
                    OpprettSøknadRequest(
                        saksnummer = saksnummer,
                        behandlingsid = behandling.id,
                        refVedtaksid = behandling.omgjøringsdetaljer?.omgjørVedtakId,
                        behandlingstype = behandling.behandlingstypeForFF,
                        behandlerenhet = behandling.behandlerEnhet,
                        behandlingstema =
                            if (stønadstype == Stønadstype.BIDRAG18AAR) {
                                Behandlingstema.BIDRAG_18_ÅR
                            } else {
                                Behandlingstema.BIDRAG
                            },
                        søknadFomDato = søktFomDato,
                        innkreving = medInnkreving,
                        barnListe = listOf(Barn(personident = barnIdent)),
                    ),
                )

            return ForholdsmessigFordelingSøknadBarn(
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstype = behandling.behandlingstypeForFF,
                behandlingstema = Behandlingstema.BIDRAG,
                mottattDato = LocalDate.now(),
                søknadFomDato = søktFomDato,
                søknadsid = søknad.søknadsid,
                enhet = behandling.behandlerEnhet,
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
        val bidraggsakerBP = hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!), behandling.finnBeregningsperiode())
        return bidraggsakerBP.any { lb ->
            val sak = sakConsumer.hentSak(lb.sak.verdi)
            val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
            behandling.roller.none { it.erSammeRolle(lb.kravhaver.verdi, lb.type) } ||
                behandling.roller.none { it.ident == bmFødselsnummer }
        }
    }

    private fun sjekkBeregningKreverForholdsmessigFordeling(behandling: Behandling): FFBeregningResultat =
        try {
            val resultat = beregningService.beregneBidrag(behandling, true, simulerBeregning = true)
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
            val lagretLøpendeBidragBarnIdenter = lagretLøpendeBidrag.map { it.gjelderBarnIdent }
            val løpendeBidragBarn =
                grunnlagsliste
                    .mapTilBeregnetBidragDto(løpendeBidrag)
                    // Lagret løpende bidrag så betyr det at det er opprettet FF på grunnlaget av løpende bidraget som ble lagret
                    // Da skal de brukes istedenfor det som kommer fra beregningen.
                    .filter { !lagretLøpendeBidragBarnIdenter.contains(it.barn.ident!!.verdi) }
                    .groupBy { it.barn.ident!!.verdi }
                    .map { (ident, løpendeBidrag) ->
                        LøpendeBidragGrunnlagForholdsmessigFordeling(
                            ident,
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
            if (relevanteKravhavereIkkeSøknadsbarn.isEmpty() && `finnesLøpendeBidragSomOverlapperMedEldsteVirkning`) {
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

    fun hentAlleRelevanteKravhavere(behandling: Behandling): Set<SakKravhaver> {
        val åpneEllerLøpendeSakerBp = hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val sakerUtenLøpendeBidrag =
            hentBarnUtenLøpendeBidrag(behandling, åpneEllerLøpendeSakerBp)

        return åpneEllerLøpendeSakerBp + sakerUtenLøpendeBidrag
    }

    private fun hentBarnUtenLøpendeBidrag(
        behandling: Behandling,
        sakerMedLøpendeBidrag: Set<SakKravhaver>? = null,
    ): List<SakKravhaver> {
        val kravhavereSomHarÅpenBehandling = sakerMedLøpendeBidrag ?: hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val søktFomDatoRevurdering = `kravhavereSomHarÅpenBehandling`.finnSøktFomRevurderingSøknad(behandling)

        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val søknadsbarnIdentStønadstypeMap =
            kravhavereSomHarÅpenBehandling.map { it.kravhaver to it.stønadstype } +
                behandling.søknadsbarn.filter { it.ident != null }.map { it.ident!! to null }

        val sakerBp = hentSakerBp(bidragspliktigFnr)
        val barneSomHarBidragssak = sakerBp.flatMap { it.barn.map { it.fødselsnummer!!.verdi } }
        val privatAvtalerUtenBidragssak =
            behandling.privatAvtale
                .filter {
                    it.rolle == null && !barneSomHarBidragssak.contains(it.personIdent!!)
                }.map {
                    val barnFødselsdato = hentPersonFødselsdato(it.personIdent!!)
                    val dato18ÅrsBidrag = barnFødselsdato!!.tilDato18årsBidrag()
                    val er18EtterSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                    val stønadstype = it.stønadstype ?: if (er18EtterSøktFom) Stønadstype.BIDRAG18AAR else Stønadstype.BIDRAG
                    SakKravhaver(
                        kravhaver = it.personIdent!!,
                        saksnummer = null,
                        løperBidragFra = null,
                        stønadstype = stønadstype,
                        eierfogd = null,
                        bidragsmottaker = null,
                        privatAvtale = it,
                        opphørsdato = behandling.finnOpphørsdato(stønadstype, it.personIdent!!),
                    )
                }
        val barnMedBidragssakSomHarPrivatAvtale =
            sakerBp
                .flatMap { sak ->
                    val barn =
                        sak.roller
                            .filter { it.type == Rolletype.BARN }
                            .filter { it.fødselsnummer != null }
                    barn
                        .flatMap { b ->
                            behandling.privatAvtale
                                .filter { pa ->
                                    pa.rolle == null &&
                                        pa.personIdent == b.fødselsnummer?.verdi
                                }.map { privatAvtale ->
                                    val stønaderMedÅpenBehandling =
                                        søknadsbarnIdentStønadstypeMap
                                            .filter {
                                                it.first == b.fødselsnummer!!.verdi
                                            }.map { it.second }
                                            .distinct()
                                    val løpendeBidrag =
                                        kravhavereSomHarÅpenBehandling.find { lb ->
                                            lb.kravhaver == b.fødselsnummer!!.verdi &&
                                                !stønaderMedÅpenBehandling.contains(lb.stønadstype)
                                        }
                                    val barnFødselsdato = hentPersonFødselsdato(b.fødselsnummer!!.verdi)
                                    val dato18ÅrsBidrag = barnFødselsdato!!.plusYears(18).plusMonths(1).withDayOfMonth(1)
                                    val er18EtterSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                                    val privatAvtalePerioder = privatAvtale?.perioder ?: emptySet()
                                    val førstePeriodePrivatAvtale = privatAvtalePerioder.minByOrNull { it.fom }

                                    // Hvis privat avtalen er før 18 års dagen så antas det at det er en ordniær bidrag ellers 18 års bidrag
                                    val stønadstypeBeregnet =
                                        when {
                                            privatAvtale.stønadstype != null -> privatAvtale.stønadstype

                                            førstePeriodePrivatAvtale != null && førstePeriodePrivatAvtale.fom < dato18ÅrsBidrag
                                            -> Stønadstype.BIDRAG

                                            førstePeriodePrivatAvtale != null &&
                                                førstePeriodePrivatAvtale.fom >= dato18ÅrsBidrag -> Stønadstype.BIDRAG18AAR

                                            er18EtterSøktFom -> Stønadstype.BIDRAG18AAR

                                            else -> Stønadstype.BIDRAG
                                        }

                                    // Hvis barnet har åpen søknad med samme stønadstype fra før så unngå å lage en ny FF søknad
                                    if (stønaderMedÅpenBehandling.contains(stønadstypeBeregnet)) return@map null

                                    SakKravhaver(
                                        kravhaver = b.fødselsnummer!!.verdi,
                                        saksnummer = sak.saksnummer.verdi,
                                        løperBidragFra = løpendeBidrag?.løperBidragFra,
                                        løperBidragTil = løpendeBidrag?.løperBidragTil,
                                        stønadstype = stønadstypeBeregnet,
                                        eierfogd = sak.eierfogd.verdi,
                                        bidragsmottaker = sak.bidragsmottaker?.fødselsnummer?.verdi,
                                        privatAvtale = privatAvtale,
                                        opphørsdato = behandling.finnOpphørsdato(stønadstypeBeregnet!!, b.fødselsnummer!!.verdi),
                                    )
                                }
                        }.filterNotNull()
                }.filter { barn -> barn.privatAvtale != null }

        return privatAvtalerUtenBidragssak + barnMedBidragssakSomHarPrivatAvtale
    }

    fun overførÅpneBisysSøknaderTilBehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = relevanteKravhavere.flatMap { it.åpneSøknader }
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr), behandling.finnBeregningsperiode())
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
                        erRevurdering = åpenSøknad.behandlingstype == behandling.behandlingstypeForFF,
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
                    val løpendeBidrag =
                        løpendeBidraggsakerBP.find {
                            erSammePerson(it.kravhaver.verdi, it.type, barn.personident, åpenSøknad.behandlingstema.tilStønadstype())
                        }
                    val åpneSøknaderRolle =
                        åpneSøknader
                            .filter { ås ->
                                ås.barn.any {
                                    erSammePerson(
                                        it.personident,
                                        ås.behandlingstema.tilStønadstype(),
                                        barn.personident,
                                        åpenSøknad.behandlingstema.tilStønadstype(),
                                    )
                                }
                            }
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
                        opphørsdato = if (åpenSøknad.innkreving) løpendeBidrag?.periodeTil else null,
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

    private fun hentÅpenSøknadFor(
        bidragspliktigFnr: String,
        behandlingstype: Behandlingstype,
        medInnkreving: Boolean,
        behandlingsid: Long,
        saksnummer: String,
        søktFomDato: LocalDate,
        stønadstype: Stønadstype?,
        omgjøringsdetaljer: Omgjøringsdetaljer?,
        erKlageEllerOmgjøring: Boolean = omgjøringsdetaljer != null,
    ) = hentÅpneSøknader(bidragspliktigFnr, behandlingstype, omgjøringsdetaljer, erKlageEllerOmgjøring).find {
        (it.innkreving == medInnkreving) &&
            it.behandlingsid == behandlingsid &&
            it.saksnummer == saksnummer &&
            it.søknadFomDato == søktFomDato && it.behandlingstema.tilStønadstype() == stønadstype
    }

    private fun hentÅpneSøknader(
        bidragspliktigFnr: String,
        behandlingstypeForFF: Behandlingstype,
        omgjøringsdetaljer: Omgjøringsdetaljer?,
        erKlageEllerOmgjøring: Boolean = omgjøringsdetaljer != null,
    ) = bbmConsumer
        .hentÅpneSøknaderForBp(bidragspliktigFnr)
        .åpneSøknader
        .filter { !behandlingstyperSomIkkeSkalInkluderesIFF.contains(it.behandlingstype) }
        .filter {
            (erKlageEllerOmgjøring && it.behandlingstype.erKlageEllerOmgjøring) ||
                !it.behandlingstype.erKlageEllerOmgjøring
        }.filter {
            (
                erKlageEllerOmgjøring &&
                    // TODO: Dette fungerer ikke nå pga BBM ikke returnerer ref
                    // TODO: Løses med refsøknader

                    (
                        (it.refVedtaksid == null || it.refVedtaksid == omgjøringsdetaljer?.omgjørVedtakId) ||
                            (it.refSøknadsid == null || it.refSøknadsid == omgjøringsdetaljer?.soknadRefId)
                    )
            ) ||
                !erKlageEllerOmgjøring
        }.sortedWith(
            compareByDescending<ÅpenSøknadDto> { it.behandlingstype == behandlingstypeForFF }
                .thenBy { it.søknadFomDato },
        )

    private fun Behandling.syncGebyrSøknadReferanse() {
        roller.forEach { rolle ->
            rolle.hentEllerOpprettGebyr().gebyrSøknader.forEach { gebyrSøknad ->
                if (gebyrSøknad.referanse.isNullOrEmpty()) {
                    val søknad = bbmConsumer.hentSøknad(gebyrSøknad.søknadsid)
                    gebyrSøknad.referanse =
                        søknad
                            ?.søknad
                            ?.partISøknadListe
                            ?.find { rolle.ident == it.personident }
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
        val eksisterendeRoller = behandling.roller.map { it.ident }
        val åpneBehandlinger = relevanteKravhavere.flatMap { it.åpneBehandlinger }
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr), behandling.finnBeregningsperiode())
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
                val eksisterendeRolle = behandling.roller.find { barn -> barn.erSammeRolle(rolle) }
                if (eksisterendeRolle == null) {
                    val behandlingerRolle = åpneBehandlinger.filter { it.søknadsbarn.any { it.erSammeRolle(rolle) } }
                    behandling.roller.add(
                        rolle.kopierRolle(behandling, null, åpneBehandlinger = behandlingerRolle),
                    )
                } else if (eksisterendeRolle.harGebyrsøknad || eksisterendeRolle.gebyr != null) {
                    eksisterendeRolle.leggTilGebyr(rolle)
                }
            }
            val bm = behandlingOverført.bidragsmottaker?.ident
            behandlingOverført.søknadsbarn.forEach { rolle ->
                val eksisterendeRolle = behandling.søknadsbarn.find { barn -> barn.erSammeRolle(rolle.ident!!, rolle.stønadstype) }
                if (eksisterendeRolle == null) {
                    val løpendeBidrag = løpendeBidraggsakerBP.find { rolle.erSammeRolle(it.kravhaver.verdi, it.type) }
                    val innkrevesFra = if (behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING) løpendeBidrag?.periodeFra else null
                    val innkrevesTil = if (behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING) løpendeBidrag?.periodeTil else null
                    val behandlingerRolle =
                        åpneBehandlinger.filter {
                            it.søknadsbarn.any {
                                it.erSammeRolle(
                                    rolle.ident!!,
                                    rolle.stønadstype,
                                )
                            }
                        }
                    behandling.roller.add(
                        rolle.kopierRolle(
                            behandling,
                            bm,
                            innkrevesFra,
                            innkrevesTil,
                            behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                            behandlingerRolle,
                        ),
                    )
                } else if (eksisterendeRolle.harGebyrsøknad || eksisterendeRolle.gebyr != null) {
                    eksisterendeRolle.leggTilGebyr(rolle)
                }
            }

            behandlingOverført.samvær.forEach { samværOverført ->
                if (behandling.samvær.none { s -> s.rolle.erSammeRolle(samværOverført.rolle) }) {
                    kopierSamvær(behandling, samværOverført)
                }
            }
            behandlingOverført.underholdskostnader.forEach { underholdskostnadOverført ->
                if (behandling.underholdskostnader.none { u ->
                        u.tilhørerPerson(underholdskostnadOverført.personIdent!!, underholdskostnadOverført.rolle?.stønadstype)
                    }
                ) {
                    underholdskostnadOverført.kopierUnderholdskostnad(behandling)
                }
            }

            // Overfør alle inntektene til BM/Barn som ikke finnes i opprinnelig behandling
            behandlingOverført.inntekter
                .filter { !eksisterendeRoller.contains(it.gjelderIdent) }
                .forEach { inntektOverført ->
                    kopierInntekt(behandling, inntektOverført)
                }

            // Overfør alle inntektene til BP/BM/Barn som ikke finnes i opprinnelig behandling
            behandlingOverført.roller
                .filter { eksisterendeRoller.contains(it.ident) }
                .forEach {
                    kopierOverInntekterForRolleFraBehandling(it, behandling, behandlingOverført)
                }

            behandlingOverført.roller.forEach {
                kopierOverBegrunnelseForBehandling(
                    it,
                    behandlingOverført,
                    behandling,
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                )
                kopierOverBegrunnelseForBehandling(
                    it,
                    behandlingOverført,
                    behandling,
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                )
                kopierOverBegrunnelseForBehandling(
                    it,
                    behandlingOverført,
                    behandling,
                    NotatGrunnlag.NotatType.INNTEKT,
                )
                if (it.rolletype == Rolletype.BARN) {
                    kopierOverBegrunnelseForBehandling(
                        it,
                        behandlingOverført,
                        behandling,
                        NotatGrunnlag.NotatType.PRIVAT_AVTALE,
                    )
                }
            }

            behandlingOverført.grunnlag
                .filter {
                    it.rolle.ident != bidragspliktigFnr &&
                        behandling.roller.any { r -> r.erSammeRolle(it.rolle) }
                }.forEach {
                    behandling.grunnlag.add(
                        it.kopierGrunnlag(behandling),
                    )
                }
            behandlingOverført.privatAvtale.filter { it.rolle != null }.forEach { privatAvtaleOverfort ->
                kopierPrivatAvtale(behandling, privatAvtaleOverfort)
            }
            behandlingOverført.husstandsmedlem.forEach { husstandsmedlemOverfort ->
                // Hvis rollen har en rolle i behandlingen eller tilhører en rolle i behandlingen så skal det overføres
                if (husstandsmedlemOverfort.rolle != null || behandling.roller.any { it.ident == husstandsmedlemOverfort.ident }) {
                    kopierHusstandsmedlem(behandling, husstandsmedlemOverfort)
                }
            }
            kopierOverBegrunnelseForBehandling(
                behandling.bidragspliktig!!,
                behandlingOverført,
                behandling,
                NotatGrunnlag.NotatType.BOFORHOLD,
            )

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
        `løpendeBidragssak`: List<SakKravhaver>,
        behandlerEnhet: String,
        `stønadstype`: Stønadstype? = null,
        `søktFomDato`: LocalDate,
        erOppdateringAvBehandlingSomErIFF: Boolean,
    ) {
        val sak = sakConsumer.hentSak(saksnummer)

        val barnUtenSøknader =
            løpendeBidragssak.filter { ls ->
                behandling.søknadsbarn.none {
                    it.erSammeRolle(ls.kravhaver, ls.stønadstype) &&
                        (it.forholdsmessigFordeling?.søknaderUnderBehandling?.isNotEmpty() == true)
                }
            }
        if (barnUtenSøknader.isEmpty()) return

        val bmFødselsnummer = hentNyesteIdent(sak.bidragsmottaker?.fødselsnummer?.verdi)?.verdi

        val barnMedInnkrevingSenereEnnFomDato =
            barnUtenSøknader
                .filter {
                    !it.løperBidragEtterDato(søktFomDato.toYearMonth())
                }.groupBy { it.løperBidragFra }
                .map { (_, barn) ->
                    val søknadsid =
                        if (erOppdateringAvBehandlingSomErIFF) {
                            val søknader =
                                barn.map {
                                    leggTilEllerOpprettSøknadForRevurderingsbarn(
                                        barnIdent = it.kravhaver,
                                        saksnummer = saksnummer,
                                        behandling = behandling,
                                        stønadstype = stønadstype,
                                        søktFomDato = søktFomDato,
                                        medInnkreving = false,
                                    )
                                }
                            // Antar at alle barn havner i samme søknad
                            val søknadsid = søknader.first().søknadsid
                            opprettForsendelseForNySøknad(saksnummer, behandling, bmFødselsnummer!!, søknadsid.toString())
                            søknadsid
                        } else {
                            opprettSøknad(
                                behandling.bidragspliktig!!.ident!!,
                                barn,
                                saksnummer,
                                behandling,
                                behandlerEnhet,
                                stønadstype,
                                søktFomDato,
                                false,
                                bmFødselsnummer!!,
                            )
                        }
                    Pair(søknadsid, barn.map { it.kravhaver })
                }

        val barnMedInnkreving = barnUtenSøknader.filter { it.løperBidragEtterDato(søktFomDato.toYearMonth()) }
        val søknadsid =
            if (barnMedInnkreving.isNotEmpty()) {
                if (erOppdateringAvBehandlingSomErIFF) {
                    val søknader =
                        barnMedInnkreving.map {
                            leggTilEllerOpprettSøknadForRevurderingsbarn(
                                barnIdent = it.kravhaver,
                                saksnummer = saksnummer,
                                behandling = behandling,
                                stønadstype = stønadstype,
                                søktFomDato = søktFomDato,
                                medInnkreving = true,
                            )
                        }
                    // Antar at alle barn havner i samme søknad
                    val søknadsid = søknader.first().søknadsid
                    opprettForsendelseForNySøknad(saksnummer, behandling, bmFødselsnummer!!, søknadsid.toString())
                    søknadsid
                } else {
                    opprettSøknad(
                        behandling.bidragspliktig!!.ident!!,
                        barnMedInnkreving,
                        saksnummer,
                        behandling,
                        behandlerEnhet,
                        stønadstype,
                        søktFomDato,
                        true,
                        bmFødselsnummer!!,
                    )
                }
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
                behandlingstype = behandling.behandlingstypeForFF,
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

        val nyeRollerBarn = mutableSetOf<Rolle>()
        barnUtenSøknader.forEach { søknad ->
            val søknadsIdUtenInnkreving =
                barnMedInnkrevingSenereEnnFomDato
                    .find { b ->
                        b.second.any { it == søknad.kravhaver }
                    }?.first
            val skalInnkreves =
                barnUtenSøknader
                    .hentForKravhaver(
                        søknad.kravhaver,
                        søknad.stønadstype,
                    )?.løperBidragEtterDato(søktFomDato.toYearMonth()) == true
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
                    innkrevesFraDato = if (skalInnkreves) søknad.løperBidragFra else null,
                    medInnkreving = skalInnkreves,
                    opphørsdato = søknad.løperBidragTil ?: søknad.opphørsdato,
                    ffDetaljer =
                        ffDetaljer.copy(
                            løperBidragFra = søknad.løperBidragFra,
                            løperBidragTil = søknad.løperBidragTil,
                            søknader = søknader.toMutableSet(),
                        ),
                )
            nyeRollerBarn.add(rolle)
            if (søknad.privatAvtale != null) {
                søknad.privatAvtale.rolle = rolle
                søknad.privatAvtale.person = null
            }
            // Hvis det var en eksisterende rolle
            if (rolle.id != null) {
                virkningstidspunktService.oppdaterVirkningstidspunkt(
                    rolle.id,
                    søktFomDato.withDayOfMonth(1),
                    behandling,
                    true,
                    rekalkulerOpplysningerVedEndring = false,
                )
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
        bidragspliktigFnr: String,
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
        val eksisterendeSøknad =
            hentÅpenSøknadFor(
                bidragspliktigFnr = bidragspliktigFnr,
                behandlingstype = behandling.behandlingstypeForFF,
                medInnkreving = medInnkreving,
                behandlingsid = behandling.id!!,
                saksnummer = saksnummer,
                søktFomDato = søktFomDato,
                stønadstype = stønadstype,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )
        if (eksisterendeSøknad != null) {
            val søknadBarnIdenter = eksisterendeSøknad.barn.map { eksisterendeSøknad.tilIdentStønadstypeNøkkel(it.personident!!) }
            barnUtenSøknader
                .filter { !søknadBarnIdenter.contains(it.distinctKey) }
                .forEach {
                    bbmConsumer.leggTilBarnISøknad(
                        LeggTilBarnIFFSøknadRequest(
                            personidentBarn = it.kravhaver,
                            søknadsid = eksisterendeSøknad.søknadsid,
                        ),
                    )
                }
            return eksisterendeSøknad.søknadsid
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
                    behandlingstype = behandling.behandlingstypeForFF,
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

    private fun hentSisteLøpendeStønader(
        bpIdent: Personident,
        periode: ÅrMånedsperiode,
    ): List<LøpendeBidragSakPeriode> =
        beløpshistorikkConsumer
            .hentAlleLøpendeStønaderIPeriode(
                LøpendeBidragPeriodeRequest(skyldner = bpIdent, periode = periode),
            ).filtrerForPeriode(periode.copy(til = null))
            .map { sak ->
                LøpendeBidragSakPeriode(
                    sak = sak.sak,
                    kravhaver = sak.kravhaver,
                    type = sak.type,
                    valutakode = sak.periodeListe.firstOrNull()?.valutakode ?: Valutakode.NOK.name,
                    periodeFra = sak.periodeListe.minOf { it.periode.fom },
                    periodeTil =
                        sak.periodeListe
                            .maxBy { it.periode.fom }
                            .periode.til,
                )
            }

    fun hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling: Behandling): Set<SakKravhaver> {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr), behandling.finnBeregningsperiode())
        val åpneBehandlinger =
            behandlingRepository
                .finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
                .filter {
                    it.søknadstype != null && !behandlingstyperSomIkkeSkalInkluderesIFF.contains(it.søknadstype)
                }.filter {
                    (
                        it.vedtakstype == Vedtakstype.KLAGE && behandling.erKlageEllerOmgjøring && (
                            it.omgjøringsdetaljer?.omgjørVedtakId == behandling.omgjøringsdetaljer?.omgjørVedtakId ||
                                it.omgjøringsdetaljer?.soknadRefId == behandling.omgjøringsdetaljer?.soknadRefId
                        )
                    ) ||
                        it.vedtakstype != Vedtakstype.KLAGE
                }
        val åpneSøknader =
            hentÅpneSøknader(
                bidragspliktigFnr,
                behandling.behandlingstypeForFF,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )

        val sakKravhaverListe = mutableSetOf<SakKravhaver>()

        åpneBehandlinger.forEach { behandling ->
            behandling.søknadsbarn.forEach { barn ->
                val stønadstype = barn.stønadstype ?: behandling.stonadstype
                val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barn.ident && it.type == stønadstype }
                val eksisterende = sakKravhaverListe.hentForKravhaver(barn.ident!!, barn.stønadstype)
                if (eksisterende != null) {
                    eksisterende.åpneBehandlinger.add(behandling)
                } else {
                    sakKravhaverListe.add(
                        SakKravhaver(
                            saksnummer = behandling.saksnummer,
                            kravhaver = barn.ident!!,
                            stønadstype = stønadstype,
                            åpneBehandlinger = mutableSetOf(behandling),
                            eierfogd = behandling.behandlerEnhet,
                            løperBidragFra = løpendeBidrag?.periodeFra,
                            løperBidragTil = løpendeBidrag?.periodeTil,
                            opphørsdato = barn.opphørsdato?.toYearMonth(),
                            privatAvtale = behandling.privatAvtale.find { it.gjelderPerson(barn.ident!!, stønadstype) },
                        ),
                    )
                }
            }
        }

        åpneSøknader
            .filter { it.behandlingsid != behandling.id }
            .filter { søknad ->
                søknad.behandlingsid == null ||
                    (
                        sakKravhaverListe.none {
                            it.åpneBehandlinger.any { it.id == søknad.behandlingsid || it.soknadsid == søknad.søknadsid }
                        } &&
                            !behandlingRepository.erIForholdsmessigFordeling(søknad.behandlingsid!!)
                    )
            }.forEach { åpenSøknad ->
                åpenSøknad.partISøknadListe
                    .filter { it.rolletype == Rolletype.BARN }
                    .forEach { barnFnr ->
                        val stønadstype = åpenSøknad.behandlingstema.tilStønadstype()
                        val løpendeBidrag =
                            løpendeBidraggsakerBP.hentBidragSakForKravhaver(barnFnr.personident!!, stønadstype)
                        val eksisterende =
                            sakKravhaverListe.hentForKravhaver(
                                barnFnr.personident!!,
                                stønadstype,
                            )
                        if (eksisterende != null) {
                            eksisterende.åpneSøknader.add(åpenSøknad)
                        } else {
                            sakKravhaverListe.add(
                                SakKravhaver(
                                    åpenSøknad.saksnummer,
                                    kravhaver = barnFnr.personident!!,
                                    eierfogd = åpenSøknad.behandlerenhet,
                                    løperBidragFra = løpendeBidrag?.periodeFra,
                                    løperBidragTil = løpendeBidrag?.periodeTil,
                                    stønadstype = stønadstype,
                                    opphørsdato = behandling.finnOpphørsdato(stønadstype!!, barnFnr.personident!!),
                                    åpneSøknader = mutableSetOf(åpenSøknad),
                                    privatAvtale = behandling.privatAvtale.find { it.gjelderPerson(barnFnr.personident!!, stønadstype) },
                                ),
                            )
                        }
                    }
            }

        val krahaverFraÅpneSaker = sakKravhaverListe.map { it.kravhaver to it.stønadstype }

        val løpendeBidragsaker =
            løpendeBidraggsakerBP
                .filter { lb -> !krahaverFraÅpneSaker.finnes(lb.kravhaver.verdi, lb.type) }
                .map {
                    SakKravhaver(
                        saksnummer = it.sak.verdi,
                        kravhaver = it.kravhaver.verdi,
                        stønadstype = it.type,
                        løperBidragFra = it.periodeFra,
                        løperBidragTil = it.periodeTil,
                        opphørsdato = behandling.finnOpphørsdato(it.type, it.kravhaver.verdi),
                        privatAvtale = behandling.privatAvtale.find { pa -> pa.gjelderPerson(it.kravhaver.verdi, it.type) },
                    )
                }.distinctBy { it.distinctKey }
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
            }.distinctBy { it.saksnummer to it.distinctKey }
            .toSet()
    }

    private fun Behandling.finnOpphørsdato(
        stønadstype: Stønadstype,
        barnFnr: String,
    ): YearMonth? {
        val fødselsdato = hentPersonFødselsdato(barnFnr)
        return if (fødselsdato != null && stønadstype == Stønadstype.BIDRAG) {
            fødselsdato.tilDato18årsBidrag().takeIf { it <= finnBeregnTilDato() }?.toYearMonth()
        } else {
            null
        }
    }
}

fun LøpendeBidragPeriodeResponse.filtrerForPeriode(beregningsperiode: ÅrMånedsperiode): List<LøpendeBidrag> =
    // Fjerner perioder som ikke overlapper med beregningsperioden
    bidragListe.mapNotNull { bidrag ->
        val beregningsperiodeTil = beregningsperiode.til
        val periodeListe =
            bidrag.periodeListe
                .filter {
                    it.periode.overlapper(beregningsperiode) &&
                        it.periode.fom != beregningsperiode.til &&
                        it.periode.til != beregningsperiode.fom
                }.map { periode ->
                    // Justerer periode.til til beregningsperiode.til hvis til er null eller etter beregningsperiode.til
                    val periodeTil = periode.periode.til
                    val justerTil = beregningsperiodeTil != null && (periodeTil == null || periodeTil.isAfter(beregningsperiodeTil))

                    // Justerer periode.fom til beregningsperiode.fom hvis fom er før beregningsperiode.fom
                    val justerFom = periode.periode.fom.isBefore(beregningsperiode.fom)

                    if (justerFom || justerTil) {
                        val nyFom = if (justerFom) beregningsperiode.fom else periode.periode.fom
                        val nyTil = if (justerTil) beregningsperiodeTil else periodeTil
                        periode.copy(periode = periode.periode.copy(fom = nyFom, til = nyTil))
                    } else {
                        periode
                    }
                }
        if (periodeListe.isNotEmpty()) {
            LøpendeBidrag(
                sak = bidrag.sak,
                type = bidrag.type,
                kravhaver = bidrag.kravhaver,
                mottaker = bidrag.mottaker,
                periodeListe = periodeListe,
            )
        } else {
            null
        }
    }
