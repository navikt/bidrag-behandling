package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.transformers.grunnlag.beregningTilDato
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnValgteInntekterGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.InntektsgrunnlagPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import java.math.BigDecimal

fun Behandling.tilInntektberegningDto(): BeregnValgteInntekterGrunnlag {
    return BeregnValgteInntekterGrunnlag(
        periode =
            ÅrMånedsperiode(
                virkningstidspunktEllerSøktFomDato,
                maxOf(virkningstidspunktEllerSøktFomDato.plusMonths(1).withDayOfMonth(1), beregningTilDato),
            ),
        barnIdentListe = søknadsbarn.map { Personident(it.ident!!) },
        bidragsmottakerIdent = Personident(bidragsmottaker?.ident!!),
        grunnlagListe =
            inntekter.filter { it.taMed }.map {
                InntektsgrunnlagPeriode(
                    periode = ÅrMånedsperiode(it.datoFom, it.datoTom),
                    beløp = it.belop,
                    inntektsrapportering = it.type,
                    inntektGjelderBarnIdent = it.gjelderBarn.takeIfNotNullOrEmpty { Personident(it) },
                    inntektEiesAvIdent = Personident(it.ident),
                )
            },
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
                        antallBarnIHusstanden = grunnlagsListe.finnAntallBarnIHusstanden(periode.grunnlagsreferanseListe),
                    )
                },
        )
    }

fun List<GrunnlagDto>.finnSivilstandForPeriode(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Sivilstandskode {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe)!!
    val sivilstandPeriode =
        find {
            it.type == Grunnlagstype.SIVILSTAND_PERIODE &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return sivilstandPeriode?.innholdTilObjekt<SivilstandPeriode>()?.sivilstand!!
}

fun List<GrunnlagDto>.finnAntallBarnIHusstanden(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Int {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return 0
    val delberegningBarnIHusstanden =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return delberegningBarnIHusstanden?.innholdTilObjekt<DelberegningBarnIHusstand>()?.antallBarn
        ?: 0
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
    find { it.type == Grunnlagstype.SLUTTBEREGNING_FORSKUDD && grunnlagsreferanseListe.contains(it.referanse) }
