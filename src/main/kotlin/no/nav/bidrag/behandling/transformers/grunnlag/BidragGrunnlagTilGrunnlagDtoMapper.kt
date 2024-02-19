package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.toCompactString
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettArbeidsforholdGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetHusstandsmedlemGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetSivilstandGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import java.time.LocalDate
import java.time.LocalDateTime

fun RelatertPersonGrunnlagDto.tilPersonGrunnlag(): GrunnlagDto {
    val personnavn = navn ?: hentPersonVisningsnavn(relatertPersonPersonId)

    return GrunnlagDto(
        referanse =
            Grunnlagstype.PERSON_HUSSTANDSMEDLEM.tilPersonreferanse(
                (fødselsdato?.toCompactString() ?: LocalDate.MIN.toCompactString()) + "_innhentet",
                (relatertPersonPersonId + 1).hashCode(),
            ),
        type = Grunnlagstype.PERSON_HUSSTANDSMEDLEM,
        innhold =
            POJONode(
                Person(
                    ident = relatertPersonPersonId?.let { Personident(it) },
                    navn = if (relatertPersonPersonId.isNullOrEmpty()) personnavn else null,
                    fødselsdato =
                        finnFødselsdato(
                            relatertPersonPersonId,
                            fødselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(-1),
                ).valider(),
            ),
    )
}

fun RelatertPersonGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    relatertTilPersonReferanse: String,
) = GrunnlagDto(
    referanse =
        opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
            gjelderReferanse,
            referanseRelatertTil = relatertTilPersonReferanse,
        ),
    type = Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetHusstandsmedlem(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetHusstandsmedlem.HusstandsmedlemPDL(
                        relatertPerson = relatertTilPersonReferanse,
                        erBarnAvBmBp = erBarnAvBmBp,
                        // TODO: Navn og fødselsdato?
                        navn = navn,
                        fødselsdato = fødselsdato,
                        perioder =
                            borISammeHusstandDtoListe.map {
                                Datoperiode(
                                    it.periodeFra ?: LocalDate.MIN,
                                    it.periodeTil,
                                )
                            },
                    ),
            ),
        ),
)

@JvmName("tilGrunnlagsobjektSivilstandInnhentetGrunnlag")
fun List<SivilstandGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = opprettInnhentetSivilstandGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_SIVILSTAND,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetSivilstand(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    this.map {
                        InnhentetSivilstand.SivilstandPDL(
                            sivilstand = it.type,
                            bekreftelsesdato = it.bekreftelsesdato,
                            master = it.master,
                            historisk = it.historisk,
                            gyldigFom = it.gyldigFom,
                            registrert = it.registrert,
                        )
                    },
            ),
        ),
)

@JvmName("tilGrunnlagsobjektArbeidsforhold")
fun List<ArbeidsforholdGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = opprettArbeidsforholdGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_ARBEIDSFORHOLD,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetArbeidsforhold(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    this.map {
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
                        )
                    },
            ),
        ),
)

fun BarnetilsynGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    søknadsbarnReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse, søknadsbarnReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetBarnetilsyn(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetBarnetilsyn.Barnetilsyn(
                        gjelderBarn = søknadsbarnReferanse,
                        beløp = beløp,
                        tilsynstype = tilsynstype,
                        skolealder = skolealder,
                    ),
            ),
        ),
)

fun AinntektGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetAinntekt(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetAinntekt.AinntektInnhentet(
                        ainntektspostListe =
                            ainntektspostListe.map { post ->
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

fun SmåbarnstilleggGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetSmåbarnstillegg(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetSmåbarnstillegg.Småbarnstillegg(
                        beløp = beløp,
                        manueltBeregnet = manueltBeregnet,
                    ),
            ),
        ),
)

fun UtvidetBarnetrygdGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetUtvidetBarnetrygd(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetUtvidetBarnetrygd.UtvidetBarnetrygd(
                        beløp = beløp,
                        manueltBeregnet = manueltBeregnet,
                    ),
            ),
        ),
)

fun BarnetilleggGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    søknadsbarnReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse, søknadsbarnReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetBarnetillegg(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetBarnetillegg.Barnetillegg(
                        gjelderBarn = søknadsbarnReferanse,
                        barnetilleggType = barnetilleggType,
                        barnType = barnType,
                        beløpBrutto = beløpBrutto,
                    ),
            ),
        ),
)

fun KontantstøtteGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    søknadsbarnReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse, søknadsbarnReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetKontantstøtte(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetKontantstøtte.Kontantstøtte(
                        gjelderBarn = søknadsbarnReferanse,
                        beløp = beløp,
                    ),
            ),
        ),
)

fun SkattegrunnlagGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = tilGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetSkattegrunnlag(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    InnhentetSkattegrunnlag.Skattegrunnlag(
                        skattegrunnlagListe =
                            skattegrunnlagspostListe.map { post ->
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
