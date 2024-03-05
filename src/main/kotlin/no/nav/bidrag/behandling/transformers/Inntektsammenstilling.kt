package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettAinntektGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettKontantstøtteGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSkattegrunnlagGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSmåbarnstilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettUtvidetbarnetrygGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
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

fun List<AinntektspostDto>.tilAinntektsposter(rolle: Rolle) =
    this.map {
        Ainntektspost(
            beløp = it.beløp,
            beskrivelse = it.beskrivelse,
            opptjeningsperiodeFra = it.opptjeningsperiodeFra,
            opptjeningsperiodeTil = it.opptjeningsperiodeTil,
            utbetalingsperiode = it.utbetalingsperiode,
            referanse = opprettAinntektGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
        )
    }

fun List<KontantstøtteGrunnlagDto>.tilKontantstøtte(rolle: Rolle) =
    this.map {
        Kontantstøtte(
            barnPersonId = it.barnPersonId,
            beløp = BigDecimal(it.beløp),
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
            referanse = opprettKontantstøtteGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
        )
    }

fun List<SkattegrunnlagGrunnlagDto>.tilSkattegrunnlagForLigningsår(rolle: Rolle): List<SkattegrunnlagForLigningsår> =
    this.map {
        SkattegrunnlagForLigningsår(
            ligningsår = it.periodeFra.year,
            skattegrunnlagsposter = it.skattegrunnlagspostListe,
            referanse =
                opprettSkattegrunnlagGrunnlagsreferanse(
                    rolle.tilGrunnlagPerson().referanse,
                    it.periodeFra.year,
                ),
        )
    }

fun List<UtvidetBarnetrygdGrunnlagDto>.tilUtvidetBarnetrygd(rolle: Rolle): List<UtvidetBarnetrygd> =
    this.map {
        UtvidetBarnetrygd(
            beløp = it.beløp,
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
            referanse = opprettUtvidetbarnetrygGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
        )
    }

fun List<SmåbarnstilleggGrunnlagDto>.tilSmåbarnstillegg(rolle: Rolle): List<Småbarnstillegg> =
    this.map {
        Småbarnstillegg(
            beløp = it.beløp,
            periodeFra = it.periodeFra,
            periodeTil = it.periodeTil,
            referanse = opprettSmåbarnstilleggGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
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
