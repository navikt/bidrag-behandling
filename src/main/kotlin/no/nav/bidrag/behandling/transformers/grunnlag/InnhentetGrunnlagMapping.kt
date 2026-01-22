package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilGrunnlagsobjekt
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilGrunnlagsobjektInnhold
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilPersonGrunnlag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilPersonGrunnlagAndreBarnTilBidragsmottaker
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.trimToNull
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetAndreBarnTilBidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetHusstandsmedlem
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettAinntektGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetHusstandsmedlemGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettKontantstøtteGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSkattegrunnlagGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSmåbarnstilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettUtvidetbarnetrygGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDateTime

fun List<Grunnlag>.tilInnhentetArbeidsforhold(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> =
    filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }
        .groupBy { it.rolle.ident }
        .map { (personId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val arbeidsforholdListe =
                grunnlag.konvertereData<List<ArbeidsforholdGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(personId)!!
            arbeidsforholdListe.tilGrunnlagsobjekt(grunnlag.innhentet, gjelder.referanse)
        }.toSet()

fun List<Grunnlag>.tilInnhentetSivilstand(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> =
    filter { it.type == Grunnlagsdatatype.SIVILSTAND }
        .groupBy { it.rolle.ident }
        .map { (personId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val sivilstandListe =
                grunnlag.konvertereData<List<SivilstandGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(personId)!!
            sivilstandListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

fun List<Grunnlag>.tilInnhentetAndreBarnTilBidragsmottaker(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    val personobjekterInnhentetAndreBarnTilBidragsmottaker = mutableSetOf<GrunnlagDto>()

    fun RelatertPersonGrunnlagDto.opprettPersonGrunnlag(
        referanse: Grunnlagsreferanse,
        gjelderReferanse: Grunnlagsreferanse,
    ): GrunnlagDto {
        val relatertPersonGrunnlag = tilPersonGrunnlagAndreBarnTilBidragsmottaker(referanse, gjelderReferanse)
        personobjekterInnhentetAndreBarnTilBidragsmottaker.add(relatertPersonGrunnlag)
        return relatertPersonGrunnlag
    }
    val grunnlag =
        filter { it.type == Grunnlagsdatatype.ANDRE_BARN }
            .groupBy { it.rolle.ident }
            .map { (partPersonId, grunnlagListe) ->
                val grunnlag = grunnlagListe.first()
                val andreBarn =
                    grunnlag.konvertereData<List<RelatertPersonGrunnlagDto>>()?.filter { it.erBarn } ?: emptyList()
                val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!

                val referanse = opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse(gjelder.referanse)
                GrunnlagDto(
                    referanse = referanse,
                    type = Grunnlagstype.INNHENTET_ANDRE_BARN_TIL_BIDRAGSMOTTAKER,
                    gjelderReferanse = gjelder.referanse,
                    innhold =
                        POJONode(
                            InnhentetAndreBarnTilBidragsmottaker(
                                hentetTidspunkt = grunnlag.innhentet,
                                grunnlag =
                                    andreBarn.map {
                                        val relatertPersonObjekt =
                                            (personobjekter + personobjekterInnhentetAndreBarnTilBidragsmottaker)
                                                .hentPerson(it.gjelderPersonId)
                                                ?: it.opprettPersonGrunnlag(referanse, gjelder.referanse)
                                        InnhentetAndreBarnTilBidragsmottaker.AndreBarnTilBidragsmottakerPDL(
                                            gjelderPerson = relatertPersonObjekt.referanse,
                                            relasjon = it.relasjon,
                                            navn = it.navn,
                                            fødselsdato = it.fødselsdato,
                                        )
                                    },
                            ),
                        ),
                )
            }.toSet()
    return grunnlag + personobjekterInnhentetAndreBarnTilBidragsmottaker
}

fun List<Grunnlag>.tilInnhentetHusstandsmedlemmer(
    personobjekter: Set<GrunnlagDto>,
    behandling: Behandling,
): Set<GrunnlagDto> {
    val personobjekterInnhentetHusstandsmedlem = mutableSetOf<GrunnlagDto>()

    fun RelatertPersonGrunnlagDto.opprettPersonGrunnlag(): GrunnlagDto {
        val relatertPersonGrunnlag = tilPersonGrunnlag()
        personobjekterInnhentetHusstandsmedlem.add(relatertPersonGrunnlag)
        return relatertPersonGrunnlag
    }

    fun opprettGrunnlagBoforhold(
        partPersonId: String?,
        grunnlagListe: List<Grunnlag>,
    ): List<GrunnlagDto> {
        val grunnlag = grunnlagListe.first()
        val husstandsmedlemList =
            grunnlag.konvertereData<List<RelatertPersonGrunnlagDto>>() ?: emptyList()
        val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
        return husstandsmedlemList
            .groupBy { it.gjelderPersonId }
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
    }
    val innhentetHusstandsmedlemGrunnlagListe =
        filter { it.type == Grunnlagsdatatype.BOFORHOLD }
            .groupBy { it.rolle.ident }
            .flatMap { (partPersonId, grunnlagListe) ->
                opprettGrunnlagBoforhold(partPersonId, grunnlagListe)
            }.toSet()

    val innhentetHusstandsmedlemBMGrunnlagListe =
        filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN }
            .groupBy { it.rolle.ident }
            .flatMap { (partPersonId, grunnlagListe) ->
                opprettGrunnlagBoforhold(partPersonId, grunnlagListe)
            }.toSet()

    val innhentetAndreVoksneIHusstandenGrunnlagListe =
        find { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN }
            ?.let { grunnlag ->
                val andreVoksneIHusstandenListe =
                    grunnlag.konvertereData<List<RelatertPersonGrunnlagDto>>()?.filter { it.relasjon != Familierelasjon.BARN }
                        ?: emptyList()
                val gjelder = personobjekter.hentPersonNyesteIdent(grunnlag.rolle.ident)!!
                GrunnlagDto(
                    referanse =
                        opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
                            gjelder.referanse,
                            referanseRelatertTil = gjelder.referanse,
                        ),
                    type = Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN,
                    gjelderReferanse = gjelder.referanse,
                    innhold =
                        POJONode(
                            andreVoksneIHusstandenListe.map {
                                it.tilGrunnlagsobjektInnhold(
                                    grunnlag.innhentet,
                                    gjelder.referanse,
                                )
                            },
                        ),
                )
            }?.let { setOf(it) } ?: emptySet()

    val innhentetGrunnlagsliste =
        innhentetHusstandsmedlemGrunnlagListe +
            personobjekterInnhentetHusstandsmedlem +
            innhentetAndreVoksneIHusstandenGrunnlagListe +
            innhentetHusstandsmedlemBMGrunnlagListe

    return innhentetGrunnlagsliste + behandling.opprettInnhentetHusstandsmedlemGrunnlagHvisMangler(innhentetGrunnlagsliste, personobjekter)
}

fun Behandling.opprettInnhentetHusstandsmedlemGrunnlagHvisMangler(
    grunnlagsliste: Set<GrunnlagDto>,
    personobjekter: Set<GrunnlagDto>,
): List<GrunnlagDto> {
    val innhentesForRolle = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(this)!!
    val personobjektInnhentesForRolle = personobjekter.hentPersonNyesteIdent(innhentesForRolle.ident)!!
    val innhentetHusstandsmedlemGrunnlagsliste =
        grunnlagsliste.filter {
            it.type == Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM &&
                it.gjelderReferanse == personobjektInnhentesForRolle.referanse
        }
    val søknadsbarnSomManglerInnhentetGrunnlag =
        søknadsbarn
            .filter { sb ->
                innhentetHusstandsmedlemGrunnlagsliste.filter { it.type == Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM }.none {
                    val barnReferanse = it.innholdTilObjekt<InnhentetHusstandsmedlem>().grunnlag.gjelderPerson
                    personobjekter.hentPersonMedReferanse(barnReferanse)?.personIdent == sb.ident
                }
            }.map {
                RelatertPersonGrunnlagDto(
                    fødselsdato = it.fødselsdato,
                    gjelderPersonId = it.ident,
                    partPersonId = innhentesForRolle.ident,
                    navn = it.navn,
                    relasjon = Familierelasjon.BARN,
                    borISammeHusstandDtoListe = emptyList(),
                ).tilGrunnlagsobjekt(
                    LocalDateTime.now().withSecond(0).withNano(0),
                    personobjektInnhentesForRolle.referanse,
                    personobjekter.hentPersonNyesteIdent(it.ident)!!.referanse,
                )
            }
    val husstandsmedlemSomManglerInnhentetGrunnlag =
        husstandsmedlem
            .filter { it.rolle == null }
            .filter { sb ->
                innhentetHusstandsmedlemGrunnlagsliste.none {
                    val barnReferanse = it.innholdTilObjekt<InnhentetHusstandsmedlem>().grunnlag.gjelderPerson
                    personobjekter.hentPersonMedReferanse(barnReferanse)?.personIdent == sb.ident
                }
            }.map {
                RelatertPersonGrunnlagDto(
                    fødselsdato = it.fødselsdato,
                    gjelderPersonId = it.ident,
                    partPersonId = personobjektInnhentesForRolle.personIdent,
                    navn = it.navn ?: hentPersonVisningsnavn(it.ident),
                    relasjon = Familierelasjon.BARN,
                    borISammeHusstandDtoListe = emptyList(),
                ).tilGrunnlagsobjekt(
                    LocalDateTime.now().withSecond(0).withNano(0),
                    personobjektInnhentesForRolle.referanse,
                    personobjekter.hentPersonNyesteIdent(it.ident)!!.referanse,
                )
            }

    val husstandsmedlemSomManglerInnhentetGrunnlagAndreVoksne =
        if (grunnlagsliste.none {
                it.type == Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN
            }
        ) {
            husstandsmedlem
                .find { it.rolle?.rolletype == Rolletype.BIDRAGSPLIKTIG }
                ?.let {
                    val gjelderReferanse =
                        personobjekter
                            .hentPersonNyesteIdent(
                                Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN.innhentesForRolle(this)!!.ident,
                            )!!
                            .referanse
                    setOf(
                        GrunnlagDto(
                            referanse =
                                opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
                                    gjelderReferanse,
                                    referanseRelatertTil = gjelderReferanse,
                                ),
                            type = Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN,
                            gjelderReferanse = gjelderReferanse,
                            innhold =
                                POJONode(
                                    emptyList<RelatertPersonGrunnlagDto>(),
                                ),
                        ),
                    )
                } ?: emptySet()
        } else {
            emptySet()
        }
    return søknadsbarnSomManglerInnhentetGrunnlag + husstandsmedlemSomManglerInnhentetGrunnlag +
        husstandsmedlemSomManglerInnhentetGrunnlagAndreVoksne
}

fun List<Grunnlag>.tilBeregnetInntekt(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> =
    filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER }
        .groupBy { it.rolle.ident }
        .map { (ident, grunnlag) ->
            val inntekter =
                grunnlag.first().konvertereData<SummerteInntekter<SummertMånedsinntekt>>()!!
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            val summerteMånedsinntekter = inntekter.inntekter
            GrunnlagDto(
                referanse = "beregnet_inntekt_${gjelder.referanse}",
                type = Grunnlagstype.BEREGNET_INNTEKT,
                gjelderReferanse = gjelder.referanse,
                grunnlagsreferanseListe =
                    summerteMånedsinntekter
                        .flatMap { it.grunnlagsreferanseListe }
                        .map { korrigerPersonReferanser(it, personobjekter) }
                        .toSet()
                        .toList(),
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
                                                    inntektstype = post.inntekstype,
                                                    beløp = post.beløp,
                                                )
                                            },
                                    )
                                },
                        ),
                    ),
            )
        }.toSet()

