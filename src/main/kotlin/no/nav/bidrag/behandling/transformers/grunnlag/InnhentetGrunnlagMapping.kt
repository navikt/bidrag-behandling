package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.arbeidsforhold
import no.nav.bidrag.behandling.database.datamodell.hentData
import no.nav.bidrag.behandling.database.datamodell.husstandmedlemmer
import no.nav.bidrag.behandling.database.datamodell.inntekt
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.opplysninger.InntektGrunnlag
import no.nav.bidrag.behandling.database.opplysninger.InntektsopplysningerBearbeidet
import no.nav.bidrag.behandling.service.beregnSivilstandPerioder
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.response.BoforholdBeregnet
import no.nav.bidrag.boforhold.response.RelatertPerson
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.toCompactString

fun List<BehandlingGrunnlag>.tilInnhentetArbeidsforhold(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return arbeidsforhold?.let { grunnlag ->
        grunnlag.hentData<List<ArbeidsforholdGrunnlagDto>>()
            ?.groupBy { it.partPersonId }
            ?.map {
                val gjelder = personobjekter.hentPerson(it.key)!!
                it.value.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse)
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetSivilstand(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return find { it.type == Grunnlagsdatatype.SIVILSTAND }?.let { grunnlag ->
        grunnlag.hentData<List<SivilstandGrunnlagDto>>()
            ?.groupBy { it.personId }
            ?.map {
                val gjelder = personobjekter.hentPerson(it.key)!!
                it.value.tilGrunnlagsobjekt(
                    grunnlag.innhentet,
                    gjelder.referanse,
                )
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetHusstandsmedlemmer(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    val personobjekterInnhentetHusstandsmedlem = mutableSetOf<GrunnlagDto>()

    fun RelatertPersonGrunnlagDto.opprettPersonGrunnlag(): GrunnlagDto {
        val relatertPersonGrunnlag = tilPersonGrunnlag()
        personobjekterInnhentetHusstandsmedlem.add(relatertPersonGrunnlag)
        return relatertPersonGrunnlag
    }

    val innhentetHusstandsmedlemGrunnlagListe =
        husstandmedlemmer?.let { grunnlag ->
            grunnlag.hentData<List<RelatertPersonGrunnlagDto>>()
                ?.groupBy { it.partPersonId }
                ?.flatMap { part ->
                    val gjelder = personobjekter.hentPerson(part.key)!!
                    part.value.groupBy { it.relatertPersonPersonId }
                        .map { relatertPersonMap ->
                            val relatertPersonObjekt =
                                (personobjekter + personobjekterInnhentetHusstandsmedlem).hentPerson(
                                    relatertPersonMap.key,
                                )
                                    ?: relatertPersonMap.value[0].opprettPersonGrunnlag()
                            val relatertPersonListe = relatertPersonMap.value
                            if (relatertPersonListe.size > 1) innhentetGrunnlagHarFlereRelatertePersonMedSammeId()

                            relatertPersonListe.first().tilGrunnlagsobjekt(
                                grunnlag.innhentet,
                                gjelder.referanse,
                                relatertPersonObjekt.referanse,
                            )
                        }
                }
        }?.toSet() ?: emptySet()

    return innhentetHusstandsmedlemGrunnlagListe + personobjekterInnhentetHusstandsmedlem
}

fun List<BehandlingGrunnlag>.tilBeregnetInntekt(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return find { it.type == Grunnlagsdatatype.INNTEKT_BEARBEIDET }?.let { grunnlag ->
        grunnlag.hentData<InntektsopplysningerBearbeidet>()
            ?.inntekt
            ?.filter { it.summertMånedsinntektListe.isNotEmpty() }
            ?.groupBy { it.ident }
            ?.map { inntektMap ->
                val gjelder = personobjekter.hentPerson(inntektMap.key)!!
                val inntekt = inntektMap.value[0]
                GrunnlagDto(
                    referanse = "beregnet_inntekt_${gjelder.referanse}",
                    type = Grunnlagstype.BEREGNET_INNTEKT,
                    gjelderReferanse = gjelder.referanse,
                    grunnlagsreferanseListe = inntekt.summertMånedsinntektListe.flatMap { it.grunnlagsreferanseListe },
                    innhold =
                        POJONode(
                            BeregnetInntekt(
                                versjon = inntekt.versjon ?: "",
                                summertMånedsinntektListe =
                                    inntekt.summertMånedsinntektListe.map { månedsinntekt ->
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
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetGrunnlagInntekt(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return mapSkattegrunnlag(personobjekter) + mapAinntekt(personobjekter) +
        mapKontantstøtte(personobjekter) +
        mapBarnetilsyn(personobjekter) +
        mapBarnetillegg(personobjekter) +
        mapUtvidetbarnetrygd(personobjekter) + mapSmåbarnstillegg(personobjekter)
}

private fun RelatertPersonGrunnlagDto.tilRelatertPerson() =
    RelatertPerson(
        relatertPersonPersonId = relatertPersonPersonId,
        fødselsdato = fødselsdato,
        erBarnAvBmBp = erBarnAvBmBp,
        borISammeHusstandDtoListe = borISammeHusstandDtoListe,
    )

fun Behandling.hentGrunnlagsreferanserForSivilstand(periode: Sivilstand): List<Grunnlagsreferanse> {
    val beregnet = beregnSivilstandPerioder()
    return beregnet.sivilstandListe.find {
        it.periodeFom == periode.datoFom && it.periodeTom == periode.datoTom && it.sivilstandskode == periode.sivilstand
    }?.grunnlagsreferanser ?: emptyList()
}

fun List<BehandlingGrunnlag>.hentGrunnlagsreferanserForHusstandsmedlem(
    gjelderIdent: String,
    gjelderBarn: String?,
    periode: Husstandsbarnperiode,
    behandling: Behandling,
): List<Grunnlagsreferanse> {
    if (gjelderBarn.isNullOrEmpty()) return emptyList()
    val husstandsmedlemmer =
        find { it.type == Grunnlagsdatatype.HUSSTANDSMEDLEMMER }.konverterData<List<RelatertPersonGrunnlagDto>>()
            ?: emptyList()
    val beregnet =
        BoforholdApi.beregn(
            behandling.virkningstidspunkt ?: behandling.søktFomDato,
            husstandsmedlemmer.map { it.tilRelatertPerson() },
        )
    val husstandsmedlemmerGjelder =
        beregnet?.filter { it.relatertPersonPersonId == gjelderIdent && it.relatertPersonPersonId == gjelderBarn }
    val referanse =
        husstandsmedlemmerGjelder?.find {
            it.periodeFom == periode.datoFom && it.periodeTom == periode.datoTom && it.bostatus == periode.bostatus
        }?.tilGrunnlagsreferanse(gjelderIdent, gjelderBarn)
    return listOfNotNull(referanse)
}

fun BoforholdBeregnet.tilGrunnlagsreferanse(
    referanseGjelder: Grunnlagsreferanse,
    referanseRelatertTil: Grunnlagsreferanse,
) = "innhentet_husstandsmedlem_${referanseGjelder}_${referanseRelatertTil}_${periodeFom.toCompactString()}"

fun List<BehandlingGrunnlag>.hentGrunnlagsreferanserForInntekt(
    gjelderIdent: String,
    inntekt: Inntekt,
): List<Grunnlagsreferanse> {
    if (inntekt.opprinneligFom == null) return emptyList()
    val periode = ÅrMånedsperiode(inntekt.opprinneligFom!!, inntekt.opprinneligTom)
    val beregnetInntekter =
        find { it.type == Grunnlagsdatatype.INNTEKT_BEARBEIDET }.konverterData<InntektsopplysningerBearbeidet>()
    val inntekterGjelder = beregnetInntekter?.inntekt?.find { it.ident == gjelderIdent }
    val beregnetInntekt =
        inntekterGjelder?.summertAarsinntektListe?.find {
            it.periode == periode &&
                inntekt.inntektsrapportering == it.inntektRapportering &&
                (inntekt.gjelderBarn.isNullOrEmpty() || inntekt.gjelderBarn == it.gjelderBarnPersonId)
        }

    return beregnetInntekt?.grunnlagsreferanseListe ?: emptyList()
}

private fun List<BehandlingGrunnlag>.mapBarnetillegg(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.barnetilleggListe?.groupBy { it.partPersonId }
            ?.flatMap { partMap ->
                val gjelder = personobjekter.hentPerson(partMap.key)!!
                partMap.value.groupBy { it.barnPersonId }
                    .flatMap { barnetilleggMap ->
                        val søknadsbarn = personobjekter.hentPerson(barnetilleggMap.key)!!
                        barnetilleggMap.value.map {
                            it.tilGrunnlagsobjekt(
                                grunnlag.innhentet,
                                gjelder.referanse,
                                søknadsbarn.referanse,
                            )
                        }
                    }
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapBarnetilsyn(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.barnetilsynListe?.groupBy { it.partPersonId }
            ?.flatMap { partMap ->
                val gjelder = personobjekter.hentPerson(partMap.key)!!
                partMap.value.groupBy { it.barnPersonId }
                    .flatMap { barnetilsynMap ->
                        val søknadsbarn = personobjekter.hentPerson(barnetilsynMap.key)!!
                        barnetilsynMap.value.map {
                            it.tilGrunnlagsobjekt(
                                grunnlag.innhentet,
                                gjelder.referanse,
                                søknadsbarn.referanse,
                            )
                        }
                    }
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapAinntekt(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.ainntektListe?.groupBy { it.personId }
            ?.flatMap { ainntektMap ->
                val gjelder = personobjekter.hentPerson(ainntektMap.key)!!
                ainntektMap.value.map {
                    it.tilGrunnlagsobjekt(
                        grunnlag.innhentet,
                        gjelder.referanse,
                    )
                }
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapKontantstøtte(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.kontantstotteListe?.groupBy { it.partPersonId }
            ?.flatMap { partMap ->
                val gjelder = personobjekter.hentPerson(partMap.key)!!
                partMap.value.groupBy { it.barnPersonId }
                    .flatMap { kontantStøtteMap ->
                        val søknadsbarn = personobjekter.hentPerson(kontantStøtteMap.key)!!
                        kontantStøtteMap.value.map {
                            it.tilGrunnlagsobjekt(
                                grunnlag.innhentet,
                                gjelder.referanse,
                                søknadsbarn.referanse,
                            )
                        }
                    }
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapSmåbarnstillegg(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.småbarnstilleggListe?.groupBy { it.personId }
            ?.flatMap { småbarnstillegMap ->
                val gjelder = personobjekter.hentPerson(småbarnstillegMap.key)!!
                småbarnstillegMap.value.map {
                    it.tilGrunnlagsobjekt(
                        grunnlag.innhentet,
                        gjelder.referanse,
                    )
                }
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapUtvidetbarnetrygd(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.utvidetBarnetrygdListe?.groupBy { it.personId }
            ?.flatMap { ubMap ->
                val gjelder = personobjekter.hentPerson(ubMap.key)!!
                ubMap.value.map { it.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse) }
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapSkattegrunnlag(personobjekter: Set<GrunnlagDto>) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.skattegrunnlagListe
            ?.groupBy { it.personId }
            ?.flatMap { skattegrunnlagMap ->
                val gjelder = personobjekter.hentPerson(skattegrunnlagMap.key)!!
                skattegrunnlagMap.value.map {
                    it.tilGrunnlagsobjekt(
                        grunnlag.innhentet,
                        gjelder.referanse,
                    )
                }
            }
    }?.toSet() ?: emptySet()
