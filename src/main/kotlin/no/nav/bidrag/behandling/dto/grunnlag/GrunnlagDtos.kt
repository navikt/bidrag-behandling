@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.grunnlag

import no.nav.bidrag.behandling.dto.v1.beregning.BeregnetBidragBarnDto
import no.nav.bidrag.beregn.barnebidrag.bo.LøpendeBidragPeriodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragPeriode

data class LøpendeBidragGrunnlagForholdsmessigFordeling(
    val gjelderBarnIdent: String,
    val løpendeBidragPerioder: List<BeregnetBidragBarnDto>,
)
