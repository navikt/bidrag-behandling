package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import java.time.LocalDate

data class ResultatForskuddsberegning(
    val resultatBarn: List<no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn> = emptyList(),
)

data class ResultatForskuddsberegningBarn(
    val barn: no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle,
    val resultat: BeregnetForskuddResultat,
)

data class ResultatRolle(
    val ident: Personident?,
    val navn: String,
    val fødselsdato: LocalDate,
)
