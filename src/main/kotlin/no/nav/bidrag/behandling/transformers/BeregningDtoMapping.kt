package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.beregning.BidragPeriodeBeregningsdetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBidragsevneDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBidragspliktigesBeregnedeTotalbidragDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBarnebidragsberegningPeriodeDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningInntekterDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erDirekteAvslag
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnValgteInntekterGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.InntektsgrunnlagPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesBeregnedeTotalbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsfradrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningVoksneIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsklassePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonBidragsevnePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonSjablontallPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.felles.ifTrue
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

fun List<ResultatBidragsberegningBarn>.tilDto(behandling: Behandling): ResultatBidragberegningDto =
    ResultatBidragberegningDto(
        resultatBarn =
            map { resultat ->
                val grunnlagsListe = resultat.resultat.grunnlagListe.toList()
                ResultatBidragsberegningBarnDto(
                    barn = resultat.barn,
                    perioder =
                        resultat.resultat.beregnetBarnebidragPeriodeListe.map {
                            val grunnlagsreferanseListe = it.grunnlagsreferanseListe
                            val bpsAndel = grunnlagsListe.finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe)
                            val sluttberegning =
                                grunnlagsListe
                                    .finnSluttberegningIReferanser(
                                        grunnlagsreferanseListe,
                                    )?.innholdTilObjekt<SluttberegningBarnebidrag>()
                            ResultatBarnebidragsberegningPeriodeDto(
                                periode = it.periode,
                                faktiskBidrag = it.resultat.beløp,
                                resultatKode = it.resultat.kode,
                                beregnetBidrag = sluttberegning?.beregnetBeløp ?: BigDecimal.ZERO,
                                samværsfradrag = grunnlagsListe.finnSamværsfradrag(grunnlagsreferanseListe),
                                bpsAndelU = bpsAndel?.endeligAndelFaktor ?: BigDecimal.ZERO,
                                erDirekteAvslag = it.resultat.kode.erDirekteAvslag(),
                                beregningsdetaljer =
                                    if (!it.resultat.kode.erDirekteAvslag()) {
                                        val delberegningBPsEvne = grunnlagsListe.finnDelberegningBidragsevne(grunnlagsreferanseListe)
                                        BidragPeriodeBeregningsdetaljer(
                                            delberegningBidragsevne = delberegningBPsEvne,
                                            samværsfradrag =
                                                grunnlagsListe.finnDelberegningSamværsfradrag(
                                                    grunnlagsreferanseListe,
                                                ),
                                            delberegningUnderholdskostnad =
                                                grunnlagsListe.finnDelberegningUnderholdskostnad(
                                                    grunnlagsreferanseListe,
                                                ),
                                            forskuddssats = grunnlagsListe.finnForskuddssats(grunnlagsreferanseListe),
                                            delberegningBidragspliktigesBeregnedeTotalBidrag =
                                                grunnlagsListe
                                                    .finnDelberegningBPsBeregnedeTotalbidrag(
                                                        grunnlagsreferanseListe,
                                                    ),
                                            bpsAndel = bpsAndel,
                                            antallBarnIHusstanden = grunnlagsListe.finnAntallBarnIHusstanden(grunnlagsreferanseListe),
                                            inntekter = grunnlagsListe.byggResultatSærbidragInntekter(grunnlagsreferanseListe),
                                            voksenIHusstanden = grunnlagsListe.finnBorMedAndreVoksne(grunnlagsreferanseListe),
                                            enesteVoksenIHusstandenErEgetBarn =
                                                grunnlagsListe.finnEnesteVoksenIHusstandenErEgetBarn(
                                                    grunnlagsreferanseListe,
                                                ),
                                            bpHarEvne = delberegningBPsEvne!!.bidragsevne > BigDecimal.ZERO,
                                        )
                                    } else {
                                        null
                                    },
                            )
                        },
                )
            },
    )

