package no.nav.bidrag.behandling.transformers.beregning

import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto

data class EvnevurderingBeregningResultat(
    val beregnetBeløpListe: BidragBeregningResponsDto,
    val løpendeBidragsaker: List<LøpendeBidragssak>,
)
