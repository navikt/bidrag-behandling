package no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.beregning.DelvedtakDto
import no.nav.bidrag.behandling.dto.v1.beregning.KlageOmgjøringDetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBarnebidragsberegningPeriodeDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.service.hentVedtak
import no.nav.bidrag.behandling.transformers.ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.byggResultatBidragsberegning
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.erForskudd
import no.nav.bidrag.behandling.transformers.finnAldersjusteringDetaljerGrunnlag
import no.nav.bidrag.behandling.transformers.finnAntallBarnIHusstanden
import no.nav.bidrag.behandling.transformers.finnSivilstandForPeriode
import no.nav.bidrag.behandling.transformers.finnTotalInntektForRolle
import no.nav.bidrag.behandling.transformers.kanOpprette35C
import no.nav.bidrag.behandling.transformers.opprettStønadDto
import no.nav.bidrag.behandling.transformers.tilGrunnlagsdatatypeBeløpshistorikk
import no.nav.bidrag.behandling.transformers.tilGrunnlagstypeBeløpshistorikk
import no.nav.bidrag.behandling.transformers.tilStønadsid
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.tilTypeBoforhold
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdVoksneRequest
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.transport.behandling.felles.grunnlag.AldersjusteringDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.EtterfølgendeManuelleVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManuellVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManueltOverstyrtGebyr
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningGebyr
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjektListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.response.StønadsendringDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.behandlingId
import no.nav.bidrag.transport.behandling.vedtak.response.erOrkestrertVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.finnOrkestreringDetaljer
import no.nav.bidrag.transport.behandling.vedtak.response.finnResultatFraAnnenVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.finnSistePeriode
import no.nav.bidrag.transport.behandling.vedtak.response.finnStønadsendring
import no.nav.bidrag.transport.behandling.vedtak.response.søknadId
import no.nav.bidrag.transport.felles.commonObjectmapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

val VedtakDto.erBisysVedtak get() = behandlingId == null && this.søknadId != null

