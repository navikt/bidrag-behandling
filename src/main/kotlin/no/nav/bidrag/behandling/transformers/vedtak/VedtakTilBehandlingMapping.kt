package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.service.hentPerson
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.finnAntallBarnIHusstanden
import no.nav.bidrag.behandling.transformers.finnSivilstandForPeriode
import no.nav.bidrag.behandling.transformers.finnTotalInntekt
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.saksnummer
import no.nav.bidrag.transport.behandling.vedtak.response.søknadId
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

fun manglerPersonGrunnlag(referanse: Grunnlagsreferanse?): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Mangler person med referanse $referanse i grunnlagslisten",
    )

val grunnlagstyperRolle =
    listOf(
        Grunnlagstype.PERSON_SØKNADSBARN,
        Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
        Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
    )
val inntektsrapporteringSomKreverBarn =
    listOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
    )

fun VedtakDto.tilBeregningResultat(): List<ResultatBeregningBarnDto> {
    return stønadsendringListe.map { stønadsendring ->
        val barnIdent = stønadsendring.kravhaver
        val barn =
            grunnlagListe.hentPerson(barnIdent.verdi)?.innholdTilObjekt<Person>()
                ?: manglerPersonGrunnlag(barnIdent.verdi)
        ResultatBeregningBarnDto(
            barn =
                ResultatRolle(
                    barn.ident,
                    barn.navn ?: hentPersonVisningsnavn(barn.ident?.verdi)!!,
                    barn.fødselsdato,
                ),
            perioder =
                stønadsendring.periodeListe.map {
                    ResultatBeregningBarnDto.ResultatPeriodeDto(
                        periode = it.periode,
                        resultatKode = Resultatkode.fraKode(it.resultatkode)!!,
                        regel = "",
                        beløp = it.beløp!!,
                        sivilstand = grunnlagListe.finnSivilstandForPeriode(it.grunnlagReferanseListe),
                        inntekt = grunnlagListe.finnTotalInntekt(it.grunnlagReferanseListe),
                        antallBarnIHusstanden = grunnlagListe.finnAntallBarnIHusstanden(it.grunnlagReferanseListe),
                    )
                },
        )
    }
}

fun VedtakDto.tilBehandling(
    vedtaksId: Long,
    medId: Boolean = true,
): Behandling {
    val behandling =
        Behandling(
            id = if (medId) 1 else null,
            vedtakstype = type,
            virkningstidspunkt = hentVedtakstidspunkt()?.virkningstidspunkt,
            årsak = hentVedtakstidspunkt()?.årsak,
            avslag = avslagskode(),
            søktFomDato = hentSøknad().søktFraDato,
            soknadFra = hentSøknad().søktAv,
            mottattdato = hentSøknad().mottattDato,
            // TODO: Er dette riktig? Hva skjer hvis det finnes flere stønadsendringer/engangsbeløp? Fungerer for Forskudd men todo fram fremtiden
            stonadstype = stønadsendringListe.firstOrNull()?.type,
            engangsbeloptype = engangsbeløpListe.firstOrNull()?.type,
            vedtaksid = vedtaksId,
            behandlerEnhet = enhetsnummer?.verdi!!,
            opprettetAv = opprettetAv,
            opprettetAvNavn = opprettetAvNavn,
            kildeapplikasjon = kildeapplikasjon,
            datoTom = null,
            saksnummer = saksnummer!!,
            soknadsid = søknadId!!,
            boforholdsbegrunnelseKunINotat = notatMedType(NotatGrunnlag.NotatType.BOFORHOLD, false),
            boforholdsbegrunnelseIVedtakOgNotat =
                notatMedType(
                    NotatGrunnlag.NotatType.BOFORHOLD,
                    true,
                ),
            virkningstidspunktbegrunnelseKunINotat =
                notatMedType(
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                    false,
                ),
            virkningstidspunktsbegrunnelseIVedtakOgNotat =
                notatMedType(
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                    true,
                ),
            inntektsbegrunnelseIVedtakOgNotat = notatMedType(NotatGrunnlag.NotatType.INNTEKT, true),
            inntektsbegrunnelseKunINotat = notatMedType(NotatGrunnlag.NotatType.INNTEKT, false),
        )
    behandling.roller =
        grunnlagListe.filter { grunnlagstyperRolle.contains(it.type) }
            .mapIndexed { i, it -> it.tilRolle(behandling, if (medId) i.toLong() else null) }
            .toMutableSet()
    behandling.inntekter =
        grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            .mapIndexed { i, it ->
                it.tilInntekt(
                    behandling,
                    grunnlagListe,
                    if (medId) i.toLong() else null,
                )
            }.toMutableSet()
    behandling.husstandsbarn =
        grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            .groupBy { it.gjelderReferanse }.map {
                it.value.tilHusstandsbarn(it.key!!, behandling, grunnlagListe)
            }.toMutableSet()
    behandling.sivilstand =
        grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            .mapIndexed { i, it ->
                it.tilSivilstand(behandling, if (medId) i.toLong() else null)
            }.toMutableSet()

    behandling.grunnlag =
        (
            grunnlagListe.hentGrunnlagIkkeInntekt(behandling, medId) +
                grunnlagListe.hentGrunnlagInntekt(
                    behandling,
                    medId,
                )
        ).toMutableSet()

    return behandling
}

