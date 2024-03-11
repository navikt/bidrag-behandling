package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.transformers.tilGrunnlagsdataType
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.trimToNull
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettAinntektGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettKontantstøtteGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSkattegrunnlagGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSmåbarnstilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettUtvidetbarnetrygGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt

fun List<Grunnlag>.tilInnhentetArbeidsforhold(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.groupBy { it.rolle.ident }
        .map { (personId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val arbeidsforholdListe =
                grunnlag.konverterData<List<ArbeidsforholdGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(personId)!!
            arbeidsforholdListe.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse)
        }.toSet()
}

fun List<Grunnlag>.tilInnhentetSivilstand(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return filter { it.type == Grunnlagsdatatype.SIVILSTAND }.groupBy { it.rolle.ident }
        .map { (personId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val sivilstandListe =
                grunnlag.konverterData<List<SivilstandGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(personId)!!
            sivilstandListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()
}

fun List<Grunnlag>.tilInnhentetHusstandsmedlemmer(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    val personobjekterInnhentetHusstandsmedlem = mutableSetOf<GrunnlagDto>()

    fun RelatertPersonGrunnlagDto.opprettPersonGrunnlag(): GrunnlagDto {
        val relatertPersonGrunnlag = tilPersonGrunnlag()
        personobjekterInnhentetHusstandsmedlem.add(relatertPersonGrunnlag)
        return relatertPersonGrunnlag
    }

    val innhentetHusstandsmedlemGrunnlagListe =
        filter { it.type == Grunnlagsdatatype.BOFORHOLD }.groupBy { it.rolle.ident }
            .flatMap { (partPersonId, grunnlagListe) ->
                val grunnlag = grunnlagListe.first()
                val husstandsmedlemList =
                    grunnlag.konverterData<List<RelatertPersonGrunnlagDto>>() ?: emptyList()
                val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
                husstandsmedlemList.groupBy { it.relatertPersonPersonId }
                    .map { (relatertPersonPersonId, relatertPersonListe) ->
                        val relatertPersonObjekt =
                            (personobjekter + personobjekterInnhentetHusstandsmedlem).hentPersonNyesteIdent(
                                relatertPersonPersonId,
                            )
                                ?: relatertPersonListe[0].opprettPersonGrunnlag()
                        if (relatertPersonListe.size > 1) innhentetGrunnlagHarFlereRelatertePersonMedSammeId()

                        relatertPersonListe.first().tilGrunnlagsobjekt(
                            grunnlag.innhentet,
                            gjelder.referanse,
                            relatertPersonObjekt.referanse,
                        )
                    }
            }.toSet()

    return innhentetHusstandsmedlemGrunnlagListe + personobjekterInnhentetHusstandsmedlem
}

fun List<Grunnlag>.tilBeregnetInntekt(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER }.groupBy { it.rolle.ident }
        .map { (ident, grunnlag) ->
            val inntekter =
                grunnlag.first().konverterData<SummerteInntekter<SummertMånedsinntekt>>()!!
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            val summerteMånedsinntekter = inntekter.inntekter
            GrunnlagDto(
                referanse = "beregnet_inntekt_${gjelder.referanse}",
                type = Grunnlagstype.BEREGNET_INNTEKT,
                gjelderReferanse = gjelder.referanse,
                grunnlagsreferanseListe =
                    summerteMånedsinntekter.flatMap { it.grunnlagsreferanseListe }.toSet().toList(),
                innhold =
                    POJONode(
                        BeregnetInntekt(
                            versjon = inntekter.versjon!!,
                            summertMånedsinntektListe =
                                summerteMånedsinntekter.map { månedsinntekt ->
                                    BeregnetInntekt.SummertMånedsinntekt(
                                        gjelderÅrMåned = månedsinntekt.gjelderÅrMåned,
                                        sumInntekt = månedsinntekt.sumInntekt,
                                        inntektPostListe =
                                            månedsinntekt.inntektPostListe.map { post ->
                                                BeregnetInntekt.InntektPost(
                                                    kode = post.kode,
                                                    inntekstype = post.inntekstype,
                                                    beløp = post.beløp,
                                                )
                                            },
                                    )
                                },
                        ),
                    ),
            )
        }.toSet()
}

fun List<Grunnlag>.tilInnhentetGrunnlagInntekt(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return mapSkattegrunnlag(personobjekter) + mapAinntekt(personobjekter) +
        mapKontantstøtte(personobjekter) +
        mapBarnetilsyn(personobjekter) +
        mapBarnetillegg(personobjekter) +
        mapUtvidetbarnetrygd(personobjekter) + mapSmåbarnstillegg(personobjekter)
}

fun List<Grunnlag>.hentVersjonForInntekt(inntekt: Inntekt): String {
    val inntekterGrunnlag =
        find { inntekt.type.tilGrunnlagsdataType() == it.type && it.erBearbeidet }
    return inntekterGrunnlag.konverterData<SummerteInntekter<SummertÅrsinntekt>>()?.versjon
        ?: vedtakmappingFeilet("Mangler versjon for beregnet inntekt ${inntekt.type}")
}

fun List<Grunnlag>.hentGrunnlagsreferanserForInntekt(
    gjelderIdent: String,
    inntekt: Inntekt,
): List<Grunnlagsreferanse> {
    if (inntekt.kilde == Kilde.MANUELL) return emptyList()
    val periode = ÅrMånedsperiode(inntekt.opprinneligFom!!, inntekt.opprinneligTom)
    val inntekterGjelderGrunnlag =
        find {
            it.type == inntekt.type.tilGrunnlagsdataType() && it.erBearbeidet && hentNyesteIdent(
                it.rolle.ident,
            )?.verdi == gjelderIdent
        }

    val inntekter =
        inntekterGjelderGrunnlag.konverterData<SummerteInntekter<SummertÅrsinntekt>>()
            ?.inntekter
    val beregnetInntekt =
        inntekter?.find {
            it.periode == periode &&
                inntekt.type == it.inntektRapportering &&
                (inntekt.gjelderBarn.isNullOrEmpty() || inntekt.gjelderBarn == it.gjelderBarnPersonId.trimToNull())
        }

    return beregnetInntekt?.grunnlagsreferanseListe?.filter { it.isNotEmpty() }
        ?: grunnlagByggingFeilet(
            "Mangler grunnlagsreferanse for offentlig inntekt ${inntekt.type} " +
                "for periode (${inntekt.opprinneligFom}-${inntekt.opprinneligTom}) og barn ${inntekt.gjelderBarn}",
        ) // ?: opprettGrunnlagsreferanserForInntekt2
}

private fun opprettGrunnlagsreferanserForInntekt2(
    inntekt: Inntekt,
    gjelderReferanse: Grunnlagsreferanse,
): List<Grunnlagsreferanse> {
    val referanse =
        when (inntekt.type) {
            Inntektsrapportering.AINNTEKT_BEREGNET_12MND, Inntektsrapportering.AINNTEKT_BEREGNET_3MND ->
                opprettAinntektGrunnlagsreferanse(gjelderReferanse)

            Inntektsrapportering.BARNETILLEGG ->
                opprettBarnetilleggGrunnlagsreferanse(gjelderReferanse)

            Inntektsrapportering.SMÅBARNSTILLEGG ->
                opprettSmåbarnstilleggGrunnlagsreferanse(gjelderReferanse)

            Inntektsrapportering.UTVIDET_BARNETRYGD ->
                opprettUtvidetbarnetrygGrunnlagsreferanse(gjelderReferanse)

            Inntektsrapportering.KONTANTSTØTTE ->
                opprettKontantstøtteGrunnlagsreferanse(gjelderReferanse)

            Inntektsrapportering.KAPITALINNTEKT, Inntektsrapportering.LIGNINGSINNTEKT ->
                opprettSkattegrunnlagGrunnlagsreferanse(
                    gjelderReferanse,
                    inntekt.opprinneligFom?.year!!,
                )

            else -> null
        }

    return listOfNotNull(referanse)
}

private fun List<Grunnlag>.mapBarnetillegg(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.BARNETILLEGG && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val barnetillegListe =
                grunnlag.konverterData<List<BarnetilleggGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            barnetillegListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                personobjekter,
            )
        }.toSet()

