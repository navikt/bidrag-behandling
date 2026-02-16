package no.nav.bidrag.behandling.transformers.forholdsmessigfordeling

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtalePeriode
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.extensions.hentDefaultÅrsak
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingBarnDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingÅpenBehandlingDto
import no.nav.bidrag.behandling.service.LøpendeBidragSakPeriode
import no.nav.bidrag.behandling.service.SakKravhaver
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.tilBehandlingstema
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.ÅpenSøknadDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.felles.toLocalDate
import no.nav.bidrag.transport.sak.BidragssakDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

val SakKravhaver.søknadsider get() = åpneSøknader.map { it.søknadsid } + åpneBehandlinger.map { it.soknadsid ?: -1 }

fun Collection<SakKravhaver>.finnEldsteSøktFomDato(behandling: Behandling) =
    (
        flatMap {
            it.åpneBehandlinger.map { it.søktFomDato } +
                it.åpneSøknader.filter { it.søknadFomDato != null }.map { it.søknadFomDato!! }
        } + listOf(behandling.søktFomDato)
    ).min()

fun SakKravhaver.løperBidragEtterDato(fraDato: YearMonth) =
    (løperBidragFra != null && løperBidragTil == null) ||
        (løperBidragTil != null && løperBidragTil > fraDato)

fun Collection<SakKravhaver>.finnSøktFomRevurderingSøknad(behandling: Behandling) =
    maxOf(finnEldsteSøktFomDato(behandling).withDayOfMonth(1), LocalDate.now().plusMonths(1).withDayOfMonth(1))

fun Rolle.fjernSøknad(søknadsid: Long) {
    if (forholdsmessigFordeling == null) return
    forholdsmessigFordeling!!.søknader =
        forholdsmessigFordeling!!
            .søknader
            .map {
                if (it.søknadsid == søknadsid) {
                    // Status feilregistrert betyr at barnet er feilregistrert fra saken
                    it.status = Behandlingstatus.FEILREGISTRERT
                }
                it
            }.toMutableSet()
}

fun Behandling.tilFFDetaljerBP() =
    ForholdsmessigFordelingRolle(
        tilhørerSak = saksnummer,
        behandlingsid = id,
        behandlerenhet = behandlerEnhet,
        delAvOpprinneligBehandling = true,
        erRevurdering = false,
        bidragsmottaker = null,
    )

fun Behandling.tilFFDetaljerBM() =
    ForholdsmessigFordelingRolle(
        delAvOpprinneligBehandling = true,
        tilhørerSak = saksnummer,
        behandlingsid = id,
        behandlerenhet = behandlerEnhet,
        bidragsmottaker = null,
        erRevurdering = false,
        søknader =
            mutableSetOf(
                tilFFBarnDetaljer(),
            ),
    )

fun Behandling.tilFFBarnDetaljer() =
    ForholdsmessigFordelingSøknadBarn(
        søktAvType = soknadFra,
        behandlingstype = søknadstype,
        behandlingstema = behandlingstema,
        mottattDato = mottattdato,
        søknadFomDato = søktFomDato,
        søknadsid = soknadsid,
        innkreving = innkrevingstype == Innkrevingstype.MED_INNKREVING,
        omgjørSøknadsid = omgjøringsdetaljer?.soknadRefId,
        omgjørVedtaksid = omgjøringsdetaljer?.omgjørVedtakId,
        enhet = behandlerEnhet,
        saksnummer = saksnummer,
    )

fun ÅpenSøknadDto.tilForholdsmessigFordelingSøknad() =
    ForholdsmessigFordelingSøknadBarn(
        behandlingstype = behandlingstype,
        behandlingstema = behandlingstema,
        mottattDato = søknadMottattDato,
        søknadFomDato = søknadFomDato,
        søktAvType = søktAvType,
        søknadsid = søknadsid,
        omgjørVedtaksid = referertVedtaksid,
        innkreving = innkreving,
        enhet = behandlerenhet ?: "9999",
        omgjørSøknadsid = referertSøknadsid,
        saksnummer = saksnummer,
    )

