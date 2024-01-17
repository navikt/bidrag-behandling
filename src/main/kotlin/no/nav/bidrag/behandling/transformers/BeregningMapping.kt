package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.Grunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.grunnlag.BeregningInntektRapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import java.time.LocalDate

fun Behandling.tilBeregnGrunnlag(
    bm: Grunnlag,
    søknadsbarn: Grunnlag,
    øvrigeBarnIHusstand: Set<Grunnlag>,
): BeregnGrunnlag {
    val personobjekterBarn = listOf(søknadsbarn) + øvrigeBarnIHusstand

    val bostatusBarn = this.tilGrunnlagBostatus(personobjekterBarn.toSet())
    val inntektBm =
        this.tilGrunnlagInntekt(bm) +
            this.tilGrunnlagInntektKontantstøtte(bm, søknadsbarn) +
            this.tilGrunnlagBarnetillegg(bm, søknadsbarn) +
            this.tilGrunnlagUtvidetbarnetrygd(bm)
    val sivilstandBm = this.tilGrunnlagSivilstand(bm)

    val inntekterBarn = this.tilGrunnlagInntekt(søknadsbarn)
    return BeregnGrunnlag(
        periode = ÅrMånedsperiode(this.virkningsdato!!, this.datoTom?.plusDays(1) ?: LocalDate.MAX),
        søknadsbarnReferanse = søknadsbarn.referanse,
        grunnlagListe =
            personobjekterBarn + bostatusBarn + inntektBm + sivilstandBm + inntekterBarn,
    )
}

fun Behandling.tilGrunnlagSivilstand(bm: Grunnlag): Set<Grunnlag> {
    return this.sivilstand.map {
        Grunnlag(
            grunnlagsreferanseListe = listOf(bm.referanse!!),
            referanse = "sivilstand-${bm.referanse}-${it.datoFom?.toCompactString()}",
            type = Grunnlagstype.SIVILSTAND_PERIODE,
            innhold =
                POJONode(
                    SivilstandPeriode(
                        sivilstand = it.sivilstand,
                        periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom?.plusDays(1)),
                    ),
                ),
        )
    }.toSet()
}

fun Behandling.tilGrunnlagBostatus(grunnlagBarn: Set<Grunnlag>): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    return grunnlagBarn.flatMap {
        val barn = mapper.treeToValue(it.innhold, Person::class.java)
        val bostatusperioderForBarn =
            this.husstandsbarn.first { hb -> hb.ident == barn.ident.verdi }
        oppretteGrunnlagForBostatusperioder(it.referanse!!, bostatusperioderForBarn.perioder)
    }.toSet()
}

private fun oppretteGrunnlagForBostatusperioder(
    personreferanse: String,
    husstandsbarnperioder: Set<Husstandsbarnperiode> = emptySet(),
): Set<Grunnlag> =
    Bostatuskode.entries.flatMap {
        husstandsbarnperioder.filter { p -> p.bostatus == it }.map {
            Grunnlag(
                grunnlagsreferanseListe = listOf(personreferanse),
                referanse = "bostatus-$personreferanse-${it.datoFom?.toCompactString()}",
                type = Grunnlagstype.BOSTATUS_PERIODE,
                innhold =
                    POJONode(
                        BostatusPeriode(
                            bostatus = it.bostatus,
                            manueltRegistrert = it.kilde == Kilde.MANUELL,
                            periode =
                                ÅrMånedsperiode(
                                    it.datoFom!!,
                                    it.datoTom?.plusDays(1),
                                ),
                        ),
                    ),
            )
        }
    }.toSet()

