package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.dto.ÅpenSøknadDto
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.extensions.hentDefaultÅrsak
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingBarnDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingÅpenBehandlingDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.plus

internal data class SakKravhaver(
    val saksnummer: String,
    val kravhaver: String,
    val åpenSøknad: ÅpenSøknadDto? = null,
    val åpenBehandling: Behandling? = null,
)

@Service
class ForholdsmessigFordelingService(
    private val sakConsumer: BidragSakConsumer,
    private val behandlingRepository: BehandlingRepository,
    private val behandlingService: BehandlingService,
    private val beløpshistorikkConsumer: BidragBeløpshistorikkConsumer,
    private val grunnlagService: GrunnlagService,
    private val bbmConsumer: BidragBBMConsumer,
) {
    @Transactional
    fun opprettForholdsmessigFordeling(behandlingId: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        behandlingRepository.finnHovedbehandlingForBpVedFF(behandling.bidragspliktig!!.ident!!)?.apply {
            ugyldigForespørsel(
                "Det finnes allerede en åpen behandling med forholdsmessig fordeling for bidragspliktig ${behandling.bidragspliktig!!.ident}",
            )
        }
        overførÅpneBehandlingTilHovedbehandling(behandling)
        overførÅpneBisysSøknaderTilBehandling(behandling)
        val bidraggsakerBP = hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!))
        bidraggsakerBP.forEach {
            opprettRollerForSak(behandling, it)
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
                        behandlerEnhet = Enhetsnummer(behandling.behandlerEnhet),
                    )
            }
        }

        opprettSamværOgUnderholdForBarn(behandling)
        behandlingService.lagreBehandling(behandling)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
    }

    private fun opprettSamværOgUnderholdForBarn(behandling: Behandling) {
        behandling.søknadsbarn.forEach {
            if (behandling.samvær.none { s -> s.rolle.ident == it.ident }) {
                behandling.samvær.add(
                    Samvær(
                        rolle = it,
                        behandling = behandling,
                    ),
                )
            }
        }
        behandling.søknadsbarn.forEach {
            if (behandling.underholdskostnader.none { s -> s.rolle?.ident == it.ident }) {
                behandling.underholdskostnader.add(
                    Underholdskostnad(
                        rolle = it,
                        behandling = behandling,
                    ),
                )
            }
        }
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

        if (behandling.forholdsmessigFordeling != null) {
            return SjekkForholdmessigFordelingResponse(false)
        }
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!

        val bpHarLøpendeBidragForAndreBarn = harLøpendeBidragForBarnIkkeIBehandling(behandling)

        val bidragsaker = hentAlleÅpneEllerLøpendeBidraggsakerForBP(bidragspliktigFnr, behandling)
        return SjekkForholdmessigFordelingResponse(
            bpHarLøpendeBidragForAndreBarn,
            false,
            bidragsaker
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
                        enhet = sak.eierfogd.verdi,
                        åpenBehandling =
                            if (lb.åpenBehandling != null) {
                                ForholdsmessigFordelingÅpenBehandlingDto(
                                    søktFraDato = lb.åpenBehandling.søktFomDato,
                                    mottattDato = lb.åpenBehandling.mottattdato,
                                    stønadstype = lb.åpenBehandling.stonadstype!!,
                                    behandlerEnhet = lb.åpenBehandling.behandlerEnhet,
                                )
                            } else if (lb.åpenSøknad != null) {
                                ForholdsmessigFordelingÅpenBehandlingDto(
                                    stønadstype = Stønadstype.valueOf(lb.åpenSøknad.stønadstype),
                                    behandlerEnhet = sak.eierfogd.verdi,
                                    søktFraDato = LocalDate.now(),
                                    mottattDato = LocalDate.now(),
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
                            ),
                    )
                },
        )
    }

    fun overførÅpneBisysSøknaderTilBehandling(behandling: Behandling) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneSøknader = bbmConsumer.hentÅpneSøknaderForBp(bidragspliktigFnr).åpneSøknader
        åpneSøknader.forEach { åpenSøknad ->

            val sak = sakConsumer.hentSak(åpenSøknad.saksnummer)
            val ffDetaljer =
                ForholdsmessigFordelingRolle(
                    delAvOpprinneligBehandling = false,
                    tilhørerSak = åpenSøknad.saksnummer,
                    søknadsid = åpenSøknad.søknadsid.toLong(),
                    behandlerEnhet = sak.eierfogd,
                    mottattDato = åpenSøknad.søknadMottattDato,
                    søknadFomDato = åpenSøknad.søknadFomDato,
                    søktAvType = åpenSøknad.søktAvType,
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
            åpenSøknad.barn.forEach {
                opprettRolle(
                    behandling,
                    Rolletype.BARN,
                    it.personident!!,
                    stønadstype = Stønadstype.valueOf(åpenSøknad.stønadstype),
                    harGebyrSøknad = it.gebyr,
                    innbetaltBeløp = it.innbetaltBeløp,
                    ffDetaljer = ffDetaljer,
                )
            }
        }

        // TODO: Kall BBM for å lagre behandlingid
    }

    @Transactional
    fun overførÅpneBehandlingTilHovedbehandling(behandling: Behandling) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
        åpneBehandlinger.forEach { behandlingOverført ->
            behandlingOverført.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    behandlesAvBehandling = behandling.id,
                )
            behandlingOverført.bidragsmottaker?.let { rolle ->
                if (behandling.roller.none { it.ident == rolle.ident }) {
                    behandling.roller.add(
                        rolle.kopierRolle(behandling),
                    )
                }
            }
            behandlingOverført.søknadsbarn.forEach { rolle ->
                if (behandling.søknadsbarn.none { barn -> barn.ident == rolle.ident }) {
                    behandling.roller.add(
                        rolle.kopierRolle(behandling),
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

            behandlingService.lagreBehandling(behandlingOverført)
        }
    }

    private fun kopierInntekt(
        hovedbehandling: Behandling,
        inntektOverført: Inntekt,
    ) {
        val inntekt =
            Inntekt(
                behandling = hovedbehandling,
                ident = inntektOverført.ident,
                kilde = inntektOverført.kilde,
                taMed = inntektOverført.taMed,
                type = inntektOverført.type,
                gjelderBarn = inntektOverført.gjelderBarn,
                opprinneligFom = inntektOverført.opprinneligFom,
                opprinneligTom = inntektOverført.opprinneligTom,
                belop = inntektOverført.belop,
                datoFom = inntektOverført.datoFom,
                datoTom = inntektOverført.datoTom,
            )
        inntektOverført.inntektsposter
            .forEach { inntektspost ->
                inntekt.inntektsposter.add(
                    Inntektspost(
                        inntekt = inntekt,
                        kode = inntektspost.kode,
                        inntektstype = inntektspost.inntektstype,
                        beløp = inntektspost.beløp,
                    ),
                )
            }

        val rolleInntekt = hovedbehandling.roller.find { it.ident == inntektOverført.ident }!!
        val notatInntekt =
            inntektOverført.behandling!!
                .notater
                .find { it.type == NotatGrunnlag.NotatType.INNTEKT && it.rolle.ident == inntektOverført.ident }
                ?.innhold ?: ""
        hovedbehandling.inntekter.add(inntekt)
        hovedbehandling.notater.add(
            Notat(behandling = hovedbehandling, innhold = notatInntekt, rolle = rolleInntekt, type = NotatGrunnlag.NotatType.INNTEKT),
        )
    }

    private fun kopierSamvær(
        hovedbehandling: Behandling,
        samværOverført: Samvær,
    ) {
        val rolle = hovedbehandling.roller.find { it.ident == samværOverført.rolle.ident }!!
        val samvær =
            Samvær(
                rolle = hovedbehandling.roller.find { r -> r.ident == samværOverført.rolle.ident }!!,
                behandling = hovedbehandling,
            )
        samvær.perioder =
            samværOverført.perioder
                .map { s ->
                    Samværsperiode(fom = s.fom, tom = s.tom, samværsklasse = s.samværsklasse, samvær = samvær)
                }.toMutableSet()
        val notatSamævr =
            samværOverført.rolle.notat
                .find { it.type == NotatGrunnlag.NotatType.SAMVÆR }
                ?.innhold ?: ""
        hovedbehandling.samvær.add(samvær)
        hovedbehandling.notater.add(
            Notat(behandling = hovedbehandling, innhold = notatSamævr, rolle = rolle, type = NotatGrunnlag.NotatType.SAMVÆR),
        )
    }

    private fun Underholdskostnad.kopierUnderholdskostnad(hovedbehandling: Behandling) {
        val rolle = hovedbehandling.roller.find { it.ident == rolle?.ident }
        val bmFraOverførtBehandling = behandling.bidragsmottaker
        val bmFraHovedbehandling = hovedbehandling.roller.find { r -> r.ident == bmFraOverførtBehandling!!.ident }!!
        val underholdskostnad =
            Underholdskostnad(
                rolle = rolle,
                person = person,
                behandling = hovedbehandling,
                kilde = kilde,
                harTilsynsordning = harTilsynsordning,
            )
        underholdskostnad.faktiskeTilsynsutgifter =
            faktiskeTilsynsutgifter
                .map {
                    FaktiskTilsynsutgift(
                        underholdskostnad = underholdskostnad,
                        fom = it.fom,
                        tom = it.tom,
                        tilsynsutgift = it.tilsynsutgift,
                        kostpenger = it.kostpenger,
                        kommentar = it.kommentar,
                    )
                }.toMutableSet()
        underholdskostnad.barnetilsyn =
            barnetilsyn
                .map {
                    Barnetilsyn(
                        underholdskostnad = underholdskostnad,
                        fom = it.fom,
                        tom = it.tom,
                        under_skolealder = it.under_skolealder,
                        kilde = it.kilde,
                        omfang = it.omfang,
                    )
                }.toMutableSet()
        underholdskostnad.tilleggsstønad =
            tilleggsstønad
                .map {
                    Tilleggsstønad(
                        underholdskostnad = underholdskostnad,
                        fom = it.fom,
                        tom = it.tom,
                        dagsats = it.dagsats,
                    )
                }.toMutableSet()
        val rolleNotat = if (underholdskostnad.rolle == null) bmFraOverførtBehandling else underholdskostnad.rolle
        val notatUnderhold =
            rolleNotat!!
                .notat
                .find { it.type == NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD }
                ?.innhold ?: ""
        hovedbehandling.underholdskostnader.add(underholdskostnad)
        hovedbehandling.notater.add(
            Notat(
                behandling = hovedbehandling,
                innhold = notatUnderhold,
                rolle = rolle ?: bmFraHovedbehandling,
                type = NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            ),
        )
    }

    fun opprettRollerForSak(
        behandling: Behandling,
        løpendeBidragssak: LøpendeBidragssak,
    ) {
        val sak = sakConsumer.hentSak(løpendeBidragssak.sak.verdi)

        // TODO: Kall BBM for å opprette søknad

        val søknadsid = 123213L
        val ffDetaljer =
            ForholdsmessigFordelingRolle(
                delAvOpprinneligBehandling = false,
                tilhørerSak = løpendeBidragssak.sak.verdi,
                søknadsid = søknadsid,
                behandlerEnhet = sak.eierfogd,
                mottattDato = LocalDate.now(),
                søknadFomDato = LocalDate.now().plusMonths(1).withDayOfMonth(1),
                søktAvType = SøktAvType.NAV_BIDRAG,
            )
        if (behandling.roller.none { it.ident == løpendeBidragssak.kravhaver.verdi }) {
            val rolleBarn =
                opprettRolle(
                    behandling,
                    Rolletype.BARN,
                    løpendeBidragssak.kravhaver.verdi,
                    ffDetaljer = ffDetaljer,
                )
            behandling.roller.add(rolleBarn)
        }

        val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
        if (bmFødselsnummer != null && behandling.roller.none { it.ident == bmFødselsnummer }) {
            val rolleBM =
                opprettRolle(
                    behandling,
                    Rolletype.BIDRAGSMOTTAKER,
                    bmFødselsnummer,
                    ffDetaljer = ffDetaljer,
                )
            behandling.roller.add(rolleBM)
        }
    }

    private fun hentSisteLøpendeStønader(bpIdent: Personident): List<LøpendeBidragssak> =
        beløpshistorikkConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bpIdent)).bidragssakerListe

    private fun opprettRolle(
        behandling: Behandling,
        rolletype: Rolletype,
        fødselsnummer: String,
        stønadstype: Stønadstype = Stønadstype.BIDRAG,
        harGebyrSøknad: Boolean = false,
        innbetaltBeløp: BigDecimal? = null,
        ffDetaljer: ForholdsmessigFordelingRolle,
    ): Rolle {
        val erBarn = rolletype == Rolletype.BARN
        return Rolle(
            harGebyrsøknad = harGebyrSøknad,
            behandling = behandling,
            rolletype = rolletype,
            innbetaltBeløp = innbetaltBeløp,
            stønadstype = stønadstype,
            virkningstidspunkt =
                if (erBarn) {
                    maxOf(
                        hentPersonFødselsdato(fødselsnummer)!!.plusMonths(1).withDayOfMonth(1),
                        behandling.globalVirkningstidspunkt!!,
                    )
                } else {
                    null
                },
            opphørsdato = if (erBarn) behandling.globalOpphørsdato else null,
            årsak = if (erBarn)hentDefaultÅrsak(behandling.tilType(), behandling.vedtakstype) else null,
            avslag = if (erBarn) behandling.avslag else null,
            beregnTil =
                if (behandling.vedtakstype == Vedtakstype.KLAGE) {
                    BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
                } else {
                    BeregnTil.INNEVÆRENDE_MÅNED
                },
            ident = fødselsnummer,
            fødselsdato = hentPersonFødselsdato(fødselsnummer)!!,
            forholdsmessigFordeling = ffDetaljer,
        )
    }

    private fun Grunnlag.kopierGrunnlag(hovedbehandling: Behandling): Grunnlag =
        Grunnlag(
            behandling = hovedbehandling,
            rolle = hovedbehandling.roller.find { it.ident == rolle.ident }!!,
            erBearbeidet = erBearbeidet,
            grunnlagFraVedtakSomSkalOmgjøres = grunnlagFraVedtakSomSkalOmgjøres,
            type = type,
            data = data,
            gjelder = gjelder,
            aktiv = aktiv,
            innhentet = innhentet,
        )

    private fun Rolle.kopierRolle(hovedbehandling: Behandling) =
        Rolle(
            behandling = hovedbehandling,
            rolletype = rolletype,
            ident = ident,
            årsak = årsak ?: behandling.årsak,
            avslag = avslag ?: behandling.avslag,
            virkningstidspunkt = virkningstidspunkt ?: hovedbehandling.globalVirkningstidspunkt,
            grunnlagFraVedtakListe = grunnlagFraVedtakListe,
            opphørsdato = opphørsdato ?: hovedbehandling.globalOpphørsdato,
            manueltOverstyrtGebyr = manueltOverstyrtGebyr,
            harGebyrsøknad = harGebyrsøknad,
            opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
            beregnTil = beregnTil,
            fødselsdato = fødselsdato,
            forholdsmessigFordeling =
                ForholdsmessigFordelingRolle(
                    delAvOpprinneligBehandling = false,
                    behandlingsid = behandling.id,
                    tilhørerSak = behandling.saksnummer,
                    behandlerEnhet = Enhetsnummer(behandling.behandlerEnhet),
                ),
        )

    private fun hentAlleÅpneEllerLøpendeBidraggsakerForBP(
        bidragspliktigFnr: String,
        behandling: Behandling,
    ): Set<SakKravhaver> {
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr))
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
        val åpneSøknader = bbmConsumer.hentÅpneSøknaderForBp(bidragspliktigFnr).åpneSøknader

        val eksisterendeSøknadsbarn = behandling.søknadsbarn.map { it.ident }
        val bidragsaker =
            løpendeBidraggsakerBP.map { SakKravhaver(it.sak.verdi, it.kravhaver.verdi) } +
                åpneSøknader.tilSakKravhaver() +
                åpneBehandlinger.flatMap { behandling ->
                    behandling.søknadsbarn.map { SakKravhaver(behandling.saksnummer, it.ident!!, åpenBehandling = behandling) }
                }
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

    private fun List<ÅpenSøknadDto>.tilSakKravhaver() =
        flatMap { åpenSøknad ->
            åpenSøknad.partISøknadListe.filter { it.rolletype == Rolletype.BARN.name }.map { barnFnr ->
                SakKravhaver(åpenSøknad.saksnummer, barnFnr.personident!!, åpenSøknad)
            }
        }
}
