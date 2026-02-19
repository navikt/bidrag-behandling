package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderBarn
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderRolle
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.minified.BehandlingSimple
import no.nav.bidrag.behandling.dto.v1.behandling.EtterfølgendeVedtakDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpphørsdetaljerRolleDto.EksisterendeOpphørsvedtakDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentVedtak
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.InntektBeløpstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.sak.Stønadsid
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadPeriodeDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkGrunnlag
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.response.StønadsendringDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakForStønad
import no.nav.bidrag.transport.behandling.vedtak.response.erIndeksEllerAldersjustering
import no.nav.bidrag.transport.behandling.vedtak.response.virkningstidspunkt
import no.nav.bidrag.transport.felles.toYearMonth
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.Year
import java.time.YearMonth

val grunnlagsreferanseSimulert = "simulert_grunnlag"
val vedtakstyperIkkeBeregning =
    listOf(Vedtakstype.ALDERSJUSTERING, Vedtakstype.INDEKSREGULERING, Vedtakstype.OPPHØR, Vedtakstype.ALDERSOPPHØR)

fun Vedtakstype.opprettForsendelse() = !listOf(Vedtakstype.ALDERSJUSTERING).contains(this)

fun Vedtakstype.kreverGrunnlag() = !listOf(Vedtakstype.ALDERSJUSTERING, Vedtakstype.INNKREVING).contains(this)

fun Behandling.skalInnkrevingKunneUtsettes() =
    erBidrag() && !listOf(Vedtakstype.ALDERSJUSTERING, Vedtakstype.OPPHØR, Vedtakstype.INNKREVING).contains(vedtakstype) &&
        !erKlageEllerOmgjøring && søknadsbarn.none { finnesLøpendeBidragForRolle(it) }

fun StønadsendringDto.tilStønadsid() =
    Stønadsid(
        type = type,
        kravhaver = kravhaver,
        skyldner = skyldner,
        sak = sak,
    )

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
val eksplisitteYtelserGrunnlagsdatatype =
    setOf(
        Grunnlagsdatatype.BARNETILLEGG,
        Grunnlagsdatatype.KONTANTSTØTTE,
        Grunnlagsdatatype.SMÅBARNSTILLEGG,
        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
    )
val inntekterSomKanHaHullIPerioder = eksplisitteYtelser

fun Behandling.tilType() = bestemTypeBehandling(stonadstype, engangsbeloptype)

fun BehandlingSimple.tilType() = bestemTypeBehandling(stønadstype, engangsbeløptype)

fun Behandling.tilTypeBoforhold() = bestemTypeBehandling18ÅrsBidrag(stonadstype, engangsbeloptype)

fun Behandling.erBidrag() = tilType() == TypeBehandling.BIDRAG_18_ÅR || tilType() == TypeBehandling.BIDRAG

fun BehandlingSimple.erBidrag() = tilType() == TypeBehandling.BIDRAG_18_ÅR || tilType() == TypeBehandling.BIDRAG

fun Behandling.erSærbidrag() = tilType() == TypeBehandling.SÆRBIDRAG

fun Behandling.erForskudd() = tilType() == TypeBehandling.FORSKUDD

fun Behandling.erDirekteAvslag() = søknadsbarn.all { it.avslag != null }

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

fun Behandling.tilStønadsid(søknadsbarn: Rolle) =
    Stønadsid(
        stonadstype!!,
        Personident(søknadsbarn.ident!!),
        Personident(bidragspliktig!!.ident!!),
        Saksnummer(saksnummer),
    )

fun <T : Comparable<T>> maxOfNullable(
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
        maxOf(a, b)
    }