fun Behandling.tilGrunnlagInntekt(gjelder: Grunnlag): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    val personidentGjelder = mapper.treeToValue(gjelder.innhold, Person::class.java).ident

    return inntekter.asSequence().filter { i -> i.taMed }
        .filter { i -> i.ident == personidentGjelder.verdi }
        .filter { i -> i.inntektsrapportering != Inntektsrapportering.KONTANTSTØTTE }.map {
            Grunnlag(
                type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
                // Ta med gjelder referanse fordi samme type inntekt med samme datoFom kan inkluderes for BM/BP/BA
                referanse = "Inntekt_${it.inntektsrapportering}_${gjelder.referanse}_${it.datoFom.toCompactString()}",
                grunnlagsreferanseListe = listOf(gjelder.referanse!!),
                innhold =
                    POJONode(
                        BeregningInntektRapporteringPeriode(
                            beløp = it.belop,
                            periode = ÅrMånedsperiode(it.datoFom, it.datoTom?.plusDays(1)),
                            inntektsrapportering = it.inntektsrapportering,
                            manueltRegistrert = it.kilde == Kilde.MANUELL,
                            valgt = it.taMed,
                            gjelderBarn = null,
                        ),
                    ),
            )
        }.toSet()
}

fun Behandling.tilGrunnlagInntektKontantstøtte(
    gjelder: Grunnlag,
    søknadsbarn: Grunnlag,
): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    val personidentGjelder = mapper.treeToValue(gjelder.innhold, Person::class.java).ident

    return inntekter.asSequence().filter { i -> i.taMed }
        .filter { i -> i.ident == personidentGjelder.verdi }
        .filter { i -> i.inntektsrapportering == Inntektsrapportering.KONTANTSTØTTE }.map {
            Grunnlag(
                type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
                referanse = "Inntekt_${it.inntektsrapportering}_${gjelder.referanse}_${it.datoFom.toCompactString()}",
                grunnlagsreferanseListe = listOf(gjelder.referanse!!),
                innhold =
                    POJONode(
                        BeregningInntektRapporteringPeriode(
                            beløp = it.belop,
                            periode = ÅrMånedsperiode(it.datoFom, it.datoTom?.plusDays(1)),
                            inntektsrapportering = it.inntektsrapportering,
                            manueltRegistrert = it.kilde == Kilde.MANUELL,
                            valgt = it.taMed,
                            gjelderBarn = søknadsbarn.referanse,
                        ),
                    ),
            )
        }.toSet()
}

fun Behandling.tilGrunnlagBarnetillegg(
    bm: Grunnlag,
    søknadsbarn: Grunnlag,
): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    val personidentSøknadsbarn = mapper.treeToValue(søknadsbarn.innhold, Person::class.java).ident
    return inntekter.asSequence()
        .filter { it.ident == personidentSøknadsbarn.verdi }
        .filter { it.inntektsrapportering == Inntektsrapportering.BARNETILLEGG }
        .map {
            Grunnlag(
                type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
                referanse = "Inntekt_${Inntektsrapportering.SMÅBARNSTILLEGG}_${bm.referanse}_${
                    it.datoFom.toCompactString()
                }",
                grunnlagsreferanseListe = listOf(bm.referanse!!),
                innhold =
                    POJONode(
                        BeregningInntektRapporteringPeriode(
                            beløp = it.belop,
                            periode =
                                ÅrMånedsperiode(
                                    it.datoFom,
                                    it.datoTom?.plusDays(1),
                                ),
                            inntektsrapportering = Inntektsrapportering.SMÅBARNSTILLEGG,
                            // TODO: Mangler informasjon for å sette dette
                            manueltRegistrert = false,
                            valgt = true,
                            gjelderBarn = null,
                        ),
                    ),
            )
        }.toSet()
}

fun Behandling.tilGrunnlagUtvidetbarnetrygd(bm: Grunnlag) =
    inntekter.asSequence().filter { it.inntektsrapportering == Inntektsrapportering.UTVIDET_BARNETRYGD }.map {
        Grunnlag(
            type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
            referanse =
                "Inntekt_" +
                    "${Inntektsrapportering.UTVIDET_BARNETRYGD}_${bm.referanse}_${it.datoFom?.toCompactString()}",
            grunnlagsreferanseListe = listOf(bm.referanse!!),
            innhold =
                POJONode(
                    BeregningInntektRapporteringPeriode(
                        beløp = it.belop,
                        periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom?.plusDays(1)),
                        inntektsrapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
                        // TODO: Mangler informasjon for å sette dette
                        manueltRegistrert = false,
                        valgt = true,
                        gjelderBarn = null,
                    ),
                ),
        )
    }.toSet()
