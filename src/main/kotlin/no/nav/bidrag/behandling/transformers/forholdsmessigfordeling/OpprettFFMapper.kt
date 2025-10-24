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
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

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
) {
    if (behandling.roller.any { it.ident == fødselsnummer }) return
    val erBarn = rolletype == Rolletype.BARN
    val rolle =
        Rolle(
            harGebyrsøknad = harGebyrSøknad,
            innkrevesFraDato = innkrevesFraDato?.let { LocalDate.from(it) },
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
) = Rolle(
    behandling = hovedbehandling,
    rolletype = rolletype,
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
            eierfogd = Enhetsnummer(behandling.behandlerEnhet),
            bidragsmottaker = bmFnr,
            løperBidragFra = periodeFra,
            erRevurdering = false,
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
