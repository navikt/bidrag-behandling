package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering

val årsinntekterPrioriteringsliste =
    listOf(
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAK,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAK,
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

fun Set<Inntekt>.årsinntekterSortert() =
    this.filter { !eksplisitteYtelser.contains(it.type) }
        .sortedWith(
            compareByDescending<Inntekt> { årsinntekterPrioriteringsliste.indexOf(it.type) }.thenByDescending {
                it.datoTom ?: it.opprinneligTom
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
    this.filter { it.type == Inntektsrapportering.BARNETILLEGG }
        .sortedWith(compareBy({ it.datoFom ?: it.opprinneligTom }, { it.gjelderBarn }))

fun List<Inntekt>.sorterEtterDato() =
    this.filter { it.type == Inntektsrapportering.BARNETILLEGG }
        .sortedWith(compareBy({ it.datoFom ?: it.opprinneligTom }, { it.gjelderBarn }))
