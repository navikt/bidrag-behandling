package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GrunnlagFraVedtak
import no.nav.bidrag.behandling.database.datamodell.grunnlagsinnhentingFeiletMap
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.UgyldigBeregningDto.UgyldigResultatPeriode
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.beregn.barnebidrag.service.EtterfølgendeVedtakSomOverlapper
import no.nav.bidrag.beregn.core.exception.BegrensetRevurderingLikEllerLavereEnnLøpendeBidragException
import no.nav.bidrag.beregn.core.exception.BegrensetRevurderingLøpendeForskuddManglerException
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erDirekteAvslag
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.lastVisningsnavnFraFil
import no.nav.bidrag.domene.util.visningsnavnIntern
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorResponse
import no.nav.bidrag.transport.behandling.felles.grunnlag.AldersjusteringDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningEndringSjekkGrensePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidragAldersjustering
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningIndeksregulering
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.response.erIndeksEllerAldersjustering
import no.nav.bidrag.transport.felles.tilVisningsnavn
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val YearMonth.formatterDatoFom get() = this.atDay(1).format(DateTimeFormatter.ofPattern("MM.YYYY"))
val YearMonth.formatterDatoTom get() = this.atEndOfMonth().format(DateTimeFormatter.ofPattern("MM.YYYY"))
val ÅrMånedsperiode.periodeString get() = "${fom.formatterDatoFom} - ${til?.formatterDatoTom ?: ""}"

fun Behandling.tilBeregningFeilmelding(): UgyldigBeregningDto? {
    val grunnlagsfeil = grunnlagsinnhentingFeiletMap()
    if (søknadstype == BisysSøknadstype.BEGRENSET_REVURDERING) {
        if (grunnlagsfeil.containsKey(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG) ||
            grunnlagsfeil.containsKey(Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD)
        ) {
            return UgyldigBeregningDto(
                tittel = "Innhenting av beløpshistorikk feilet",
                begrunnelse =
                    "Det skjedde en feil ved innhenting av beløpshistorikk for forskudd og bidrag. ",
                resultatPeriode = emptyList(),
            )
        }
    }
    if (stonadstype == Stønadstype.BIDRAG18AAR) {
        if (grunnlagsfeil.containsKey(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR)) {
            return UgyldigBeregningDto(
                tittel = "Innhenting av beløpshistorikk feilet",
                begrunnelse =
                    "Det skjedde en feil ved innhenting av beløpshistorikk for bidrag. ",
                resultatPeriode = emptyList(),
            )
        }
    }
    if (grunnlagsfeil.containsKey(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG)) {
        return UgyldigBeregningDto(
            tittel = "Innhenting av beløpshistorikk feilet",
            begrunnelse =
                "Det skjedde en feil ved innhenting av beløpshistorikk for bidrag. ",
            resultatPeriode = emptyList(),
        )
    }

    return null
}

fun BegrensetRevurderingLøpendeForskuddManglerException.opprettBegrunnelse(): UgyldigBeregningDto {
    val allePerioder = data.beregnetBarnebidragPeriodeListe.sortedBy { it.periode.fom }
    val perioderUtenForskudd =
        allePerioder
            .filter {
                val sluttberegning =
                    data.grunnlagListe
                        .finnSluttberegningIReferanser(
                            it.grunnlagsreferanseListe,
                        )?.innholdTilObjekt<SluttberegningBarnebidrag>()
                sluttberegning?.resultat == SluttberegningBarnebidrag::bidragJustertTilForskuddssats.name &&
                    it.resultat.beløp == BigDecimal.ZERO
            }.map { it.periode }
            .takeIf { it.isNotEmpty() } ?: periodeListe
    val resultatPerioderUtenForskudd =
        perioderUtenForskudd.map { periode ->
            UgyldigResultatPeriode(
                periode = periode,
                type = UgyldigBeregningDto.UgyldigBeregningType.BEGRENSET_REVURDERING_UTEN_LØPENDE_FORSKUDD,
            )
        }
    return UgyldigBeregningDto(
        tittel = "Begrenset revurdering",
        resultatPeriode = resultatPerioderUtenForskudd,
        begrunnelse =
            if (perioderUtenForskudd.size > 1) {
                "Perioder ${perioderUtenForskudd.joinToString {it.periodeString}} har ingen løpende forskudd"
            } else {
                "Periode ${perioderUtenForskudd.first().periodeString} har ingen løpende forskudd"
            },
        perioder = perioderUtenForskudd,
    )
}

