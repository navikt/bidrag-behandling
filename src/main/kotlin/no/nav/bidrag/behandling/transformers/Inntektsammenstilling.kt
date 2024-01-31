package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.Ainntektspost
import no.nav.bidrag.transport.behandling.inntekt.request.Kontantstøtte
import no.nav.bidrag.transport.behandling.inntekt.request.SkattegrunnlagForLigningsår
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygdOgSmåbarnstillegg
import no.nav.bidrag.transport.behandling.inntekt.response.TransformerInntekterResponse
import java.math.BigDecimal

fun List<AinntektspostDto>.tilAinntektsposter() =
    this.map {
        Ainntektspost(
            beløp = it.belop,
            beskrivelse = it.beskrivelse,
            opptjeningsperiodeFra = it.opptjeningsperiodeFra,
            opptjeningsperiodeTil = it.opptjeningsperiodeTil,
            utbetalingsperiode = it.utbetalingsperiode,
        )
    }

fun List<KontantstøtteGrunnlagDto>.tilKontantstøtte() =
    this.map {
        Kontantstøtte(
            barnPersonId = it.barnPersonId,
            beløp = BigDecimal(it.beløp),
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
        )
    }

fun List<SkattegrunnlagGrunnlagDto>.tilSkattegrunnlagForLigningsår(): List<SkattegrunnlagForLigningsår> =
    this.map {
        SkattegrunnlagForLigningsår(
            ligningsår = it.periodeFra.year,
            skattegrunnlagsposter = it.skattegrunnlagspostListe,
        )
    }

fun List<UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto>.tilUtvidetBarnetrygdOgSmåbarnstillegg(): List<UtvidetBarnetrygdOgSmåbarnstillegg> =
    this.map {
        UtvidetBarnetrygdOgSmåbarnstillegg(
            type = it.type,
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
            beløp = it.beløp,
        )
    }

fun TransformerInntekterResponse.tilSummerteMånedsOgÅrsinntekter() =
    SummerteMånedsOgÅrsinntekter(
        versjon = versjon,
        summerteMånedsinntekter = summertMånedsinntektListe,
        summerteÅrsinntekter = summertÅrsinntektListe,
    )
