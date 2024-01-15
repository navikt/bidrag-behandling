package no.nav.bidrag.behandling.database.opplysninger

import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstotteDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdOgSmaabarnstilleggDto

data class InntektGrunnlag(
    val ainntektListe: List<AinntektDto> = emptyList(),
    val skattegrunnlagListe: List<SkattegrunnlagDto> = emptyList(),
    val ubstListe: List<UtvidetBarnetrygdOgSmaabarnstilleggDto> = emptyList(),
    val barnetilleggListe: List<BarnetilleggDto> = emptyList(),
    val kontantstotteListe: List<KontantstotteDto> = emptyList(),
    val barnetilsynListe: List<BarnetilsynDto> = emptyList(),
)