/**
 * Personreferanser var formatter på følgende måte person_<type>_<fødselsdato>_<rolleId>
 * Dette kunne skape problemer ved FF mtp at person får ny rolleId. Dette korrigerer slik at riktig referanse brukes
 */
private fun korrigerPersonReferanser(
    oldRef: String,
    personobjekter: Set<GrunnlagDto>,
): String {
    // Extract the person reference part starting with "person_"
    val personRefStart = oldRef.indexOf("person_")
    if (personRefStart == -1) return oldRef
    val personRef = oldRef.substring(personRefStart).substringBeforeLast('_') // Remove last _XXXX
    // Check if it matches one of the specified Grunnlagstype values and ends with YYYYMMDD (8 digits)
    val parts = personRef.split('_')
    if (parts.size < 3) return oldRef
    val type = parts[1] + "_" + parts[2] // e.g., PERSON_SØKNADSBARN
    val validTypes =
        Grunnlagstype.entries
            .filter { it.name.startsWith("PERSON") }
            .map { it.name }
            .toSet()
    if (type !in validTypes) return oldRef
    val datePart = parts.last()
    if (datePart.length != 8 || !datePart.all { it.isDigit() }) return oldRef
    // Find the matching person in personobjekter by referanse prefix
    val correctPerson = personobjekter.find { it.referanse.startsWith(personRef) }
    // Return the correct referanse or fallback to oldRef if not found
    return correctPerson?.referanse ?: oldRef
}

