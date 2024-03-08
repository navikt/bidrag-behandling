package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.finnAntallBarnIHusstanden
import no.nav.bidrag.behandling.transformers.finnSivilstandForPeriode
import no.nav.bidrag.behandling.transformers.finnTotalInntekt
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
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
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.saksnummer
import no.nav.bidrag.transport.behandling.vedtak.response.søknadId
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
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
    vedtakId: Long,
    lesemodus: Boolean = true,
    vedtakType: Vedtakstype? = null,
    mottattdato: LocalDate? = null,
    søktFomDato: LocalDate? = null,
    soknadFra: SøktAvType? = null,
    søknadRefId: Long? = null,
    søknadId: Long? = null,
    enhet: String? = null,
): Behandling {
    val opprettetAv =
        if (lesemodus) {
            this.opprettetAv
        } else {
            TokenUtils.hentSaksbehandlerIdent()
                ?: TokenUtils.hentApplikasjonsnavn()!!
        }
    val opprettetAvNavn =
        if (lesemodus) {
            this.opprettetAvNavn
        } else {
            TokenUtils.hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        }
    val behandling =
        Behandling(
            id = if (lesemodus) 1 else null,
            vedtakstype = vedtakType ?: type,
            virkningstidspunkt = if (lesemodus) hentVedtakstidspunkt()?.virkningstidspunkt else null,
            årsak = if (lesemodus) hentVedtakstidspunkt()?.årsak else null,
            avslag = if (lesemodus) avslagskode() else null,
            søktFomDato = søktFomDato ?: hentSøknad().søktFraDato,
            soknadFra = soknadFra ?: hentSøknad().søktAv,
            mottattdato = mottattdato ?: hentSøknad().mottattDato,
            // TODO: Er dette riktig? Hva skjer hvis det finnes flere stønadsendringer/engangsbeløp? Fungerer for Forskudd men todo fram fremtiden
            stonadstype = stønadsendringListe.firstOrNull()?.type,
            engangsbeloptype = engangsbeløpListe.firstOrNull()?.type,
            vedtaksid = null,
            soknadRefId = søknadRefId,
            refVedtaksid = vedtakId,
            behandlerEnhet = enhet ?: enhetsnummer?.verdi!!,
            opprettetAv = opprettetAv,
            opprettetAvNavn = opprettetAvNavn,
            kildeapplikasjon = if (lesemodus) kildeapplikasjon else TokenUtils.hentApplikasjonsnavn()!!,
            datoTom = null,
            saksnummer = saksnummer!!,
            soknadsid = søknadId ?: this.søknadId!!,
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
    behandling.roller = grunnlagListe.mapRoller(behandling, lesemodus)
    behandling.inntekter = grunnlagListe.mapInntekter(behandling, lesemodus)
    behandling.husstandsbarn = grunnlagListe.mapHusstandsbarn(behandling, lesemodus)
    behandling.sivilstand = grunnlagListe.mapSivilstand(behandling, lesemodus)
    behandling.grunnlag = grunnlagListe.mapGrunnlag(behandling, lesemodus)

    return behandling
}

private fun List<GrunnlagDto>.mapGrunnlag(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Grunnlag> =
    (
        hentGrunnlagIkkeInntekt(behandling, lesemodus) +
            hentGrunnlagInntekt(
                behandling,
                lesemodus,
            ) + hentInnntekterBearbeidet(behandling, lesemodus)
    ).toMutableSet()

private fun List<GrunnlagDto>.mapRoller(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Rolle> =
    filter { grunnlagstyperRolle.contains(it.type) }
        .mapIndexed { i, it -> it.tilRolle(behandling, if (lesemodus) i.toLong() else null) }
        .toMutableSet()

private fun List<GrunnlagDto>.mapHusstandsbarn(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Husstandsbarn> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
        .groupBy { it.gjelderReferanse }.map {
            it.value.tilHusstandsbarn(it.key!!, behandling, this)
        }.toMutableSet()

private fun List<GrunnlagDto>.mapSivilstand(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Sivilstand> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
        .mapIndexed { i, it ->
            it.tilSivilstand(behandling, if (lesemodus) i.toLong() else null)
        }.toMutableSet()

private fun List<GrunnlagDto>.mapInntekter(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Inntekt> {
    val inntekter =
        filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            .mapIndexed { i, it ->
                it.tilInntekt(
                    behandling,
                    this,
                    if (lesemodus) i.toLong() else null,
                )
            }.toMutableSet()
    if (!lesemodus) {
        inntekter.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }.ifTaMed {
            it.copy(
                type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAK,
                kilde = Kilde.MANUELL,
            ).run {
                inntekter.add(this)
                it.taMed = false
            }
        }

        inntekter.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND }.ifTaMed {
            it.copy(
                type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAK,
                kilde = Kilde.MANUELL,
            ).run {
                inntekter.add(this)
                it.taMed = false
            }
        }
    }
    return inntekter
}

fun List<GrunnlagDto>.innhentetTidspunkt(grunnlagstype: Grunnlagstype) =
    filtrerBasertPåEgenReferanse(grunnlagstype)
        .firstOrNull()?.innhold?.get("hentetTidspunkt")?.let {
            commonObjectmapper.treeToValue(it, LocalDateTime::class.java)
        } ?: LocalDateTime.now()

fun List<GrunnlagDto>.hentGrunnlagIkkeInntekt(
    behandling: Behandling,
    lesemodus: Boolean,
) = listOf(
    hentGrunnlagArbeidsforhold().groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD),
                lesemodus,
            )
        },
    hentInnhentetSivilstand().groupBy { it.personId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.SIVILSTAND,
                grunnlag,
                gjelderIdent!!,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_SIVILSTAND),
                lesemodus,
            )
        },
    hentInnhentetHusstandsmedlem().groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.BOFORHOLD,
                grunnlag,
                gjelderIdent!!,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                lesemodus,
            )
        },
).flatten()