private fun List<Grunnlag>.mapBarnetilsyn(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.BARNETILSYN && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val barnetilsynListe =
                grunnlag.konverterData<List<BarnetilsynGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            barnetilsynListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                personobjekter,
            )
        }.toSet()

private fun List<Grunnlag>.mapAinntekt(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .map { (ident, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val ainntektListe =
                grunnlag.konverterData<SkattepliktigeInntekter>()?.ainntekter
                    ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            ainntektListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

private fun List<Grunnlag>.mapKontantstøtte(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.KONTANTSTØTTE && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val kontantstøtteListe =
                grunnlag.konverterData<List<KontantstøtteGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            kontantstøtteListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                personobjekter,
            )
        }.toSet()

private fun List<Grunnlag>.mapSmåbarnstillegg(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.SMÅBARNSTILLEGG && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val småbarnstilleggListe =
                grunnlag.konverterData<List<SmåbarnstilleggGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            småbarnstilleggListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

private fun List<Grunnlag>.mapUtvidetbarnetrygd(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.UTVIDET_BARNETRYGD && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val utvidetBarnetrygdListe =
                grunnlag.konverterData<List<UtvidetBarnetrygdGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            utvidetBarnetrygdListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

private fun List<Grunnlag>.mapSkattegrunnlag(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }.groupBy { it.rolle.ident }
        .flatMap { (ident, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val skattegrunnlag =
                grunnlag.konverterData<SkattepliktigeInntekter>()?.skattegrunnlag
                    ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            skattegrunnlag.map {
                it.tilGrunnlagsobjekt(
                    grunnlag.innhentet,
                    gjelder.referanse,
                )
            }
        }.toSet()
