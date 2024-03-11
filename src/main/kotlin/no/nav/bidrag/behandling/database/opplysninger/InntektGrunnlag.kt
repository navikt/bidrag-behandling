package no.nav.bidrag.behandling.database.opplysninger

import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto

data class InntektGrunnlag(
    val ainntektListe: List<AinntektGrunnlagDto> = emptyList(),
    val skattegrunnlagListe: List<SkattegrunnlagGrunnlagDto> = emptyList(),
    val utvidetBarnetrygdListe: List<UtvidetBarnetrygdGrunnlagDto> = emptyList(),
    val småbarnstilleggListe: List<SmåbarnstilleggGrunnlagDto> = emptyList(),
    val barnetilleggListe: List<BarnetilleggGrunnlagDto> = emptyList(),
    val kontantstøtteListe: List<KontantstøtteGrunnlagDto> = emptyList(),
    val barnetilsynListe: List<BarnetilsynGrunnlagDto> = emptyList(),
)