fun manglerPersonGrunnlag(referanse: Grunnlagsreferanse?): Nothing =
    vedtakmappingFeilet(
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

fun VedtakDto.tilBeregningResultatForskudd(): List<ResultatBeregningBarnDto> =
    stønadsendringListe.map { stønadsendring ->
        val barnIdent = stønadsendring.kravhaver
        val barnGrunnlag = grunnlagListe.hentPerson(barnIdent.verdi) ?: manglerPersonGrunnlag(barnIdent.verdi)
        val barn = barnGrunnlag.innholdTilObjekt<Person>()

        ResultatBeregningBarnDto(
            barn =
                ResultatRolle(
                    barn.ident,
                    barn.navn ?: hentPersonVisningsnavn(barn.ident?.verdi)!!,
                    barn.fødselsdato,
                    referanse = barnGrunnlag.referanse,
                ),
            perioder =
                stønadsendring.periodeListe.map {
                    ResultatBeregningBarnDto.ResultatPeriodeDto(
                        periode = it.periode,
                        resultatKode = Resultatkode.fraKode(it.resultatkode)!!,
                        vedtakstype = type,
                        regel = "",
                        beløp = it.beløp ?: BigDecimal.ZERO,
                        sivilstand = grunnlagListe.finnSivilstandForPeriode(it.grunnlagReferanseListe),
                        inntekt = grunnlagListe.finnTotalInntektForRolle(it.grunnlagReferanseListe),
                        antallBarnIHusstanden = grunnlagListe.finnAntallBarnIHusstanden(it.grunnlagReferanseListe).toInt(),
                    )
                },
        )
    }

fun VedtakDto.tilBeregningResultatBidrag(vedtakBeregning: VedtakDto?): ResultatBidragberegningDto =
    ResultatBidragberegningDto(
        stønadsendringListe.map { stønadsendring ->
            val barnIdent = stønadsendring.kravhaver
            val barnGrunnlag = grunnlagListe.hentPerson(barnIdent.verdi)
            val barn = barnGrunnlag?.innholdTilObjekt<Person>()
            val erResultatUtenBeregning =
                stønadsendring.periodeListe.isEmpty() || stønadsendring.finnSistePeriode()?.resultatkode == "IV" ||
                    type == Vedtakstype.INNKREVING
            val orkestreringDetaljer = grunnlagListe.finnOrkestreringDetaljer(stønadsendring.grunnlagReferanseListe)
            ResultatBidragsberegningBarnDto(
                resultatUtenBeregning = erResultatUtenBeregning,
                barn =
                    ResultatRolle(
                        barn?.ident ?: stønadsendring.kravhaver,
                        hentPersonVisningsnavn(stønadsendring.kravhaver.verdi) ?: "",
                        barn?.fødselsdato ?: LocalDate.now(),
                        hentDirekteOppgjørBeløp(barnIdent.verdi),
                        referanse = barnGrunnlag?.referanse ?: "",
                    ),
                indeksår = stønadsendring.førsteIndeksreguleringsår,
                delvedtak = hentDelvedtak(stønadsendring),
                innkrevesFraDato = orkestreringDetaljer?.innkrevesFraDato,
                perioder =
                    vedtakBeregning?.let {
                        val stønadsendringBeregning = vedtakBeregning.finnStønadsendring(stønadsendring.tilStønadsid())!!
                        it.hentBeregningsperioder(stønadsendringBeregning)
                    } ?: hentBeregningsperioder(stønadsendring),
            )
        },
    )

fun VedtakDto.erVedtakUtenBeregning() =
    type == Vedtakstype.INDEKSREGULERING ||
        stønadsendringListe.all {
            it.periodeListe.isEmpty() || it.finnSistePeriode()?.resultatkode == "IV" ||
                erOrkestrertVedtak && type == Vedtakstype.INNKREVING
        }

internal fun VedtakDto.hentDelvedtak(stønadsendring: StønadsendringDto): List<DelvedtakDto> {
    val barnIdent = stønadsendring.kravhaver

    val orkestreringDetaljer = grunnlagListe.finnOrkestreringDetaljer(stønadsendring.grunnlagReferanseListe)
    val delvedtak =
        stønadsendring.periodeListe
            .mapNotNull { periode ->
                grunnlagListe.finnResultatFraAnnenVedtak(periode.grunnlagReferanseListe)?.let {
                    if (it.vedtaksid == null) {
                        return@let DelvedtakDto(
                            type = Vedtakstype.OPPHØR,
                            omgjøringsvedtak = false,
                            vedtaksid = it.vedtaksid,
                            delvedtak = true,
                            beregnet = true,
                            indeksår = 1,
                            perioder =
                                listOf(
                                    ResultatBarnebidragsberegningPeriodeDto(
                                        periode.periode,
                                        vedtakstype = Vedtakstype.OPPHØR,
                                        resultatKode = Resultatkode.OPPHØR,
                                        erOpphør = true,
                                    ),
                                ),
                        )
                    }
                    val vedtak = hentVedtak(it.vedtaksid)
                    val vedtakPeriode =
                        vedtak!!
                            .stønadsendringListe
                            .find {
                                it.kravhaver == barnIdent
                            }!!
                            .periodeListe
                            .find { it.periode.inneholder(periode.periode) }!!
                    DelvedtakDto(
                        type = vedtak.type,
                        omgjøringsvedtak = it.omgjøringsvedtak,
                        vedtaksid = it.vedtaksid,
                        delvedtak = !it.omgjøringsvedtak,
                        beregnet = it.beregnet,
                        resultatFraVedtakVedtakstidspunkt = vedtak.vedtakstidspunkt,
                        indeksår = vedtak.stønadsendringListe.first().førsteIndeksreguleringsår ?: 1,
                        perioder =
                            listOf(
                                vedtak.grunnlagListe
                                    .byggResultatBidragsberegning(
                                        vedtakPeriode.periode,
                                        vedtakPeriode.beløp,
                                        try {
                                            Resultatkode.fraKode(vedtakPeriode.resultatkode)!!
                                        } catch (_: Exception) {
                                            Resultatkode.BEREGNET_BIDRAG
                                        },
                                        vedtakPeriode.grunnlagReferanseListe,
                                        null,
                                        Resultatkode.fraKode(vedtakPeriode.resultatkode) == Resultatkode.INGEN_ENDRING_UNDER_GRENSE,
                                        vedtak.type,
                                        barnIdent = stønadsendring.kravhaver,
                                    ).copy(
                                        klageOmgjøringDetaljer =
                                            KlageOmgjøringDetaljer(
                                                resultatFraVedtak = it.vedtaksid,
                                                omgjøringsvedtak = it.omgjøringsvedtak,
                                                beregnTilDato = orkestreringDetaljer?.beregnTilDato,
                                                resultatFraVedtakVedtakstidspunkt = it.vedtakstidspunkt,
                                                kanOpprette35c =
                                                    orkestreringDetaljer?.let {
                                                        kanOpprette35C(
                                                            periode.periode,
                                                            orkestreringDetaljer.beregnTilDato,
                                                            vedtak.type,
                                                        )
                                                    } ?: false,
                                                skalOpprette35c = it.opprettParagraf35c,
                                            ),
                                    ),
                            ),
                    )
                }
            }.groupBy { it.vedtaksid }
            .map { (_, delvedtakListe) ->
                val første = delvedtakListe.first()
                første.copy(
                    perioder =
                        delvedtakListe
                            .flatMap { it.perioder }
                            .map {
                                it.copy(
                                    periode =
                                        stønadsendring.periodeListe
                                            .find { st ->
                                                it.periode.inneholder(st.periode)
                                            }!!
                                            .periode,
                                )
                            },
                )
            }

    val endeligVedtak =
        DelvedtakDto(
            type = type,
            omgjøringsvedtak = false,
            vedtaksid = null,
            delvedtak = false,
            beregnet = false,
            indeksår = 1,
            perioder =
                delvedtak
                    .flatMap { it.perioder }
                    .map { p ->
                        val periodeVedtak = delvedtak.find { it.perioder.any { it.periode.inneholder(p.periode) } }
                        p.copy(
                            vedtakstype = periodeVedtak?.type ?: p.vedtakstype,
                            resultatFraVedtak =
                                ResultatFraVedtakGrunnlag(
                                    vedtaksid = periodeVedtak?.vedtaksid,
                                    vedtakstype = periodeVedtak?.type,
                                    beregnet = periodeVedtak?.beregnet ?: false,
                                ),
                            klageOmgjøringDetaljer =
                                KlageOmgjøringDetaljer(
                                    resultatFraVedtak = periodeVedtak?.vedtaksid,
                                    omgjøringsvedtak = periodeVedtak?.omgjøringsvedtak ?: false,
                                    kanOpprette35c =
                                        periodeVedtak?.perioder?.any { it.klageOmgjøringDetaljer?.kanOpprette35c == true } == true,
                                    skalOpprette35c =
                                        periodeVedtak?.perioder?.any { it.klageOmgjøringDetaljer?.skalOpprette35c == true } == true,
                                    resultatFraVedtakVedtakstidspunkt = periodeVedtak?.resultatFraVedtakVedtakstidspunkt,
                                    beregnTilDato = orkestreringDetaljer?.beregnTilDato,
                                ),
                        )
                    },
        )
    return delvedtak + listOf(endeligVedtak)
}

internal fun VedtakDto.hentBeregningsperioder(stønadsendring: StønadsendringDto): List<ResultatBarnebidragsberegningPeriodeDto> {
    val grunnlagsliste = grunnlagListe
    val aldersjusteringDetaljer = grunnlagListe.finnAldersjusteringDetaljerGrunnlag()
    val erResultatUtenBeregning =
        stønadsendring.periodeListe.isEmpty() || stønadsendring.finnSistePeriode()?.resultatkode == "IV" ||
            type == Vedtakstype.INNKREVING
    return if (aldersjusteringDetaljer != null && !aldersjusteringDetaljer.aldersjustert) {
        listOf(
            ResultatBarnebidragsberegningPeriodeDto(
                periode = aldersjusteringDetaljer.periode,
                vedtakstype = Vedtakstype.ALDERSJUSTERING,
                resultatKode = null,
                aldersjusteringDetaljer = aldersjusteringDetaljer,
            ),
        )
    } else if (erResultatUtenBeregning) {
        stønadsendring.periodeListe.map {
            ResultatBarnebidragsberegningPeriodeDto(
                periode = it.periode,
                vedtakstype = type,
                resultatKode = Resultatkode.fraKode(it.resultatkode),
                beregnetBidrag = it.beløp ?: BigDecimal.ZERO,
                faktiskBidrag = it.beløp ?: BigDecimal.ZERO,
                erOpphør = it.beløp == null,
            )
        }
    } else {
        stønadsendring.periodeListe.filter { Resultatkode.fraKode(it.resultatkode) != Resultatkode.OPPHØR }.map {
            grunnlagsliste.byggResultatBidragsberegning(
                it.periode,
                it.beløp,
                try {
                    Resultatkode.fraKode(it.resultatkode)!!
                } catch (_: Exception) {
                    Resultatkode.BEREGNET_BIDRAG
                },
                it.grunnlagReferanseListe,
                null,
                Resultatkode.fraKode(it.resultatkode) == Resultatkode.INGEN_ENDRING_UNDER_GRENSE,
                type,
                barnIdent = stønadsendring.kravhaver,
            )
        }
    }
}

internal fun List<GrunnlagDto>.mapGrunnlag(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Grunnlag> =
    (
        hentGrunnlagIkkeInntekt(behandling, lesemodus) +
            hentGrunnlagBarnetilsyn(behandling, lesemodus) +
            hentGrunnlagInntekt(
                behandling,
                lesemodus,
            ) + hentInnntekterBearbeidet(behandling, lesemodus)
    ).toMutableSet()

internal fun List<GrunnlagDto>.mapRoller(
    vedtak: VedtakDto,
    behandling: Behandling,
    lesemodus: Boolean,
    opprinneligVirkningstidspunkt: LocalDate,
): MutableSet<Rolle> =
    filter { grunnlagstyperRolle.contains(it.type) }
        .mapIndexed { i, rolle ->
            val virkningstidspunktGrunnlag = hentVirkningstidspunkt(rolle.referanse)
            val aldersjustering = hentAldersjusteringDetaljerForBarn(rolle.referanse)
            rolle.tilRolle(
                behandling,
                if (lesemodus) i.toLong() else null,
                virkningstidspunktGrunnlag,
                aldersjustering,
                opprinneligVirkningstidspunkt,
                lesemodus,
            )
        }.toMutableSet()
        .ifEmpty {
            val roller = mutableSetOf<Rolle>()
            val bpIdent = vedtak.stønadsendringListe.firstOrNull()?.skyldner ?: vedtak.engangsbeløpListe.first().skyldner
            val bpGrunnlag =
                GrunnlagDto(
                    type = Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
                    referanse = "",
                    innhold =
                        POJONode(
                            Person(
                                ident = bpIdent,
                                fødselsdato = hentPersonFødselsdato(bpIdent.verdi)!!,
                            ),
                        ),
                )
            roller.add(bpGrunnlag.tilRolle(behandling, if (lesemodus) 1 else null, null, null, opprinneligVirkningstidspunkt, lesemodus))

            val bmIdent = vedtak.stønadsendringListe.firstOrNull()?.mottaker ?: vedtak.engangsbeløpListe.first().mottaker
            val bmGrunnlag =
                GrunnlagDto(
                    type = Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
                    referanse = "",
                    innhold =
                        POJONode(
                            Person(
                                ident = bmIdent,
                                fødselsdato = hentPersonFødselsdato(bmIdent.verdi)!!,
                            ),
                        ),
                )
            roller.add(bmGrunnlag.tilRolle(behandling, if (lesemodus) 2 else null, null, null, opprinneligVirkningstidspunkt, lesemodus))
            roller.addAll(
                vedtak.stønadsendringListe.mapIndexed { i, it ->
                    val baIdent = it.kravhaver
                    val baGrunnlag =
                        GrunnlagDto(
                            type = Grunnlagstype.PERSON_SØKNADSBARN,
                            referanse = "",
                            innhold =
                                POJONode(
                                    Person(
                                        ident = baIdent,
                                        fødselsdato = hentPersonFødselsdato(baIdent.verdi)!!,
                                    ),
                                ),
                        )

                    val virkningstidspunktGrunnlag = hentVirkningstidspunkt(baGrunnlag.referanse)
                    val aldersjustering = hentAldersjusteringDetaljerForBarn(baGrunnlag.referanse)
                    baGrunnlag.tilRolle(
                        behandling,
                        if (lesemodus) (i + 2).toLong() else null,
                        virkningstidspunktGrunnlag,
                        aldersjustering,
                        opprinneligVirkningstidspunkt,
                        lesemodus,
                    )
                },
            )
            roller
        }

internal fun VedtakDto.oppdaterDirekteOppgjørBeløp(
    behandling: Behandling,
    lesemodus: Boolean,
) = if (lesemodus) {
    behandling.søknadsbarn.forEach {
        it.innbetaltBeløp = hentDirekteOppgjørBeløp(it.ident!!)
    }
} else {
    null
}

internal fun VedtakDto.hentDirekteOppgjørBeløp(kravhaver: String) =
    engangsbeløpListe
        .find { it.type == Engangsbeløptype.DIREKTE_OPPGJØR && it.kravhaver.verdi == kravhaver }
        ?.beløp

internal fun List<GrunnlagDto>.oppdaterRolleGebyr(behandling: Behandling) =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SLUTTBEREGNING_GEBYR)
        .groupBy { it.gjelderReferanse }
        .forEach { (gjelderReferanse, grunnlag) ->
            val person = hentPersonMedReferanse(gjelderReferanse)!!
            val rolle = behandling.roller.find { it.ident == person.personIdent }!!
            rolle.harGebyrsøknad = true
            val sluttberegning = grunnlag.first().innholdTilObjekt<SluttberegningGebyr>()
            val manueltOverstyrtGebyr =
                finnGrunnlagSomErReferertAv(
                    Grunnlagstype.MANUELT_OVERSTYRT_GEBYR,
                    grunnlag.first(),
                ).firstOrNull()?.innholdTilObjekt<ManueltOverstyrtGebyr>()
            rolle.manueltOverstyrtGebyr =
                RolleManueltOverstyrtGebyr(
                    manueltOverstyrtGebyr != null,
                    sluttberegning.ilagtGebyr,
                    manueltOverstyrtGebyr?.begrunnelse,
                    if (manueltOverstyrtGebyr != null) {
                        !sluttberegning.ilagtGebyr
                    } else {
                        sluttberegning.ilagtGebyr
                    },
                )
        }

internal fun List<GrunnlagDto>.mapHusstandsmedlem(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Husstandsmedlem> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
        .groupBy { if (it.gjelderBarnReferanse.isNullOrEmpty()) it.gjelderReferanse else it.gjelderBarnReferanse }
        .map {
            it.value.tilHusstandsmedlem(it.key!!, behandling, this, lesemodus)
        }.toMutableSet()

internal fun List<GrunnlagDto>.mapSivilstand(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Sivilstand> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
        .mapIndexed { i, it ->
            it.tilSivilstand(behandling, if (lesemodus) i.toLong() else null)
        }.toMutableSet()

internal fun List<GrunnlagDto>.mapInntekter(
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
    val erForskuddOmgjøring =
        behandling.soknadFra == SøktAvType.NAV_BIDRAG && behandling.vedtakstype == Vedtakstype.ENDRING
    if (!lesemodus && !erForskuddOmgjøring) {
        inntekter.groupBy { it.ident }.forEach { (_, inntekterRolle) ->
            inntekterRolle
                .find {
                    it.type ==
                        Inntektsrapportering.AINNTEKT_BEREGNET_12MND
                }?.let { originalInntekt ->
                    originalInntekt
                        .copy(
                            type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
                        ).run {
                            inntekter.add(this)
                            originalInntekt.taMed = false
                            originalInntekt.datoFom = null
                            originalInntekt.datoTom = null
                        }
                }
            inntekterRolle.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND }?.let { originalInntekt ->
                originalInntekt
                    .copy(
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
                    ).run {
                        inntekter.add(this)
                        originalInntekt.taMed = false
                        originalInntekt.datoFom = null
                        originalInntekt.datoTom = null
                    }
            }
        }
    }
    val inntekterBeregnet =
        inntekter
            .filter { ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt.contains(it.type) }
            .sortedByDescending { it.taMed }
            .distinctBy { listOfNotNull(it.opprinneligFom, it.opprinneligTom, it.type, it.gjelderBarn, it.ident) }
    val andreInntekter = inntekter.filter { !ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt.contains(it.type) }
    return (andreInntekter + inntekterBeregnet).toMutableSet()
}

fun List<GrunnlagDto>.innhentetTidspunkt(grunnlagstype: Grunnlagstype) =
    filtrerBasertPåEgenReferanse(grunnlagstype)
        .firstOrNull()
        ?.innhold
        ?.get("hentetTidspunkt")
        ?.let {
            commonObjectmapper.treeToValue(it, LocalDateTime::class.java)
        } ?: LocalDateTime.now()

fun List<GrunnlagDto>.hentGrunnlagIkkeInntekt(
    behandling: Behandling,
    lesemodus: Boolean,
) = listOfNotNull(
    hentInnhentetAndreBarnTilBidragsmottaker()
        ?.let {
            if (it.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.ANDRE_BARN,
                        it,
                        it.firstOrNull()?.partPersonId!!,
                        innhentetTidspunkt(Grunnlagstype.INNHENTET_ANDRE_BARN_TIL_BIDRAGSMOTTAKER),
                        lesemodus,
                        false,
                    ),
                )
            }
        },
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
        .groupBy { it.gjelderBarnReferanse }
        .map { (gjelderBarn, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val gjelderBarnGrunnlag = hentPersonMedReferanse(gjelderBarn)!!
            val gjelderGrunnlag = hentPersonMedReferanse(grunnlag.gjelderReferanse)!!
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.MANUELLE_VEDTAK,
                grunnlag.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>(),
                gjelder = gjelderBarnGrunnlag.personIdent!!,
                rolleIdent = gjelderGrunnlag.personIdent!!,
                innhentetTidspunkt = LocalDateTime.now(),
                lesemodus = lesemodus,
            )
        },
    filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK)
        .groupBy { it.gjelderBarnReferanse }
        .map { (gjelderBarn, grunnlagListe) ->
            val grunnlag = grunnlagListe.first()
            val gjelderBarnGrunnlag = hentPersonMedReferanse(gjelderBarn)!!
            val gjelderGrunnlag = hentPersonMedReferanse(grunnlag.gjelderReferanse)!!
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.ETTERFØLGENDE_VEDTAK,
                grunnlag.innholdTilObjekt<EtterfølgendeManuelleVedtakGrunnlag>().vedtaksliste,
                gjelder = gjelderBarnGrunnlag.personIdent!!,
                rolleIdent = gjelderGrunnlag.personIdent!!,
                innhentetTidspunkt = LocalDateTime.now(),
                lesemodus = lesemodus,
            )
        },
    if (behandling.vedtakstype == Vedtakstype.KLAGE && !lesemodus) {
        behandling.stonadstype?.let {
            filtrerOgKonverterBasertPåEgenReferanse<BeløpshistorikkGrunnlag>(
                grunnlagType = it.tilGrunnlagstypeBeløpshistorikk(),
            ).groupBy { it.gjelderBarnReferanse }
                .map { (gjelderBarnReferanse, grunnlagsliste) ->
                    val grunnlag = grunnlagsliste.firstOrNull()
                    val gjelderBarn = hentPersonMedReferanse(gjelderBarnReferanse)!!
                    val gjelder = grunnlag?.let { hentPersonMedReferanse(grunnlag.gjelderReferanse) }
                    val rolleBarn = behandling.søknadsbarn.find { it.ident == gjelderBarn.personIdent }!!
                    behandling.opprettGrunnlag(
                        it.tilGrunnlagsdatatypeBeløpshistorikk(),
                        behandling.opprettStønadDto(rolleBarn, grunnlag?.innhold),
                        rolleIdent = rolleBarn.ident!!,
                        gjelder = gjelder?.personIdent,
                        innhentetTidspunkt = behandling.omgjøringsdetaljer?.opprinneligVedtakstidspunkt!!.min(),
                        lesemodus = lesemodus,
                    )
                }
        }
    } else {
        null
    },
    hentGrunnlagArbeidsforhold()
        .groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD),
                lesemodus,
            )
        },
    hentGrunnlagArbeidsforhold()
        .groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD),
                lesemodus,
            )
        },
    hentInnhentetSivilstand()
        .groupBy { it.personId }
        .flatMap { (gjelderIdent, grunnlag) ->
            val sivilstandPeriodisert =
                SivilstandApi.beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    grunnlag.toSet().tilSivilstandRequest(
                        emptySet(),
                        behandling.bidragsmottaker!!.fødselsdato,
                    ),
                )

            listOf(
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SIVILSTAND,
                    grunnlag,
                    gjelderIdent!!,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_SIVILSTAND),
                    lesemodus,
                ),
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SIVILSTAND,
                    sivilstandPeriodisert,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_SIVILSTAND),
                    lesemodus,
                    true,
                ),
            )
        },
    hentInnhentetAndreVoksneIHusstanden().let {
        if (it.isEmpty() && !behandling.erForskudd()) {
            val andreVoksneIHusstandPeriodisert =
                BoforholdApi.beregnBoforholdAndreVoksne(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    BoforholdVoksneRequest(
                        innhentedeOffentligeOpplysninger = emptyList(),
                        behandledeBostatusopplysninger = emptyList(),
                        endreBostatus = null,
                    ),
                    beregnTilDato = behandling.finnBeregnTilDatoBehandling(),
                )
            behandling.bidragspliktig?.let {
                listOf(
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        emptyList<RelatertPersonGrunnlagDto>(),
                        it.ident!!,
                        innhentetTidspunkt(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN),
                        lesemodus,
                    ),
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        andreVoksneIHusstandPeriodisert,
                        it.ident!!,
                        innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                        lesemodus,
                        true,
                    ),
                )
            }
        } else {
            it.groupBy { it.partPersonId }.flatMap { (gjelderRolle, grunnlag) ->

                val andreVoksneIHusstandPeriodisert =
                    BoforholdApi.beregnBoforholdAndreVoksne(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        BoforholdVoksneRequest(
                            innhentedeOffentligeOpplysninger = grunnlag.tilHusstandsmedlemmer(),
                            behandledeBostatusopplysninger = emptyList(),
                            endreBostatus = null,
                        ),
                        beregnTilDato = behandling.finnBeregnTilDatoBehandling(),
                    )
                listOf(
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        grunnlag,
                        gjelderRolle!!,
                        innhentetTidspunkt(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN),
                        lesemodus,
                    ),
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        andreVoksneIHusstandPeriodisert,
                        gjelderRolle,
                        innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                        lesemodus,
                        true,
                    ),
                )
            }
        }
    },
    hentInnhentetHusstandsmedlem()
        .groupBy { it.partPersonId }
        .flatMap { (innhentetForIdent, grunnlag) ->

            val grunnlagsdatatype =
                if (behandling.tilType() != TypeBehandling.FORSKUDD &&
                    innhentetForIdent == behandling.bidragsmottaker?.ident
                ) {
                    Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN
                } else {
                    Grunnlagsdatatype.BOFORHOLD
                }
            val boforholdPeriodisert =
                BoforholdApi.beregnBoforholdBarnV3(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.globalOpphørsdato,
                    behandling.finnBeregnTilDatoBehandling(),
                    behandling.tilTypeBoforhold(),
                    grunnlag.tilBoforholdBarnRequest(behandling, true),
                )
            listOf(
                behandling.opprettGrunnlag(
                    grunnlagsdatatype,
                    grunnlag,
                    innhentetForIdent!!,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                    lesemodus,
                ),
            ) +
                boforholdPeriodisert
                    .filter { it.gjelderPersonId != null }
                    .groupBy { it.gjelderPersonId }
                    .map {
                        behandling.opprettGrunnlag(
                            grunnlagsdatatype,
                            it.value,
                            grunnlag.firstOrNull()?.partPersonId!!,
                            innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                            lesemodus,
                            true,
                            gjelder = it.key!!,
                        )
                    }
        },
).flatten()

