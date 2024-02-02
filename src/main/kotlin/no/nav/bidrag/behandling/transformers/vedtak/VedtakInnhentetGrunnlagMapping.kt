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
import no.nav.bidrag.behandling.transformers.personObjekt
import no.nav.bidrag.behandling.transformers.tilPersonGrunnlag
import no.nav.bidrag.behandling.transformers.toCompactString
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetAinntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetArbeidsforhold
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetBarnetillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetBarnetilsyn
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetHusstandsmedlem
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetKontantstøtte
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSivilstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSkattegrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSmåbarnstillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetUtvidetBarnetrygd
import no.nav.bidrag.transport.behandling.felles.grunnlag.NULL_PERIODE_FRA
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto

fun List<BehandlingGrunnlag>.tilInnhentetArbeidsforhold(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personObjekt.ident.verdi
    return arbeidsforhold?.let { grunnlag ->
        grunnlag.hentData<List<ArbeidsforholdGrunnlagDto>>()
            ?.filter { it.partPersonId == personidentGjelder }
            ?.map {
                GrunnlagDto(
                    referanse =
                        "innhentet_arbeidsforhold_${gjelder.referanse}_" +
                            "${it.arbeidsgiverOrgnummer}_${it.startdato?.toCompactString()}",
                    type = Grunnlagstype.INNHENTET_ARBEIDSFORHOLD_PERIODE,
                    grunnlagsreferanseListe = listOf(gjelder.referanse),
                    innhold =
                        POJONode(
                            InnhentetArbeidsforhold(
                                periode =
                                    ÅrMånedsperiode(
                                        it.startdato ?: NULL_PERIODE_FRA,
                                        it.sluttdato,
                                    ),
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    InnhentetArbeidsforhold.Arbeidsforhold(
                                        startdato = it.startdato,
                                        sluttdato = it.sluttdato,
                                        arbeidsgiverNavn = it.arbeidsgiverNavn,
                                        arbeidsgiverOrgnummer = it.arbeidsgiverOrgnummer,
                                        permisjonListe =
                                            it.permisjonListe?.map { permisjon ->
                                                InnhentetArbeidsforhold.Arbeidsforhold.Permisjon(
                                                    startdato = permisjon.startdato,
                                                    sluttdato = permisjon.sluttdato,
                                                    beskrivelse = permisjon.beskrivelse,
                                                    prosent = permisjon.prosent,
                                                )
                                            } ?: emptyList(),
                                        permitteringListe =
                                            it.permitteringListe?.map { permittering ->
                                                InnhentetArbeidsforhold.Arbeidsforhold.Permittering(
                                                    startdato = permittering.startdato,
                                                    sluttdato = permittering.sluttdato,
                                                    beskrivelse = permittering.beskrivelse,
                                                    prosent = permittering.prosent,
                                                )
                                            } ?: emptyList(),
                                        ansettelsesdetaljerListe =
                                            it.ansettelsesdetaljerListe?.map { ansettelsesdetalj ->
                                                InnhentetArbeidsforhold.Ansettelsesdetaljer(
                                                    periodeFra = ansettelsesdetalj.periodeFra,
                                                    periodeTil = ansettelsesdetalj.periodeTil,
                                                    arbeidsforholdType = ansettelsesdetalj.arbeidsforholdType,
                                                    arbeidstidsordningBeskrivelse =
                                                        ansettelsesdetalj.arbeidstidsordningBeskrivelse,
                                                    ansettelsesformBeskrivelse =
                                                        ansettelsesdetalj.ansettelsesformBeskrivelse,
                                                    yrkeBeskrivelse = ansettelsesdetalj.yrkeBeskrivelse,
                                                    antallTimerPrUke = ansettelsesdetalj.antallTimerPrUke,
                                                    avtaltStillingsprosent =
                                                        ansettelsesdetalj.avtaltStillingsprosent,
                                                    sisteStillingsprosentendringDato = ansettelsesdetalj.sisteStillingsprosentendringDato,
                                                    sisteLønnsendringDato = ansettelsesdetalj.sisteLønnsendringDato,
                                                )
                                            } ?: emptyList(),
                                    ),
                            ),
                        ),
                )
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetSivilstand(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personObjekt.ident.verdi
    return find { it.type == Grunnlagsdatatype.SIVILSTAND }?.let { grunnlag ->
        grunnlag.hentData<List<SivilstandGrunnlagDto>>()
            ?.filter { it.personId == personidentGjelder }
            ?.mapIndexed { i, it ->
                GrunnlagDto(
                    referanse = "innhentet_sivilstand_${gjelder.referanse}_${it.type}_$i",
                    type = Grunnlagstype.INNHENTET_SIVILSTAND_PERIODE,
                    grunnlagsreferanseListe = listOf(gjelder.referanse),
                    innhold =
                        POJONode(
                            InnhentetSivilstand(
                                periode = ÅrMånedsperiode(it.gyldigFom ?: NULL_PERIODE_FRA, null),
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    InnhentetSivilstand.SivilstandPDL(
                                        sivilstand = it.type,
                                        bekreftelsesdato = it.bekreftelsesdato,
                                        master = it.master,
                                        historisk = it.historisk,
                                    ),
                            ),
                        ),
                )
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilInnhentetHusstandsmedlemmer(
    gjelder: GrunnlagDto,
    personobjekter: List<GrunnlagDto>,
): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personObjekt.ident.verdi
    return husstandmedlemmer?.let { grunnlag ->
        grunnlag.hentData<List<RelatertPersonGrunnlagDto>>()
            ?.filter { it.partPersonId == personidentGjelder }
            ?.flatMapIndexed { i, relaterPerson ->
                val relatertPersonObjekt =
                    personobjekter.find { it.personIdent == relaterPerson.relatertPersonPersonId }
                        ?: relaterPerson.tilPersonGrunnlag(i.toLong())
                relaterPerson.borISammeHusstandDtoListe.map {
                    GrunnlagDto(
                        referanse =
                            "innhentet_husstandsmedlem_${gjelder.referanse}_" +
                                "${relatertPersonObjekt.referanse}_${it.periodeFra?.toCompactString()}",
                        type = Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE,
                        grunnlagsreferanseListe = listOf(gjelder.referanse),
                        innhold =
                            POJONode(
                                InnhentetHusstandsmedlem(
                                    periode =
                                        ÅrMånedsperiode(
                                            it.periodeFra ?: NULL_PERIODE_FRA,
                                            it.periodeTil,
                                        ),
                                    hentetTidspunkt = grunnlag.innhentet,
                                    grunnlag =
                                        InnhentetHusstandsmedlem.HusstandsmedlemPDL(
                                            relatertPerson = relatertPersonObjekt?.referanse,
                                            erBarnAvBmBp = relaterPerson.erBarnAvBmBp,
                                        ),
                                ),
                            ),
                    )
                } + relatertPersonObjekt
            }
    }?.toSet() ?: emptySet()
}

fun List<BehandlingGrunnlag>.tilBeregnetInntekt(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personObjekt.ident.verdi
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
                        grunnlagsreferanseListe = listOf(gjelder.referanse),
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
            GrunnlagDto(
                referanse =
                    "innhentet_barnetillegg_${gjelder.referanse}_" +
                        "barn_${søknadsbarn.referanse}_${it.periodeFra.toCompactString()}",
                type = Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG_PERIODE,
                grunnlagsreferanseListe = listOf(gjelder.referanse, søknadsbarn.referanse),
                innhold =
                    POJONode(
                        InnhentetBarnetillegg(
                            periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                            hentetTidspunkt = grunnlag.innhentet,
                            grunnlag =
                                InnhentetBarnetillegg.Barnetillegg(
                                    gjelderBarn = søknadsbarn.referanse,
                                    barnetilleggType = it.barnetilleggType,
                                    barnType = it.barnType,
                                    beløpBrutto = it.beløpBrutto,
                                ),
                        ),
                    ),
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
            GrunnlagDto(
                referanse = "innhentet_barnetilsyn_${gjelder.referanse}_barn_${søknadsbarn.referanse}_${it.periodeFra.toCompactString()}",
                type = Grunnlagstype.INNHENTET_INNTEKT_AORDNING_PERIODE,
                grunnlagsreferanseListe = listOf(gjelder.referanse, søknadsbarn.referanse),
                innhold =
                    POJONode(
                        InnhentetBarnetilsyn(
                            periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                            hentetTidspunkt = grunnlag.innhentet,
                            grunnlag =
                                InnhentetBarnetilsyn.Barnetilsyn(
                                    gjelderBarn = søknadsbarn.referanse,
                                    beløp = it.beløp,
                                    tilsynstype = it.tilsynstype,
                                    skolealder = it.skolealder,
                                ),
                        ),
                    ),
            )
        }
}?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapAinntekt(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.ainntektListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                GrunnlagDto(
                    referanse = "innhentet_ainntekt_${gjelder.referanse}_${it.periodeFra.toCompactString()}",
                    type = Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE,
                    grunnlagsreferanseListe = listOf(gjelder.referanse),
                    innhold =
                        POJONode(
                            InnhentetAinntekt(
                                periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    InnhentetAinntekt.AinntektInnhentet(
                                        ainntektspostListe =
                                            it.ainntektspostListe.map { post ->
                                                InnhentetAinntekt.Ainntektspost(
                                                    utbetalingsperiode = post.utbetalingsperiode,
                                                    opptjeningsperiodeFra = post.opptjeningsperiodeFra,
                                                    opptjeningsperiodeTil = post.opptjeningsperiodeTil,
                                                    kategori = post.kategori,
                                                    fordelType = post.fordelType,
                                                    beløp = post.beløp,
                                                    etterbetalingsperiodeFra = post.etterbetalingsperiodeFra,
                                                    etterbetalingsperiodeTil = post.etterbetalingsperiodeTil,
                                                )
                                            },
                                    ),
                            ),
                        ),
                )
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapKontantstøtte(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto,
) = inntekt?.let { grunnlag ->
    grunnlag.hentData<InntektGrunnlag>()?.kontantstotteListe?.filter {
        it.partPersonId == gjelder.personIdent && it.barnPersonId == søknadsbarn.personIdent
    }
        ?.map {
            GrunnlagDto(
                referanse = "innhentet_kontantstøtte_${gjelder.referanse}_barn_${søknadsbarn.referanse}_${it.periodeFra.toCompactString()}",
                type = Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE_PERIODE,
                grunnlagsreferanseListe = listOf(gjelder.referanse),
                innhold =
                    POJONode(
                        InnhentetKontantstøtte(
                            periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                            hentetTidspunkt = grunnlag.innhentet,
                            grunnlag =
                                InnhentetKontantstøtte.Kontantstøtte(
                                    gjelderBarn = søknadsbarn.referanse,
                                    beløp = it.beløp,
                                ),
                        ),
                    ),
            )
        }
}?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapSmåbarnstillegg(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.småbarnstilleggListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                GrunnlagDto(
                    referanse = "innhentet_småbarnstillegg_${gjelder.referanse}_${it.periodeFra.toCompactString()}",
                    type = Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG_PERIODE,
                    grunnlagsreferanseListe = listOf(gjelder.referanse),
                    innhold =
                        POJONode(
                            InnhentetSmåbarnstillegg(
                                periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    InnhentetSmåbarnstillegg.Småbarnstillegg(
                                        beløp = it.beløp,
                                        manueltBeregnet = it.manueltBeregnet,
                                    ),
                            ),
                        ),
                )
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapUtvidetbarnetrygd(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.utvidetBarnetrygdListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                GrunnlagDto(
                    referanse = "innhentet_utvidetbarnetrygd_${gjelder.referanse}_${it.periodeFra.toCompactString()}",
                    type = Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD_PERIODE,
                    grunnlagsreferanseListe = listOf(gjelder.referanse),
                    innhold =
                        POJONode(
                            InnhentetUtvidetBarnetrygd(
                                periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    InnhentetUtvidetBarnetrygd.UtvidetBarnetrygd(
                                        beløp = it.beløp,
                                        manueltBeregnet = it.manueltBeregnet,
                                    ),
                            ),
                        ),
                )
            }
    }?.toSet() ?: emptySet()

private fun List<BehandlingGrunnlag>.mapSkattegrunnlag(gjelder: GrunnlagDto) =
    inntekt?.let { grunnlag ->
        grunnlag.hentData<InntektGrunnlag>()?.skattegrunnlagListe?.filter { it.personId == gjelder.personIdent }
            ?.map {
                GrunnlagDto(
                    referanse = "innhentet_skattegrunnlag_${gjelder.referanse}_${it.periodeFra.toCompactString()}",
                    type = Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE,
                    grunnlagsreferanseListe = listOf(gjelder.referanse),
                    innhold =
                        POJONode(
                            InnhentetSkattegrunnlag(
                                periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    InnhentetSkattegrunnlag.Skattegrunnlag(
                                        skattegrunnlagListe =
                                            it.skattegrunnlagspostListe.map { post ->
                                                InnhentetSkattegrunnlag.Skattegrunnlagspost(
                                                    skattegrunnlagType = post.skattegrunnlagType,
                                                    kode = post.kode,
                                                    beløp = post.beløp,
                                                )
                                            },
                                    ),
                            ),
                        ),
                )
            }
    }?.toSet() ?: emptySet()
