package no.nav.bidrag.behandling.dto.beregning

import no.nav.bidrag.behandling.consumer.Grunnlag
import no.nav.bidrag.behandling.dto.behandling.ResultatPeriode

data class ForskuddBeregningRespons(
    val resultat: List<ForskuddBeregningPerBarn>?,
    val feil: List<String>?,
)

data class ForskuddBeregningPerBarn(
    val ident: String,
    val beregnetForskuddPeriodeListe: List<ResultatPeriode>,
    val grunnlagListe: List<Grunnlag>? = null,
)