private fun List<GrunnlagDto>.hentGrunnlagBarnetilsyn(
    behandling: Behandling,
    lesemodus: Boolean,
) = henteGrunnlagBarnetilsyn()
    .groupBy { it.partPersonId }
    .map { (gjelderIdent, grunnlag) ->

        val ikkebearbeida =
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.BARNETILSYN,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_BARNETILSYN),
                lesemodus,
            )

        val bearbeida =
            grunnlag.groupBy { it.barnPersonId }.map { (personidentBarn, barnetsGrunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILSYN,
                    barnetsGrunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_BARNETILSYN),
                    lesemodus,
                    true,
                    personidentBarn,
                )
            }
        listOf(ikkebearbeida) + bearbeida
    }.flatten()

private fun List<GrunnlagDto>.hentGrunnlagInntekt(
    behandling: Behandling,
    lesemodus: Boolean,
): List<Grunnlag> =
    listOf(
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
        hentBarnetillegListe()
            .groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG),
                    lesemodus,
                )
            },
        hentUtvidetbarnetrygdListe()
            .groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD),
                    lesemodus,
                )
            },
        hentSmåbarnstilleggListe()
            .groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG),
                    lesemodus,
                )
            },
        hentKontantstøtteListe()
            .groupBy { it.partPersonId }
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

