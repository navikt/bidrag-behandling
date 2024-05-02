package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.ainntektListe
import no.nav.bidrag.behandling.transformers.grunnlag.skattegrunnlagListe
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
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
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDateTime

data class SummerteInntekt(
    val versjon: String,
    val inntekt: SummertÅrsinntekt,
)

fun List<GrunnlagDto>.hentBeregnetInntekt(): Map<String, SummerteInntekter<SummertMånedsinntekt>> {
    return filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.BEREGNET_INNTEKT).groupBy {
        val gjelder = hentPersonMedReferanse(it.gjelderReferanse)!!
        gjelder.personIdent
    }.map { (ident, beregnetInntekt) ->
        val innhold = beregnetInntekt.innholdTilObjekt<BeregnetInntekt>().first()
        ident to
            SummerteInntekter(
                versjon = innhold.versjon,
                inntekter =
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

fun List<GrunnlagDto>.hentInnhentetHusstandsmedlem(): List<RelatertPersonGrunnlagDto> =
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

fun List<GrunnlagDto>.hentGrunnlagSkattepliktig(): Map<String, SkattepliktigeInntekter> {
    val skattepliktigGruppert = hentGrunnlagSkattegrunnlag().groupBy { it.personId }
    val ainntektGruppert = hentGrunnlagAinntekt().groupBy { it.personId }
    val identer = ainntektGruppert.keys + skattepliktigGruppert.keys
    return identer.associate { personident ->
        personident to
            SkattepliktigeInntekter(
                skattegrunnlag = skattepliktigGruppert[personident] ?: emptyList(),
                ainntekter = ainntektGruppert[personident] ?: emptyList(),
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

fun List<GrunnlagDto>.hentInnntekterBearbeidet(
    behandling: Behandling,
    lesemodus: Boolean = false,
): MutableSet<Grunnlag> {
    return filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
        .filter { !it.innholdTilObjekt<InntektsrapporteringPeriode>().manueltRegistrert }
        .groupBy {
            hentPersonMedReferanse(it.gjelderReferanse) ?: manglerPersonGrunnlag(
                it.gjelderReferanse,
            )
        }
        .flatMap { (gjelder, grunnlagListe) ->
            val årsinntekter =
                grunnlagListe.map {
                    it.tilInntektBearbeidet(this)
                }

            fun opprettGrunnlagBearbeidet(
                type: Grunnlagsdatatype,
                inntektsrapportering: Inntektsrapportering,
                innhentetTidspunkt: LocalDateTime,
            ) = behandling.opprettGrunnlag(
                type,
                SummerteInntekter(
                    versjon = årsinntekter.versjon(inntektsrapportering),
                    inntekter = årsinntekter.filter { it.inntekt.inntektRapportering == inntektsrapportering },
                ),
                rolleIdent = gjelder.personIdent!!,
                erBearbeidet = true,
                innhentetTidspunkt = innhentetTidspunkt,
                lesemodus = lesemodus,
            )

            val inntekter = årsinntekter.map { it.inntekt }
            listOf(
                opprettGrunnlagBearbeidet(
                    Grunnlagsdatatype.BARNETILLEGG,
                    Inntektsrapportering.BARNETILLEGG,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG),
                ),
                opprettGrunnlagBearbeidet(
                    Grunnlagsdatatype.BARNETILSYN,
                    Inntektsrapportering.BARNETILSYN,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN),
                ),
                opprettGrunnlagBearbeidet(
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    Inntektsrapportering.UTVIDET_BARNETRYGD,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD),
                ),
                opprettGrunnlagBearbeidet(
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    Inntektsrapportering.SMÅBARNSTILLEGG,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG),
                ),
                opprettGrunnlagBearbeidet(
                    Grunnlagsdatatype.KONTANTSTØTTE,
                    Inntektsrapportering.KONTANTSTØTTE,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE),
                ),
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    SummerteInntekter(
                        versjon = årsinntekter.versjon(Inntektsrapportering.AINNTEKT_BEREGNET_3MND),
                        inntekter = inntekter.skattegrunnlagListe + inntekter.ainntektListe,
                    ),
                    rolleIdent = gjelder.personIdent!!,
                    erBearbeidet = true,
                    innhentetTidspunkt = innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT),
                    lesemodus = lesemodus,
                ),
            )
        }
        .toMutableSet()
}

fun List<SummerteInntekt>.versjon(type: Inntektsrapportering) = find { it.inntekt.inntektRapportering == type }?.versjon ?: ""

private fun BaseGrunnlag.tilInntektBearbeidet(grunnlagsListe: List<GrunnlagDto>): SummerteInntekt {
    val inntektPeriode = innholdTilObjekt<InntektsrapporteringPeriode>()
    val gjelderBarn = grunnlagsListe.hentPersonMedReferanse(inntektPeriode.gjelderBarn)
    if (inntektsrapporteringSomKreverBarn.contains(inntektPeriode.inntektsrapportering) && gjelderBarn == null) {
        vedtakmappingFeilet(
            "Mangler barn for inntekt ${inntektPeriode.inntektsrapportering} med referanse $referanse i grunnlagslisten",
        )
    }
    val opprinneligFom =
        inntektPeriode.opprinneligPeriode?.fom?.atDay(1) ?: vedtakmappingFeilet(
            "Inntekt ${inntektPeriode.inntektsrapportering} mangler opprinnelig periode",
        )
    val opprinneligTom = inntektPeriode.opprinneligPeriode?.til?.atDay(1)?.minusDays(1)

    return SummerteInntekt(
        inntektPeriode.versjon ?: "", // TODO: Midlertidlig for å støtte eldre vedtak i Q2
        SummertÅrsinntekt(
            gjelderBarnPersonId = gjelderBarn?.personIdent ?: "",
            inntektRapportering = inntektPeriode.inntektsrapportering,
            grunnlagsreferanseListe = grunnlagsreferanseListe,
            periode = ÅrMånedsperiode(opprinneligFom, opprinneligTom),
            sumInntekt = inntektPeriode.beløp,
            inntektPostListe =
                inntektPeriode.inntekstpostListe.map {
                    InntektPost(
                        inntekstype = it.inntekstype,
                        beløp = it.beløp,
                        kode = it.kode,
                    )
                },
        ),
    )
}
