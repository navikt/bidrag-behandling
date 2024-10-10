package no.nav.bidrag.behandling.transformers.beregning

import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak

data class EvnevurderingBeregningResultat(
    val beregnetBeløpListe: BidragBeregningResponsDto,
    val løpendeBidragsaker: List<LøpendeBidragssak>,
)