fun Behandling.opprettGrunnlag(
    type: Grunnlagsdatatype,
    grunnlag: Any,
    rolleIdent: String,
    innhentetTidspunkt: LocalDateTime,
    lesemodus: Boolean,
    erBearbeidet: Boolean = false,
    gjelder: String? = null,
) = Grunnlag(
    behandling = this,
    id = if (lesemodus) 1 else null,
    innhentet = innhentetTidspunkt,
    data = commonObjectmapper.writeValueAsString(grunnlag),
    type = type,
    erBearbeidet = erBearbeidet,
    gjelder = gjelder,
    grunnlagFraVedtakSomSkalOmgjøres = true,
    aktiv = innhentetTidspunkt,
    rolle = roller.find { it.ident == rolleIdent }!!,
)

internal fun VedtakDto.notatMedTypeBegge(
    type: NotatGrunnlag.NotatType,
    gjelderReferanse: Grunnlagsreferanse? = null,
) = notatMedType(type, false, gjelderReferanse) ?: notatMedType(type, true, gjelderReferanse)

internal fun VedtakDto.notatMedType(
    type: NotatGrunnlag.NotatType,
    fraOmgjortVedtak: Boolean,
    gjelderReferanse: Grunnlagsreferanse? = null,
) = grunnlagListe
    .filtrerBasertPåEgenReferanse(Grunnlagstype.NOTAT)
    .filter {
        gjelderReferanse.isNullOrEmpty() || it.gjelderReferanse.isNullOrEmpty() && it.gjelderBarnReferanse.isNullOrEmpty() ||
            it.gjelderReferanse == gjelderReferanse || it.gjelderBarnReferanse == gjelderReferanse
    }.map { it.innholdTilObjekt<NotatGrunnlag>() }
    .find { it.type == type && it.fraOmgjortVedtak == fraOmgjortVedtak }
    ?.innhold

