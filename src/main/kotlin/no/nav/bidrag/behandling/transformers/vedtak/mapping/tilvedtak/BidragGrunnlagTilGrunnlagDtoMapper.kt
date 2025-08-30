package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.behandling.transformers.grunnlag.manglerRolleIGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.valider
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.opprettPersonBarnBPBMReferanse
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagDatakilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetAinntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetArbeidsforhold
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetBarnetillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetBarnetilsyn
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetHusstandsmedlem
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetKontantstøtte
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSivilstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSkattegrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSmåbarnstillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetTilleggstønad
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetUtvidetBarnetrygd
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettAinntektGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettArbeidsforholdGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilsynGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetHusstandsmedlemGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetSivilstandGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettKontantstøtteGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSkattegrunnlagGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSmåbarnstilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettUtvidetbarnetrygGrunnlagsreferanse
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
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.felles.toCompactString
import java.time.LocalDate
import java.time.LocalDateTime

fun RelatertPersonGrunnlagDto.tilPersonGrunnlagAndreBarnTilBidragsmottaker(
    innhentetReferanse: Grunnlagsreferanse,
    referanse: Grunnlagsreferanse? = null,
): GrunnlagDto {
    val personnavn = navn ?: hentPersonVisningsnavn(gjelderPersonId)

    return GrunnlagDto(
        referanse = opprettPersonBarnBPBMReferanse(type = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER, fødselsdato!!, gjelderPersonId, navn),
        grunnlagsreferanseListe = listOf(innhentetReferanse),
        type = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
        innhold =
            POJONode(
                Person(
                    ident = gjelderPersonId?.let { Personident(it) },
                    navn = if (gjelderPersonId.isNullOrEmpty()) personnavn else null,
                    fødselsdato =
                        finnFødselsdato(
                            gjelderPersonId,
                            fødselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til husstandsmedlem er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(-1),
                ).valider(),
            ),
    )
}

fun RelatertPersonGrunnlagDto.tilPersonGrunnlag(): GrunnlagDto {
    val personnavn = navn ?: hentPersonVisningsnavn(gjelderPersonId)

    val nyesteIdent =
        gjelderPersonId.takeIf { !it.isNullOrEmpty() }?.let {
            val ident = hentNyesteIdent(it)
            if (ident == null && !erBarn) Personident(it) else ident
        }
    return GrunnlagDto(
        referanse =
            Grunnlagstype.PERSON_HUSSTANDSMEDLEM.tilPersonreferanse(
                (fødselsdato?.toCompactString() ?: LocalDate.MIN.toCompactString()) + "_innhentet",
                (gjelderPersonId + 1).hashCode(),
            ),
        type = Grunnlagstype.PERSON_HUSSTANDSMEDLEM,
        innhold =
            POJONode(
                Person(
                    ident = nyesteIdent,
                    navn = if (gjelderPersonId.isNullOrEmpty()) personnavn else null,
                    fødselsdato =
                        finnFødselsdato(
                            gjelderPersonId,
                            fødselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til husstandsmedlem er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(-1),
                ).valider(),
            ),
    )
}

fun RelatertPersonGrunnlagDto.tilGrunnlagsobjektInnhold(
    hentetTidspunkt: LocalDateTime,
    gjelderPersonReferanse: String,
) = InnhentetHusstandsmedlem(
    hentetTidspunkt = hentetTidspunkt,
    grunnlag =
        InnhentetHusstandsmedlem.HusstandsmedlemPDL(
            relatertPerson = gjelderPersonReferanse,
            gjelderPerson = gjelderPersonReferanse,
            erBarnAvBmBp = erBarn,
            relasjon = relasjon,
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
)

fun RelatertPersonGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    gjelderPersonReferanse: String,
) = GrunnlagDto(
    referanse =
        opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
            gjelderReferanse,
            referanseRelatertTil = gjelderPersonReferanse,
        ),
    type = Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            tilGrunnlagsobjektInnhold(
                hentetTidspunkt,
                gjelderPersonReferanse,
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

@JvmName("tilInnhentetBarnetilsynGrunnlag")
fun List<BarnetilsynGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    personobjekter: Set<GrunnlagDto>,
) = GrunnlagDto(
    referanse = opprettBarnetilsynGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_BARNETILSYN,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetBarnetilsyn(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        val søknadsbarn = personobjekter.hentPersonNyesteIdent(it.barnPersonId)!!
                        InnhentetBarnetilsyn.Barnetilsyn(
                            periode = Datoperiode(it.periodeFra, it.periodeTil),
                            beløp = it.beløp,
                            tilsynstype = it.tilsynstype,
                            skolealder = it.skolealder,
                            gjelderBarn = søknadsbarn.referanse,
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetAinntektGrunnlag")
fun List<AinntektGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = opprettAinntektGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetAinntekt(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        InnhentetAinntekt.AinntektInnhentet(
                            periode = Datoperiode(it.periodeFra, it.periodeTil),
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
                                        beskrivelse = post.beskrivelse,
                                        opplysningspliktigId = post.opplysningspliktigId,
                                        virksomhetId = post.virksomhetId,
                                    )
                                },
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetSmåbarnstilleggGrunnlag")
fun List<SmåbarnstilleggGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = opprettSmåbarnstilleggGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetSmåbarnstillegg(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        InnhentetSmåbarnstillegg.Småbarnstillegg(
                            periode = Datoperiode(it.periodeFra, it.periodeTil),
                            beløp = it.beløp,
                            manueltBeregnet = it.manueltBeregnet,
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetUtvidetbarnetrygdGrunnlag")
fun List<UtvidetBarnetrygdGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = opprettUtvidetbarnetrygGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetUtvidetBarnetrygd(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        InnhentetUtvidetBarnetrygd.UtvidetBarnetrygd(
                            periode = Datoperiode(it.periodeFra, it.periodeTil),
                            beløp = it.beløp,
                            manueltBeregnet = it.manueltBeregnet,
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetBarnetilleggGrunnlag")
fun List<BarnetilleggGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    personobjekter: Set<GrunnlagDto>,
) = GrunnlagDto(
    referanse =
        opprettBarnetilleggGrunnlagsreferanse(
            gjelderReferanse,
            kilde = GrunnlagDatakilde.PENSJON,
        ),
    type = Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetBarnetillegg(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        val søknadsbarn =
                            personobjekter.hentPersonNyesteIdent(it.barnPersonId) ?: manglerRolleIGrunnlag(
                                Rolletype.BARN,
                                fødselsnummer = it.barnPersonId,
                            )
                        InnhentetBarnetillegg.Barnetillegg(
                            periode = Datoperiode(it.periodeFra, it.periodeTil),
                            gjelderBarn = søknadsbarn.referanse,
                            barnetilleggType = it.barnetilleggType,
                            barnType = it.barnType,
                            beløpBrutto = it.beløpBrutto,
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetTilleggsstønadGrunnlag")
fun List<TilleggsstønadGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    søktFomDato: LocalDate,
) = GrunnlagDto(
    referanse =
        "innhentet_tilleggsstønad_begrenset_$gjelderReferanse",
    type = Grunnlagstype.INNHENTET_TILLEGGSSTØNAD_BEGRENSET,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetTilleggstønad(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        InnhentetTilleggstønad.Tilleggsstønad(
                            periode = Datoperiode(søktFomDato, null),
                            harInnvilgetVedtak = it.harInnvilgetVedtak,
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetKontantstøtteGrunnlag")
fun List<KontantstøtteGrunnlagDto>.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
    personobjekter: Set<GrunnlagDto>,
) = GrunnlagDto(
    referanse = opprettKontantstøtteGrunnlagsreferanse(gjelderReferanse),
    type = Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetKontantstøtte(
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    map {
                        val søknadsbarn =
                            personobjekter.hentPersonNyesteIdent(it.barnPersonId) ?: manglerRolleIGrunnlag(
                                Rolletype.BARN,
                                fødselsnummer = it.barnPersonId,
                            )
                        InnhentetKontantstøtte.Kontantstøtte(
                            periode = Datoperiode(it.periodeFra, it.periodeTil),
                            gjelderBarn = søknadsbarn.referanse,
                            beløp = it.beløp,
                        )
                    },
            ),
        ),
)

@JvmName("tilInnhentetSkattegrunnlagGrunnlag")
fun SkattegrunnlagGrunnlagDto.tilGrunnlagsobjekt(
    hentetTidspunkt: LocalDateTime,
    gjelderReferanse: String,
) = GrunnlagDto(
    referanse = opprettSkattegrunnlagGrunnlagsreferanse(gjelderReferanse, periodeFra.year),
    type = Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            InnhentetSkattegrunnlag(
                periode = Datoperiode(periodeFra, periodeTil),
                hentetTidspunkt = hentetTidspunkt,
                grunnlag =
                    skattegrunnlagspostListe.map { post ->
                        InnhentetSkattegrunnlag.Skattegrunnlagspost(
                            skattegrunnlagType = post.skattegrunnlagType,
                            kode = post.kode,
                            beløp = post.beløp,
                        )
                    },
            ),
        ),
)
