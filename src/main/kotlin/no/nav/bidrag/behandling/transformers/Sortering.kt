package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.AndreVoksneIHusstandenDetaljerDto
import no.nav.bidrag.behandling.dto.v2.behandling.TotalBeregningUtgifterDto
import no.nav.bidrag.behandling.transformers.inntekt.erOpprinneligPeriodeInnenforVirkningstidspunktEllerOpphør
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDate

fun Set<Rolle>.sorterForInntektsbildet() =
    sortedWith(
        compareBy<Rolle> {
            when (it.rolletype) {
                Rolletype.BIDRAGSMOTTAKER -> 0
                Rolletype.BIDRAGSPLIKTIG -> 1
                Rolletype.BARN -> 2
                else -> 3
            }
        }.then(sorterPersonEtterEldsteFødselsdato({ it.fødselsdato }, { it.identifikator })),
    )

fun <T> sorterPersonEtterEldsteFødselsdato(
    fødselsdato: (T) -> LocalDate,
    identifikator: (T) -> String?,
) = compareBy(fødselsdato, identifikator)

fun Set<Utgiftspost>.sorter() = sortedBy { it.dato }

fun List<TotalBeregningUtgifterDto>.sorterBeregnetUtgifter() =
    sortedWith(
        compareByDescending<TotalBeregningUtgifterDto> { it.totalGodkjentBeløp }
            .thenBy { it.utgiftstypeVisningsnavn },
    )

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

@OptIn(ExperimentalStdlibApi::class)
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
                    this.inntekter
                        .sortedBy { it.periode.fom }
                        .lastOrNull { it.inntektRapportering == inntekt.inntektRapportering }
                        ?.periode
                        ?.fom
                        ?.year
                        ?: return@filter true
                ligningsinntekter.contains(
                    inntekt.inntektRapportering,
                ) &&
                    inntekt.periode.fom.year == sisteLigningsår
            },
    )

fun Inntekt.erHistorisk(inntekter: Collection<Inntekt>): Boolean {
    if (ligningsinntekter.contains(type)) return erLigningsinntektHistorisk(inntekter)
    if (årsinntekterYtelser.contains(type)) return erYtelseHistorisk()
    return false
}

fun Inntekt.erYtelseHistorisk(): Boolean {
    if (!årsinntekterYtelser.contains(type) || opprinneligFom == null) return false
    val virkningstidspunkt = behandling?.virkningstidspunkt ?: return false
    if (opprinneligTom != null && opprinneligTom!! < virkningstidspunkt) return true
    return false
}

fun Inntekt.erLigningsinntektHistorisk(inntekter: Collection<Inntekt>): Boolean {
    if (!ligningsinntekter.contains(type) || opprinneligFom == null) return false
    val sisteLigningsår =
        inntekter
            .filter { this.tilhørerSammePerson(it) }
            .sortedBy { it.opprinneligFom }
            .lastOrNull { it.type == type }
            ?.opprinneligFom
            ?.year
            ?: return false
    val erHistorisk = ligningsinntekter.contains(type) && opprinneligFom?.year != sisteLigningsår
    return erHistorisk || (datoFom != null && datoFom!! < behandling!!.virkningstidspunktEllerSøktFomDato)
}

fun Collection<Inntekt>.filtrerUtHistoriskeInntekter() =
    this.filter { inntekt ->
        inntekt.taMed || !inntekt.erHistorisk(this)
    }

fun List<Inntekt>.ekskluderYtelserFørVirkningstidspunkt(eksluderYtelserFørVirkningstidspunkt: Boolean = true) =
    filter {
        if (eksluderYtelserFørVirkningstidspunkt && !it.taMed &&
            (årsinntekterYtelser.contains(it.type) || eksplisitteYtelser.contains(it.type))
        ) {
//            val periode = it.opprinneligPeriode ?: return@filter true
//            val virkningstidspunkt =
//                it.behandling?.virkningstidspunktEllerSøktFomDato?.let { YearMonth.from(it) } ?: return@filter true
//            periode.fom >= virkningstidspunkt || periode.til != null && periode.til!! >= virkningstidspunkt
            it.kilde == Kilde.MANUELL || it.erOpprinneligPeriodeInnenforVirkningstidspunktEllerOpphør()
        } else {
            true
        }
    }

fun List<AndreVoksneIHusstandenDetaljerDto>.begrensAntallPersoner(): List<AndreVoksneIHusstandenDetaljerDto> {
    val medRelasjon = this.filter { it.harRelasjonTilBp }

    if (medRelasjon.size >= 10) {
        return medRelasjon.take(10)
    }

    // Hvis det er færre enn 10 med relasjon, tar vi resten fra listen uten relasjon, til vi når 10
    val utenRelasjon = this.filterNot { it.harRelasjonTilBp }.take(10 - medRelasjon.size)

    // Returnerer en kombinert liste av begge, med relasjon først
    return medRelasjon + utenRelasjon
}

fun List<AndreVoksneIHusstandenDetaljerDto>.sorter() =
    sortedWith(
        compareByDescending<AndreVoksneIHusstandenDetaljerDto> { it.harRelasjonTilBp }
            .thenBy { it.navn.split(" ").last() },
    )

fun Set<Inntekt>.årsinntekterSortert(
    sorterTaMed: Boolean = true,
    inkluderHistoriskeInntekter: Boolean = false,
) = when (inkluderHistoriskeInntekter) {
    true -> this.filter { !eksplisitteYtelser.contains(it.type) }
    else -> this.filter { !eksplisitteYtelser.contains(it.type) }.filtrerUtHistoriskeInntekter()
}.sortedWith(
    compareBy<Inntekt> {
        it.taMed && sorterTaMed
    }.thenBy {
        val index =
            årsinntekterPrioriteringsliste.indexOf(
                it.type,
            )
        if (index == -1 || it.taMed && sorterTaMed) 1000 else index
    }.thenBy {
        val manuelleInntekterPrioritering = manuelleInntekter.map { it.name }.sorted()
        val index =
            årsinntekterPrioriteringsliste
                .indexOf(
                    it.type,
                ).let { prioritering ->
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

fun Husstandsmedlem.erSøknadsbarn() =
    this.behandling.søknadsbarn
        .map { it.ident }
        .contains(this.ident)

fun Set<Husstandsmedlem>.sortert() =
    sortedWith(
        compareByDescending<Husstandsmedlem> { it.erSøknadsbarn() }
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