private fun List<GrunnlagDto>.hentGrunnlagInntekt(
    behandling: Behandling,
    lesemodus: Boolean,
): List<Grunnlag> {
    return listOf(
        hentBeregnetInntekt().entries.map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.BEREGNET_INNTEKT),
                lesemodus,
                erBearbeidet = true,
            )
        },
        hentBarnetillegListe().groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG),
                    lesemodus,
                )
            },
        hentUtvidetbarnetrygdListe().groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD),
                    lesemodus,
                )
            },
        hentSmåbarnstilleggListe().groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG),
                    lesemodus,
                )
            },
        hentBarnetilsynListe().groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILSYN,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN),
                    lesemodus,
                )
            },
        hentKontantstøtteListe().groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.KONTANTSTØTTE,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE),
                    lesemodus,
                )
            },
        hentGrunnlagSkattepliktig()
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE),
                    lesemodus,
                )
            },
    ).flatten()
}

fun Behandling.opprettGrunnlag(
    type: Grunnlagsdatatype,
    grunnlag: Any,
    gjelderIdent: String,
    innhentetTidspunkt: LocalDateTime,
    lesemodus: Boolean,
    erBearbeidet: Boolean = false,
) = Grunnlag(
    behandling = this,
    id = if (lesemodus) 1 else null,
    innhentet = innhentetTidspunkt,
    data = commonObjectmapper.writeValueAsString(grunnlag),
    type = type,
    erBearbeidet = erBearbeidet,
    aktiv = innhentetTidspunkt,
    rolle = roller.find { it.ident == gjelderIdent }!!,
)

private fun VedtakDto.notatMedType(
    type: NotatGrunnlag.NotatType,
    medIVedtak: Boolean,
) = grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.NOTAT)
    .map { it.innholdTilObjekt<NotatGrunnlag>() }
    .find { it.type == type && it.erMedIVedtaksdokumentet == medIVedtak }?.innhold

private fun VedtakDto.avslagskode() =
    if (stønadsendringListe.all { it.periodeListe.size == 1 }) {
        Resultatkode.fraKode(stønadsendringListe.first().periodeListe.first().resultatkode)
    } else {
        null
    }

private fun VedtakDto.hentVedtakstidspunkt(): VirkningstidspunktGrunnlag? {
    return grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT)
        .firstOrNull()?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
}

private fun VedtakDto.hentSøknad(): SøknadGrunnlag {
    return grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD).first()
        .innholdTilObjekt<SøknadGrunnlag>()
}

private fun List<BaseGrunnlag>.tilHusstandsbarn(
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

private fun BaseGrunnlag.tilSivilstand(
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

private fun BaseGrunnlag.tilInntekt(
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
    val datoFom = inntektPeriode.periode.fom.atDay(1)
    val datoTom = inntektPeriode.periode.til?.atDay(1)?.minusDays(1)
    val opprinneligFom = inntektPeriode.opprinneligPeriode?.fom?.atDay(1)
    val opprinneligTom = inntektPeriode.opprinneligPeriode?.til?.atDay(1)?.minusDays(1)
    val inntektBO =
        Inntekt(
            id = id,
            type = inntektPeriode.inntektsrapportering,
            belop = inntektPeriode.beløp,
            gjelderBarn = gjelderBarn?.personIdent,
            taMed = inntektPeriode.valgt,
            datoFom = datoFom,
            datoTom = datoTom,
            opprinneligFom =
                if (!inntektPeriode.manueltRegistrert) {
                    opprinneligFom
                        ?: datoFom
                } else {
                    null
                },
            opprinneligTom =
                if (!inntektPeriode.manueltRegistrert) {
                    opprinneligTom
                        ?: datoTom
                } else {
                    null
                },
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

private fun GrunnlagDto.tilRolle(
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

private fun Inntekt.copy(
    type: Inntektsrapportering? = null,
    kilde: Kilde? = null,
): Inntekt {
    return Inntekt(
        type = type ?: this.type,
        belop = belop,
        gjelderBarn = gjelderBarn,
        taMed = taMed,
        datoFom = datoFom,
        datoTom = datoTom,
        ident = ident,
        kilde = kilde ?: this.kilde,
        behandling = behandling,
        inntektsposter =
            inntektsposter.map {
                Inntektspost(
                    beløp = it.beløp,
                    inntektstype = it.inntektstype,
                    visningsnavn = it.visningsnavn,
                    kode = it.kode,
                    inntekt = this,
                )
            }.toMutableSet(),
    )
}