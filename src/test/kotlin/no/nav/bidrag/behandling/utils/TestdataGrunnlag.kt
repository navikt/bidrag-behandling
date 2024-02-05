package no.nav.bidrag.behandling.utils

import no.nav.bidrag.domene.enums.barnetillegg.Barnetilleggstype
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.Ansettelsesdetaljer
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.Permisjon
import no.nav.bidrag.transport.behandling.grunnlag.response.Permittering
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

fun opprettArbeidsforholdGrunnlagListe() =
    listOf(
        ArbeidsforholdGrunnlagDto(
            partPersonId = testdataBM.ident,
            startdato = LocalDate.parse("2008-01-01"),
            sluttdato = LocalDate.parse("2021-12-31"),
            arbeidsgiverNavn = "Snekker Hansen",
            arbeidsgiverOrgnummer = "88123123",
            ansettelsesdetaljerListe =
                listOf(
                    Ansettelsesdetaljer(
                        periodeFra = YearMonth.parse("2008-01"),
                        periodeTil = YearMonth.parse("2009-01"),
                        arbeidsforholdType = "Ordinaer",
                        arbeidstidsordningBeskrivelse = "Dagtid",
                        ansettelsesformBeskrivelse = "Dagtid",
                        yrkeBeskrivelse = "KONTORLEDER",
                        antallTimerPrUke = 28.5,
                        avtaltStillingsprosent = 80.0,
                        sisteStillingsprosentendringDato = null,
                        sisteLønnsendringDato = null,
                    ),
                    Ansettelsesdetaljer(
                        periodeFra = YearMonth.parse("2008-01"),
                        periodeTil = YearMonth.parse("2022-01"),
                        arbeidsforholdType = "Ordinaer",
                        arbeidstidsordningBeskrivelse = "Dagtid",
                        ansettelsesformBeskrivelse = "Dagtid",
                        yrkeBeskrivelse = "KONTORLEDER",
                        antallTimerPrUke = 37.5,
                        avtaltStillingsprosent = 100.0,
                        sisteStillingsprosentendringDato = LocalDate.parse("2009-01-01"),
                        sisteLønnsendringDato = LocalDate.parse("2009-01-01"),
                    ),
                ),
            permisjonListe =
                listOf(
                    Permisjon(
                        startdato = LocalDate.parse("2015-01-01"),
                        sluttdato = LocalDate.parse("2015-06-01"),
                        beskrivelse = "Foreldrepermisjon",
                        prosent = 50.0,
                    ),
                ),
            permitteringListe =
                listOf(
                    Permittering(
                        startdato = LocalDate.parse("2009-01-01"),
                        sluttdato = LocalDate.parse("2010-01-01"),
                        beskrivelse = "Finanskrise",
                        prosent = 50.0,
                    ),
                ),
        ),
        ArbeidsforholdGrunnlagDto(
            partPersonId = testdataBM.ident,
            startdato = LocalDate.parse("2022-01-01"),
            sluttdato = null,
            arbeidsgiverNavn = "Snekker Bamsen",
            arbeidsgiverOrgnummer = "1233123123",
            ansettelsesdetaljerListe =
                listOf(
                    Ansettelsesdetaljer(
                        periodeFra = YearMonth.parse("2022-01"),
                        periodeTil = null,
                        arbeidsforholdType = "Ordinaer",
                        arbeidstidsordningBeskrivelse = "Dagtid",
                        ansettelsesformBeskrivelse = "Dagtid",
                        yrkeBeskrivelse = "KONTORLEDER",
                        antallTimerPrUke = 37.5,
                        avtaltStillingsprosent = 100.0,
                        sisteStillingsprosentendringDato = null,
                        sisteLønnsendringDato = null,
                    ),
                ),
            permisjonListe = emptyList(),
            permitteringListe = emptyList(),
        ),
        ArbeidsforholdGrunnlagDto(
            partPersonId = testdataBarn1.ident,
            startdato = LocalDate.parse("2023-01-01"),
            sluttdato = null,
            arbeidsgiverNavn = "Barnehage Kransen",
            arbeidsgiverOrgnummer = "45454545",
            ansettelsesdetaljerListe =
                listOf(
                    Ansettelsesdetaljer(
                        periodeFra = YearMonth.parse("2023-01"),
                        periodeTil = null,
                        arbeidsforholdType = "Ordinaer",
                        arbeidstidsordningBeskrivelse = "Dagtid",
                        ansettelsesformBeskrivelse = "Dagtid",
                        yrkeBeskrivelse = "ASSISTENT",
                        antallTimerPrUke = 10.0,
                        avtaltStillingsprosent = 50.0,
                        sisteStillingsprosentendringDato = null,
                        sisteLønnsendringDato = null,
                    ),
                ),
            permisjonListe = emptyList(),
            permitteringListe = emptyList(),
        ),
    )

