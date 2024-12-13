package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.beregning.BidragPeriodeBeregningsdetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBarnetilleggDto
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
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto.Beregningsdetaljer
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BeregnGebyrResultat
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erAvslag
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erDirekteAvslag
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnValgteInntekterGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.InntektsgrunnlagPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnetilleggSkattesats
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesBeregnedeTotalbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningNettoBarnetillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningNettoTilsynsutgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsfradrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningTilleggsstønad
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningVoksneIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnholdMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsklassePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonBidragsevnePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonMaksTilsynPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonSjablontallPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilsynsutgiftBarn
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.felles.ifTrue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

fun BeregnGebyrResultat.tilDto(rolle: Rolle): GebyrRolleDto {
    val erManueltOverstyrt = rolle.manueltOverstyrtGebyr?.overstyrGebyr == true

    return GebyrRolleDto(
        inntekt =
            GebyrRolleDto.GebyrInntektDto(
                skattepliktigInntekt = skattepliktigInntekt,
                maksBarnetillegg = maksBarnetillegg,
            ),
        beregnetIlagtGebyr = ilagtGebyr,
        begrunnelse = if (erManueltOverstyrt) rolle.manueltOverstyrtGebyr?.begrunnelse else null,
        endeligIlagtGebyr =
            if (erManueltOverstyrt) {
                rolle.manueltOverstyrtGebyr!!.ilagtGebyr == true
            } else {
                ilagtGebyr
            },
        beløpGebyrsats = beløpGebyrsats,
        rolle = rolle.tilDto(),
    )
}

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