fun BegrensetRevurderingLikEllerLavereEnnLøpendeBidragException.opprettBegrunnelse(): UgyldigBeregningDto {
    val allePerioder = data.beregnetBarnebidragPeriodeListe.sortedBy { it.periode.fom }
    val perioderUtenForskudd =
        allePerioder.filter {
            val sluttberegning =
                data.grunnlagListe
                    .finnSluttberegningIReferanser(
                        it.grunnlagsreferanseListe,
                    )?.innholdTilObjekt<SluttberegningBarnebidrag>()
            sluttberegning?.resultat == SluttberegningBarnebidrag::bidragJustertTilForskuddssats.name &&
                it.resultat.beløp == BigDecimal.ZERO
        }
    val resultatPerioderUnderLøpendeBidrag =
        (periodeListe - perioderUtenForskudd.map { it.periode }).map { periode ->
            UgyldigResultatPeriode(
                periode = periode,
                type = UgyldigBeregningDto.UgyldigBeregningType.BEGRENSET_REVURDERING_LIK_ELLER_LAVERE_ENN_LØPENDE_BIDRAG,
            )
        }
    return UgyldigBeregningDto(
        tittel = "Begrenset revurdering",
        perioder = this.periodeListe,
        resultatPeriode = resultatPerioderUnderLøpendeBidrag,
        begrunnelse =
            if (this.periodeListe.size > 1) {
                "Flere perioder er lik eller lavere enn løpende bidrag"
            } else {
                "Periode ${this.periodeListe.first().periodeString} er lik eller lavere enn løpende bidrag"
            },
    )
}

data class ResultatBidragsberegningBarn(
    val barn: ResultatRolle,
    val vedtakstype: Vedtakstype,
    val resultat: BeregnetBarnebidragResultat,
    val resultatVedtak: BidragsberegningOrkestratorResponse? = null,
    val avslagskode: Resultatkode? = null,
    val ugyldigBeregning: UgyldigBeregningDto? = null,
    val omgjøringsdetaljer: Omgjøringsdetaljer? = null,
    val innkrevesFraDato: YearMonth? = null,
    val beregnTilDato: YearMonth? = null,
)

data class UgyldigBeregningDto(
    val tittel: String,
    val begrunnelse: String,
    val vedtaksliste: List<EtterfølgendeVedtakSomOverlapper> = emptyList(),
    val resultatPeriode: List<UgyldigResultatPeriode> = emptyList(),
    val perioder: List<ÅrMånedsperiode> = emptyList(),
) {
    data class UgyldigResultatPeriode(
        val periode: ÅrMånedsperiode,
        val type: UgyldigBeregningType,
    )

    enum class UgyldigBeregningType {
        BEGRENSET_REVURDERING_LIK_ELLER_LAVERE_ENN_LØPENDE_BIDRAG,
        BEGRENSET_REVURDERING_UTEN_LØPENDE_FORSKUDD,
    }
}

data class ResultatBidragberegningDto(
    val resultatBarn: List<ResultatBidragsberegningBarnDto> = emptyList(),
)

data class ResultatBidragsberegningBarnDto(
    val barn: ResultatRolle,
    val innkrevesFraDato: YearMonth? = null,
    val resultatUtenBeregning: Boolean = false,
    val indeksår: Int? = null,
    val ugyldigBeregning: UgyldigBeregningDto? = null,
    val forsendelseDistribueresAutomatisk: Boolean = false,
    val perioder: List<ResultatBarnebidragsberegningPeriodeDto>,
    val delvedtak: List<DelvedtakDto> = emptyList(),
)