fun List<GrunnlagDto>.innhentetTidspunkt(grunnlagstype: Grunnlagstype) =
    filtrerBasertPåEgenReferanse(grunnlagstype)
        .firstOrNull()?.innhold?.get("hentetTidspunkt")?.let {
            commonObjectmapper.treeToValue(it, LocalDateTime::class.java)
        } ?: LocalDateTime.now()

fun List<GrunnlagDto>.hentGrunnlagIkkeInntekt(
    behandling: Behandling,
    medId: Boolean,
) = listOf(
    hentGrunnlagArbeidsforhold().groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD),
                medId,
            )
        },
    hentInnhentetSivilstand().groupBy { it.personId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.SIVILSTAND,
                grunnlag,
                gjelderIdent!!,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_SIVILSTAND),
                medId,
            )
        },
    hentInnhenetHusstandsmedlem().groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
                grunnlag,
                gjelderIdent!!,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                medId,
            )
        },
).flatten()

fun List<GrunnlagDto>.hentGrunnlagInntekt(
    behandling: Behandling,
    medId: Boolean,
): List<Grunnlag> {
    return listOf(
        hentBeregnetInntekt().entries.map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.INNTEKT_BEARBEIDET,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.BEREGNET_INNTEKT),
                medId,
            )
        },
        hentBarnetillegListe().groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG),
                    medId,
                )
            },
        hentUtvidetbarnetrygdListe().groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD),
                    medId,
                )
            },
        hentSmåbarnstilleggListe().groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG),
                    medId,
                )
            },
        hentBarnetilsynListe().groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILSYN,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN),
                    medId,
                )
            },
        hentKontantstøtteListe().groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.KONTANTSTØTTE,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE),
                    medId,
                )
            },
        hentGrunnlagAinntekt().groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.AINNTEKT,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT),
                    medId,
                )
            },
        hentGrunnlagAinntekt().groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SKATTEGRUNNLAG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE),
                    medId,
                )
            },
    ).flatten()
}

fun Behandling.opprettGrunnlagEntityForInntekt(
    ainntektListe: List<AinntektGrunnlagDto>,
    skattegrunnlagListe: List<SkattegrunnlagGrunnlagDto>,
): List<Grunnlag> {
    val ainntektPerPersonMap = ainntektListe.groupBy { it.personId }
    val skattegrunnlagPerPersonMap = skattegrunnlagListe.groupBy { it.personId }

    val personer = ainntektPerPersonMap.keys + skattegrunnlagPerPersonMap.keys
    return personer.map {
        val ainntektListe = ainntektPerPersonMap[it] ?: emptyList()
        val skattegrunnlagListe = skattegrunnlagPerPersonMap[it] ?: emptyList()
        opprettGrunnlag(
            Grunnlagsdatatype.INNTEKT,
            GrunnlagInntekt(
                ainntekt = ainntektListe,
                skattegrunnlag = skattegrunnlagListe,
            ),
            it,
            LocalDateTime.now(),
            true,
        )
    }
}

private fun Behandling.opprettGrunnlag(
    type: Grunnlagsdatatype,
    grunnlag: Any,
    gjelderIdent: String,
    innhentetTidspunkt: LocalDateTime,
    medId: Boolean,
) = Grunnlag(
    behandling = this,
    id = if (medId) 1 else null,
    innhentet = innhentetTidspunkt,
    data = commonObjectmapper.writeValueAsString(grunnlag),
    type = type,
    rolle = roller.find { it.ident == gjelderIdent }!!,
)

fun VedtakDto.notatMedType(
    type: NotatGrunnlag.NotatType,
    medIVedtak: Boolean,
) = grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.NOTAT)
    .map { it.innholdTilObjekt<NotatGrunnlag>() }
    .find { it.type == type && it.erMedIVedtaksdokumentet == medIVedtak }?.innhold

fun VedtakDto.avslagskode() =
    if (stønadsendringListe.all { it.periodeListe.size == 1 }) {
        Resultatkode.fraKode(stønadsendringListe.first().periodeListe.first().resultatkode)
    } else {
        null
    }

fun VedtakDto.hentVedtakstidspunkt(): VirkningstidspunktGrunnlag? {
    return grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT)
        .firstOrNull()?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
}

