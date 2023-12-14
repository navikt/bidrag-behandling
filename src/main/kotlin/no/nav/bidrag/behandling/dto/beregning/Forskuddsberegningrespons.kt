package no.nav.bidrag.behandling.dto.beregning

import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import java.time.LocalDate

data class Forskuddsberegningrespons(
    val resultatBarn: List<ForskuddberegningResultatBarn> = emptyList(),
)

data class ForskuddberegningResultatBarn(
    val barn: ResultatRolle,
    val resultat: BeregnetForskuddResultat,
)

data class ResultatRolle(
    val ident: Personident?,
    val navn: String,
    val fødselsdato: LocalDate,
)
