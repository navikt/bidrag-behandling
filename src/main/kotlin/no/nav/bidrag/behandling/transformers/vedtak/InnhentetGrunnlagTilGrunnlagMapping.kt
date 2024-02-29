package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.Ansettelsesdetaljer
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.Permisjon
import no.nav.bidrag.transport.behandling.grunnlag.response.Permittering
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt

fun List<GrunnlagDto>.hentBeregnetInntekt(): Map<String, SummerteMånedsOgÅrsinntekter> {
    return filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.BEREGNET_INNTEKT).groupBy {
        val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
        gjelder.personIdent
    }.map { (ident, beregnetInntekt) ->
        val innhold = beregnetInntekt.innholdTilObjekt<BeregnetInntekt>().first()
        ident to
            SummerteMånedsOgÅrsinntekter(
                versjon = innhold.versjon,
                summerteÅrsinntekter = emptyList(),
                summerteMånedsinntekter =
                    innhold.summertMånedsinntektListe
                        .map {
                            SummertMånedsinntekt(
                                gjelderÅrMåned = it.gjelderÅrMåned,
                                sumInntekt = it.sumInntekt,
                                inntektPostListe =
                                    it.inntektPostListe.map {
                                        InntektPost(
                                            kode = it.kode,
                                            beløp = it.beløp,
                                            inntekstype = it.inntekstype,
                                        )
                                    },
                            )
                        },
            )
    }.associate { it.first!! to it.second }
}

fun List<GrunnlagDto>.hentInnhenetHusstandsmedlem(): List<RelatertPersonGrunnlagDto> =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM)
        .map {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
            val relatertPerson = hentPersonMedReferanse(innhold.grunnlag.relatertPerson)!!
            RelatertPersonGrunnlagDto(
                partPersonId = gjelder.personIdent!!,
                relatertPersonPersonId = relatertPerson.personIdent,
                navn = innhold.grunnlag.navn,
                fødselsdato = innhold.grunnlag.fødselsdato,
                erBarnAvBmBp = innhold.grunnlag.erBarnAvBmBp,
                borISammeHusstandDtoListe =
                    innhold.grunnlag.perioder.map {
                        BorISammeHusstandDto(it.fom, it.til)
                    },
            )
        }

fun List<GrunnlagDto>.hentInnhentetSivilstand() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_SIVILSTAND)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetSivilstand>()
            innhold.grunnlag.map {
                SivilstandGrunnlagDto(
                    personId = gjelder.personIdent!!,
                    gyldigFom = it.gyldigFom,
                    type = it.sivilstand,
                    bekreftelsesdato = it.bekreftelsesdato,
                    master = it.master,
                    registrert = it.registrert,
                    historisk = it.historisk,
                )
            }
        }

fun List<GrunnlagDto>.hentBarnetilsynListe() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetBarnetilsyn>()
            innhold.grunnlag.map {
                val barn = hentPersonMedReferanse(it.gjelderBarn)!!
                BarnetilsynGrunnlagDto(
                    partPersonId = gjelder.personIdent!!,
                    periodeFra = it.periode.fom,
                    periodeTil = it.periode.til,
                    beløp = it.beløp,
                    barnPersonId = barn.personIdent!!,
                    skolealder = it.skolealder,
                    tilsynstype = it.tilsynstype,
                )
            }
        }

fun List<GrunnlagDto>.hentKontantstøtteListe() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetKontantstøtte>()
            innhold.grunnlag.map {
                val barn = hentPersonMedReferanse(it.gjelderBarn)!!
                KontantstøtteGrunnlagDto(
                    partPersonId = gjelder.personIdent!!,
                    periodeFra = it.periode.fom,
                    periodeTil = it.periode.til,
                    beløp = it.beløp,
                    barnPersonId = barn.personIdent!!,
                )
            }
        }

fun List<GrunnlagDto>.hentBarnetillegListe() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetBarnetillegg>()
            innhold.grunnlag.map {
                val barn = hentPersonMedReferanse(it.gjelderBarn)!!
                BarnetilleggGrunnlagDto(
                    partPersonId = gjelder.personIdent!!,
                    periodeFra = it.periode.fom,
                    periodeTil = it.periode.til,
                    beløpBrutto = it.beløpBrutto,
                    barnType = it.barnType,
                    barnetilleggType = it.barnetilleggType,
                    barnPersonId = barn.personIdent!!,
                )
            }
        }

fun List<GrunnlagDto>.hentSmåbarnstilleggListe() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetSmåbarnstillegg>()
            innhold.grunnlag.map {
                SmåbarnstilleggGrunnlagDto(
                    personId = gjelder.personIdent!!,
                    periodeFra = it.periode.fom,
                    periodeTil = it.periode.til!!,
                    beløp = it.beløp,
                    manueltBeregnet = it.manueltBeregnet,
                )
            }
        }

