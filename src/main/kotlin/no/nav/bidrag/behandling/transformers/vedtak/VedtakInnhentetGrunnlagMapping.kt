package no.nav.bidrag.behandling.transformers.vedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.arbeidsforhold
import no.nav.bidrag.behandling.database.datamodell.hentData
import no.nav.bidrag.behandling.database.datamodell.husstandmedlemmer
import no.nav.bidrag.behandling.database.datamodell.inntekt
import no.nav.bidrag.behandling.database.opplysninger.InntektGrunnlag
import no.nav.bidrag.behandling.database.opplysninger.InntektsopplysningerBearbeidet
import no.nav.bidrag.behandling.transformers.personIdent
import no.nav.bidrag.behandling.transformers.tilPersonGrunnlag
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto

fun List<BehandlingGrunnlag>.tilInnhentetArbeidsforhold(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personIdent
    return arbeidsforhold?.let { grunnlag ->
        grunnlag.hentData<List<ArbeidsforholdGrunnlagDto>>()
            ?.filter { it.partPersonId == personidentGjelder }
            ?.map { it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse) }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetSivilstand(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personIdent
    return find { it.type == Grunnlagsdatatype.SIVILSTAND }?.let { grunnlag ->
        grunnlag.hentData<List<SivilstandGrunnlagDto>>()
            ?.filter { it.personId == personidentGjelder }
            ?.mapIndexed { i, it ->
                it.tilGrunnlagsobjekt(
                    grunnlag.innhentet,
                    gjelder.referanse,
                    i,
                )
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetHusstandsmedlemmer(
    gjelder: GrunnlagDto,
    personobjekter: List<GrunnlagDto>,
): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personIdent
    return husstandmedlemmer?.let { grunnlag ->
        grunnlag.hentData<List<RelatertPersonGrunnlagDto>>()
            ?.filter { it.partPersonId == personidentGjelder }
            ?.flatMapIndexed { i, relaterPerson ->
                val relatertPersonObjekt =
                    personobjekter.find { it.personIdent == relaterPerson.relatertPersonPersonId }
                        ?: relaterPerson.tilPersonGrunnlag(i)
                relaterPerson.borISammeHusstandDtoListe.map {
                    it.tilGrunnlagsobjekt(
                        grunnlag.innhentet,
                        gjelder.referanse,
                        relatertPersonObjekt.referanse,
                        relaterPerson,
                    )
                    // Duplikat grunnlagsobjekter fjernes i ettertid
                } + relatertPersonObjekt
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilBeregnetInntekt(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personIdent
    return find { it.type == Grunnlagsdatatype.INNTEKT_BEARBEIDET }?.let { grunnlag ->
        grunnlag.hentData<InntektsopplysningerBearbeidet>()
            ?.inntekt
            ?.find { it.ident == personidentGjelder && it.summertAarsinntektListe.isNotEmpty() }
            ?.let {
                setOf(
                    GrunnlagDto(
                        referanse =
                            "beregnet_inntekt_${gjelder.referanse}",
                        type = Grunnlagstype.BEREGNET_INNTEKT,
                        gjelderReferanse = gjelder.referanse,
                        innhold =
                            POJONode(
                                BeregnetInntekt(
                                    versjon = it.versjon ?: "",
                                    summertMånedsinntektListe =
                                        it.summertMånedsinntektListe.map { månedsinntekt ->
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
                    ),
                )
            }
    } ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetGrunnlagInntekt(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto,
): Set<GrunnlagDto> {
    return mapSkattegrunnlag(gjelder) + mapAinntekt(gjelder) +
        mapKontantstøtte(gjelder, søknadsbarn) +
        mapBarnetilsyn(gjelder, søknadsbarn) +
        mapBarnetillegg(gjelder, søknadsbarn) +
        mapUtvidetbarnetrygd(gjelder) + mapSmåbarnstillegg(gjelder)
}

fun List<BehandlingGrunnlag>.tilInnhentetGrunnlagInntektBarn(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    return mapSkattegrunnlag(gjelder) + mapAinntekt(gjelder)
}

private fun List<BehandlingGrunnlag>.mapBarnetillegg(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto,
) = inntekt?.let { grunnlag ->
    grunnlag.hentData<InntektGrunnlag>()?.barnetilleggListe?.filter {
        it.partPersonId == gjelder.personIdent && it.barnPersonId == søknadsbarn.personIdent
    }
        ?.map {
            it.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                søknadsbarn.referanse,
            )
        }
}?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapBarnetilsyn(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto,
) = inntekt?.let { grunnlag ->
    grunnlag.hentData<InntektGrunnlag>()?.barnetilsynListe?.filter {
        it.partPersonId == gjelder.personIdent && it.barnPersonId == søknadsbarn.personIdent
    }
        ?.map {
            it.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                søknadsbarn.referanse,
            )
        }
}?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapAinntekt(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.ainntektListe?.filter { it.personId == gjelder.personIdent }
            ?.map { it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse) }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapKontantstøtte(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto,
) = inntekt?.let { grunnlag ->
    grunnlag.hentData<InntektGrunnlag>()?.kontantstotteListe?.filter {
        it.partPersonId == gjelder.personIdent && it.barnPersonId == søknadsbarn.personIdent
    }
        ?.map {
            it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse, søknadsbarn.referanse)
        }
}?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapSmåbarnstillegg(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.småbarnstilleggListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse)
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapUtvidetbarnetrygd(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.utvidetBarnetrygdListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse)
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapSkattegrunnlag(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.skattegrunnlagListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse)
            }
    }?.toSet() ?: emptySet()
