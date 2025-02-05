package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.behandling.transformers.finnSluttberegningIReferanser
import no.nav.bidrag.beregn.core.exception.BegrensetRevurderingLikEllerLavereEnnLøpendeBidragException
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erDirekteAvslag
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavnIntern
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val YearMonth.formatterDatoFom get() = this.atDay(1).format(DateTimeFormatter.ofPattern("MM.YYYY"))
val YearMonth.formatterDatoTom get() = this.atEndOfMonth().format(DateTimeFormatter.ofPattern("MM.YYYY"))
val ÅrMånedsperiode.periodeString get() = "${fom.formatterDatoFom} - ${til?.formatterDatoTom ?: ""}"

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
    if (perioderUtenForskudd.isNotEmpty()) {
        return UgyldigBeregningDto(
            tittel = "Begrenset revurdering",
            begrunnelse =
                if (perioderUtenForskudd.size > 1) {
                    "Perioder ${perioderUtenForskudd.joinToString {it.periode.periodeString}} er uten løpende forskudd"
                } else {
                    "Periode ${perioderUtenForskudd.first().periode.periodeString} er uten løpende forskudd"
                },
            perioder = perioderUtenForskudd.map { it.periode },
        )
    }
    return UgyldigBeregningDto(
        tittel = "Begrenset revurdering",
        perioder = this.periodeListe,
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
    val resultat: BeregnetBarnebidragResultat,
    val avslaskode: Resultatkode? = null,
    val ugyldigBeregning: UgyldigBeregningDto? = null,
)

data class UgyldigBeregningDto(
    val tittel: String,
    val begrunnelse: String,
    val perioder: List<ÅrMånedsperiode> = emptyList(),
)

data class ResultatBidragberegningDto(
    val resultatBarn: List<ResultatBidragsberegningBarnDto> = emptyList(),
)

data class ResultatBidragsberegningBarnDto(
    val barn: ResultatRolle,
    val ugyldigBeregning: UgyldigBeregningDto? = null,
    val perioder: List<ResultatBarnebidragsberegningPeriodeDto>,
)

data class ResultatBarnebidragsberegningPeriodeDto(
    val periode: ÅrMånedsperiode,
    val underholdskostnad: BigDecimal,
    val bpsAndelU: BigDecimal,
    val bpsAndelBeløp: BigDecimal,
    val samværsfradrag: BigDecimal,
    val beregnetBidrag: BigDecimal,
    val faktiskBidrag: BigDecimal,
    val resultatKode: Resultatkode?,
    val erDirekteAvslag: Boolean = false,
    val beregningsdetaljer: BidragPeriodeBeregningsdetaljer? = null,
) {
    @Suppress("unused")
    val resultatkodeVisningsnavn get() =
        if (resultatKode?.erDirekteAvslag() == true) {
            resultatKode.visningsnavnIntern()
        } else {
            beregningsdetaljer
                ?.sluttberegning
                ?.resultatVisningsnavn
                ?.intern
        }
}

data class BidragPeriodeBeregningsdetaljer(
    val bpHarEvne: Boolean,
    val antallBarnIHusstanden: Double? = null,
    val forskuddssats: BigDecimal,
    val barnetilleggBM: DelberegningBarnetilleggDto,
    val barnetilleggBP: DelberegningBarnetilleggDto,
    val voksenIHusstanden: Boolean? = null,
    val enesteVoksenIHusstandenErEgetBarn: Boolean? = null,
    val bpsAndel: DelberegningBidragspliktigesAndel? = null,
    val inntekter: ResultatBeregningInntekterDto? = null,
    val delberegningBidragsevne: DelberegningBidragsevneDto? = null,
    val samværsfradrag: BeregningsdetaljerSamværsfradrag? = null,
    val sluttberegning: SluttberegningBarnebidrag? = null,
    val delberegningUnderholdskostnad: DelberegningUnderholdskostnad? = null,
    val delberegningBidragspliktigesBeregnedeTotalBidrag: DelberegningBidragspliktigesBeregnedeTotalbidragDto? = null,
) {
    data class BeregningsdetaljerSamværsfradrag(
        val samværsfradrag: BigDecimal,
        val samværsklasse: Samværsklasse,
        val gjennomsnittligSamværPerMåned: BigDecimal,
    )

    val deltBosted get() =
        sluttberegning!!.bidragJustertForDeltBosted ||
            sluttberegning.resultat == SluttberegningBarnebidrag::bidragJustertForDeltBosted.name
}