data class DelvedtakDto(
    val type: Vedtakstype?,
    val omgjøringsvedtak: Boolean,
    val vedtaksid: Int? = null,
    val delvedtak: Boolean,
    val beregnet: Boolean,
    val indeksår: Int,
    val resultatFraVedtakVedtakstidspunkt: LocalDateTime? = null,
    val perioder: List<ResultatBarnebidragsberegningPeriodeDto>,
    val grunnlagFraVedtak: List<GrunnlagFraVedtak> = emptyList(),
) {
    val endeligVedtak get() = !omgjøringsvedtak && !delvedtak
}

data class ResultatBarnebidragsberegningPeriodeDto(
    val periode: ÅrMånedsperiode,
    val ugyldigBeregning: UgyldigResultatPeriode? = null,
    val aldersjusteringDetaljer: AldersjusteringDetaljerGrunnlag? = null,
    val underholdskostnad: BigDecimal = BigDecimal(0),
    val bpsAndelU: BigDecimal = BigDecimal(0),
    val bpsAndelBeløp: BigDecimal = BigDecimal(0),
    val samværsfradrag: BigDecimal = BigDecimal(0),
    val beregnetBidrag: BigDecimal = BigDecimal(0),
    val faktiskBidrag: BigDecimal = BigDecimal(0),
    val resultatKode: Resultatkode?,
    val erDirekteAvslag: Boolean = false,
    val erOpphør: Boolean = false,
    val endeligVedtak: Boolean = false,
    val erBeregnetAvslag: Boolean = false,
    val erEndringUnderGrense: Boolean = false,
    val beregningsdetaljer: BidragPeriodeBeregningsdetaljer? = null,
    val vedtakstype: Vedtakstype,
    val klageOmgjøringDetaljer: KlageOmgjøringDetaljer? = null,
    val resultatFraVedtak: ResultatFraVedtakGrunnlag? = null,
) {
    val delvedtakstypeVisningsnavn
        get(): String {
            if (klageOmgjøringDetaljer == null) return ""
            return when {
                klageOmgjøringDetaljer.omgjøringsvedtak && vedtakstype == Vedtakstype.KLAGE -> "Klagevedtak"
                klageOmgjøringDetaljer.omgjøringsvedtak && !vedtakstype.erIndeksEllerAldersjustering -> "Omgjøringsvedtak"
                (klageOmgjøringDetaljer.resultatFraVedtak == null || resultatFraVedtak?.beregnet == true) &&
                    vedtakstype == Vedtakstype.ALDERSJUSTERING -> "Beregnet aldersjustering"
                (klageOmgjøringDetaljer.resultatFraVedtak == null || resultatFraVedtak?.beregnet == true) &&
                    vedtakstype == Vedtakstype.INDEKSREGULERING -> "Beregnet indeksregulering"
                klageOmgjøringDetaljer.beregnTilDato != null && periode.fom >= klageOmgjøringDetaljer.beregnTilDato
                -> {
                    val prefiks =
                        if (vedtakstype == Vedtakstype.ALDERSJUSTERING) {
                            "Aldersjustering"
                        } else if (vedtakstype == Vedtakstype.INDEKSREGULERING) {
                            "Indeksregulering"
                        } else {
                            "Vedtak"
                        }
                    "$prefiks (${klageOmgjøringDetaljer.resultatFraVedtakVedtakstidspunkt?.toLocalDate().tilVisningsnavn()})"
                }
                else -> "Gjenopprettet beløpshistorikk"
            }
        }

    @Suppress("unused")
    val resultatkodeVisningsnavn get() =
        when {
            erOpphør ->
                if (beregningsdetaljer?.sluttberegning?.ikkeOmsorgForBarnet == true ||
                    beregningsdetaljer?.sluttberegning?.barnetErSelvforsørget == true
                ) {
                    beregningsdetaljer.sluttberegning.resultatVisningsnavn!!.intern
                } else {
                    "Opphør"
                }
            vedtakstype == Vedtakstype.INNKREVING -> "Innkreving"

            vedtakstype == Vedtakstype.ALDERSJUSTERING ->
                when {
                    klageOmgjøringDetaljer?.delAvVedtaket == false -> "Manuell aldersjustering (ikke del av vedtaket)"
                    endeligVedtak -> "Aldersjustering"
                    aldersjusteringDetaljer?.aldersjustert == false -> aldersjusteringDetaljer.begrunnelserVisningsnavn
                    else ->
                        beregningsdetaljer?.sluttberegningAldersjustering?.resultatVisningsnavn?.intern
                            ?: lastVisningsnavnFraFil("sluttberegningBarnebidrag.yaml")["kostnadsberegnet"]?.intern
                }

            vedtakstype == Vedtakstype.INDEKSREGULERING -> "Indeksregulering"

            resultatKode?.erDirekteAvslag() == true ||
                resultatKode == Resultatkode.INGEN_ENDRING_UNDER_GRENSE ||
                resultatKode == Resultatkode.INNVILGET_VEDTAK -> resultatKode.visningsnavnIntern(vedtakstype)

            ugyldigBeregning != null ->
                when (ugyldigBeregning.type) {
                    UgyldigBeregningDto.UgyldigBeregningType.BEGRENSET_REVURDERING_LIK_ELLER_LAVERE_ENN_LØPENDE_BIDRAG,
                    -> "Lavere enn løpende bidrag"
                    UgyldigBeregningDto.UgyldigBeregningType.BEGRENSET_REVURDERING_UTEN_LØPENDE_FORSKUDD -> "Ingen løpende forskudd"
                }

            else -> beregningsdetaljer?.sluttberegning?.resultatVisningsnavn?.intern
        }
}