fun BeregnetSærbidragResultat.tilDto(behandling: Behandling) =
    let {
        val grunnlagsListe = grunnlagListe.toList()
        val periode = beregnetSærbidragPeriodeListe.first()
        grunnlagsListe.byggResultatSærbidragsberegning(
            periode.periode.fom.atDay(1),
            periode.resultat.beløp,
            periode.resultat.resultatkode,
            periode.grunnlagsreferanseListe,
            behandling.utgift?.tilBeregningDto() ?: UtgiftBeregningDto(),
            behandling.utgift
                ?.utgiftsposter
                ?.sorter()
                ?.map { it.tilDto() } ?: emptyList(),
            behandling.utgift?.maksGodkjentBeløpTaMed?.ifTrue { behandling.utgift?.maksGodkjentBeløp },
        )
    }

fun List<GrunnlagDto>.byggResultatSærbidragInntekter(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    ResultatBeregningInntekterDto(
        inntektBarn = finnTotalInntektForRolle(grunnlagsreferanseListe, Rolletype.BARN),
        barnEndeligInntekt = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe)?.barnEndeligInntekt,
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
    beregning: UtgiftBeregningDto,
    utgiftsposter: List<UtgiftspostDto>,
    maksGodkjentBeløp: BigDecimal?,
) = ResultatSærbidragsberegningDto(
    periode =
        ÅrMånedsperiode(
            virkningstidspunkt,
            finnBeregnTilDato(virkningstidspunkt),
        ),
    resultat = resultat ?: BigDecimal.ZERO,
    resultatKode = resultatkode,
    beregning = beregning,
    forskuddssats = finnForskuddssats(grunnlagsreferanseListe),
    bpsAndel = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe),
    antallBarnIHusstanden = finnAntallBarnIHusstanden(grunnlagsreferanseListe),
    inntekter = byggResultatSærbidragInntekter(grunnlagsreferanseListe),
    utgiftsposter = utgiftsposter,
    maksGodkjentBeløp = maksGodkjentBeløp,
    delberegningUtgift = finnDelberegningUtgift(grunnlagsreferanseListe),
    delberegningBidragsevne = finnDelberegningBidragsevne(grunnlagsreferanseListe),
    delberegningBidragspliktigesBeregnedeTotalBidrag = finnDelberegningBPsBeregnedeTotalbidrag(grunnlagsreferanseListe),
    voksenIHusstanden = finnBorMedAndreVoksne(grunnlagsreferanseListe),
    enesteVoksenIHusstandenErEgetBarn = finnEnesteVoksenIHusstandenErEgetBarn(grunnlagsreferanseListe),
    erDirekteAvslag = resultatkode.erDirekteAvslag(),
)

