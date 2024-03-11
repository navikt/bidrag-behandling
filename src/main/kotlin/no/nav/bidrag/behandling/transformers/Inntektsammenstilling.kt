package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.Ainntektspost
import no.nav.bidrag.transport.behandling.inntekt.request.Barnetillegg
import no.nav.bidrag.transport.behandling.inntekt.request.Kontantstøtte
import no.nav.bidrag.transport.behandling.inntekt.request.SkattegrunnlagForLigningsår
import no.nav.bidrag.transport.behandling.inntekt.request.Småbarnstillegg
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygd
import java.math.BigDecimal
import java.time.LocalDate

fun List<AinntektspostDto>.tilAinntektsposter() =
    this.map {
        Ainntektspost(
            beløp = it.beløp,
            beskrivelse = it.beskrivelse,
            opptjeningsperiodeFra = it.opptjeningsperiodeFra,
            opptjeningsperiodeTil = it.opptjeningsperiodeTil,
            utbetalingsperiode = it.utbetalingsperiode,
        )
    }

fun List<BarnetilleggGrunnlagDto>.tilBarnetillegg() =
    this.map {
        Barnetillegg(
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
            beløp = it.beløpBrutto,
            barnPersonId = it.barnPersonId,
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

fun List<UtvidetBarnetrygdGrunnlagDto>.tilUtvidetBarnetrygd(): List<UtvidetBarnetrygd> =
    this.map {
        UtvidetBarnetrygd(
            beløp = it.beløp,
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
        )
    }

fun List<SmåbarnstilleggGrunnlagDto>.tilSmåbarnstillegg(): List<Småbarnstillegg> =
    this.map {
        Småbarnstillegg(
            beløp = it.beløp,
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
        )
    }

data class TransformerInntekterRequestBuilder(
    val ainntektHentetDato: LocalDate? = LocalDate.now(),
    val ainntektsposter: List<Ainntektspost>? = emptyList(),
    val barnetillegg: List<Barnetillegg>? = emptyList(),
    val kontantstøtte: List<Kontantstøtte>? = emptyList(),
    val skattegrunnlag: List<SkattegrunnlagForLigningsår>? = emptyList(),
    val småbarnstillegg: List<Småbarnstillegg>? = emptyList(),
    val utvidetBarnetrygd: List<UtvidetBarnetrygd>? = emptyList(),
) {
    fun bygge(): TransformerInntekterRequest {
        return TransformerInntekterRequest(
            ainntektHentetDato!!,
            ainntektsposter!!,
            skattegrunnlag!!,
            kontantstøtte!!,
            utvidetBarnetrygd!!,
            småbarnstillegg!!,
            barnetillegg!!,
        )
    }
}