fun opprettRolle(
    behandling: Behandling,
    rolletype: Rolletype,
    fødselsnummer: String,
    stønadstype: Stønadstype = Stønadstype.BIDRAG,
    harGebyrSøknad: GebyrRolleSøknad? = null,
    innbetaltBeløp: BigDecimal? = null,
    ffDetaljer: ForholdsmessigFordelingRolle,
    innkrevesFraDato: YearMonth? = null,
    medInnkreving: Boolean? = null,
    opphørsdato: YearMonth? = null,
): Rolle {
    behandling.roller.find { it.erSammeRolle(fødselsnummer, stønadstype) }?.let {
        if (harGebyrSøknad != null) {
            val gebyr = it.hentEllerOpprettGebyr()
            it.harGebyrsøknad = true
            it.gebyr =
                gebyr.let {
                    it.gebyrSøknader.add(harGebyrSøknad)
                    it
                }
        }
        it.forholdsmessigFordeling = ffDetaljer

        return it
    }
    val erBarn = rolletype == Rolletype.BARN
    val rolle =
        Rolle(
            harGebyrsøknad = harGebyrSøknad != null,
            gebyr =
                GebyrRolle(
                    overstyrGebyr = false,
                    gebyrSøknader = listOfNotNull(harGebyrSøknad).toMutableSet(),
                ),
            innkrevesFraDato = innkrevesFraDato?.atDay(1),
            innkrevingstype =
                if (medInnkreving == null) {
                    null
                } else if (medInnkreving) {
                    Innkrevingstype.MED_INNKREVING
                } else {
                    Innkrevingstype.UTEN_INNKREVING
                },
            behandling = behandling,
            rolletype = rolletype,
            innbetaltBeløp = innbetaltBeløp,
            stønadstype = stønadstype,
            behandlingstema = stønadstype.tilBehandlingstema(),
            behandlingstatus = Behandlingstatus.UNDER_BEHANDLING,
            virkningstidspunkt =
                if (erBarn) {
                    val virkningstidspunkt =
                        maxOf(
                            hentPersonFødselsdato(fødselsnummer)!!.plusMonths(1).withDayOfMonth(1),
                            ffDetaljer.eldsteSøknad.søknadFomDato ?: behandling.eldsteVirkningstidspunkt,
                        )
                    if (opphørsdato != null) minOf(opphørsdato.toLocalDate(), virkningstidspunkt) else virkningstidspunkt
                } else {
                    null
                },
            opphørsdato = if (erBarn) opphørsdato?.toLocalDate() ?: behandling.globalOpphørsdato else null,
            årsak =
                if (erBarn && ffDetaljer.erRevurdering) {
                    VirkningstidspunktÅrsakstype.REVURDERING_MÅNEDEN_ETTER
                } else if (erBarn) {
                    hentDefaultÅrsak(behandling.tilType(), behandling.vedtakstype)
                } else {
                    null
                },
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
    behandling.roller.add(rolle)
    return rolle
}

fun Grunnlag.kopierGrunnlag(hovedbehandling: Behandling): Grunnlag =
    Grunnlag(
        behandling = hovedbehandling,
        rolle = hovedbehandling.roller.find { it.erSammeRolle(rolle) }!!,
        erBearbeidet = erBearbeidet,
        grunnlagFraVedtakSomSkalOmgjøres = grunnlagFraVedtakSomSkalOmgjøres,
        type = type,
        data = data,
        gjelder = gjelder,
        aktiv = aktiv,
        innhentet = innhentet,
    )

fun Rolle.kopierRolle(
    hovedbehandling: Behandling,
    bmFnr: String?,
    periodeFra: YearMonth? = null,
    periodeTil: YearMonth? = null,
    medInnkreving: Boolean? = null,
    åpneBehandlinger: List<Behandling> = emptyList(),
) = Rolle(
    behandling = hovedbehandling,
    rolletype = rolletype,
    stønadstype = stønadstype,
    innkrevingstype =
        if (medInnkreving == null) {
            null
        } else if (medInnkreving) {
            Innkrevingstype.MED_INNKREVING
        } else {
            Innkrevingstype.UTEN_INNKREVING
        },
    ident = ident,
    årsak = årsak ?: behandling.årsak,
    avslag = avslag ?: behandling.avslag,
    behandlingstema = behandlingstema,
    behandlingstatus = behandlingstatus,
    virkningstidspunkt = virkningstidspunkt ?: hovedbehandling.eldsteVirkningstidspunkt,
    grunnlagFraVedtakListe = grunnlagFraVedtakListe,
    opphørsdato = opphørsdato ?: hovedbehandling.globalOpphørsdato,
    gebyr = hentEllerOpprettGebyr(),
    harGebyrsøknad = harGebyrsøknad,
    opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
    beregnTil = beregnTil,
    fødselsdato = fødselsdato,
    forholdsmessigFordeling =
        ForholdsmessigFordelingRolle(
            delAvOpprinneligBehandling = false,
            behandlingsid = behandling.id,
            tilhørerSak = behandling.saksnummer,
            behandlerenhet = behandling.behandlerEnhet,
            bidragsmottaker = bmFnr,
            harLøpendeBidrag = medInnkreving == true,
            løperBidragFra = periodeFra,
            løperBidragTil = periodeTil,
            erRevurdering = false,
            søknader =
                (
                    setOf(
                        behandling.tilFFBarnDetaljer(),
                    ) + åpneBehandlinger.map { it.tilFFBarnDetaljer() }
                ).toMutableSet(),
        ),
)

fun kopierInntekt(
    hovedbehandling: Behandling,
    inntektOverført: Inntekt,
) {
    val eksisterndeInntekt =
        hovedbehandling.inntekter
            .filter {
                it.taMed && it.tilhørerSammePerson(inntektOverført) &&
                    it.type == inntektOverført.type
            }.find {
                val periodeOverført = ÅrMånedsperiode(inntektOverført.datoFom!!, inntektOverført.datoTom)
                val periodeInntekt = ÅrMånedsperiode(it.datoFom!!, it.datoTom)
                periodeOverført.overlapper(periodeInntekt) && it.belop == inntektOverført.belop
            }
    // Ikke overfør manuell inntekt hvis det overlapper
    if (eksisterndeInntekt != null && (inntektOverført.datoTom == null || eksisterndeInntekt.datoTom == inntektOverført.datoTom)) return

    val inntekt =
        Inntekt(
            behandling = hovedbehandling,
            ident = inntektOverført.gjelderIdent,
            rolle = inntektOverført.rolle,
            kilde = inntektOverført.kilde,
            taMed = inntektOverført.taMed,
            type = inntektOverført.type,
            gjelderBarn = inntektOverført.gjelderBarnIdent,
            gjelderBarnRolle = inntektOverført.gjelderBarnRolle,
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

    val rolleInntekt = hovedbehandling.roller.find { it.erSammeRolle(inntekt.rolle!!) }!!

    hovedbehandling.inntekter.add(inntekt)

    kopierOverBegrunnelseForBehandling(rolleInntekt, inntektOverført.behandling!!, hovedbehandling, NotatGrunnlag.NotatType.INNTEKT)
}

fun kopierOverInntekterForRolleFraBehandling(
    gjelderPerson: Rolle,
    behandling: Behandling,
    behandlingOverført: Behandling,
) {
    behandlingOverført.inntekter
        .filter { it.erSammeRolle(gjelderPerson) }
        .filter { it.taMed && it.type.kanLeggesInnManuelt }
        .forEach { inntektOverført ->
            kopierInntekt(behandling, inntektOverført)
        }

    behandlingOverført.inntekter
        .filter { it.erSammeRolle(gjelderPerson) }
        .filter { it.taMed && !it.type.kanLeggesInnManuelt }
        .forEach { inntektOverført ->
            val tilsvarendeInntekt =
                behandling.inntekter
                    .filter { it.erSammeRolle(gjelderPerson) }
                    .find {
                        it.type == inntektOverført.type && it.opprinneligFom == inntektOverført.opprinneligFom &&
                            it.opprinneligTom == inntektOverført.opprinneligTom
                    }
            if (tilsvarendeInntekt != null && !tilsvarendeInntekt.taMed) {
                tilsvarendeInntekt.datoFom = inntektOverført.datoFom
                tilsvarendeInntekt.datoTom = inntektOverført.datoTom
                tilsvarendeInntekt.taMed = true
            }
        }
}

fun kopierOverBegrunnelseForBehandling(
    rolle: Rolle,
    fraBehandling: Behandling,
    tilBehandling: Behandling,
    notatType: NotatGrunnlag.NotatType,
) {
    val rolleHovedbehandling = tilBehandling.roller.find { it.erSammeRolle(rolle) }!!
    val begrunnelseOverført =
        fraBehandling
            .notater
            .find { it.type == notatType && it.rolle.erSammeRolle(rolle) }
            ?.innhold ?: ""
    val eksisterendeBegrunnelse =
        tilBehandling.notater
            .find {
                it.type == notatType &&
                    it.rolle.erSammeRolle(rolle)
            }
    if (eksisterendeBegrunnelse == null) {
        tilBehandling.notater.add(
            Notat(behandling = tilBehandling, innhold = begrunnelseOverført, rolle = rolleHovedbehandling, type = notatType),
        )
        return
    }
    val endeligBegrunnelse =
        if (begrunnelseOverført.isNotEmpty() &&
            !eksisterendeBegrunnelse.innhold.contains(begrunnelseOverført, ignoreCase = true)
        ) {
            """${eksisterendeBegrunnelse.innhold} <br><u>Overført fra sak ${fraBehandling.saksnummer}</u><br> $begrunnelseOverført"""
        } else {
            eksisterendeBegrunnelse.innhold
        }
    eksisterendeBegrunnelse.innhold = endeligBegrunnelse
}

fun kopierPrivatAvtale(
    hovedbehandling: Behandling,
    privatAvtaleOveført: PrivatAvtale,
) {
    if (privatAvtaleOveført.rolle == null ||
        hovedbehandling.privatAvtale
            .any { it.rolle != null && it.rolle!!.erSammeRolle(privatAvtaleOveført.rolle!!) }
    ) {
        // Ikke overfør hvis barnet er allerede lagt inn i privat avtale eller tilhører annen barn
        return
    }

    val rolleBarn =
        hovedbehandling.roller.find {
            (privatAvtaleOveført.rolle != null && it.erSammeRolle(privatAvtaleOveført.rolle!!))
        } ?: return
    val eksisterendePrivatAvtale =
        hovedbehandling.privatAvtale
            .find {
                (it.rolle == null && it.person?.ident == privatAvtaleOveført.rolle!!.ident) ||
                    (it.rolle != null && it.rolle!!.erSammeRolle(privatAvtaleOveført.rolle!!))
            }
    val privatAvtaleNy =
        eksisterendePrivatAvtale
            ?: run {
                val nyPrivatAvtale =
                    PrivatAvtale(
                        rolle = rolleBarn,
                        behandling = hovedbehandling,
                    )
                hovedbehandling.privatAvtale.add(nyPrivatAvtale)
                nyPrivatAvtale
            }
    privatAvtaleNy.rolle = rolleBarn

    privatAvtaleNy.utenlandsk = privatAvtaleOveført.utenlandsk
    privatAvtaleNy.avtaleDato = privatAvtaleOveført.avtaleDato
    privatAvtaleNy.avtaleType = privatAvtaleOveført.avtaleType
    privatAvtaleNy.skalIndeksreguleres = privatAvtaleOveført.skalIndeksreguleres
    privatAvtaleNy.grunnlagFraVedtak = privatAvtaleOveført.grunnlagFraVedtak
    privatAvtaleNy.perioder.clear()
    privatAvtaleNy.perioder.addAll(
        privatAvtaleOveført.perioder.map { p ->
            PrivatAvtalePeriode(
                fom = p.fom,
                tom = p.tom,
                beløp = p.beløp,
                privatAvtale = privatAvtaleNy,
            )
        },
    )
}

fun kopierHusstandsmedlem(
    hovedbehandling: Behandling,
    husstandsmedlemOverført: Husstandsmedlem,
) {
    if (hovedbehandling.husstandsmedlem
            .any { it.rolle != null && husstandsmedlemOverført.rolle != null && it.rolle!!.erSammeRolle(husstandsmedlemOverført.rolle!!) }
    ) {
        // Ikke overfør hvis barnet er allerede lagt inn som husstandsmedlem
        return
    }

    val rolleBarn =
        hovedbehandling.roller.find {
            (husstandsmedlemOverført.rolle != null && it.erSammeRolle(husstandsmedlemOverført.rolle!!)) ||
                husstandsmedlemOverført.ident == it.ident
        } ?: return // Bare overfør husstandsmedlem informasjon for søknadsbarn som overføres. Resten hentes inn via grunnlagsinnhenting
    val eksisterendeHusstandsmedlem =
        hovedbehandling.husstandsmedlem
            .find { it.rolle == null && it.ident == husstandsmedlemOverført.ident }
    val husstandsmedlemNy =
        eksisterendeHusstandsmedlem
            ?: run {
                val nyHM =
                    Husstandsmedlem(
                        rolle = rolleBarn,
                        behandling = hovedbehandling,
                        kilde = Kilde.OFFENTLIG,
                    )
                hovedbehandling.husstandsmedlem.add(nyHM)
                nyHM
            }
    husstandsmedlemNy.rolle = rolleBarn
    husstandsmedlemNy.kilde = Kilde.OFFENTLIG

    // TODO: Skal perioder overføres?
//    husstandsmedlemNy.perioder.clear()
//    husstandsmedlemNy.perioder.addAll(
//        husstandsmedlemOverført.perioder
//            .map { p ->
//                Bostatusperiode(
//                    kilde = p.kilde,
//                    datoFom = p.datoFom,
//                    datoTom = p.datoTom,
//                    bostatus = p.bostatus,
//                    husstandsmedlem = husstandsmedlemNy,
//                )
//            }.toMutableSet(),
//    )
}

fun kopierSamvær(
    hovedbehandling: Behandling,
    samværOverført: Samvær,
) {
    val rolle = hovedbehandling.roller.find { it.erSammeRolle(samværOverført.rolle) }!!
    val samvær =
        Samvær(
            rolle = rolle,
            behandling = hovedbehandling,
        )
    samvær.perioder =
        samværOverført.perioder
            .map { s ->
                Samværsperiode(fom = s.fom, tom = s.tom, samværsklasse = s.samværsklasse, samvær = samvær)
            }.toMutableSet()
    val notatSamvær =
        samværOverført.rolle.notat
            .find { it.type == NotatGrunnlag.NotatType.SAMVÆR }
            ?.innhold ?: ""
    hovedbehandling.samvær.add(samvær)
    hovedbehandling.notater.add(
        Notat(behandling = hovedbehandling, innhold = notatSamvær, rolle = rolle, type = NotatGrunnlag.NotatType.SAMVÆR),
    )
}

fun Underholdskostnad.kopierUnderholdskostnad(hovedbehandling: Behandling) {
    val rolle = hovedbehandling.roller.find { rolle != null && it.erSammeRolle(rolle!!) }
    val bmFraOverførtBehandling = behandling.bidragsmottaker
    val bmFraHovedbehandling = hovedbehandling.roller.find { r -> r.erSammeRolle(bmFraOverførtBehandling!!) }!!
    val nyUnderholdskostnad =
        Underholdskostnad(
            rolle = rolle,
            person = person,
            behandling = hovedbehandling,
            kilde = kilde,
            harTilsynsordning = harTilsynsordning,
        )
    nyUnderholdskostnad.faktiskeTilsynsutgifter =
        faktiskeTilsynsutgifter
            .map {
                FaktiskTilsynsutgift(
                    underholdskostnad = nyUnderholdskostnad,
                    fom = it.fom,
                    tom = it.tom,
                    tilsynsutgift = it.tilsynsutgift,
                    kostpenger = it.kostpenger,
                    kommentar = it.kommentar,
                )
            }.toMutableSet()
    nyUnderholdskostnad.barnetilsyn =
        barnetilsyn
            .map {
                Barnetilsyn(
                    underholdskostnad = nyUnderholdskostnad,
                    fom = it.fom,
                    tom = it.tom,
                    under_skolealder = it.under_skolealder,
                    kilde = it.kilde,
                    omfang = it.omfang,
                )
            }.toMutableSet()
    nyUnderholdskostnad.tilleggsstønad =
        tilleggsstønad
            .map {
                Tilleggsstønad(
                    underholdskostnad = nyUnderholdskostnad,
                    fom = it.fom,
                    tom = it.tom,
                    beløp = it.beløp,
                    beløpstype = it.beløpstype,
                )
            }.toMutableSet()
    val rolleNotat = if (nyUnderholdskostnad.gjelderAndreBarn) bmFraOverførtBehandling else this.rolle
    val notatUnderhold =
        rolleNotat!!
            .notat
            .find { it.type == NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD }
            ?.innhold ?: ""
    hovedbehandling.underholdskostnader.add(nyUnderholdskostnad)
    hovedbehandling.notater.add(
        Notat(
            behandling = hovedbehandling,
            innhold = notatUnderhold,
            rolle = rolle ?: bmFraHovedbehandling,
            type = NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
        ),
    )
}

fun Collection<LøpendeBidragSakPeriode>.hentBidragSakForKravhaver(
    kravhaverIdent: String,
    stønadstype: Stønadstype?,
) = find {
    it.kravhaver.verdi == kravhaverIdent &&
        (stønadstype == null || it.type == stønadstype)
}

fun Collection<SakKravhaver>.hentForKravhaver(
    kravhaverIdent: String,
    type: Stønadstype?,
) = find {
    it.erLik(kravhaverIdent, type)
}

fun SakKravhaver.erLik(
    kravhaverIdent: String,
    type: Stønadstype?,
) = kravhaver == kravhaverIdent &&
    (type == null || type == stønadstype)

fun SakKravhaver.mapSakKravhaverTilForholdsmessigFordelingDto(
    sak: BidragssakDto?,
    behandling: Behandling,
    løpendeBidrag: Boolean = true,
    erRevurdering: Boolean = true,
): ForholdsmessigFordelingBarnDto {
    val bmFødselsnummer = sak?.bidragsmottaker?.fødselsnummer?.verdi ?: bidragsmottaker
    val barnFødselsnummer = kravhaver
    val enhet = sak?.eierfogd?.verdi ?: eierfogd ?: "Ukjent"

    val åpneBehandlinger = åpneBehandlinger.map { it.tilFFBarnDto() } + åpneSøknader.map { it.tilFFBarnDto(sak, enhet) }
    return ForholdsmessigFordelingBarnDto(
        ident = barnFødselsnummer,
        navn = hentPersonVisningsnavn(barnFødselsnummer) ?: "Ukjent",
        fødselsdato = hentPersonFødselsdato(barnFødselsnummer),
        saksnr = saksnummer,
        sammeSakSomBehandling = behandling.saksnummer == saksnummer,
        erRevurdering = erRevurdering,
        enhet = sak?.eierfogd?.verdi ?: eierfogd ?: "Ukjent",
        harLøpendeBidrag = løpendeBidrag,
        stønadstype = stønadstype,
        eldsteSøktFraDato = åpneBehandlinger.filter { it.søktFraDato != null }.minOfOrNull { it.søktFraDato!! },
        innkrevesFraDato =
            if (løpendeBidrag) {
                løperBidragFra
            } else {
                null
            },
        opphørsdato = if (løpendeBidrag) løperBidragTil else null,
        åpneBehandlinger = åpneBehandlinger,
        bidragsmottaker =
            RolleDto(
                id = -1,
                ident = bmFødselsnummer,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                navn = hentPersonVisningsnavn(bmFødselsnummer) ?: "Ukjent",
                fødselsdato = hentPersonFødselsdato(bmFødselsnummer),
                delAvOpprinneligBehandling = false,
                erRevurdering = erRevurdering,
                stønadstype = null,
                saksnummer = saksnummer ?: "",
            ),
    )
}

private fun Behandling.tilFFBarnDto() =
    ForholdsmessigFordelingÅpenBehandlingDto(
        søktFraDato = søktFomDato,
        mottattDato = mottattdato,
        stønadstype = stonadstype!!,
        behandlerEnhet = behandlerEnhet,
        behandlingId = id,
        medInnkreving = innkrevingstype == Innkrevingstype.MED_INNKREVING,
        søknadsid = null,
        behandlingstype = søknadstype,
        søktAvType = soknadFra,
        behandlingstema = behandlingstema,
    )

private fun ÅpenSøknadDto.tilFFBarnDto(
    sak: BidragssakDto?,
    eierfogd: String,
) = ForholdsmessigFordelingÅpenBehandlingDto(
    behandlingstema = behandlingstema,
    behandlerEnhet = sak?.eierfogd?.verdi ?: eierfogd!!,
    søktFraDato = LocalDate.now(),
    mottattDato = LocalDate.now(),
    behandlingstype = behandlingstype,
    søktAvType = søktAvType,
    behandlingId = null,
    medInnkreving = innkreving,
    stønadstype = behandlingstema?.tilStønadstype() ?: Stønadstype.BIDRAG,
    søknadsid = søknadsid,
)
