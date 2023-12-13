package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
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
    val inntektBm = this.tilGrunnlagInntekt(bm)
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
            referanse = "sivilstand-${bm.referanse}",
            type = Grunnlagstype.SIVILSTAND_PERIODE,
            innhold =
                POJONode(
                    SivilstandPeriode(
                        sivilstand = it.sivilstand,
                        periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom),
                    ),
                ),
        )
    }.toSet()
}

fun Behandling.tilGrunnlagBostatus(grunnlagBarn: Set<Grunnlag>): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    return grunnlagBarn.flatMap {
        val barn = mapper.treeToValue(it.innhold, Person::class.java)
        val bostatusperioderForBarn = this.husstandsbarn.filter { hb -> hb.ident == barn.ident.verdi }.first()
        oppretteGrunnlagForBostatusperioder(it.referanse!!, bostatusperioderForBarn.perioder)
    }.toSet()
}

private fun oppretteGrunnlagForBostatusperioder(
    personreferanse: String,
    husstandsbarnperioder: Set<Husstandsbarnperiode>,
): Set<Grunnlag> {
    var bostatusperiodegrunnlag = mutableSetOf<Grunnlag>()

    Bostatuskode.values().forEach {
        val husstandsbarnperioder = husstandsbarnperioder.filter { p -> p.bostatus == it }
        husstandsbarnperioder.forEach {
            bostatusperiodegrunnlag.add(
                Grunnlag(
                    grunnlagsreferanseListe = listOf(personreferanse),
                    referanse = "bostatus-$personreferanse",
                    type = Grunnlagstype.BOSTATUS_PERIODE,
                    innhold =
                        POJONode(
                            BostatusPeriode(
                                bostatus = it.bostatus!!,
                                manueltRegistrert = it.kilde == Kilde.MANUELL,
                                periode =
                                    ÅrMånedsperiode(
                                        it.datoFom!!,
                                        it.datoTom?.plusDays(1),
                                    ),
                            ),
                        ),
                ),
            )
        }
    }

    return bostatusperiodegrunnlag
}

fun Behandling.tilGrunnlagInntekt(bm: Grunnlag): Set<Grunnlag> {
    val mapper = jacksonObjectMapper()
    val personidentBm = mapper.treeToValue(bm.innhold, Person::class.java).ident

    return inntekter.filter { i -> i.taMed }.filter { i -> i.ident == personidentBm.verdi }
        .filter { i -> i.inntektstype != Inntektsrapportering.KONTANTSTØTTE }.map {
            Grunnlag(
                type = Grunnlagstype.BEREGNING_INNTEKT_RAPPORTERING_PERIODE,
                referanse = "Inntekt_${it.inntektstype}_${it.datoFom?.toCompactString()}",
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

fun String.tilSivilstandskodeForBeregning(): Sivilstandskode {
    return when (this) {
        SivilstandskodePDL.GIFT.name, SivilstandskodePDL.REGISTRERT_PARTNER.name, Sivilstandskode.GIFT_SAMBOER.name,
        -> Sivilstandskode.GIFT_SAMBOER

        else -> {
            Sivilstandskode.BOR_ALENE_MED_BARN
        }
    }
}
