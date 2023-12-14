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

fun Behandling.tilBeregnGrunnlag(
    bm: Grunnlag,
    søknadsbarn: Grunnlag,
    øvrigeBarnIHusstand: Set<Grunnlag>,
): BeregnGrunnlag {
    val personobjekterBarn = listOf(søknadsbarn) + øvrigeBarnIHusstand

    val bostatusBarn = this.tilGrunnlagBostatus(personobjekterBarn.toSet())
    val inntektBm = this.tilGrunnlagInntekt(bm) +
            this.tilGrunnlagBarnetillegg(bm, søknadsbarn) + this.tilGrunnlagUtvidetbarnetrygd(bm)
    val sivilstandBm = this.tilGrunnlagSivilstand(bm)

    return BeregnGrunnlag(
        periode = ÅrMånedsperiode(this.virkningsdato!!, this.datoTom.plusDays(1)),
        søknadsbarnReferanse = søknadsbarn.referanse,
        grunnlagListe =
        personobjekterBarn + bostatusBarn + inntektBm + sivilstandBm,
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
                    periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom?.plusMonths(1)),
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

fun Behandling.tilGrunnlagInntekt(bm: Grunnlag): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    val personidentBm = mapper.treeToValue(bm.innhold, Person::class.java).ident

    return inntekter.asSequence().filter { i -> i.taMed }
        .filter { i -> i.ident == personidentBm.verdi }
        .filter { i -> i.inntektstype != Inntektsrapportering.KONTANTSTØTTE }.map {
            Grunnlag(
                type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
                referanse = "Inntekt_${it.inntektstype}_${it.datoFom.toCompactString()}",
                grunnlagsreferanseListe = listOf(bm.referanse!!),
                innhold =
                POJONode(
                    BeregningInntektRapporteringPeriode(
                        beløp = it.belop,
                        periode = ÅrMånedsperiode(it.datoFom, it.datoTom),
                        inntektsrapportering = it.inntektstype,
                        manueltRegistrert = !it.fraGrunnlag,
                        valgt = it.taMed,
                        gjelderBarn = null,
                    ),
                ),
            )
        }.toSet()
}

fun Behandling.tilGrunnlagBarnetillegg(bm: Grunnlag, søknadsbarn: Grunnlag): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    val personidentSøknadsbarn = mapper.treeToValue(søknadsbarn.innhold, Person::class.java).ident
    return barnetillegg.asSequence()
        .filter { it.ident == personidentSøknadsbarn.verdi }
        .map {
            Grunnlag(
                type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
                referanse = "Inntekt_${Inntektsrapportering.SMÅBARNSTILLEGG}_${
                    it.datoFom?.toLocalDate()?.toCompactString()
                }",
                grunnlagsreferanseListe = listOf(bm.referanse!!),
                innhold =
                POJONode(
                    BeregningInntektRapporteringPeriode(
                        beløp = it.barnetillegg,
                        periode = ÅrMånedsperiode(
                            it.datoFom!!.toLocalDate(),
                            it.datoTom?.toLocalDate()
                        ),
                        inntektsrapportering = Inntektsrapportering.SMÅBARNSTILLEGG,
                        manueltRegistrert = false, // TODO: Mangler informasjon for å sette dette
                        valgt = true,
                        gjelderBarn = null,
                    ),
                ),
            )
        }.toSet()
}

fun Behandling.tilGrunnlagUtvidetbarnetrygd(bm: Grunnlag) = utvidetBarnetrygd.asSequence().map {
    Grunnlag(
        type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
        referanse = "Inntekt_${no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.UTVIDET_BARNETRYGD}_${it.datoFom?.toCompactString()}",
        grunnlagsreferanseListe = listOf(bm.referanse!!),
        innhold =
        POJONode(
            BeregningInntektRapporteringPeriode(
                beløp = it.belop,
                periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom),
                inntektsrapportering = no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.UTVIDET_BARNETRYGD,
                manueltRegistrert = false, // TODO: Mangler informasjon for å sette dette
                valgt = true,
                gjelderBarn = null,
            ),
        ),
    )
}.toSet()