data class KlageOmgjøringDetaljer(
    val resultatFraVedtak: Int? = null,
    val resultatFraVedtakVedtakstidspunkt: LocalDateTime? = null,
    val beregnTilDato: YearMonth? = null,
    val omgjøringsvedtak: Boolean = false,
    val manuellAldersjustering: Boolean = false,
    val delAvVedtaket: Boolean = true,
    val kanOpprette35c: Boolean = false,
    val skalOpprette35c: Boolean = false,
)

data class BidragPeriodeBeregningsdetaljer(
    val bpHarEvne: Boolean = false,
    val antallBarnIHusstanden: Double? = null,
    val forskuddssats: BigDecimal = BigDecimal.ZERO,
    val barnetilleggBM: DelberegningBarnetilleggDto = DelberegningBarnetilleggDto(),
    val barnetilleggBP: DelberegningBarnetilleggDto = DelberegningBarnetilleggDto(),
    val voksenIHusstanden: Boolean? = null,
    val enesteVoksenIHusstandenErEgetBarn: Boolean? = null,
    val bpsAndel: DelberegningBidragspliktigesAndel? = null,
    val inntekter: ResultatBeregningInntekterDto? = null,
    val delberegningBidragsevne: DelberegningBidragsevneDto? = null,
    val samværsfradrag: BeregningsdetaljerSamværsfradrag? = null,
    val endringUnderGrense: DelberegningEndringSjekkGrensePeriode? = null,
    val sluttberegning: SluttberegningBarnebidrag? = null,
    val sluttberegningAldersjustering: SluttberegningBarnebidragAldersjustering? = null,
    val delberegningUnderholdskostnad: DelberegningUnderholdskostnad? = null,
    val indeksreguleringDetaljer: IndeksreguleringDetaljer? = null,
    val delberegningBidragspliktigesBeregnedeTotalBidrag: DelberegningBidragspliktigesBeregnedeTotalbidragDto? = null,
) {
    data class BeregningsdetaljerSamværsfradrag(
        val samværsfradrag: BigDecimal,
        val samværsklasse: Samværsklasse,
        val gjennomsnittligSamværPerMåned: BigDecimal,
    )

    val deltBosted get() =
        sluttberegning?.bidragJustertForDeltBosted == true ||
            sluttberegning?.resultat == SluttberegningBarnebidrag::bidragJustertForDeltBosted.name
}

data class IndeksreguleringDetaljer(
    val sluttberegning: SluttberegningIndeksregulering?,
    val faktor: BigDecimal,
)

fun List<GrunnlagDto>.finnSluttberegningIReferanser(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    find {
        listOf(
            Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
            Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
            Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG,
            Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG_ALDERSJUSTERING,
        ).contains(it.type) &&
            grunnlagsreferanseListe.contains(it.referanse)
    }
