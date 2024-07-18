@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndelSærbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import java.math.BigDecimal

data class ResultatSærbidragsberegningDto(
    val periode: ÅrMånedsperiode,
    val bpsAndel: DelberegningBidragspliktigesAndelSærbidrag? = null,
    val beregning: UtgiftBeregningDto? = null,
    val inntekter: ResultatSærbidragsberegningInntekterDto? = null,
    val delberegningUtgift: DelberegningUtgift? = null,
    val resultat: BigDecimal,
    val resultatKode: Resultatkode,
    val antallBarnIHusstanden: Double? = null,
    val voksenIHusstanden: Boolean? = null,
    val erDirekteAvslag: Boolean = false,
) {
    val beløpSomInnkreves: BigDecimal get() = maxOf(resultat - (beregning?.totalBeløpBetaltAvBp ?: BigDecimal.ZERO), BigDecimal.ZERO)
}

data class ResultatSærbidragsberegningInntekterDto(
    val inntektBM: BigDecimal? = null,
    val inntektBP: BigDecimal? = null,
    val inntektBarn: BigDecimal? = null,
)
