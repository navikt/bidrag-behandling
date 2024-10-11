@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumLøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class ResultatSærbidragsberegningDto(
    val periode: ÅrMånedsperiode,
    val bpsAndel: DelberegningBidragspliktigesAndel? = null,
    val beregning: UtgiftBeregningDto? = null,
    val inntekter: ResultatSærbidragsberegningInntekterDto? = null,
    val utgiftsposter: List<UtgiftspostDto> = emptyList(),
    val delberegningUtgift: DelberegningUtgift? = null,
    val delberegningBidragsevne: DelberegningBidragsevneDto? = null,
    val delberegningSumLøpendeBidrag: DelberegningSumLøpendeBidrag? = null,
    val maksGodkjentBeløp: BigDecimal? = null,
    val forskuddssats: BigDecimal? = null,
    val resultat: BigDecimal,
    val resultatKode: Resultatkode,
    val antallBarnIHusstanden: Double? = null,
    val voksenIHusstanden: Boolean? = null,
    val enesteVoksenIHusstandenErEgetBarn: Boolean? = null,
    val erDirekteAvslag: Boolean = false,
    val bpHarEvne: Boolean = resultatKode != Resultatkode.SÆRBIDRAG_IKKE_FULL_BIDRAGSEVNE,
) {
    val beløpSomInnkreves: BigDecimal get() = maxOf(resultat - (beregning?.totalBeløpBetaltAvBp ?: BigDecimal.ZERO), BigDecimal.ZERO)
}

data class DelberegningBidragsevneDto(
    val bidragsevne: BigDecimal,
    val skatt: Skatt,
    val underholdEgneBarnIHusstand: UnderholdEgneBarnIHusstand,
    val utgifter: BidragsevneUtgifterBolig,
) {
    data class UnderholdEgneBarnIHusstand(
        val resultat: BigDecimal,
        val sjablon: BigDecimal,
        val antallBarnIHusstanden: Double,
    )

    data class Skatt(
        val sumSkatt: BigDecimal,
        val skattAlminneligInntekt: BigDecimal,
        val trinnskatt: BigDecimal,
        val trygdeavgift: BigDecimal,
    ) {
        val skattResultat get() = sumSkatt.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
        val trinnskattResultat get() = trinnskatt.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
        val skattAlminneligInntektResultat get() = skattAlminneligInntekt.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
        val trygdeavgiftResultat get() = trygdeavgift.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
    }

    data class BidragsevneUtgifterBolig(
        val borMedAndreVoksne: Boolean,
        val boutgiftBeløp: BigDecimal,
        val underholdBeløp: BigDecimal,
    )
}

data class ResultatSærbidragsberegningInntekterDto(
    val inntektBM: BigDecimal? = null,
    val inntektBP: BigDecimal? = null,
    val inntektBarn: BigDecimal? = null,
    val barnEndeligInntekt: BigDecimal? = null,
) {
    val totalEndeligInntekt get() =
        (inntektBM ?: BigDecimal.ZERO) + (inntektBP ?: BigDecimal.ZERO) +
            (barnEndeligInntekt ?: BigDecimal.ZERO)
    val inntektBPMånedlig get() = inntektBP?.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
    val inntektBMMånedlig get() = inntektBM?.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
    val inntektBarnMånedlig get() = inntektBarn?.divide(BigDecimal(12), MathContext(10, RoundingMode.HALF_UP))
}