fun List<ResultatBidragsberegningBarn>.tilDto(): ResultatBidragberegningDto =
    ResultatBidragberegningDto(
        resultatBarn =
            map { resultat ->
                val grunnlagsListe = resultat.resultat.grunnlagListe.toList()
                ResultatBidragsberegningBarnDto(
                    barn = resultat.barn,
                    perioder =
                        resultat.resultat.beregnetBarnebidragPeriodeListe.map {
                            grunnlagsListe.byggResultatBidragsberegning(
                                it.periode,
                                it.resultat.beløp,
                                resultat.avslaskode,
                                it.grunnlagsreferanseListe,
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

fun List<GrunnlagDto>.byggResultatBidragsberegning(
    periode: ÅrMånedsperiode,
    resultat: BigDecimal?,
    resultatkode: Resultatkode?,
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): ResultatBarnebidragsberegningPeriodeDto {
    val bpsAndel = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe)
    val delberegningUnderholdskostnad = finnDelberegningUnderholdskostnad(grunnlagsreferanseListe)
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)?.innholdTilObjekt<SluttberegningBarnebidrag>()
    return ResultatBarnebidragsberegningPeriodeDto(
        periode = periode,
        underholdskostnad = delberegningUnderholdskostnad?.underholdskostnad ?: BigDecimal.ZERO,
        faktiskBidrag = resultat ?: BigDecimal.ZERO,
        resultatKode = resultatkode,
        beregnetBidrag = sluttberegning?.beregnetBeløp ?: BigDecimal.ZERO,
        samværsfradrag = finnSamværsfradrag(grunnlagsreferanseListe),
        bpsAndelU = bpsAndel?.endeligAndelFaktor ?: BigDecimal.ZERO,
        bpsAndelBeløp = bpsAndel?.andelBeløp ?: BigDecimal.ZERO,
        erDirekteAvslag = resultatkode?.erDirekteAvslag() ?: false,
        beregningsdetaljer =
            if (resultatkode?.erAvslag() != true) {
                val delberegningBPsEvne = finnDelberegningBidragsevne(grunnlagsreferanseListe)
                BidragPeriodeBeregningsdetaljer(
                    delberegningBidragsevne = delberegningBPsEvne,
                    barnetilleggBM = finnBarnetillegg(grunnlagsreferanseListe, Grunnlagstype.PERSON_BIDRAGSMOTTAKER),
                    barnetilleggBP = finnBarnetillegg(grunnlagsreferanseListe, Grunnlagstype.PERSON_BIDRAGSPLIKTIG),
                    samværsfradrag =
                        finnDelberegningSamværsfradrag(
                            grunnlagsreferanseListe,
                        ),
                    delberegningUnderholdskostnad = delberegningUnderholdskostnad,
                    forskuddssats = finnForskuddssats(grunnlagsreferanseListe),
                    delberegningBidragspliktigesBeregnedeTotalBidrag =
                        finnDelberegningBPsBeregnedeTotalbidrag(
                            grunnlagsreferanseListe,
                        ),
                    bpsAndel = bpsAndel,
                    sluttberegning = sluttberegning,
                    antallBarnIHusstanden = finnAntallBarnIHusstanden(grunnlagsreferanseListe),
                    inntekter = byggResultatSærbidragInntekter(grunnlagsreferanseListe),
                    voksenIHusstanden = finnBorMedAndreVoksne(grunnlagsreferanseListe),
                    enesteVoksenIHusstandenErEgetBarn =
                        finnEnesteVoksenIHusstandenErEgetBarn(
                            grunnlagsreferanseListe,
                        ),
                    bpHarEvne = delberegningBPsEvne?.let { it.bidragsevne > BigDecimal.ZERO } ?: false,
                )
            } else {
                null
            },
    )
}

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
    val samværsfradrag =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, sluttberegning).firstOrNull()
    return samværsfradrag?.innholdTilObjekt<DelberegningSamværsfradrag>()?.beløp
        ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnAntallBarnIHusstanden(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Double {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return 0.0
    val delberegningBarnIHusstanden =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND, sluttberegning).firstOrNull()
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
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, sluttberegning).firstOrNull()
            ?: return null
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
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, sluttberegning).firstOrNull()
            ?: return null
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

fun List<InnholdMedReferanse<DelberegningUnderholdskostnad>>.tilUnderholdskostnadDto(underholdBeregning: List<GrunnlagDto>) =
    this
        .map { delberegning ->
            val it = delberegning.innhold
            UnderholdskostnadDto(
                stønadTilBarnetilsyn = it.barnetilsynMedStønad ?: BigDecimal.ZERO,
                tilsynsutgifter = it.nettoTilsynsutgift ?: BigDecimal.ZERO,
                periode =
                    DatoperiodeDto(
                        it.periode.fom.atDay(1),
                        it.periode.til
                            ?.atDay(1)
                            ?.minusDays(1),
                    ),
                forbruk = it.forbruksutgift,
                barnetrygd = it.barnetrygd,
                boutgifter = it.boutgift,
                total = it.underholdskostnad,
                beregningsdetaljer =
                    underholdBeregning.tilUnderholdskostnadDetaljer(
                        delberegning.grunnlag.grunnlagsreferanseListe,
                        delberegning.gjelderBarnReferanse,
                    ),
            )
        }.toSet()

fun List<GrunnlagDto>.tilUnderholdskostnadDetaljer(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    gjelderBarnReferanse: String?,
): Beregningsdetaljer? {
    val nettoTilsyn =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningNettoTilsynsutgift>(
            Grunnlagstype.DELBEREGNING_NETTO_TILSYNSUTGIFT,
            grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val sjablonMaksTilsyn =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<SjablonMaksTilsynPeriode>(
            Grunnlagstype.SJABLON_MAKS_TILSYN,
            grunnlagsreferanseListe,
        )
    val maksTilsynBeløp = sjablonMaksTilsyn.firstOrNull()?.innhold?.maksBeløpTilsyn ?: BigDecimal.ZERO
    val søknadsbarnEndeligBeløp =
        nettoTilsyn.innhold.tilsynsutgiftBarnListe
            .find { it.gjelderBarn == gjelderBarnReferanse }
            ?.endeligSumTilsynsutgifter
            ?: BigDecimal.ZERO
    val sumTilsynsutgifter = nettoTilsyn.innhold.tilsynsutgiftBarnListe.sumOf { it.sumTilsynsutgifter }
    val erBegrensetAvMaksTilsyn =
        nettoTilsyn.innhold.totalTilsynsutgift.setScale(0, RoundingMode.HALF_UP) != sumTilsynsutgifter.setScale(0, RoundingMode.HALF_UP)
    return Beregningsdetaljer(
        erBegrensetAvMaksTilsyn = erBegrensetAvMaksTilsyn,
        endeligBeløp = søknadsbarnEndeligBeløp,
        sjablonMaksTilsynsutgift = maksTilsynBeløp,
        faktiskBeløp = nettoTilsyn.innhold.andelTilsynsutgiftBeløp,
        nettoBeløp = nettoTilsyn.innhold.nettoTilsynsutgift,
        totalTilsynsutgift = nettoTilsyn.innhold.totalTilsynsutgift,
        sumTilsynsutgifter = sumTilsynsutgifter,
        fordelingFaktor = nettoTilsyn.innhold.andelTilsynsutgiftFaktor,
        skattefradrag = nettoTilsyn.innhold.skattefradrag,
        tilsynsutgifterBarn =
            nettoTilsyn.innhold.tilsynsutgiftBarnListe.sortedBy { it.gjelderBarn }.map { fu ->
                tilsynsutgifterBarn(grunnlagsreferanseListe, fu)
            },
    )
}

fun List<GrunnlagDto>.tilsynsutgifterBarn(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    tilsynsutgiftBarn: TilsynsutgiftBarn,
): UnderholdskostnadDto.TilsynsutgiftBarn {
    val faktiskUtgiftPeriode =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<FaktiskUtgiftPeriode>(
            Grunnlagstype.FAKTISK_UTGIFT_PERIODE,
            grunnlagsreferanseListe,
        ).find { it.gjelderBarnReferanse == tilsynsutgiftBarn.gjelderBarn }!!

    val delberegningTilleggsstønad =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningTilleggsstønad>(
            Grunnlagstype.DELBEREGNING_TILLEGGSSTØNAD,
            grunnlagsreferanseListe,
        ).find { it.gjelderBarnReferanse == tilsynsutgiftBarn.gjelderBarn }
    val person = hentPersonMedReferanse(tilsynsutgiftBarn.gjelderBarn)?.innholdTilObjekt<Person>()
    val navn = if (person?.navn.isNullOrEmpty()) hentPersonVisningsnavn(person?.ident?.verdi) else person?.navn
    return UnderholdskostnadDto.TilsynsutgiftBarn(
        gjelderBarn = PersoninfoDto(1, person?.ident, navn),
        totalTilsynsutgift = faktiskUtgiftPeriode.innhold.faktiskUtgiftBeløp,
        beløp = tilsynsutgiftBarn.sumTilsynsutgifter,
        kostpenger = faktiskUtgiftPeriode.innhold.kostpengerBeløp,
        tilleggsstønad = delberegningTilleggsstønad?.innhold?.beregnetBeløp,
    )
}

fun List<GrunnlagDto>.finnAlleDelberegningUnderholdskostnad(): List<InnholdMedReferanse<DelberegningUnderholdskostnad>> =
    this
        .filtrerOgKonverterBasertPåEgenReferanse<DelberegningUnderholdskostnad>(
            Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD,
        ).sortedBy {
            it.innhold.periode.fom
        }

fun List<GrunnlagDto>.finnBarnetillegg(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    personGrunnlagstype: Grunnlagstype,
): DelberegningBarnetilleggDto {
    val personGrunnlag = find { it.type == personGrunnlagstype } ?: return DelberegningBarnetilleggDto()
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return DelberegningBarnetilleggDto()
    val nettoBarnetillegg =
        finnOgKonverterGrunnlagSomErReferertAv<DelberegningNettoBarnetillegg>(
            Grunnlagstype.DELBEREGNING_NETTO_BARNETILLEGG,
            sluttberegning,
        ).find { it.gjelderReferanse == personGrunnlag.referanse }

    val delberegningBarnetilleggSkattesats =
        nettoBarnetillegg?.let {
            finnOgKonverterGrunnlagSomErReferertAv<DelberegningBarnetilleggSkattesats>(
                Grunnlagstype.DELBEREGNING_BARNETILLEGG_SKATTESATS,
                nettoBarnetillegg.grunnlag,
            ).firstOrNull()
        }
    val delberegningSumInntekt =
        delberegningBarnetilleggSkattesats?.let {
            finnOgKonverterGrunnlagSomErReferertAv<DelberegningSumInntekt>(
                Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                nettoBarnetillegg.grunnlag,
            ).firstOrNull()
        }
    return DelberegningBarnetilleggDto(
        sumInntekt = delberegningSumInntekt?.innhold?.totalinntekt ?: BigDecimal.ZERO,
        skattFaktor = delberegningBarnetilleggSkattesats?.innhold?.skattFaktor ?: BigDecimal.ZERO,
        sumBruttoBeløp = nettoBarnetillegg?.innhold?.summertBruttoBarnetillegg ?: BigDecimal.ZERO,
        sumNettoBeløp = nettoBarnetillegg?.innhold?.summertNettoBarnetillegg ?: BigDecimal.ZERO,
        barnetillegg =
            nettoBarnetillegg
                ?.innhold
                ?.barnetilleggTypeListe
                ?.map {
                    DelberegningBarnetilleggDto.BarnetilleggDetaljerDto(
                        bruttoBeløp = it.bruttoBarnetillegg,
                        nettoBeløp = it.nettoBarnetillegg,
                        visningsnavn = it.barnetilleggType.visningsnavn.intern,
                    )
                } ?: emptyList(),
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
        sumInntekt25Prosent = delberegningBidragsevne.sumInntekt25Prosent,
        skatt =
            DelberegningBidragsevneDto.Skatt(
                sumSkattFaktor = delberegningBidragsevne.skatt.sumSkattFaktor,
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