fun opprettAinntektGrunnlagListe() =
    listOf(
        AinntektGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2023-01-01"),
            ainntektspostListe =
                listOf(
                    AinntektspostDto(
                        utbetalingsperiode = "2023-01",
                        opptjeningsperiodeFra = LocalDate.parse("2022-01-31"),
                        opptjeningsperiodeTil = LocalDate.parse("2023-01-01"),
                        opplysningspliktigId = "123213",
                        virksomhetId = "123",
                        fordelType = "kontantytelse",
                        beskrivelse = "fastloenn",
                        inntektType = "LOENNSINNTEKT",
                        belop = BigDecimal(60000),
                        etterbetalingsperiodeFra = null,
                        etterbetalingsperiodeTil = null,
                    ),
                    AinntektspostDto(
                        utbetalingsperiode = "2023-01",
                        opptjeningsperiodeFra = LocalDate.parse("2022-05-31"),
                        opptjeningsperiodeTil = LocalDate.parse("2023-08-01"),
                        opplysningspliktigId = "123213",
                        virksomhetId = "123",
                        fordelType = "kontantytelse",
                        beskrivelse = "fastloenn",
                        inntektType = "LOENNSINNTEKT",
                        belop = BigDecimal(70000),
                        etterbetalingsperiodeFra = null,
                        etterbetalingsperiodeTil = null,
                    ),
                ),
        ),
    )

fun opprettSkattegrunnlagGrunnlagListe() =
    listOf(
        SkattegrunnlagGrunnlagDto(
            personId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2023-01-01"),
            skattegrunnlagspostListe =
                listOf(
                    SkattegrunnlagspostDto(
                        skattegrunnlagType = "ORDINÆR",
                        inntektType = "annenArbeidsinntekt",
                        belop = BigDecimal.valueOf(5000.0),
                    ),
                ),
        ),
        SkattegrunnlagGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2023-01-01"),
            skattegrunnlagspostListe =
                listOf(
                    SkattegrunnlagspostDto(
                        skattegrunnlagType = "ORDINÆR",
                        inntektType = "annenArbeidsinntekt",
                        belop = BigDecimal.valueOf(5000.0),
                    ),
                    SkattegrunnlagspostDto(
                        skattegrunnlagType = "ORDINÆR",
                        inntektType = "annenSkattepliktigKapitalinntektFraAnnetFinansprodukt",
                        belop = BigDecimal.valueOf(4000.0),
                    ),
                ),
        ),
        SkattegrunnlagGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2021-01-01"),
            periodeTil = LocalDate.parse("2022-01-01"),
            skattegrunnlagspostListe =
                listOf(
                    SkattegrunnlagspostDto(
                        skattegrunnlagType = "ORDINÆR",
                        inntektType = "annenArbeidsinntekt",
                        belop = BigDecimal.valueOf(4000.0),
                    ),
                    SkattegrunnlagspostDto(
                        skattegrunnlagType = "ORDINÆR",
                        inntektType = "annenSkattepliktigKapitalinntektFraAnnetFinansprodukt",
                        belop = BigDecimal.valueOf(3000.0),
                    ),
                ),
        ),
    )