fun List<ResultatForskuddsberegningBarn>.tilDto(vedtakstype: Vedtakstype? = null) =
    map { resultat ->
        val grunnlagsListe = resultat.resultat.grunnlagListe.toList()
        ResultatBeregningBarnDto(
            barn = resultat.barn,
            perioder =
                resultat.resultat.beregnetForskuddPeriodeListe.map { periode ->
                    ResultatBeregningBarnDto.ResultatPeriodeDto(
                        periode = periode.periode,
                        beløp = periode.resultat.belop,
                        vedtakstype = vedtakstype,
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

fun List<GrunnlagDto>.finnSamværsfradrag(grunnlagsreferanseListe: List<Grunnlagsreferanse>): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    val samværsfradrag = finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, sluttberegning).firstOrNull()
    return samværsfradrag?.innholdTilObjekt<DelberegningSamværsfradrag>()?.beløp
        ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnAntallBarnIHusstanden(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Double {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return 0.0
    val delberegningBarnIHusstanden = finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND, sluttberegning).firstOrNull()
    return delberegningBarnIHusstanden?.innholdTilObjekt<DelberegningBarnIHusstand>()?.antallBarn
        ?: 0.0
}

fun List<GrunnlagDto>.finnDelberegningBidragspliktigesAndel(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): DelberegningBidragspliktigesAndel? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningBidragspliktigesAndel>()
}

fun List<GrunnlagDto>.finnDelberegningBPsBeregnedeTotalbidrag(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): DelberegningBidragspliktigesBeregnedeTotalbidragDto? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegning =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_BEREGNEDE_TOTALBIDRAG &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    val delberegningObjekt = delberegning.innholdTilObjekt<DelberegningBidragspliktigesBeregnedeTotalbidrag>()

    return DelberegningBidragspliktigesBeregnedeTotalbidragDto(
        bidragspliktigesBeregnedeTotalbidrag = delberegningObjekt.bidragspliktigesBeregnedeTotalbidrag,
        periode = delberegningObjekt.periode,
        beregnetBidragPerBarnListe =
            delberegningObjekt.beregnetBidragPerBarnListe.map {
                DelberegningBidragspliktigesBeregnedeTotalbidragDto.BeregnetBidragPerBarnDto(
                    beregnetBidragPerBarn = it,
                    personidentBarn = hentPersonMedReferanse(it.gjelderBarn)?.personIdent!!,
                )
            },
    )
}

fun List<GrunnlagDto>.finnEnesteVoksenIHusstandenErEgetBarn(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Boolean? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, sluttberegning).firstOrNull() ?: return null
    val bosstatuser = finnGrunnlagSomErReferertAv(Grunnlagstype.BOSTATUS_PERIODE, delberegningBidragspliktigesAndel)
    val borMedVoksenBarn =
        bosstatuser
            .find { it.gjelderReferanse != bidragspliktig?.referanse }
            ?.innholdTilObjekt<BostatusPeriode>() != null
    val borIkkeMedAndreVoksne =
        bosstatuser
            .find { it.gjelderReferanse == bidragspliktig?.referanse }
            ?.innholdTilObjekt<BostatusPeriode>()
            ?.bostatus == Bostatuskode.BOR_IKKE_MED_ANDRE_VOKSNE
    return borMedVoksenBarn && borIkkeMedAndreVoksne
}

fun List<GrunnlagDto>.finnBorMedAndreVoksne(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Boolean? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, sluttberegning).firstOrNull() ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningVoksneIHusstand>().borMedAndreVoksne
}

fun List<GrunnlagDto>.finnDelberegningUtgift(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningUtgift? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningBidragspliktigesAndelReferanser =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    val delberegningUtgift =
        find {
            it.type == Grunnlagstype.DELBEREGNING_UTGIFT &&
                delberegningBidragspliktigesAndelReferanser.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningUtgift.innholdTilObjekt<DelberegningUtgift>()
}

fun List<GrunnlagDto>.finnDelberegningSamværsfradrag(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): BidragPeriodeBeregningsdetaljer.BeregningsdetaljerSamværsfradrag? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningSamværsfradragGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val samværsperiodeGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.SAMVÆRSPERIODE,
            delberegningSamværsfradragGrunnlag.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val delberegningSamværsklasseGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE,
            samværsperiodeGrunnlag.grunnlagsreferanseListe,
        ).firstOrNull()

    val delberegningSamværsfradrag = delberegningSamværsfradragGrunnlag.innholdTilObjekt<DelberegningSamværsfradrag>()
    return BidragPeriodeBeregningsdetaljer.BeregningsdetaljerSamværsfradrag(
        samværsfradrag = delberegningSamværsfradrag.beløp,
        samværsklasse = samværsperiodeGrunnlag.innholdTilObjekt<SamværsklassePeriode>().samværsklasse,
        gjennomsnittligSamværPerMåned =
            delberegningSamværsklasseGrunnlag
                ?.innholdTilObjekt<DelberegningSamværsklasse>()
                ?.gjennomsnittligSamværPerMåned ?: BigDecimal.ZERO,
    )
}