fun VedtakDto.hentSøknad(): SøknadGrunnlag {
    return grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD).first()
        .innholdTilObjekt<SøknadGrunnlag>()
}

fun List<BaseGrunnlag>.tilHusstandsbarn(
    gjelderReferanse: Grunnlagsreferanse,
    behandling: Behandling,
    grunnlagsListe: List<GrunnlagDto>,
): Husstandsbarn {
    val gjelderBarnGrunnlag =
        grunnlagsListe.hentPersonMedReferanse(gjelderReferanse) ?: manglerPersonGrunnlag(
            gjelderReferanse,
        )
    val gjelderBarn = gjelderBarnGrunnlag.innholdTilObjekt<Person>()

    val husstandsbarnBO =
        Husstandsbarn(
            ident = gjelderBarnGrunnlag.personIdent,
            navn = gjelderBarn.navn,
            foedselsdato = gjelderBarn.fødselsdato,
            medISaken =
                when (gjelderBarnGrunnlag.type) {
                    Grunnlagstype.PERSON_SØKNADSBARN -> true
                    else -> false
                },
            behandling = behandling,
        )
    husstandsbarnBO.perioder =
        this.map {
            val bosstatusPeriode = it.innholdTilObjekt<BostatusPeriode>()
            Husstandsbarnperiode(
                husstandsbarn = husstandsbarnBO,
                datoFom = bosstatusPeriode.periode.fom.atDay(1),
                datoTom = bosstatusPeriode.periode.til?.atDay(1)?.minusDays(1),
                bostatus = bosstatusPeriode.bostatus,
                kilde = if (bosstatusPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
            )
        }.toMutableSet()
    return husstandsbarnBO
}

fun BaseGrunnlag.tilSivilstand(
    behandling: Behandling,
    id: Long? = null,
): Sivilstand {
    val sivilstandPeriode = innholdTilObjekt<SivilstandPeriode>()

    return Sivilstand(
        id = id,
        sivilstand = sivilstandPeriode.sivilstand,
        datoFom = sivilstandPeriode.periode.fom.atDay(1),
        datoTom = sivilstandPeriode.periode.til?.atDay(1)?.minusDays(1),
        behandling = behandling,
        kilde = if (sivilstandPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
    )
}

fun BaseGrunnlag.tilInntekt(
    behandling: Behandling,
    grunnlagsListe: List<GrunnlagDto>,
    id: Long? = null,
): Inntekt {
    val inntektPeriode = innholdTilObjekt<InntektsrapporteringPeriode>()
    val gjelderBarn = grunnlagsListe.hentPersonMedReferanse(inntektPeriode.gjelderBarn)
    val gjelder =
        grunnlagsListe.hentPersonMedReferanse(gjelderReferanse) ?: manglerPersonGrunnlag(
            gjelderReferanse,
        )
    if (inntektsrapporteringSomKreverBarn.contains(inntektPeriode.inntektsrapportering) && gjelderBarn == null) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Mangler barn for inntekt ${inntektPeriode.inntektsrapportering} med referanse $referanse i grunnlagslisten",
        )
    }
    val inntektBO =
        Inntekt(
            id = id,
            type = inntektPeriode.inntektsrapportering,
            belop = inntektPeriode.beløp,
            gjelderBarn = gjelderBarn?.personIdent,
            taMed = inntektPeriode.valgt,
            datoFom = inntektPeriode.periode.fom.atDay(1),
            datoTom = inntektPeriode.periode.til?.atDay(1)?.minusDays(1),
            ident = gjelder.personIdent!!,
            kilde = if (inntektPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
            behandling = behandling,
        )

    inntektBO.inntektsposter =
        inntektPeriode.inntekstpostListe.mapIndexed { i, it ->
            Inntektspost(
                id = if (id != null) id + i else null,
                kode = it.kode,
                inntektstype = it.inntekstype,
                beløp = it.beløp,
                inntekt = inntektBO,
                visningsnavn = finnVisningsnavn(it.kode),
            )
        }.toMutableSet()

    return inntektBO
}

fun GrunnlagDto.tilRolle(
    behandling: Behandling,
    id: Long? = null,
) = Rolle(
    behandling,
    id = id,
    rolletype =
        when (type) {
            Grunnlagstype.PERSON_SØKNADSBARN -> Rolletype.BARN
            Grunnlagstype.PERSON_BIDRAGSMOTTAKER -> Rolletype.BIDRAGSMOTTAKER
            Grunnlagstype.PERSON_REELL_MOTTAKER -> Rolletype.REELMOTTAKER
            Grunnlagstype.PERSON_BIDRAGSPLIKTIG -> Rolletype.BIDRAGSPLIKTIG
            else -> throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Ukjent rolletype $type",
            )
        },
    ident = personIdent,
    foedselsdato = personObjekt.fødselsdato,
)