fun List<GrunnlagDto>.hentUtvidetbarnetrygdListe() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetUtvidetBarnetrygd>()
            innhold.grunnlag.map {
                UtvidetBarnetrygdGrunnlagDto(
                    personId = gjelder.personIdent!!,
                    periodeFra = it.periode.fom,
                    periodeTil = it.periode.til!!,
                    beløp = it.beløp,
                    manueltBeregnet = it.manueltBeregnet,
                )
            }
        }

fun List<GrunnlagDto>.hentGrunnlagSkattegrunnlag() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE)
        .map {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetSkattegrunnlag>()
            SkattegrunnlagGrunnlagDto(
                personId = gjelder.personIdent!!,
                periodeFra = innhold.periode.fom,
                periodeTil = innhold.periode.til!!,
                skattegrunnlagspostListe =
                    innhold.grunnlag.map {
                        SkattegrunnlagspostDto(
                            skattegrunnlagType = it.skattegrunnlagType,
                            kode = it.kode,
                            beløp = it.beløp,
                            belop = it.beløp,
                            inntektType = it.kode,
                        )
                    },
            )
        }

fun List<GrunnlagDto>.hentGrunnlagAinntekt() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
        .flatMap {
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            val innhold = it.innholdTilObjekt<InnhentetAinntekt>()
            innhold.grunnlag.map {
                AinntektGrunnlagDto(
                    personId = gjelder.personIdent!!,
                    periodeFra = it.periode.fom,
                    periodeTil = it.periode.til!!,
                    ainntektspostListe =
                        it.ainntektspostListe.map {
                            AinntektspostDto(
                                utbetalingsperiode = it.utbetalingsperiode,
                                beløp = it.beløp,
                                opptjeningsperiodeFra = it.opptjeningsperiodeFra,
                                opptjeningsperiodeTil = it.opptjeningsperiodeTil,
                                fordelType = it.fordelType,
                                etterbetalingsperiodeFra = it.etterbetalingsperiodeFra,
                                etterbetalingsperiodeTil = it.etterbetalingsperiodeTil,
                                kategori = it.kategori,
                                belop = it.beløp,
                                beskrivelse = it.beskrivelse,
                                inntektType = it.kategori,
                                opplysningspliktigId = it.opplysningspliktigId,
                                virksomhetId = it.virksomhetId,
                            )
                        },
                )
            }
        }

fun List<GrunnlagDto>.hentGrunnlagArbeidsforhold() =
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.INNHENTET_ARBEIDSFORHOLD)
        .flatMap {
            val innhold = it.innholdTilObjekt<InnhentetArbeidsforhold>()
            val arbeidsforholdGrunnlag = innhold.grunnlag
            val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
            arbeidsforholdGrunnlag.map {
                ArbeidsforholdGrunnlagDto(
                    partPersonId = gjelder.personIdent!!,
                    startdato = it.startdato,
                    sluttdato = it.sluttdato,
                    arbeidsgiverNavn = it.arbeidsgiverNavn,
                    arbeidsgiverOrgnummer = it.arbeidsgiverOrgnummer,
                    ansettelsesdetaljerListe =
                        it.ansettelsesdetaljerListe.map {
                            Ansettelsesdetaljer(
                                periodeFra = it.periodeFra,
                                periodeTil = it.periodeTil,
                                arbeidsforholdType = it.arbeidsforholdType,
                                arbeidstidsordningBeskrivelse = it.arbeidstidsordningBeskrivelse,
                                ansettelsesformBeskrivelse = it.ansettelsesformBeskrivelse,
                                yrkeBeskrivelse = it.yrkeBeskrivelse,
                                avtaltStillingsprosent = it.avtaltStillingsprosent,
                                antallTimerPrUke = it.antallTimerPrUke,
                                sisteLønnsendringDato = it.sisteLønnsendringDato,
                                sisteStillingsprosentendringDato = it.sisteStillingsprosentendringDato,
                            )
                        },
                    permisjonListe =
                        it.permisjonListe.map {
                            Permisjon(
                                startdato = it.startdato,
                                beskrivelse = it.beskrivelse,
                                prosent = it.prosent,
                                sluttdato = it.sluttdato,
                            )
                        },
                    permitteringListe =
                        it.permitteringListe.map {
                            Permittering(
                                startdato = it.startdato,
                                sluttdato = it.sluttdato,
                                beskrivelse = it.beskrivelse,
                                prosent = it.prosent,
                            )
                        },
                )
            }
        }
