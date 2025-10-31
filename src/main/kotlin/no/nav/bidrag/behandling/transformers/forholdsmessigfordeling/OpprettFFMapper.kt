package no.nav.bidrag.behandling.transformers.forholdsmessigfordeling

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
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingBarnDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingÅpenBehandlingDto
import no.nav.bidrag.behandling.service.SakKravhaver
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.transport.behandling.beregning.felles.ÅpenSøknadDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.felles.toYearMonth
import no.nav.bidrag.transport.sak.BidragssakDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

fun Rolle.fjernSøknad(søknadsid: Long) {
    if (forholdsmessigFordeling == null) return
    forholdsmessigFordeling!!.søknader = forholdsmessigFordeling!!.søknader.filter { it.søknadsid != søknadsid }.toMutableSet()
}

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
    )

fun opprettRolle(
    behandling: Behandling,
    rolletype: Rolletype,
    fødselsnummer: String,
    stønadstype: Stønadstype = Stønadstype.BIDRAG,
    harGebyrSøknad: Boolean = false,
    innbetaltBeløp: BigDecimal? = null,
    ffDetaljer: ForholdsmessigFordelingRolle,
    innkrevesFraDato: YearMonth? = null,
    medInnkreving: Boolean? = null,
): Rolle {
    behandling.roller.find { it.ident == fødselsnummer }?.let {
        return it
    }
    val erBarn = rolletype == Rolletype.BARN
    val rolle =
        Rolle(
            harGebyrsøknad = harGebyrSøknad,
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
    behandling.roller.add(rolle)
    return rolle
}

fun Grunnlag.kopierGrunnlag(hovedbehandling: Behandling): Grunnlag =
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

fun Rolle.kopierRolle(
    hovedbehandling: Behandling,
    bmFnr: String?,
    periodeFra: YearMonth? = null,
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
            behandlerenhet = behandling.behandlerEnhet,
            bidragsmottaker = bmFnr,
            løperBidragFra = periodeFra,
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

fun kopierSamvær(
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

fun Underholdskostnad.kopierUnderholdskostnad(hovedbehandling: Behandling) {
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

fun opprettSamværOgUnderholdForBarn(behandling: Behandling) {
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

fun Set<SakKravhaver>.hentForKravhaver(kravhaverIdent: String) = find { it.kravhaver == kravhaverIdent }

fun SakKravhaver.mapSakKravhaverTilForholdsmessigFordelingDto(
    sak: BidragssakDto?,
    behandling: Behandling,
    løpendeBidrag: Boolean = true,
    erRevurdering: Boolean = true,
): ForholdsmessigFordelingBarnDto {
    val bmFødselsnummer = sak?.bidragsmottaker?.fødselsnummer?.verdi ?: bidragsmottaker
    val barnFødselsnummer = kravhaver
    val enhet = sak?.eierfogd?.verdi ?: eierfogd ?: "Ukjent"

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
        innkrevesFraDato =
            if (løperBidragFra != null &&
                løperBidragFra > behandling.søktFomDato.toYearMonth()
            ) {
                løperBidragFra
            } else {
                null
            },
        åpneBehandlinger = åpneBehandlinger.map { it.tilFFBarnDto() } + åpneSøknader.map { it.tilFFBarnDto(sak, enhet) },
        bidragsmottaker =
            RolleDto(
                id = -1,
                ident = bmFødselsnummer,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                navn = hentPersonVisningsnavn(bmFødselsnummer) ?: "Ukjent",
                fødselsdato = hentPersonFødselsdato(bmFødselsnummer),
                delAvOpprinneligBehandling = false,
                erRevurdering = erRevurdering,
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
