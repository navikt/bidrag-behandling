package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagLøpendeBidragFF
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.grunnlag.LøpendeBidragGrunnlagForholdsmessigFordeling
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.barn
import no.nav.bidrag.behandling.transformers.behandling.finnes
import no.nav.bidrag.behandling.transformers.filtrerSakerHvorPersonErBP
import no.nav.bidrag.behandling.transformers.filtrerUtPrivatAvtalerSomIkkeErInnenforBeregningsperiode
import no.nav.bidrag.behandling.transformers.tilDato18årsBidrag
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.samhandler.Valutakode
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragPeriodeRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.toYearMonth
import java.time.LocalDateTime

private const val FORETRUKKET_BEHANDLERENHET = "2103"
private val STØTTEDE_BEHANDLERENHETER_FOR_FF = setOf("4883", FORETRUKKET_BEHANDLERENHET)

class ForholdsmessigFordelingKravhaverService(
    private val sakConsumer: BidragSakConsumer,
    private val behandlingRepository: BehandlingRepository,
    private val beløpshistorikkConsumer: BidragBeløpshistorikkConsumer,
    private val bbmConsumer: BidragBBMConsumer,
) {
    fun finnEnhetForBarnIBehandling(behandling: Behandling): String {
        val sakerBp = hentSakerBp(behandling.bidragspliktig!!.ident!!)
        val relevanteSaker = sakerBp.filter { it.eierfogd.verdi in STØTTEDE_BEHANDLERENHETER_FOR_FF }
        return relevanteSaker.find { it.eierfogd.verdi == FORETRUKKET_BEHANDLERENHET }?.eierfogd?.verdi
            ?: relevanteSaker.firstOrNull()?.eierfogd?.verdi
            ?: behandling.behandlerEnhet
    }

    fun hentSisteLøpendeStønader(
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
                    perioderLøperBidrag = sak.periodeListe.map { it.periode },
                )
            }

    fun hentAlleRelevanteKravhavere(behandling: Behandling): Set<SakKravhaver> {
        val åpneEllerLøpendeSakerBp = hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val sakerUtenLøpendeBidrag =
            hentBarnUtenLøpendeBidrag(behandling, åpneEllerLøpendeSakerBp)

        val relevanteKravhavere = åpneEllerLøpendeSakerBp + sakerUtenLøpendeBidrag
        return relevanteKravhavere + hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, relevanteKravhavere)
    }

    fun hentAlleÅpneEllerLøpendeBidraggsakerForBP(
        behandling: Behandling,
        eksisterendeRelevanteKravhavere: Set<SakKravhaver>? = null,
    ): Set<SakKravhaver> {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val beregningsperiode =
            finnBeregningsperiodeForKravhavere(eksisterendeRelevanteKravhavere, behandling)
        val løpendeBidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr), beregningsperiode)
        val åpneBehandlinger =
            behandlingRepository
                .finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
                .filter {
                    it.søknadstype != null && !behandlingstyperSomIkkeSkalInkluderesIFF.contains(it.søknadstype)
                }.filter {
                    (
                        it.erKlageEllerOmgjøring && behandling.erKlageEllerOmgjøring && (
                            it.omgjøringsdetaljer?.omgjørVedtakId == behandling.omgjøringsdetaljer?.omgjørVedtakId ||
                                it.omgjøringsdetaljer?.soknadRefId == behandling.omgjøringsdetaljer?.soknadRefId
                        )
                    ) ||
                        !it.erKlageEllerOmgjøring
                }
        val åpneSøknader =
            hentÅpneSøknader(
                bidragspliktigFnr,
                behandling.behandlingstypeForFF,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )

        val sakKravhaverListe = mutableSetOf<SakKravhaver>()

        åpneBehandlinger.forEach { åpenBehandling ->
            åpenBehandling.søknadsbarn.forEach { barn ->
                val stønadstype = barn.stønadstype ?: åpenBehandling.stonadstype
                val løpendeBidrag = løpendeBidraggsakerBP.find { it.kravhaver.verdi == barn.ident && it.type == stønadstype }
                val eksisterende = sakKravhaverListe.hentForKravhaver(barn.ident!!, barn.stønadstype)
                if (eksisterende != null) {
                    eksisterende.åpneBehandlinger.add(åpenBehandling)
                } else {
                    sakKravhaverListe.add(
                        SakKravhaver(
                            saksnummer = åpenBehandling.saksnummer,
                            kravhaver = barn.ident!!,
                            stønadstype = stønadstype,
                            åpneBehandlinger = mutableSetOf(åpenBehandling),
                            eierfogd = åpenBehandling.behandlerEnhet,
                            bidragsmottaker = åpenBehandling.bidragsmottaker?.ident,
                            løperBidragFra = løpendeBidrag?.periodeFra,
                            løperBidragTil = løpendeBidrag?.periodeTil,
                            opphørsdato = barn.opphørsdato?.toYearMonth(),
                            privatAvtale = åpenBehandling.privatAvtale.find { it.gjelderPerson(barn.ident!!, stønadstype) },
                            perioderLøperBidrag = løpendeBidrag?.perioderLøperBidrag ?: emptyList(),
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
                            it.åpneBehandlinger.any { åpenBehandling ->
                                åpenBehandling.id == søknad.behandlingsid || åpenBehandling.soknadsid == søknad.søknadsid
                            }
                        } &&
                            !behandlingRepository.erIForholdsmessigFordeling(søknad.behandlingsid!!)
                    )
            }.forEach { åpenSøknad ->
                åpenSøknad.parterUnderBehandling
                    .filter { it.rolletype == Rolletype.BARN }
                    .forEach { barnFnr ->
                        val stønadstype = åpenSøknad.behandlingstema.tilStønadstype()
                        val løpendeBidrag = løpendeBidraggsakerBP.hentBidragSakForKravhaver(barnFnr.personident!!, stønadstype)
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
                                    bidragsmottaker = åpenSøknad.bidragsmottaker?.personident,
                                    opphørsdato = behandling.finnOpphørsdato(stønadstype!!, barnFnr.personident!!),
                                    åpneSøknader = mutableSetOf(åpenSøknad),
                                    privatAvtale = behandling.privatAvtale.find { it.gjelderPerson(barnFnr.personident!!, stønadstype) },
                                ),
                            )
                        }
                    }
            }

        val kravhaverFraÅpneSaker = sakKravhaverListe.map { it.kravhaver to it.stønadstype }

        val løpendeBidragsaker =
            løpendeBidraggsakerBP
                .filter { lb -> !kravhaverFraÅpneSaker.finnes(lb.kravhaver.verdi, lb.type) }
                .map {
                    val sak = sakConsumer.hentSak(it.sak.verdi)
                    SakKravhaver(
                        saksnummer = it.sak.verdi,
                        kravhaver = it.kravhaver.verdi,
                        stønadstype = it.type,
                        bidragsmottaker = sak.bidragsmottaker?.fødselsnummer?.verdi,
                        løperBidragFra = it.periodeFra,
                        løperBidragTil = it.periodeTil,
                        opphørsdato = behandling.finnOpphørsdato(it.type, it.kravhaver.verdi),
                        privatAvtale = behandling.privatAvtale.find { pa -> pa.gjelderPerson(it.kravhaver.verdi, it.type) },
                    )
                }.distinctBy { it.distinctKey }
        val bidragsaker = løpendeBidragsaker + sakKravhaverListe
        return bidragsaker
            .filter {
                eksisterendeRelevanteKravhavere == null ||
                    eksisterendeRelevanteKravhavere.hentForKravhaver(it.kravhaver, it.stønadstype) == null
            }.sortedWith { a, b ->
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

    fun opprettGrunnlagLøpendeBidrag(
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

    fun hentÅpenSøknadFFForBP(
        bidragspliktigFnr: String,
        behandlingstype: Behandlingstype,
        medInnkreving: Boolean,
        saksnummer: String,
        søktFomDato: java.time.LocalDate,
        stønadstype: no.nav.bidrag.domene.enums.vedtak.Stønadstype?,
        omgjøringsdetaljer: Omgjøringsdetaljer?,
        erKlageEllerOmgjøring: Boolean = omgjøringsdetaljer != null,
    ) = hentÅpneSøknader(bidragspliktigFnr, behandlingstype, omgjøringsdetaljer, erKlageEllerOmgjøring)
        .filter { it.behandlingstype.erForholdsmessigFordeling }
        .find {
            (it.innkreving == medInnkreving) &&
                it.saksnummer == saksnummer &&
                it.søknadFomDato == søktFomDato &&
                it.behandlingstema.tilStønadstype() == stønadstype
        }

    fun hentÅpneSøknaderRevurdering(bidragspliktigFnr: String): List<HentSøknad> =
        bbmConsumer
            .hentÅpneSøknaderForBp(bidragspliktigFnr)
            .åpneSøknader
            .filter { it.behandlingstype == Behandlingstype.REVURDERING && it.søktAvType == SøktAvType.NAV_BIDRAG }

    fun hentÅpneSøknader(
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
                (!erKlageEllerOmgjøring && !it.behandlingstype.erKlageEllerOmgjøring)
        }.filter {
            (
                erKlageEllerOmgjøring &&
                    ((it.refVedtaksid == omgjøringsdetaljer?.omgjørVedtakId) || (it.refSøknadsid == omgjøringsdetaljer?.soknadRefId))
            ) ||
                !erKlageEllerOmgjøring
        }.sortedWith(
            compareByDescending<HentSøknad> { it.behandlingstype == behandlingstypeForFF }
                .thenBy { it.søknadFomDato },
        )

    fun hentSakerBp(bpIdent: String) = sakConsumer.hentSakerPerson(bpIdent).filtrerSakerHvorPersonErBP(bpIdent)

    fun hentBarnUtenLøpendeBidrag(
        behandling: Behandling,
        sakerMedLøpendeBidrag: Set<SakKravhaver>? = null,
    ): List<SakKravhaver> {
        val kravhavereSomHarÅpenBehandling = sakerMedLøpendeBidrag ?: hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling)
        val søktFomDatoRevurdering = kravhavereSomHarÅpenBehandling.finnSøktFomRevurderingSøknad(behandling)
        val beregningsperiode =
            finnBeregningsperiodeForKravhavere(sakerMedLøpendeBidrag, behandling)
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val søknadsbarnIdentStønadstypeMap =
            kravhavereSomHarÅpenBehandling.map { it.kravhaver to it.stønadstype } +
                behandling.søknadsbarn.filter { it.ident != null }.map { it.ident!! to null }

        val sakerBp = hentSakerBp(bidragspliktigFnr)
        val barnSomHarBidragssak = sakerBp.flatMap { it.barn.map { barn -> barn.fødselsnummer!!.verdi } }

        val privatAvtalerUtenBidragssak =
            behandling.privatAvtale
                .filtrerUtPrivatAvtalerSomIkkeErInnenforBeregningsperiode(beregningsperiode)
                .filter {
                    it.rolle == null && !barnSomHarBidragssak.contains(it.personIdent!!) && (
                        it.rolle?.forholdsmessigFordeling == null ||
                            it.rolle
                                ?.forholdsmessigFordeling
                                ?.søknaderUnderBehandling
                                ?.isEmpty() == true
                    )
                }.map {
                    val barnFødselsdato = hentPersonFødselsdato(it.personIdent!!)
                    val dato18ÅrsBidrag = barnFødselsdato!!.tilDato18årsBidrag()
                    val er18EtterSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                    val stønadstype =
                        it.stønadstype
                            ?: if (er18EtterSøktFom) {
                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG18AAR
                            } else {
                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG
                            }
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
        val privatAvtalerBarnIBehandling =
            behandling.privatAvtale
                .filtrerUtPrivatAvtalerSomIkkeErInnenforBeregningsperiode(beregningsperiode)
                .filter {
                    it.rolle != null &&
                        kravhavereSomHarÅpenBehandling.none { kravhaver ->
                            kravhaver.kravhaver == it.personIdent &&
                                kravhaver.stønadstype == it.stønadstype
                        }
                }.mapNotNull { privatAvtale ->
                    val rollePA = privatAvtale.rolle!!
                    val sak = sakerBp.find { it.roller.any { rolle -> rolle.fødselsnummer!!.verdi == rollePA.ident } }!!

                    val stønaderMedÅpenBehandling =
                        søknadsbarnIdentStønadstypeMap
                            .filter {
                                it.first == rollePA.ident && (it.second == null || it.second == rollePA.stønadstype)
                            }.map { it.second }
                            .distinct()

                    val løpendeBidrag =
                        kravhavereSomHarÅpenBehandling.find { lb ->
                            lb.erSammePerson(rollePA.ident!!, rollePA.stønadstype)
                        }
                    val barnFødselsdato = hentPersonFødselsdato(rollePA.ident)
                    val dato18ÅrsBidrag = barnFødselsdato!!.plusYears(18).plusMonths(1).withDayOfMonth(1)
                    val er18EtterSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                    val privatAvtalePerioder = privatAvtale.perioder ?: emptySet()
                    val førstePeriodePrivatAvtale = privatAvtalePerioder.minByOrNull { it.fom }

                    val stønadstypeBeregnet =
                        when {
                            privatAvtale.stønadstype != null -> {
                                privatAvtale.stønadstype
                            }

                            førstePeriodePrivatAvtale != null && førstePeriodePrivatAvtale.fom < dato18ÅrsBidrag -> {
                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG
                            }

                            førstePeriodePrivatAvtale != null &&
                                førstePeriodePrivatAvtale.fom >= dato18ÅrsBidrag -> {
                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG18AAR
                            }

                            er18EtterSøktFom -> {
                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG18AAR
                            }

                            else -> {
                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG
                            }
                        }

                    if (stønaderMedÅpenBehandling.contains(stønadstypeBeregnet)) return@mapNotNull null

                    SakKravhaver(
                        kravhaver = rollePA.ident!!,
                        saksnummer = sak.saksnummer.verdi,
                        løperBidragFra = løpendeBidrag?.løperBidragFra,
                        løperBidragTil = løpendeBidrag?.løperBidragTil,
                        stønadstype = stønadstypeBeregnet,
                        eierfogd = sak.eierfogd.verdi,
                        bidragsmottaker = sak.bidragsmottaker?.fødselsnummer?.verdi,
                        privatAvtale = privatAvtale,
                        opphørsdato = behandling.finnOpphørsdato(stønadstypeBeregnet!!, rollePA.ident!!),
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
                        .flatMap { barnRolle ->
                            behandling.privatAvtale
                                .filtrerUtPrivatAvtalerSomIkkeErInnenforBeregningsperiode(beregningsperiode)
                                .filter { pa ->
                                    pa.rolle == null &&
                                        pa.personIdent == barnRolle.fødselsnummer?.verdi
                                }.map { privatAvtale ->
                                    val stønaderMedÅpenBehandling =
                                        søknadsbarnIdentStønadstypeMap
                                            .filter {
                                                it.first == barnRolle.fødselsnummer!!.verdi
                                            }.map { it.second }
                                            .distinct()
                                    val løpendeBidrag =
                                        kravhavereSomHarÅpenBehandling.find { lb ->
                                            lb.kravhaver == barnRolle.fødselsnummer!!.verdi &&
                                                !stønaderMedÅpenBehandling.contains(lb.stønadstype)
                                        }
                                    val barnFødselsdato = hentPersonFødselsdato(barnRolle.fødselsnummer!!.verdi)
                                    val dato18ÅrsBidrag = barnFødselsdato!!.plusYears(18).plusMonths(1).withDayOfMonth(1)
                                    val er18EtterSøktFom = søktFomDatoRevurdering > dato18ÅrsBidrag
                                    val privatAvtalePerioder = privatAvtale.perioder ?: emptySet()
                                    val førstePeriodePrivatAvtale = privatAvtalePerioder.minByOrNull { it.fom }

                                    val stønadstypeBeregnet =
                                        when {
                                            privatAvtale.stønadstype != null -> {
                                                privatAvtale.stønadstype
                                            }

                                            førstePeriodePrivatAvtale != null && førstePeriodePrivatAvtale.fom < dato18ÅrsBidrag -> {
                                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG
                                            }

                                            førstePeriodePrivatAvtale != null &&
                                                førstePeriodePrivatAvtale.fom >= dato18ÅrsBidrag -> {
                                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG18AAR
                                            }

                                            er18EtterSøktFom -> {
                                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG18AAR
                                            }

                                            else -> {
                                                no.nav.bidrag.domene.enums.vedtak.Stønadstype.BIDRAG
                                            }
                                        }

                                    if (stønaderMedÅpenBehandling.contains(stønadstypeBeregnet)) return@map null

                                    SakKravhaver(
                                        kravhaver = barnRolle.fødselsnummer!!.verdi,
                                        saksnummer = sak.saksnummer.verdi,
                                        løperBidragFra = løpendeBidrag?.løperBidragFra,
                                        løperBidragTil = løpendeBidrag?.løperBidragTil,
                                        stønadstype = stønadstypeBeregnet,
                                        eierfogd = sak.eierfogd.verdi,
                                        bidragsmottaker = sak.bidragsmottaker?.fødselsnummer?.verdi,
                                        privatAvtale = privatAvtale,
                                        opphørsdato = behandling.finnOpphørsdato(stønadstypeBeregnet!!, barnRolle.fødselsnummer!!.verdi),
                                    )
                                }
                        }.filterNotNull()
                }.filter { barn -> barn.privatAvtale != null }

        return privatAvtalerBarnIBehandling + barnMedBidragssakSomHarPrivatAvtale + privatAvtalerUtenBidragssak
    }
}
