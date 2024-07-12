package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.transformers.grunnlag.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnValgteInntekterGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.InntektsgrunnlagPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndelSærbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningVoksneIHustand
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import java.math.BigDecimal

fun Behandling.tilInntektberegningDto(): BeregnValgteInntekterGrunnlag =
    BeregnValgteInntekterGrunnlag(
        periode =
            ÅrMånedsperiode(
                virkningstidspunktEllerSøktFomDato,
                finnBeregnTilDato(virkningstidspunktEllerSøktFomDato),
            ),
        barnIdentListe = søknadsbarn.map { Personident(it.ident!!) },
        bidragsmottakerIdent = Personident(bidragsmottaker?.ident!!),
        grunnlagListe =
            inntekter.filter { it.taMed }.map {
                InntektsgrunnlagPeriode(
                    periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom?.plusDays(1)),
                    beløp = it.belop,
                    inntektsrapportering = it.type,
                    inntektGjelderBarnIdent = it.gjelderBarn.takeIfNotNullOrEmpty { Personident(it) },
                    inntektEiesAvIdent = Personident(it.ident),
                )
            },
    )

fun BeregnetSærbidragResultat.tilDto(behandling: Behandling) =
    let {
        val grunnlagsListe = grunnlagListe.toList()
        val periode = beregnetSærbidragPeriodeListe.first()
        val delberegningVoksneIHustand = grunnlagsListe.finnDelberegningVoksneIHusstand(periode.grunnlagsreferanseListe)
        val bidragsevne = grunnlagsListe.finnDelberegningBidragsevne(periode.grunnlagsreferanseListe)
        ResultatSærbidragsberegningDto(
            periode = periode.periode,
            resultat = periode.resultat.beløp,
            resultatKode = periode.resultat.resultatkode,
            bpsAndel = grunnlagsListe.finnDelberegningBidragspliktigesAndel(periode.grunnlagsreferanseListe),
            beregning = behandling.utgift?.tilBeregningDto(),
            voksenIHusstanden = delberegningVoksneIHustand?.borMedAndreVoksne,
            antallBarnIHusstanden = grunnlagsListe.finnAntallBarnIHusstanden(periode.grunnlagsreferanseListe),
            delberegningUtgift = grunnlagsListe.finnDelberegningUtgift(periode.grunnlagsreferanseListe),
        )
    }

fun List<ResultatForskuddsberegningBarn>.tilDto() =
    map { resultat ->
        val grunnlagsListe = resultat.resultat.grunnlagListe.toList()
        ResultatBeregningBarnDto(
            barn = resultat.barn,
            perioder =
                resultat.resultat.beregnetForskuddPeriodeListe.map { periode ->
                    ResultatBeregningBarnDto.ResultatPeriodeDto(
                        periode = periode.periode,
                        beløp = periode.resultat.belop,
                        resultatKode = periode.resultat.kode,
                        regel = periode.resultat.regel,
                        sivilstand = grunnlagsListe.finnSivilstandForPeriode(periode.grunnlagsreferanseListe),
                        inntekt = grunnlagsListe.finnTotalInntekt(periode.grunnlagsreferanseListe),
                        antallBarnIHusstanden =
                            grunnlagsListe
                                .finnAntallBarnIHusstanden(periode.grunnlagsreferanseListe)
                                .toInt(),
                    )
                },
        )
    }

fun List<GrunnlagDto>.finnSivilstandForPeriode(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Sivilstandskode? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val sivilstandPeriode =
        find {
            it.type == Grunnlagstype.SIVILSTAND_PERIODE &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return sivilstandPeriode?.innholdTilObjekt<SivilstandPeriode>()?.sivilstand
}

fun List<GrunnlagDto>.finnAntallBarnIHusstanden(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Double {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return 0.0
    val delberegningBarnIHusstanden =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return delberegningBarnIHusstanden?.innholdTilObjekt<DelberegningBarnIHusstand>()?.antallBarn
        ?: 0.0
}

fun List<GrunnlagDto>.finnDelberegningBidragspliktigesAndel(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): DelberegningBidragspliktigesAndelSærbidrag? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL_SÆRBIDRAG &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningBidragspliktigesAndelSærbidrag>()
}

fun List<GrunnlagDto>.finnDelberegningVoksneIHusstand(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningVoksneIHustand? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningVoksneIHustand>()
}

fun List<GrunnlagDto>.finnDelberegningUtgift(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningUtgift? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_UTGIFT &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningUtgift>()
}

fun List<GrunnlagDto>.finnDelberegningBidragsevne(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningBidragsevne? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSEVNE &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningBidragsevne>()
}

fun List<GrunnlagDto>.finnTotalInntekt(grunnlagsreferanseListe: List<Grunnlagsreferanse>): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    val delberegningSumInntekt =
        find {
            it.type == Grunnlagstype.DELBEREGNING_SUM_INNTEKT &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return delberegningSumInntekt?.innholdTilObjekt<DelberegningSumInntekt>()?.totalinntekt
        ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnSluttberegningIReferanser(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    find {
        listOf(
            Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
            Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
        ).contains(it.type) &&
            grunnlagsreferanseListe.contains(it.referanse)
    }