internal fun VedtakDto.avslagskode(): Resultatkode? {
    val virkningstidspunkt = hentVirkningstidspunkt()
    return if (virkningstidspunkt == null && stønadsendringListe.all { it.periodeListe.size == 1 }) {
        Resultatkode.fraKode(
            stønadsendringListe
                .first()
                .periodeListe
                .first()
                .resultatkode,
        )
    } else if (virkningstidspunkt?.avslag != null) {
        virkningstidspunkt.avslag
    } else {
        null
    }
}

internal fun VedtakDto.hentVirkningstidspunkt(gjelderBarnReferanse: String? = null): VirkningstidspunktGrunnlag? =
    grunnlagListe.hentVirkningstidspunkt(gjelderBarnReferanse)

internal fun List<GrunnlagDto>.hentAldersjusteringDetaljerForBarn(gjelderBarnReferanse: String? = null): AldersjusteringDetaljerGrunnlag? =
    filtrerBasertPåEgenReferanse(Grunnlagstype.ALDERSJUSTERING_DETALJER)
        .firstOrNull { gjelderBarnReferanse.isNullOrEmpty() || it.gjelderBarnReferanse == gjelderBarnReferanse }
        ?.innholdTilObjekt<AldersjusteringDetaljerGrunnlag>()

internal fun List<GrunnlagDto>.hentVirkningstidspunkt(gjelderBarnReferanse: String? = null): VirkningstidspunktGrunnlag? =
    filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT)
        .firstOrNull { gjelderBarnReferanse.isNullOrEmpty() || it.gjelderBarnReferanse == gjelderBarnReferanse }
        ?.innholdTilObjekt<VirkningstidspunktGrunnlag>()

