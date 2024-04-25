package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

val BigDecimal.nærmesteHeltall get() = this.setScale(0, RoundingMode.HALF_UP)
val ainntekt12Og3Måneder =
    listOf(
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
    )
val årsinntekterYtelser =
    listOf(
        Inntektsrapportering.OVERGANGSSTØNAD,
        Inntektsrapportering.INTRODUKSJONSSTØNAD,
        Inntektsrapportering.KVALIFISERINGSSTØNAD,
        Inntektsrapportering.SYKEPENGER,
        Inntektsrapportering.FORELDREPENGER,
        Inntektsrapportering.DAGPENGER,
        Inntektsrapportering.AAP,
        Inntektsrapportering.PENSJON,
    )
val inntekstrapporteringerSomKreverGjelderBarn =
    listOf(Inntektsrapportering.BARNETILLEGG, Inntektsrapportering.KONTANTSTØTTE, Inntektsrapportering.BARNETILSYN)
val eksplisitteYtelser =
    setOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.SMÅBARNSTILLEGG,
        Inntektsrapportering.UTVIDET_BARNETRYGD,
    )

val inntekterSomKanHaHullIPerioder = eksplisitteYtelser

fun <T : Comparable<T>> minOfNullable(
    a: T?,
    b: T?,
): T? {
    return if (a == null && b == null) {
        null
    } else if (a == null) {
        b
    } else if (b == null) {
        a
    } else {
        minOf(a, b)
    }
}

fun finnCutoffHusstandsmedlemDatoFom(
    virkningstidspunkt: LocalDate,
    fødselsdato: LocalDate,
) = if (virkningstidspunkt.isAfter(LocalDate.now())) {
    maxOf(virkningstidspunkt.withDayOfMonth(1), fødselsdato)
} else {
    virkningstidspunkt
}

fun finnCutoffSivilstandDatoFom(virkningstidspunkt: LocalDate) =
    minOf(LocalDate.now().withDayOfMonth(1), virkningstidspunkt.withDayOfMonth(1))
