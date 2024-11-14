package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erDirekteAvslag
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavnIntern
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import java.math.BigDecimal
import java.math.RoundingMode

data class ResultatBidragsberegningBarn(
    val barn: ResultatRolle,
    val resultat: BeregnetBarnebidragResultat,
)

data class ResultatBidragberegningDto(
    val resultatBarn: List<ResultatBidragsberegningBarnDto> = emptyList(),
)

data class ResultatBidragsberegningBarnDto(
    val barn: ResultatRolle,
    val perioder: List<ResultatBarnebidragsberegningPeriodeDto>,
)

data class ResultatBarnebidragsberegningPeriodeDto(
    val periode: ÅrMånedsperiode,
    val bpsAndelU: BigDecimal,
    val samværsfradrag: BigDecimal,
    val beregnetBidrag: BigDecimal,
    val faktiskBidrag: BigDecimal,
    val resultatKode: Resultatkode,
    val erDirekteAvslag: Boolean = false,
    val beregningsdetaljer: BidragPeriodeBeregningsdetaljer? = null,
) {
    @Suppress("unused")
    val resultatkodeVisningsnavn get() =
        if (resultatKode.erDirekteAvslag()) {
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

    val underholdskostnadMinusBMsNettoBarnetillegg get() =
        maxOf(
            delberegningUnderholdskostnad!!.underholdskostnad - sluttberegning!!.nettoBarnetilleggBM,
            BigDecimal.ZERO,
        )
    val beløpEtterVurderingAv25ProsentInntektOgEvne get(): BigDecimal {
        if (sluttberegning!!.justertNedTil25ProsentAvInntekt) return delberegningBidragsevne?.sumInntekt25Prosent ?: BigDecimal.ZERO
        if (sluttberegning.justertNedTilEvne) return delberegningBidragsevne?.bidragsevne ?: BigDecimal.ZERO
        return bpsAndel?.andelBeløp ?: BigDecimal.ZERO
    }
    val beløpEtterVurderingAvBMsBarnetillegg get(): BigDecimal {
        if (sluttberegning!!.justertForNettoBarnetilleggBM) return underholdskostnadMinusBMsNettoBarnetillegg
        return beløpEtterVurderingAv25ProsentInntektOgEvne
    }
    val beløpSamværsfradragTrekkesFra get(): BigDecimal {
        if (sluttberegning!!.justertForNettoBarnetilleggBP) return sluttberegning.nettoBarnetilleggBP
        return beløpEtterVurderingAvBMsBarnetillegg
    }

    val beløpEtterFratrekkDeltBosted get() =
        if (deltBosted) {
            bpsAndel!!.andelBeløp -
                delberegningUnderholdskostnad!!.underholdskostnad.divide(BigDecimal(2), RoundingMode.HALF_UP)
        } else {
            bpsAndel!!.andelBeløp
        }

    val deltBosted get() =
        listOf(
            Resultatkode.DELT_BOSTED,
            Resultatkode.BIDRAG_IKKE_BEREGNET_DELT_BOSTED,
        ).contains(sluttberegning!!.resultatKode)
}
