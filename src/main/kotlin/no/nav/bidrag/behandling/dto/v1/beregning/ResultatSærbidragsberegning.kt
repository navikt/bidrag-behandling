@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import java.math.BigDecimal

data class ResultatSærbidragsberegningDto(
    val periode: ÅrMånedsperiode,
    val bpsAndel: DelberegningBidragspliktigesAndel? = null,
    val beregning: UtgiftBeregningDto? = null,
    val inntekter: ResultatSærbidragsberegningInntekterDto? = null,
    val utgiftsposter: List<UtgiftspostDto> = emptyList(),
    val delberegningUtgift: DelberegningUtgift? = null,
    val delberegningBidragsevne: DelberegningBidragsevne? = null,
    val maksGodkjentBeløp: BigDecimal? = null,
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

data class ResultatSærbidragsberegningInntekterDto(
    val inntektBM: BigDecimal? = null,
    val inntektBP: BigDecimal? = null,
    val inntektBarn: BigDecimal? = null,
)