fun List<Grunnlag>.tilInnhentetGrunnlagUnderholdskostnad(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> =
    mapBarnetilsyn(personobjekter) + mapTilleggsstønad(personobjekter)

fun List<Grunnlag>.tilInnhentetGrunnlagInntekt(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> =
    mapSkattegrunnlag(personobjekter) + mapAinntekt(personobjekter) +
        mapKontantstøtte(personobjekter) +
        mapBarnetillegg(personobjekter) +
        mapUtvidetbarnetrygd(personobjekter) + mapSmåbarnstillegg(personobjekter)

fun List<Grunnlag>.hentVersjonForInntekt(inntekt: Inntekt): String {
    val inntekterGrunnlag =
        find { inntekt.type.tilGrunnlagsdataType() == it.type && it.erBearbeidet }
    return inntekterGrunnlag.konvertereData<SummerteInntekter<SummertÅrsinntekt>>()?.versjon
        ?: vedtakmappingFeilet("Mangler versjon for beregnet inntekt ${inntekt.type}")
}

fun Set<Grunnlag>.hentGrunnlagsreferanserForInntekt(
    gjelderIdent: String,
    inntekt: Inntekt,
    sjekkAktive: Boolean = true,
): List<Grunnlagsreferanse> {
    if (inntekt.kilde == Kilde.MANUELL) return emptyList()
    val periode = ÅrMånedsperiode(inntekt.opprinneligFom!!, inntekt.opprinneligTom)
    val grunnlag = if (sjekkAktive) hentAlleAktiv() else hentAlleIkkeAktiv()
    val inntekterGjelderGrunnlag =
        grunnlag.find {
            it.type == inntekt.type.tilGrunnlagsdataType() &&
                it.erBearbeidet &&
                hentNyesteIdent(
                    it.rolle.ident,
                )?.verdi == gjelderIdent
        }

    val inntekter =
        inntekterGjelderGrunnlag
            .konvertereData<SummerteInntekter<SummertÅrsinntekt>>()
            ?.inntekter
    val beregnetInntekt =
        inntekter?.find {
            it.periode == periode &&
                inntekt.type == it.inntektRapportering &&
                (inntekt.gjelderBarn.isNullOrEmpty() || inntekt.gjelderBarn == it.gjelderBarnPersonId.trimToNull())
        }
    return if (UnleashFeatures.GRUNNLAGSINNHENTING_FUNKSJONELL_FEIL_TEKNISK.isEnabled && inntekterGjelderGrunnlag != null) {
        opprettGrunnlagsreferanserForInntekt2(inntekt, inntekterGjelderGrunnlag!!.rolle.tilGrunnlagsreferanse())
    } else {
        beregnetInntekt?.grunnlagsreferanseListe?.filter { it.isNotEmpty() }
            ?: run {
                if (sjekkAktive) {
                    return hentGrunnlagsreferanserForInntekt(
                        gjelderIdent,
                        inntekt,
                        false,
                    )
                } else if (UnleashFeatures.GRUNNLAGSINNHENTING_FUNKSJONELL_FEIL_TEKNISK.isEnabled) {
                    emptyList()
                } else {
                    grunnlagByggingFeilet(
                        "Mangler grunnlagsreferanse for offentlig inntekt ${inntekt.type} " +
                            "for periode (${inntekt.opprinneligFom}-${inntekt.opprinneligTom}) og barn ${inntekt.gjelderBarn}",
                    )
                }
            }
    }
}

private fun opprettGrunnlagsreferanserForInntekt2(
    inntekt: Inntekt,
    gjelderReferanse: Grunnlagsreferanse,
): List<Grunnlagsreferanse> {
    val referanse =
        when (inntekt.type) {
            Inntektsrapportering.BARNETILLEGG -> {
                opprettBarnetilleggGrunnlagsreferanse(gjelderReferanse)
            }

            Inntektsrapportering.SMÅBARNSTILLEGG -> {
                opprettSmåbarnstilleggGrunnlagsreferanse(gjelderReferanse)
            }

            Inntektsrapportering.UTVIDET_BARNETRYGD -> {
                opprettUtvidetbarnetrygGrunnlagsreferanse(gjelderReferanse)
            }

            Inntektsrapportering.KONTANTSTØTTE -> {
                opprettKontantstøtteGrunnlagsreferanse(gjelderReferanse)
            }

            Inntektsrapportering.KAPITALINNTEKT, Inntektsrapportering.LIGNINGSINNTEKT -> {
                opprettSkattegrunnlagGrunnlagsreferanse(
                    gjelderReferanse,
                    inntekt.opprinneligFom?.year!!,
                )
            }

            else -> {
                opprettAinntektGrunnlagsreferanse(gjelderReferanse)
            }
        }

    return listOfNotNull(referanse)
}

private fun List<Grunnlag>.mapBarnetillegg(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.BARNETILLEGG && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val barnetillegListe =
                grunnlag.konvertereData<List<BarnetilleggGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            barnetillegListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                personobjekter,
            )
        }.toSet()

private fun List<Grunnlag>.mapTilleggsstønad(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.TILLEGGSSTØNAD && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val tilleggsstønadListe =
                grunnlag.konvertereData<List<TilleggsstønadGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            tilleggsstønadListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                first().behandling.søktFomDato,
            )
        }.toSet()

