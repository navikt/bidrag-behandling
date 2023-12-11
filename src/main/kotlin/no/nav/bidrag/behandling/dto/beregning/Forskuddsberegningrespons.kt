package no.nav.bidrag.behandling.dto.beregning

import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat

data class Forskuddsberegningrespons(
    val resultat: List<BeregnetForskuddResultat>?,
)
