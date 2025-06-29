package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteBeløpshistorikkGrunnlag
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.behandling.OpphørsdetaljerRolleDto.EksisterendeOpphørsvedtakDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadPeriodeDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth

fun Vedtakstype.kreverGrunnlag() = !listOf(Vedtakstype.ALDERSJUSTERING).contains(this)

val BigDecimal.nærmesteHeltall get() = this.setScale(0, RoundingMode.HALF_UP)
val ainntekt12Og3Måneder =
    listOf(
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
    )

val ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt =
    listOf(
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

fun Behandling.tilType() = bestemTypeBehandling(stonadstype, engangsbeloptype)

fun Behandling.tilTypeBoforhold() = bestemTypeBehandling18ÅrsBidrag(stonadstype, engangsbeloptype)

fun Behandling.erBidrag() = tilType() == TypeBehandling.BIDRAG_18_ÅR || tilType() == TypeBehandling.BIDRAG

fun Behandling.erSærbidrag() = tilType() == TypeBehandling.SÆRBIDRAG

fun Behandling.erForskudd() = tilType() == TypeBehandling.FORSKUDD

fun bestemTypeBehandling18ÅrsBidrag(
    stønadstype: Stønadstype?,
    engangsbeløptype: Engangsbeløptype?,
) = if (engangsbeløptype != null && engangsbeløptype == Engangsbeløptype.SÆRBIDRAG) {
    TypeBehandling.SÆRBIDRAG
} else if (stønadstype == Stønadstype.FORSKUDD) {
    TypeBehandling.FORSKUDD
} else if (stønadstype == Stønadstype.BIDRAG18AAR) {
    TypeBehandling.BIDRAG_18_ÅR
} else {
    TypeBehandling.BIDRAG
}

fun bestemTypeBehandling(
    stønadstype: Stønadstype?,
    engangsbeløptype: Engangsbeløptype?,
) = if (engangsbeløptype != null && engangsbeløptype == Engangsbeløptype.SÆRBIDRAG) {
    TypeBehandling.SÆRBIDRAG
} else if (stønadstype == Stønadstype.FORSKUDD) {
    TypeBehandling.FORSKUDD
} else {
    TypeBehandling.BIDRAG
}

fun <T : Comparable<T>> minOfNullable(
    a: T?,
    b: T?,
): T? =
    if (a == null && b == null) {
        null
    } else if (a == null) {
        b
    } else if (b == null) {
        a
    } else {
        minOf(a, b)
    }

fun finnCutoffDatoFom(
    virkningstidspunkt: LocalDate,
    fødselsdato: LocalDate? = null,
) = if (virkningstidspunkt.isAfter(LocalDate.now()) && fødselsdato != null) {
    maxOf(virkningstidspunkt.withDayOfMonth(1), fødselsdato)
} else {
    virkningstidspunkt
}

fun LocalDate.erOverEllerLik18År() = plusYears(18).isBefore(LocalDate.now()) || plusYears(18).isEqual(LocalDate.now())

fun LocalDate?.erUnder12År(basertPåDato: LocalDate = LocalDate.now()) =
    Period
        .between(
            this,
            basertPåDato
                .plusYears(1)
                .withMonth(1)
                .withDayOfMonth(1),
        ).years < 13

fun Behandling.finnesLøpendeBidragForRolle(rolle: Rolle): Boolean = finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(rolle) != null

fun Behandling.finnPerioderHvorDetLøperBidrag(rolle: Rolle): List<ÅrMånedsperiode> {
    val eksisterendeVedtak =
        grunnlag.hentSisteBeløpshistorikkGrunnlag(rolle.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR)
            ?: grunnlag.hentSisteBeløpshistorikkGrunnlag(rolle.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG)
            ?: return emptyList()
    val stønad = eksisterendeVedtak.konvertereData<StønadDto>() ?: return emptyList()
    return stønad.periodeListe
        .filter {
            it.beløp != null
        }.map { it.periode }
        .mergePeriods()
}

fun List<ÅrMånedsperiode>.mergePeriods(): List<ÅrMånedsperiode> {
    if (isEmpty()) return emptyList()

    val sortedPeriods = sortedBy { it.fom }
    val mergedPeriods = mutableListOf<ÅrMånedsperiode>()

    var currentPeriod = sortedPeriods.first()

    for (period in sortedPeriods.drop(1)) {
        if (currentPeriod.til == null || period.fom.isAfter(currentPeriod.til)) {
            mergedPeriods.add(currentPeriod)
            currentPeriod = period
        } else {
            val tilDato =
                when {
                    period.til == null -> null
                    else -> maxOf(currentPeriod.til!!, period.til!!)
                }
            currentPeriod = ÅrMånedsperiode(currentPeriod.fom, tilDato)
        }
    }

    mergedPeriods.add(currentPeriod)
    return mergedPeriods
}

fun Behandling.finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(rolle: Rolle): StønadPeriodeDto? {
    val eksisterendeVedtak =
        grunnlag.hentSisteBeløpshistorikkGrunnlag(rolle.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR)
            ?: grunnlag.hentSisteBeløpshistorikkGrunnlag(rolle.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG)
            ?: return null
    val stønad = eksisterendeVedtak.konvertereData<StønadDto>() ?: return null
    val sistePeriode = stønad.periodeListe.maxByOrNull { it.periode.fom } ?: return null
    return if (sistePeriode.periode.til == null || sistePeriode.periode.til!! > YearMonth.from(søktFomDato)) {
        sistePeriode
    } else {
        null
    }
}

fun Behandling.finnEksisterendeVedtakMedOpphør(rolle: Rolle): EksisterendeOpphørsvedtakDto? {
    val opphørPeriode = finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(rolle)?.takeIf { it.periode.til != null } ?: return null
    return EksisterendeOpphørsvedtakDto(
        vedtaksid = opphørPeriode.vedtaksid,
        opphørsdato = opphørPeriode.periode.til!!.atDay(1),
        vedtaksdato = opphørPeriode.gyldigFra.toLocalDate(),
    )
}