internal fun VedtakDto.hentSøknad(): SøknadGrunnlag =
    grunnlagListe
        .filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD)
        .firstOrNull()
        ?.innholdTilObjekt<SøknadGrunnlag>() ?: SøknadGrunnlag(
        mottattDato = vedtakstidspunkt?.toLocalDate() ?: opprettetTidspunkt.toLocalDate(),
        søktFraDato = vedtakstidspunkt?.toLocalDate() ?: opprettetTidspunkt.toLocalDate(),
        søktAv = SøktAvType.NAV_BIDRAG,
    )

internal fun List<BaseGrunnlag>.tilHusstandsmedlem(
    gjelderReferanse: Grunnlagsreferanse,
    behandling: Behandling,
    grunnlagsListe: List<GrunnlagDto>,
    lesemodus: Boolean,
): Husstandsmedlem {
    val gjelderGrunnlag =
        grunnlagsListe.hentPersonMedReferanse(gjelderReferanse) ?: manglerPersonGrunnlag(
            gjelderReferanse,
        )

    val gjelderPerson = gjelderGrunnlag.innholdTilObjekt<Person>()

    val gjelderRolle =
        behandling.roller
            .find { hentNyesteIdent(it.ident) == hentNyesteIdent(gjelderPerson.ident?.verdi) }
    val erBmBpBosstatus =
        gjelderRolle?.let { listOf(Rolletype.BIDRAGSPLIKTIG, Rolletype.BIDRAGSMOTTAKER).contains(it.rolletype) }
            ?: false
    val erOffentligKilde =
        grunnlagsListe
            .hentInnhentetHusstandsmedlem()
            .any { it.gjelderPersonId == gjelderGrunnlag.personIdent }
    val husstandsmedlemBO =
        Husstandsmedlem(
            id = if (lesemodus) 1 else null,
            ident = gjelderGrunnlag.personIdent,
            navn = gjelderPerson.navn,
            fødselsdato = gjelderPerson.fødselsdato,
            kilde = if (erOffentligKilde || erBmBpBosstatus) Kilde.OFFENTLIG else Kilde.MANUELL,
            behandling = behandling,
            rolle = gjelderRolle,
        )
    husstandsmedlemBO.perioder =
        this
            .mapIndexed { index, it ->
                val bosstatusPeriode = it.innholdTilObjekt<BostatusPeriode>()
                Bostatusperiode(
                    id = if (lesemodus) index.toLong() else null,
                    husstandsmedlem = husstandsmedlemBO,
                    datoFom = bosstatusPeriode.periode.fom.atDay(1),
                    datoTom =
                        bosstatusPeriode.periode.til
                            ?.atDay(1)
                            ?.minusDays(1),
                    bostatus = bosstatusPeriode.bostatus,
                    kilde = if (bosstatusPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
                )
            }.toMutableSet()
    return husstandsmedlemBO
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
        datoTom =
            sivilstandPeriode.periode.til
                ?.atDay(1)
                ?.minusDays(1),
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
        vedtakmappingFeilet(
            "Mangler barn for inntekt ${inntektPeriode.inntektsrapportering} med referanse $referanse i grunnlagslisten",
        )
    }
    val datoFom = if (inntektPeriode.valgt) inntektPeriode.periode.fom.atDay(1) else null
    val datoTom =
        if (inntektPeriode.valgt) {
            inntektPeriode.periode.til
                ?.atDay(1)
                ?.minusDays(1)
        } else {
            null
        }
    val opprinneligFom = inntektPeriode.opprinneligPeriode?.fom?.atDay(1)
    val opprinneligTom =
        inntektPeriode.opprinneligPeriode
            ?.til
            ?.atDay(1)
            ?.minusDays(1)
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
        inntektPeriode.inntektspostListe
            .mapIndexed { i, it ->
                Inntektspost(
                    id = if (id != null) id + i else null,
                    kode = it.kode,
                    inntektstype = it.inntektstype,
                    beløp = it.beløp,
                    inntekt = inntektBO,
                )
            }.toMutableSet()

    return inntektBO
}

