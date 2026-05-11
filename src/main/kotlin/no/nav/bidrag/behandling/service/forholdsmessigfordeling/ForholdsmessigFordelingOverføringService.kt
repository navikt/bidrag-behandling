package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.leggTilGebyr
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.behandling.erSammePerson
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregningsperiode
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlerenhetRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.sak.OpprettMidlertidligTilgangRequest

private val OVERFØRING_LOGGER = KotlinLogging.logger {}

/**
 * Service for overføring av åpne behandlinger og søknader til en FF-hovedbehandling.
 * Håndterer kopiering av roller, inntekter, samvær, underholdskostnader, grunnlag etc.
 */
class ForholdsmessigFordelingOverføringService(
    private val bbmConsumer: BidragBBMConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val behandlingService: BehandlingService,
    private val behandlingRepository: BehandlingRepository,
    private val kravhaverService: ForholdsmessigFordelingKravhaverService,
) {
    /**
     * Gir sak tilgang til en enhet og oppdaterer behandlerEnhet hvis den er ulik.
     */
    fun giSakTilgangTilEnhet(
        behandling: Behandling,
        behandlerEnhet: String,
    ) {
        if (behandlerEnhet == behandling.behandlerEnhet) return
        behandling.behandlerEnhet = behandlerEnhet
        oppdaterSakOgSøknadBehandlerEnhet(behandling.saksnummer, behandling.soknadsid!!, behandlerEnhet)
    }

    /**
     * Oppdaterer midlertidig tilgang for sak og behandlerenhet for søknad i BBM.
     */
    fun oppdaterSakOgSøknadBehandlerEnhet(
        saksnummer: String,
        søknadsid: Long,
        tilgangTilEnhet: String,
    ) {
        sakConsumer.opprettMidlertidligTilgang(OpprettMidlertidligTilgangRequest(saksnummer, tilgangTilEnhet))
        bbmConsumer.lagreBehandlerEnhet(OppdaterBehandlerenhetRequest(søknadsid, tilgangTilEnhet))
    }

    /**
     * Overfører åpne behandlinger og søknader for søknadsbarn til hovedbehandlingen.
     */
    fun overførÅpneBehandlingerOgSøknaderSøknadsbarn(
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
                OVERFØRING_LOGGER.warn {
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

    /**
     * Overfører alle åpne Bisys-søknader til hovedbehandlingen.
     * Oppretter roller for BM og barn, og kopierer gebyr-informasjon.
     */
    fun overførÅpneBisysSøknaderTilBehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = relevanteKravhavere.flatMap { it.åpneSøknader }
        val løpendeBidraggsakerBP =
            kravhaverService.hentSisteLøpendeStønader(
                Personident(bidragspliktigFnr),
                behandling.finnBeregningsperiode(),
            )
        åpneSøknader
            .forEach { åpenSøknad ->
                overførEnkeltBisysSøknad(åpenSøknad, behandling, åpneSøknader, løpendeBidraggsakerBP)
            }
    }

    private fun overførEnkeltBisysSøknad(
        åpenSøknad: no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad,
        behandling: Behandling,
        åpneSøknader: List<no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad>,
        løpendeBidraggsakerBP: List<LøpendeBidragSakPeriode>,
    ) {
        if (åpenSøknad.behandlingsid != null) {
            OVERFØRING_LOGGER.warn {
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

        OVERFØRING_LOGGER.info {
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
            opprettEllerOppdaterRolle(
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
            opprettEllerOppdaterRolle(
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

    /**
     * Overfører alle åpne behandlinger til hovedbehandlingen.
     * Kopierer roller, inntekter, samvær, underholdskostnader, grunnlag, husstandsmedlem, privat avtale og notater.
     * @return liste med id-er til de overførte behandlingene
     */
    fun overførÅpneBehandlingTilHovedbehandling(
        behandling: Behandling,
        relevanteKravhavere: Set<SakKravhaver>,
    ): List<Long> {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val eksisterendeRoller = behandling.roller.map { it.ident }
        val åpneBehandlinger = relevanteKravhavere.flatMap { it.åpneBehandlinger }
        val løpendeBidraggsakerBP =
            kravhaverService.hentSisteLøpendeStønader(
                Personident(bidragspliktigFnr),
                behandling.finnBeregningsperiode(),
            )
        åpneBehandlinger.forEach { behandlingOverført ->
            if (behandlingOverført.forholdsmessigFordeling?.behandlesAvBehandling == behandling.id) return@forEach
            OVERFØRING_LOGGER.info {
                "Overfører behandling ${behandlingOverført.id} til behandling ${behandling.id} etter FF ble opprettet for behandlingen"
            }
            behandlingOverført.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    behandlesAvBehandling = behandling.id,
                )
            mergeBehandlingIntoHovedbehandling(
                behandling = behandling,
                behandlingOverført = behandlingOverført,
                åpneBehandlinger = åpneBehandlinger,
                eksisterendeRoller = eksisterendeRoller,
                løpendeBidraggsakerBP = løpendeBidraggsakerBP,
                bidragspliktigFnr = bidragspliktigFnr,
            )

            bbmConsumer.lagreBehandlingsid(
                OppdaterBehandlingsidRequest(behandlingOverført.soknadsid!!, behandlingOverført.id, behandling.id!!),
            )
            giSakTilgangTilEnhet(behandlingOverført, behandling.behandlerEnhet)
            behandlingService.lagreBehandling(behandlingOverført)
        }
        return åpneBehandlinger.map { it.id!! }
    }

    /**
     * Kopierer alle relevante data fra en overført behandling til hovedbehandlingen.
     */
    private fun mergeBehandlingIntoHovedbehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
        åpneBehandlinger: List<Behandling>,
        eksisterendeRoller: List<String?>,
        løpendeBidraggsakerBP: List<LøpendeBidragSakPeriode>,
        bidragspliktigFnr: String,
    ) {
        kopierRollerFraBehandling(behandling, behandlingOverført, åpneBehandlinger, løpendeBidraggsakerBP)
        kopierSamværFraBehandling(behandling, behandlingOverført)
        kopierUnderholdFraBehandling(behandling, behandlingOverført)
        kopierInntekterFraBehandling(behandling, behandlingOverført, eksisterendeRoller)
        kopierBegrunnelserFraBehandling(behandling, behandlingOverført)
        kopierGrunnlagFraBehandling(behandling, behandlingOverført, bidragspliktigFnr)
        kopierPrivatAvtaleFraBehandling(behandling, behandlingOverført)
        kopierHusstandsmedlemFraBehandling(behandling, behandlingOverført)

        kopierOverBegrunnelseForBehandling(
            behandling.bidragspliktig!!,
            behandlingOverført,
            behandling,
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
    }

    private fun kopierRollerFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
        åpneBehandlinger: List<Behandling>,
        løpendeBidraggsakerBP: List<LøpendeBidragSakPeriode>,
    ) {
        behandlingOverført.bidragsmottaker?.let { rolle ->
            val eksisterendeRolle = behandling.roller.find { rolleBehandling -> rolleBehandling.erSammeRolle(rolle) }
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
    }

    private fun kopierSamværFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
    ) {
        behandlingOverført.samvær.forEach { samværOverført ->
            if (behandling.samvær.none { s -> s.rolle.erSammeRolle(samværOverført.rolle) }) {
                kopierSamvær(behandling, samværOverført)
            }
        }
    }

    private fun kopierUnderholdFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
    ) {
        behandlingOverført.underholdskostnader.forEach { underholdskostnadOverført ->
            if (behandling.underholdskostnader.none { u ->
                    u.tilhørerPerson(underholdskostnadOverført.personIdent!!, underholdskostnadOverført.rolle?.stønadstype)
                }
            ) {
                underholdskostnadOverført.kopierUnderholdskostnad(behandling)
            }
        }
    }

    private fun kopierInntekterFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
        eksisterendeRoller: List<String?>,
    ) {
        // Overfør alle inntektene til BM/Barn som ikke finnes i opprinnelig behandling
        behandlingOverført.inntekter
            .filter { !eksisterendeRoller.contains(it.gjelderIdent) }
            .forEach { inntektOverført ->
                kopierInntekt(behandling, inntektOverført)
            }

        // Overfør alle inntektene til BP/BM/Barn som finnes i opprinnelig behandling
        behandlingOverført.roller
            .filter { eksisterendeRoller.contains(it.ident) }
            .forEach {
                kopierOverInntekterForRolleFraBehandling(it, behandling, behandlingOverført)
            }
    }

    private fun kopierBegrunnelserFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
    ) {
        val notatTyperAlleRoller =
            listOf(
                NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                NotatGrunnlag.NotatType.INNTEKT,
            )
        behandlingOverført.roller.forEach { rolle ->
            notatTyperAlleRoller.forEach { notatType ->
                kopierOverBegrunnelseForBehandling(rolle, behandlingOverført, behandling, notatType)
            }
            if (rolle.rolletype == Rolletype.BARN) {
                kopierOverBegrunnelseForBehandling(
                    rolle,
                    behandlingOverført,
                    behandling,
                    NotatGrunnlag.NotatType.PRIVAT_AVTALE,
                )
            }
        }
    }

    private fun kopierGrunnlagFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
        bidragspliktigFnr: String,
    ) {
        behandlingOverført.grunnlag
            .filter {
                it.rolle.ident != bidragspliktigFnr &&
                    behandling.roller.any { r -> r.erSammeRolle(it.rolle) }
            }.forEach {
                behandling.grunnlag.add(
                    it.kopierGrunnlag(behandling),
                )
            }
    }

    private fun kopierPrivatAvtaleFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
    ) {
        behandlingOverført.privatAvtale.filter { it.rolle != null }.forEach { privatAvtaleOverfort ->
            kopierPrivatAvtale(behandling, privatAvtaleOverfort)
        }
    }

    private fun kopierHusstandsmedlemFraBehandling(
        behandling: Behandling,
        behandlingOverført: Behandling,
    ) {
        behandlingOverført.husstandsmedlem.forEach { husstandsmedlemOverfort ->
            if (husstandsmedlemOverfort.rolle != null || behandling.roller.any { it.ident == husstandsmedlemOverfort.ident }) {
                kopierHusstandsmedlem(behandling, husstandsmedlemOverfort)
            }
        }
    }
}
