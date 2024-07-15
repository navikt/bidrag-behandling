package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningInntekterDto
import no.nav.bidrag.behandling.transformers.grunnlag.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import java.math.BigDecimal
import java.time.LocalDate

fun Behandling.tilInntektberegningDto(rolle: Rolle): BeregnValgteInntekterGrunnlag =
    BeregnValgteInntekterGrunnlag(
        periode =
            ÅrMånedsperiode(
                virkningstidspunktEllerSøktFomDato,
                finnBeregnTilDato(virkningstidspunktEllerSøktFomDato),
            ),
        barnIdentListe = søknadsbarn.map { Personident(it.ident!!) },
        gjelderIdent = Personident(rolle.ident!!),
        grunnlagListe =
            inntekter.filter { it.ident == rolle.ident }.filter { it.taMed }.map {
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
        val bidragsevne = grunnlagsListe.finnDelberegningBidragsevne(periode.grunnlagsreferanseListe)
        grunnlagsListe.byggResultatSærbidragsberegning(
            periode.periode.fom.atDay(1),
            periode.resultat.beløp,
            periode.resultat.resultatkode,
            periode.grunnlagsreferanseListe,
        )
    }

fun List<GrunnlagDto>.byggResultatSærbidragInntekter(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    ResultatSærbidragsberegningInntekterDto(
        inntektBarn = finnTotalInntektForRolle(grunnlagsreferanseListe, Rolletype.BARN),
        inntektBP =
            finnTotalInntektForRolle(
                grunnlagsreferanseListe,
                Rolletype.BIDRAGSPLIKTIG,
            ),
        inntektBM =
            finnTotalInntektForRolle(
                grunnlagsreferanseListe,
                Rolletype.BIDRAGSMOTTAKER,
            ),
    )

fun List<GrunnlagDto>.byggResultatSærbidragsberegning(
    virkningstidspunkt: LocalDate,
    resultat: BigDecimal?,
    resultatkode: Resultatkode,
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
) = ResultatSærbidragsberegningDto(
    periode =
        ÅrMånedsperiode(
            virkningstidspunkt,
            finnBeregnTilDato(virkningstidspunkt),
        ),
    resultat = resultat ?: BigDecimal.ZERO,
    resultatKode = resultatkode,
    bpsAndel = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe),
    antallBarnIHusstanden = finnAntallBarnIHusstanden(grunnlagsreferanseListe),
    inntekter = byggResultatSærbidragInntekter(grunnlagsreferanseListe),
    delberegningUtgift = finnDelberegningUtgift(grunnlagsreferanseListe),
    voksenIHusstanden = finnBorMedAndreVoksne(grunnlagsreferanseListe),
)

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
                        inntekt =
                            grunnlagsListe.finnTotalInntektForRolle(periode.grunnlagsreferanseListe),
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

fun List<GrunnlagDto>.finnBorMedAndreVoksne(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Boolean? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningVoksneIHustand>().borMedAndreVoksne
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

fun List<GrunnlagDto>.finnTotalInntektForRolle(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    rolletype: Rolletype? = null,
): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    val delberegningSumInntekt =
        find {
            it.type == Grunnlagstype.DELBEREGNING_SUM_INNTEKT &&
                rolletype?.let { type -> inntektTilhørerRolle(it.grunnlagsreferanseListe, type) } ?: true &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return delberegningSumInntekt?.innholdTilObjekt<DelberegningSumInntekt>()?.totalinntekt
        ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.inntektTilhørerRolle(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    rolletype: Rolletype,
): Boolean {
    val rolleReferanse =
        this
            .hentAllePersoner()
            .find { it.type == rolletype.tilGrunnlagstype() }
            ?.referanse
    return this
        .filter { grunnlagsreferanseListe.contains(it.referanse) }
        .filter { it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE }
        .any {
            it.gjelderReferanse == rolleReferanse
        }
}

fun List<GrunnlagDto>.finnSluttberegningIReferanser(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    find {
        listOf(
            Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
            Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
        ).contains(it.type) &&
            grunnlagsreferanseListe.contains(it.referanse)
    }