private fun GrunnlagDto.tilRolle(
    behandling: Behandling,
    id: Long? = null,
    virkningstidspunktGrunnlag: VirkningstidspunktGrunnlag?,
    aldersjustering: AldersjusteringDetaljerGrunnlag?,
    opprinneligVirkningstidspunkt: LocalDate,
    lesemodus: Boolean,
): Rolle =
    Rolle(
        behandling,
        id = id,
        rolletype =
            when (type) {
                Grunnlagstype.PERSON_SØKNADSBARN -> Rolletype.BARN
                Grunnlagstype.PERSON_BIDRAGSMOTTAKER -> Rolletype.BIDRAGSMOTTAKER
                Grunnlagstype.PERSON_REELL_MOTTAKER -> Rolletype.REELMOTTAKER
                Grunnlagstype.PERSON_BIDRAGSPLIKTIG -> Rolletype.BIDRAGSPLIKTIG
                else ->
                    vedtakmappingFeilet(
                        "Ukjent rolletype $type",
                    )
            },
        ident = personIdent,
        opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
        virkningstidspunkt = virkningstidspunktGrunnlag?.virkningstidspunkt,
        årsak = virkningstidspunktGrunnlag?.årsak,
        avslag = virkningstidspunktGrunnlag?.avslag,
        opphørsdato = virkningstidspunktGrunnlag?.opphørsdato,
        fødselsdato = personObjekt.fødselsdato,
        beregnTil =
            if (lesemodus) {
                virkningstidspunktGrunnlag?.beregnTil ?: BeregnTil.INNEVÆRENDE_MÅNED
            } else if (behandling.erBidrag()) {
                BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
            } else {
                BeregnTil.INNEVÆRENDE_MÅNED
            },
        grunnlagFraVedtak = aldersjustering?.grunnlagFraVedtak,
    )

private fun Inntekt.copy(
    type: Inntektsrapportering? = null,
    kilde: Kilde? = null,
): Inntekt {
    val nyInntekt =
        Inntekt(
            type = type ?: this.type,
            belop = belop,
            gjelderBarn = gjelderBarn,
            taMed = taMed,
            datoFom = datoFom,
            datoTom = datoTom,
            ident = ident,
            kilde = kilde ?: this.kilde,
            behandling = behandling,
            opprinneligFom = opprinneligFom,
            opprinneligTom = opprinneligTom,
        )
    nyInntekt.inntektsposter =
        inntektsposter
            .map {
                Inntektspost(
                    beløp = it.beløp,
                    inntektstype = it.inntektstype,
                    kode = it.kode,
                    inntekt = nyInntekt,
                )
            }.toMutableSet()
    return nyInntekt
}