private fun List<Grunnlag>.mapBarnetilsyn(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.BARNETILSYN && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val barnetilsynListe =
                grunnlag.konvertereData<List<BarnetilsynGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            barnetilsynListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                personobjekter,
            )
        }.toSet()

fun List<Grunnlag>.mapAinntekt(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (ident, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val ainntektListe =
                grunnlag.konvertereData<SkattepliktigeInntekter>()?.ainntekter
                    ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            ainntektListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

private fun List<Grunnlag>.mapKontantstøtte(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.KONTANTSTØTTE && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val kontantstøtteListe =
                grunnlag.konvertereData<List<KontantstøtteGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            kontantstøtteListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
                personobjekter,
            )
        }.toSet()

private fun List<Grunnlag>.mapSmåbarnstillegg(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.SMÅBARNSTILLEGG && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val småbarnstilleggListe =
                grunnlag.konvertereData<List<SmåbarnstilleggGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            småbarnstilleggListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

private fun List<Grunnlag>.mapUtvidetbarnetrygd(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.UTVIDET_BARNETRYGD && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .map { (partPersonId, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val utvidetBarnetrygdListe =
                grunnlag.konvertereData<List<UtvidetBarnetrygdGrunnlagDto>>() ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(partPersonId)!!
            utvidetBarnetrygdListe.tilGrunnlagsobjekt(
                grunnlag.innhentet,
                gjelder.referanse,
            )
        }.toSet()

fun List<Grunnlag>.mapSkattegrunnlag(personobjekter: Set<GrunnlagDto>) =
    filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
        .groupBy { it.rolle.ident }
        .flatMap { (ident, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val skattegrunnlag =
                grunnlag.konvertereData<SkattepliktigeInntekter>()?.skattegrunnlag
                    ?: emptyList()
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            skattegrunnlag.map {
                it.tilGrunnlagsobjekt(
                    grunnlag.innhentet,
                    gjelder.referanse,
                )
            }
        }.toSet()
