package no.nav.bidrag.behandling.dto.beregning

import no.nav.bidrag.behandling.dto.behandling.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.felles.Grunnlag

data class ForskuddBeregningRespons(
    val resultat: List<ForskuddBeregningPerBarn>?,
    val feil: List<String>?,
)

data class ForskuddBeregningPerBarn(
    val referanseTilBarn: String?,
    val beregnetForskuddPeriodeListe: List<ResultatPeriode>,
    val grunnlagListe: List<Grunnlag>? = null,
)