fun List<GrunnlagDto>.finnDelberegningUnderholdskostnad(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningUnderholdskostnad? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningUnderholdskostnad =
        find {
            it.type == Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningUnderholdskostnad.innholdTilObjekt<DelberegningUnderholdskostnad>()
}

fun List<GrunnlagDto>.finnDelberegningBidragsevne(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningBidragsevneDto? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSEVNE &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    val delberegningBoforhold =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BOFORHOLD &&
                delberegningBidragspliktigesAndel.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    val delberegningVoksneIHusstand =
        find {
            it.type == Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND &&
                delberegningBoforhold.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }?.innholdTilObjekt<DelberegningVoksneIHusstand>() ?: return null
    val delberegningBarnIHusstanden =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND &&
                delberegningBoforhold.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }?.innholdTilObjekt<DelberegningBarnIHusstand>() ?: return null
    val sjablonBidragsevne =
        find {
            it.type == Grunnlagstype.SJABLON_BIDRAGSEVNE &&
                delberegningBidragspliktigesAndel.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }?.innholdTilObjekt<SjablonBidragsevnePeriode>() ?: return null
    val sjablonUnderholdEgnebarnIHusstand =
        find {
            it.type == Grunnlagstype.SJABLON_SJABLONTALL &&
                delberegningBidragspliktigesAndel.grunnlagsreferanseListe.contains(
                    it.referanse,
                ) &&
                it.innholdTilObjekt<SjablonSjablontallPeriode>().sjablon == SjablonTallNavn.UNDERHOLD_EGNE_BARN_I_HUSSTAND_BELØP
        }?.innholdTilObjekt<SjablonSjablontallPeriode>() ?: return null
    val delberegningBidragsevne = delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningBidragsevne>()
    return DelberegningBidragsevneDto(
        bidragsevne = delberegningBidragsevne.beløp,
        skatt =
            DelberegningBidragsevneDto.Skatt(
                sumSkatt = delberegningBidragsevne.skatt.sumSkatt,
                skattAlminneligInntekt = delberegningBidragsevne.skatt.skattAlminneligInntekt,
                trinnskatt = delberegningBidragsevne.skatt.trinnskatt,
                trygdeavgift = delberegningBidragsevne.skatt.trygdeavgift,
            ),
        underholdEgneBarnIHusstand =
            DelberegningBidragsevneDto.UnderholdEgneBarnIHusstand(
                årsbeløp = delberegningBidragsevne.underholdBarnEgenHusstand,
                sjablon = sjablonUnderholdEgnebarnIHusstand.verdi,
                antallBarnIHusstanden = delberegningBarnIHusstanden.antallBarn,
            ),
        utgifter =
            DelberegningBidragsevneDto.BidragsevneUtgifterBolig(
                underholdBeløp = sjablonBidragsevne.underholdBeløp,
                boutgiftBeløp = sjablonBidragsevne.boutgiftBeløp,
                borMedAndreVoksne = delberegningVoksneIHusstand.borMedAndreVoksne,
            ),
    )
}

fun List<GrunnlagDto>.finnForskuddssats(grunnlagsreferanseListe: List<Grunnlagsreferanse>): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    return finnGrunnlagSomErReferertAv(Grunnlagstype.SJABLON_SJABLONTALL, sluttberegning)
        .find { it.innholdTilObjekt<SjablonSjablontallPeriode>().sjablon == SjablonTallNavn.FORSKUDDSSATS_BELØP }
        ?.innholdTilObjekt<SjablonSjablontallPeriode>()
        ?.verdi ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnTotalInntektForRolle(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    rolletype: Rolletype? = null,
): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    val gjelderReferanse = hentAllePersoner().find { it.type == rolletype?.tilGrunnlagstype() }?.referanse
    val delberegningSumInntekter = finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SUM_INNTEKT, sluttberegning)
    val delberegningSumInntektForRolle =
        if (gjelderReferanse.isNullOrEmpty()) {
            delberegningSumInntekter.firstOrNull()
        } else {
            delberegningSumInntekter.find {
                it.gjelderReferanse ==
                    gjelderReferanse
            }
        }
    return delberegningSumInntektForRolle?.innholdTilObjekt<DelberegningSumInntekt>()?.totalinntekt
        ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnSluttberegningIReferanser(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    find {
        listOf(
            Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
            Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
            Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG,
        ).contains(it.type) &&
            grunnlagsreferanseListe.contains(it.referanse)
    }
