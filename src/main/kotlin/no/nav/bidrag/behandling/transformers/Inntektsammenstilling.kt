package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.Ainntektspost
import no.nav.bidrag.transport.behandling.inntekt.request.Kontantstøtte
import no.nav.bidrag.transport.behandling.inntekt.request.SkattegrunnlagForLigningsår
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygdOgSmåbarnstillegg
import kotlin.reflect.full.memberProperties


fun List<AinntektspostDto>.tilAinntektsposter() = this.map { it.tilAinntektspost() }
private fun AinntektspostDto.tilAinntektspost() = with(::Ainntektspost) {
    val propertiesByName = AinntektspostDto::class.memberProperties.associateBy { it.name }
    callBy(parameters.associateWith { parameter -> propertiesByName[parameter.name]?.get(this@tilAinntektspost) })
}

fun List<KontantstøtteGrunnlagDto>.tilKontantstøtte() = this.map { it.tilKontantstøtte() }

private fun KontantstøtteGrunnlagDto.tilKontantstøtte() = with(::Kontantstøtte) {
    val propertiesByName = KontantstøtteGrunnlagDto::class.memberProperties.associateBy { it.name }
    callBy(parameters.associateWith { parameter -> propertiesByName[parameter.name]?.get(this@tilKontantstøtte) })
}

fun List<SkattegrunnlagGrunnlagDto>.tilSkattegrunnlagForLigningsår(): List<SkattegrunnlagForLigningsår> = this.map {
    SkattegrunnlagForLigningsår(
        ligningsår = it.periodeFra.year,
        skattegrunnlagsposter = it.skattegrunnlagspostListe
    )
}

fun List<UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto>.tilUtvidetBarnetrygdOgSmåbarnstillegg(): List<UtvidetBarnetrygdOgSmåbarnstillegg> =
    this.map {
        UtvidetBarnetrygdOgSmåbarnstillegg(
            type = it.type,
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
            beløp = it.beløp
        )
    }