fun <T : Comparable<T>> minOfNullable(vararg values: T?): T? {
    val nonNull = values.filterNotNull()
    return nonNull.minOrNull()
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

fun Behandling.finnesLøpendeForskuddForRolle(rolle: Rolle): Boolean =
    finnSistePeriodeLøpendeForskuddPeriodeInnenforSøktFomDato(rolle) != null

fun Behandling.finnesLøpendeBidragForRolle(rolle: Rolle): Boolean = finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(rolle) != null

fun Stønadstype.tilGrunnlagsdatatypeBeløpshistorikk() =
    when (this) {
        Stønadstype.BIDRAG -> Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG
        Stønadstype.BIDRAG18AAR -> Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR
        Stønadstype.FORSKUDD -> Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD
        else -> throw IllegalArgumentException("Ukjent stønadstype: $this")
    }

fun Stønadstype.tilGrunnlagstypeBeløpshistorikk() =
    when (this) {
        Stønadstype.BIDRAG -> Grunnlagstype.BELØPSHISTORIKK_BIDRAG
        Stønadstype.BIDRAG18AAR -> Grunnlagstype.BELØPSHISTORIKK_BIDRAG_18_ÅR
        Stønadstype.FORSKUDD -> Grunnlagstype.BELØPSHISTORIKK_FORSKUDD
        else -> throw IllegalArgumentException("Ukjent stønadstype: $this")
    }

fun Behandling.løperBidragEtterEldsteVirkning(rolle: Rolle): Boolean {
    val fraPeriodeLøperBidrag = finnPerioderHvorDetLøperBidrag(rolle).minByOrNull { it.fom }?.fom
    val tilPeriodeLøperBidrag = finnPerioderHvorDetLøperBidrag(rolle).maxByOrNull { it.fom }?.til
    if (fraPeriodeLøperBidrag == null) return false
    if (tilPeriodeLøperBidrag == null) return true
    return tilPeriodeLøperBidrag >= eldsteVirkningstidspunkt.toYearMonth()
}

fun Behandling.finnPeriodeLøperBidrag(rolle: Rolle): ÅrMånedsperiode? {
    val fraPeriodeLøperBidrag = finnPerioderHvorDetLøperBidrag(rolle).minByOrNull { it.fom }?.fom
    val tilPeriodeLøperBidrag = finnPerioderHvorDetLøperBidrag(rolle).maxByOrNull { it.fom }?.til
    val fraPeriodePrivatAvtale =
        privatAvtale
            .find {
                it.rolle?.id == rolle.id
            }?.perioderInnkreving
            ?.minByOrNull { it.fom }
            ?.fom
            ?.toYearMonth()
    val tilPeriodePrivatAvtale =
        privatAvtale
            .find {
                it.rolle?.id == rolle.id
            }?.perioderInnkreving
            ?.maxByOrNull { it.fom }
            ?.tom
            ?.plusMonths(1)
            ?.withDayOfMonth(1)
            ?.toYearMonth()
    return minOfNullable(fraPeriodeLøperBidrag, fraPeriodePrivatAvtale)?.let {
        ÅrMånedsperiode(it, maxOfNullable(tilPeriodeLøperBidrag, tilPeriodePrivatAvtale))
    }
}

fun Behandling.finnPerioderHvorDetLøperBidrag(rolle: Rolle): List<ÅrMånedsperiode> {
    val eksisterendeVedtak = hentGrunnlagBeløpshistorikkForRolle(rolle, erKlageEllerOmgjøring) ?: return emptyList()
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

fun Behandling.hentEtterfølgendeVedtak(rolle: Rolle) =
    grunnlag.hentSisteGrunnlagSomGjelderBarn(rolle.ident!!, Grunnlagsdatatype.ETTERFØLGENDE_VEDTAK)

fun Behandling.hentNesteEtterfølgendeVedtakFelles(): EtterfølgendeVedtakDto? {
    val grunnlag = søknadsbarn.mapNotNull { hentNesteEtterfølgendeVedtak(it) }
    return grunnlag.sortedByDescending { it.virkningstidspunkt }.find {
        val vedtak = hentVedtak(it.vedtaksid)
        val kravhavere = vedtak?.stønadsendringListe?.map { hentNyesteIdent(it.kravhaver.verdi) } ?: emptyList()

        val søknadsbarnIdenter = søknadsbarn.map { it.ident }
        søknadsbarnIdenter.sortedBy { it }.toSet() == kravhavere.sortedBy { it }.toSet()
    }
}

fun Behandling.hentNesteEtterfølgendeVedtak(rolle: Rolle): EtterfølgendeVedtakDto? {
    val grunnlag =
        hentEtterfølgendeVedtak(rolle)
    return grunnlag
        .konvertereData<List<VedtakForStønad>>()
        ?.asSequence()
        ?.filter { it.virkningstidspunkt != null }
        ?.groupBy { it.virkningstidspunkt }
        ?.asSequence()
        ?.mapNotNull { (_, group) -> group.maxByOrNull { it.vedtakstidspunkt } }
        ?.filter { !it.type.erIndeksEllerAldersjustering && it.stønadsendring.periodeListe.isNotEmpty() }
        ?.filter { it.virkningstidspunkt!!.isAfter(rolle.virkningstidspunkt!!.toYearMonth()) }
        ?.map {
            EtterfølgendeVedtakDto(
                vedtaksttidspunkt = it.vedtakstidspunkt,
                vedtakstype = it.type,
                virkningstidspunkt = it.virkningstidspunkt!!,
                sistePeriodeDatoFom = it.stønadsendring.periodeListe.maxOf { it.periode.fom },
                opphørsdato =
                    it.stønadsendring.periodeListe
                        .filter { it.beløp == null }
                        .maxOfOrNull { it.periode.fom },
                vedtaksid = it.vedtaksid,
            )
        }?.minByOrNull { it.virkningstidspunkt }
}

fun Behandling.hentGrunnlagBeløpshistorikkForRolle(
    rolle: Rolle,
    grunnlagFraVedtakSomSkalOmgjøres: Boolean? = null,
) = if (rolle.stønadstype == Stønadstype.BIDRAG18AAR) {
    grunnlag.hentSisteGrunnlagSomGjelderRolle(
        rolle,
        Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR,
        grunnlagFraVedtakSomSkalOmgjøres,
    )
} else if (rolle.stønadstype == Stønadstype.BIDRAG) {
    grunnlag.hentSisteGrunnlagSomGjelderRolle(
        rolle,
        Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
        grunnlagFraVedtakSomSkalOmgjøres,
    )
} else {
    grunnlag.hentSisteGrunnlagSomGjelderRolle(
        rolle,
        Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR,
        grunnlagFraVedtakSomSkalOmgjøres,
    ) ?: grunnlag.hentSisteGrunnlagSomGjelderRolle(
        rolle,
        Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
        grunnlagFraVedtakSomSkalOmgjøres,
    )
}

fun Behandling.finnSistePeriodeLøpendeForskuddPeriodeInnenforSøktFomDato(rolle: Rolle): StønadPeriodeDto? {
    val eksisterendeVedtak =
        grunnlag.hentSisteGrunnlagSomGjelderBarn(rolle.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD, false)
            ?: return null
    val stønad = eksisterendeVedtak.konvertereData<StønadDto>() ?: return null
    val sistePeriode = stønad.periodeListe.maxByOrNull { it.periode.fom } ?: return null
    return if (sistePeriode.periode.til == null || sistePeriode.periode.til!! > YearMonth.from(søktFomDato)) {
        sistePeriode
    } else {
        null
    }
}

fun Behandling.finnPeriodeLøpendePeriodeInnenforSøktFomDato(rolle: Rolle): ÅrMånedsperiode? {
    val eksisterendeVedtak = hentGrunnlagBeløpshistorikkForRolle(rolle, false)
    val stønad = eksisterendeVedtak.konvertereData<StønadDto>() ?: return null
    if (stønad.periodeListe.isEmpty()) {
        return null
    }
    return ÅrMånedsperiode(
        stønad.periodeListe.minOf { it.periode.fom },
        stønad.periodeListe
            .maxByOrNull { it.periode.fom }
            ?.periode
            ?.til,
    )
}

fun Behandling.finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(rolle: Rolle): StønadPeriodeDto? {
    val eksisterendeVedtak = hentGrunnlagBeløpshistorikkForRolle(rolle, false)
    val stønad = eksisterendeVedtak.konvertereData<StønadDto>() ?: return null
    val sistePeriode = stønad.periodeListe.maxByOrNull { it.periode.fom } ?: return null
    return if (sistePeriode.periode.til == null || sistePeriode.periode.til!! > YearMonth.from(eldsteSøktFomDato)) {
        sistePeriode
    } else {
        null
    }
}

fun Rolle.løperBidragFørOpphør() =
    opphørsdato != null && finnLøperBidragFra() != null &&
        opphørsdato!! > behandling.eldsteVirkningstidspunkt &&
        opphørsdato!!.toYearMonth() > finnLøperBidragFra()!!

fun Rolle.finnLøperBidragFra() = behandling.finnPeriodeLøperBidrag(this)?.fom

fun Rolle.finnEksisterendeVedtakMedOpphørForRolle(): EksisterendeOpphørsvedtakDto? = behandling.finnEksisterendeVedtakMedOpphør(this)

fun Behandling.finnEksisterendeVedtakMedOpphør(rolle: Rolle): EksisterendeOpphørsvedtakDto? {
    val opphørPeriode = finnSistePeriodeLøpendePeriodeInnenforSøktFomDato(rolle)?.takeIf { it.periode.til != null } ?: return null
    return EksisterendeOpphørsvedtakDto(
        vedtaksid = opphørPeriode.vedtaksid,
        opphørsdato = opphørPeriode.periode.til!!.atDay(1),
        vedtaksdato = opphørPeriode.gyldigFra.toLocalDate(),
    )
}

fun Behandling.opprettStønadDto(
    søknadsbarn: Rolle,
    grunnlag: BeløpshistorikkGrunnlag?,
) = StønadDto(
    sak = Saksnummer(saksnummer),
    skyldner = if (stonadstype == Stønadstype.FORSKUDD) personIdentNav else Personident(bidragspliktig!!.ident!!),
    kravhaver = Personident(søknadsbarn.ident!!),
    mottaker = Personident(bidragsmottaker!!.ident!!),
    førsteIndeksreguleringsår = grunnlag?.nesteIndeksreguleringsår ?: (Year.now().value + 1),
    nesteIndeksreguleringsår = grunnlag?.nesteIndeksreguleringsår ?: (Year.now().value + 1),
    innkreving = Innkrevingstype.MED_INNKREVING,
    opprettetAv = "",
    opprettetTidspunkt = grunnlag?.tidspunktInnhentet ?: opprettetTidspunkt,
    endretAv = null,
    endretTidspunkt = null,
    stønadsid = 1,
    type = stonadstype!!,
    periodeListe =
        grunnlag?.beløpshistorikk?.map {
            StønadPeriodeDto(
                periode = it.periode,
                periodeid = 1,
                stønadsid = 1,
                vedtaksid = it.vedtaksid ?: 1,
                beløp = it.beløp,
                gyldigFra = grunnlag.tidspunktInnhentet,
                gyldigTil = null,
                valutakode = it.valutakode ?: "NOK",
                resultatkode = "",
                periodeGjortUgyldigAvVedtaksid = null,
            )
        } ?: emptyList(),
)

fun opprettHentGrunnlagDto() =
    HentGrunnlagDto(
        ainntektListe = emptyList(),
        skattegrunnlagListe = emptyList(),
        arbeidsforholdListe = emptyList(),
        barnetilsynListe = emptyList(),
        barnetilleggListe = emptyList(),
        kontantstøtteListe = emptyList(),
        utvidetBarnetrygdListe = emptyList(),
        småbarnstilleggListe = emptyList(),
        sivilstandListe = emptyList(),
        husstandsmedlemmerOgEgneBarnListe = emptyList(),
        feilrapporteringListe = emptyList(),
        hentetTidspunkt = LocalDateTime.now(),
        tilleggsstønadBarnetilsynListe = emptyList(),
    )
