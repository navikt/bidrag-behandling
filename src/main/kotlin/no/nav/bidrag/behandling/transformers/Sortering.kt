package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.YearMonth

val årsinntekterPrioriteringsliste =
    listOf(
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
        Inntektsrapportering.OVERGANGSSTØNAD,
        Inntektsrapportering.INTRODUKSJONSSTØNAD,
        Inntektsrapportering.KVALIFISERINGSSTØNAD,
        Inntektsrapportering.SYKEPENGER,
        Inntektsrapportering.FORELDREPENGER,
        Inntektsrapportering.DAGPENGER,
        Inntektsrapportering.AAP,
        Inntektsrapportering.PENSJON,
        Inntektsrapportering.AINNTEKT,
        Inntektsrapportering.KAPITALINNTEKT,
        Inntektsrapportering.LIGNINGSINNTEKT,
    )

val manuelleInntekter = Inntektsrapportering.entries.filter { it.kanLeggesInnManuelt }
val ligningsinntekter =
    listOf(
        Inntektsrapportering.LIGNINGSINNTEKT,
        Inntektsrapportering.KAPITALINNTEKT,
        Inntektsrapportering.AINNTEKT,
    )

fun SummerteInntekter<SummertÅrsinntekt>.filtrerUtHistoriskeInntekter() =
    copy(
        inntekter =
            this.inntekter.filter { inntekt ->
                if (!ligningsinntekter.contains(inntekt.inntektRapportering)) return@filter true
                val sisteLigningsår =
                    this.inntekter.sortedBy { it.periode.fom }
                        .lastOrNull { it.inntektRapportering == inntekt.inntektRapportering }?.periode?.fom?.year
                        ?: return@filter true
                ligningsinntekter.contains(
                    inntekt.inntektRapportering,
                ) && inntekt.periode.fom.year == sisteLigningsår
            },
    )

fun Inntekt.erHistorisk(inntekter: Collection<Inntekt>): Boolean {
    if (!ligningsinntekter.contains(type) || taMed || opprinneligFom == null) return false
    val sisteLigningsår =
        inntekter.sortedBy { it.opprinneligFom }
            .lastOrNull { it.type == type }?.opprinneligFom?.year
            ?: return false
    return ligningsinntekter.contains(type) && opprinneligFom?.year != sisteLigningsår
}

fun Collection<Inntekt>.filtrerUtHistoriskeInntekter() =
    this.filter { inntekt ->
        !inntekt.erHistorisk(this)
    }

fun List<Inntekt>.ekskluderYtelserFørVirkningstidspunkt(eksluderYtelserFørVirkningstidspunkt: Boolean = true) =
    filter {
        if (eksluderYtelserFørVirkningstidspunkt && årsinntekterYtelser.contains(it.type) ||
            eksplisitteYtelser.contains(
                it.type,
            )
        ) {
            val periode = it.opprinneligPeriode ?: return@filter true
            val virkningstidspunkt =
                it.behandling?.virkningstidspunktEllerSøktFomDato?.let { YearMonth.from(it) } ?: return@filter true
            periode.fom >= virkningstidspunkt || periode.til != null && periode.til!! >= virkningstidspunkt
        } else {
            true
        }
    }

fun Set<Inntekt>.årsinntekterSortert(
    sorterTaMed: Boolean = true,
    inkluderHistoriskeInntekter: Boolean = false,
) = when (inkluderHistoriskeInntekter) {
    true -> this.filter { !eksplisitteYtelser.contains(it.type) }
    else -> this.filter { !eksplisitteYtelser.contains(it.type) }.filtrerUtHistoriskeInntekter()
}
    .sortedWith(
        compareBy<Inntekt> {
            it.taMed && sorterTaMed
        }
            .thenBy {
                val index =
                    årsinntekterPrioriteringsliste.indexOf(
                        it.type,
                    )
                if (index == -1 || it.taMed && sorterTaMed) 1000 else index
            }.thenBy {
                val manuelleInntekterPrioritering = manuelleInntekter.map { it.name }.sorted()
                val index =
                    årsinntekterPrioriteringsliste.indexOf(
                        it.type,
                    )
                        .let { prioritering ->
                            if (prioritering == -1) 1000 + manuelleInntekterPrioritering.indexOf(it.type.name) else prioritering
                        }
                if (it.taMed && sorterTaMed) {
                    (
                        it.datoFom?.toEpochDay()
                            ?: 1
                    ) * 1000 + index
                } else {
                    it.opprinneligFom
                }
            },
    )

fun Husstandsbarn.erSøknadsbarn() = this.behandling.søknadsbarn.map { it.ident }.contains(this.ident)

fun Set<Husstandsbarn>.sortert() =
    sortedWith(
        compareByDescending<Husstandsbarn> { it.erSøknadsbarn() }
            .thenByDescending { it.kilde == Kilde.OFFENTLIG }
            .thenBy { it.fødselsdato },
    )

fun List<Inntekt>.sorterEtterDatoOgBarn() =
    sortedWith(
        compareBy({
            it.datoFom ?: it.opprinneligFom
        }, { it.gjelderBarn }),
    )

fun List<Inntekt>.sorterEtterDato() = sortedWith(compareBy { it.datoFom ?: it.opprinneligFom })