fun opprettUtvidetBarnetrygdGrunnlagListe() =
    listOf(
        UtvidetBarnetrygdGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-03-30"),
            beløp = BigDecimal(5000),
            manueltBeregnet = false,
        ),
        UtvidetBarnetrygdGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2022-04-01"),
            periodeTil = LocalDate.parse("2022-12-31"),
            beløp = BigDecimal(6000),
            manueltBeregnet = true,
        ),
        UtvidetBarnetrygdGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2023-01-01"),
            periodeTil = null,
            beløp = BigDecimal(3000),
            manueltBeregnet = true,
        ),
    )

fun opprettSmåbarnstillegListe() =
    listOf(
        SmåbarnstilleggGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-03-30"),
            beløp = BigDecimal(5000),
            manueltBeregnet = false,
        ),
        SmåbarnstilleggGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2022-04-01"),
            periodeTil = LocalDate.parse("2022-12-31"),
            beløp = BigDecimal(2000),
            manueltBeregnet = true,
        ),
        SmåbarnstilleggGrunnlagDto(
            personId = testdataBM.ident,
            periodeFra = LocalDate.parse("2023-01-01"),
            periodeTil = null,
            beløp = BigDecimal(1000),
            manueltBeregnet = true,
        ),
        SmåbarnstilleggGrunnlagDto(
            personId = testdataBP.ident,
            periodeFra = LocalDate.parse("2023-01-01"),
            periodeTil = null,
            beløp = BigDecimal(1000),
            manueltBeregnet = true,
        ),
    )

fun opprettBarnetilleggListe() =
    listOf(
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBP.ident,
            barnPersonId = testdataBarn1.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-04-30"),
            beløpBrutto = BigDecimal(1000),
            barnType = "FELLES",
        ),
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-04-30"),
            beløpBrutto = BigDecimal(1000),
            barnType = "FELLES",
        ),
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2022-05-01"),
            periodeTil = LocalDate.parse("2022-12-31"),
            beløpBrutto = BigDecimal(2000),
            barnType = "FELLES",
        ),
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-06-30"),
            beløpBrutto = BigDecimal(3000),
            barnType = "FELLES",
        ),
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn2.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-12-31"),
            beløpBrutto = BigDecimal(2000),
            barnType = "FELLES",
        ),
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn2.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2023-01-01"),
            periodeTil = LocalDate.parse("2023-12-31"),
            beløpBrutto = BigDecimal(1000),
            barnType = "FELLES",
        ),
        BarnetilleggGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataHusstandsmedlem1.ident,
            barnetilleggType = Barnetilleggstype.PENSJON.toString(),
            periodeFra = LocalDate.parse("2023-07-01"),
            periodeTil = LocalDate.parse("2023-12-31"),
            beløpBrutto = BigDecimal(1000),
            barnType = "SÆRKULL",
        ),
    )

fun opprettKontantstøtteListe() =
    listOf(
        KontantstøtteGrunnlagDto(
            partPersonId = testdataBP.ident,
            barnPersonId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = null,
            beløp = 1000,
        ),
        KontantstøtteGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-07-31"),
            beløp = 1000,
        ),
        KontantstøtteGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2023-01-01"),
            periodeTil = LocalDate.parse("2023-03-30"),
            beløp = 1000,
        ),
        KontantstøtteGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2024-01-01"),
            periodeTil = null,
            beløp = 1000,
        ),
        KontantstøtteGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn2.ident,
            periodeFra = LocalDate.parse("2024-02-01"),
            periodeTil = null,
            beløp = 1000,
        ),
    )

fun opprettBarnetilsynListe() =
    listOf(
        BarnetilsynGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2022-01-01"),
            periodeTil = LocalDate.parse("2022-07-31"),
            beløp = 1000,
            tilsynstype = Tilsynstype.HELTID,
            skolealder = Skolealder.IKKE_ANGITT,
        ),
        BarnetilsynGrunnlagDto(
            partPersonId = testdataBM.ident,
            barnPersonId = testdataBarn1.ident,
            periodeFra = LocalDate.parse("2024-01-01"),
            periodeTil = null,
            beløp = 5000,
            tilsynstype = Tilsynstype.DELTID,
            skolealder = Skolealder.OVER,
        ),
    )
