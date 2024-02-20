package no.nav.bidrag.behandling.database.grunnlag

import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto

data class GrunnlagInntekt(
    val ainntekt: List<AinntektGrunnlagDto> = emptyList(),
    val skattegrunnlag: List<SkattegrunnlagGrunnlagDto> = emptyList(),
)

fun HentGrunnlagDto.tilGrunnlagInntekt() =
    GrunnlagInntekt(
        ainntekt = this.ainntektListe,
        skattegrunnlag = this.skattegrunnlagListe,
    )
