package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering

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

fun Set<Inntekt>.årsinntekterSortert(inkluderTaMed: Boolean = true) =
    this.filter { !eksplisitteYtelser.contains(it.type) }
        .sortedWith(
            compareBy<Inntekt> {
                it.taMed && inkluderTaMed
            }
                .thenBy {
                    val index =
                        årsinntekterPrioriteringsliste.indexOf(
                            it.type,
                        )
                    if (index == -1 || it.taMed && inkluderTaMed) 1000 else index
                }.thenBy {
                    val index =
                        årsinntekterPrioriteringsliste.indexOf(
                            it.type,
                        )
                    if (it.taMed && inkluderTaMed) {
                        (
                            it.datoFom?.toEpochDay()
                                ?: 1
                        ) + index
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

fun List<Inntekt>.sorterEtterDato() = sortedWith(compareBy({ it.datoFom ?: it.opprinneligFom }))
