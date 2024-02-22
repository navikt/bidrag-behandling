package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetArbeidsforhold
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetGrunnlagPeriodeInnhold
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.grunnlag.response.Ansettelsesdetaljer
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.Permisjon
import no.nav.bidrag.transport.behandling.grunnlag.response.Permittering
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
        Grunnlagstype.PERSON_REELL_MOTTAKER,
        Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
    )
val inntektsrapporteringSomKreverBarn =
    listOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
    )

fun List<GrunnlagDto>.innhentetTidspunkt(grunnlagstype: Grunnlagstype) =
    filtrerBasertPåEgenReferanse(grunnlagType = grunnlagstype)
        .firstOrNull()?.innholdTilObjekt<InnhentetGrunnlagPeriodeInnhold<Any>>()?.hentetTidspunkt
        ?: LocalDateTime.now()

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

fun VedtakDto.tilBehandling(vedtaksId: Long) {
    val behandling =
        Behandling(
            vedtakstype = type,
            virkningstidspunkt = hentVedtakstidspunkt()?.virkningstidspunkt,
            årsak = hentVedtakstidspunkt()?.årsak,
            avslag = avslagskode(),
            søktFomDato = hentSøknad().søktFraDato,
            soknadFra = hentSøknad().søktAv,
            mottattdato = hentSøknad().mottattDato,
            stonadstype = stønadsendringListe.firstOrNull()?.type,
            engangsbeloptype = engangsbeløpListe.firstOrNull()?.type,
            vedtaksid = vedtaksId,
            behandlerEnhet = enhetsnummer?.verdi!!,
            opprettetAv = opprettetAv,
            opprettetAvNavn = opprettetAvNavn,
            kildeapplikasjon = kildeapplikasjon,
            datoTom = null,
            saksnummer = saksnummer ?: "",
            soknadsid = søknadId ?: -1,
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
            .map { it.tilRolle(behandling) }.toMutableSet()
    behandling.inntekter =
        grunnlagListe.filter { it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE }
            .map { it.tilInntekt(behandling, grunnlagListe) }.toMutableSet()
    behandling.husstandsbarn =
        grunnlagListe.filter { it.type == Grunnlagstype.BOSTATUS_PERIODE }
            .groupBy { it.gjelderReferanse }.map {
                it.value.tilHusstandsbarn(it.key!!, behandling, grunnlagListe)
            }.toMutableSet()
    behandling.sivilstand =
        grunnlagListe.filter { it.type == Grunnlagstype.SIVILSTAND_PERIODE }
            .map {
                it.tilSivilstand(behandling)
            }.toMutableSet()

    behandling.grunnlagListe =
        listOf(
            BehandlingGrunnlag(
                behandling = behandling,
                innhentet = grunnlagListe.innhentetTidspunkt(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD),
                data = commonObjectmapper.writeValueAsString(grunnlagListe.hentGrunnlagArbeidsforhold()),
                type = Grunnlagsdatatype.ARBEIDSFORHOLD,
            ),
        )
}

fun VedtakDto.hentVedtakstidspunkt(): VirkningstidspunktGrunnlag? {
    return grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT)
        .firstOrNull()?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
}

fun VedtakDto.hentSøknad(): SøknadGrunnlag {
    return grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD).first()
        .innholdTilObjekt<SøknadGrunnlag>()
}

fun List<GrunnlagDto>.tilHusstandsbarn(
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

fun GrunnlagDto.tilSivilstand(behandling: Behandling): Sivilstand {
    val sivilstandPeriode = innholdTilObjekt<SivilstandPeriode>()

    return Sivilstand(
        sivilstand = sivilstandPeriode.sivilstand,
        datoFom = sivilstandPeriode.periode.fom.atDay(1),
        datoTom = sivilstandPeriode.periode.til?.atDay(1)?.minusDays(1),
        behandling = behandling,
        kilde = if (sivilstandPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
    )
}

fun GrunnlagDto.tilInntekt(
    behandling: Behandling,
    grunnlagsListe: List<GrunnlagDto>,
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
            inntektsrapportering = inntektPeriode.inntektsrapportering,
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
        inntektPeriode.inntekstpostListe.map {
            Inntektspost(
                kode = it.kode,
                inntektstype = it.inntekstype,
                beløp = it.beløp,
                inntekt = inntektBO,
                visningsnavn = "",
            )
        }.toMutableSet()

    return inntektBO
}

fun GrunnlagDto.tilRolle(behandling: Behandling) =
    Rolle(
        behandling,
        rolletype =
            when (type) {
                Grunnlagstype.PERSON_SØKNADSBARN -> Rolletype.BARN
                Grunnlagstype.PERSON_BIDRAGSMOTTAKER -> Rolletype.BIDRAGSMOTTAKER
                Grunnlagstype.PERSON_REELL_MOTTAKER -> Rolletype.REELMOTTAKER
                Grunnlagstype.PERSON_BIDRAGSPLIKTIG -> Rolletype.BIDRAGSPLIKTIG
                else -> throw RuntimeException("")
            },
        ident = personIdent,
        foedselsdato = personObjekt.fødselsdato,
    )
