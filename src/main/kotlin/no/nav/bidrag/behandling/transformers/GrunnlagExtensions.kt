package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt

val summertAinntektstyper =
    setOf(
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
    )
val inntektstyperYtelser =
    setOf(
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.BARNETILSYN,
        Inntektsrapportering.UTVIDET_BARNETRYGD,
        Inntektsrapportering.SMÅBARNSTILLEGG,
    )

fun List<SummertÅrsinntekt>.filtrerAinntekt() = filter { summertAinntektstyper.contains(it.inntektRapportering) }

fun List<SummertÅrsinntekt>.filtrerSkattegrunnlag() = filter { !summertAinntektstyper.contains(it.inntektRapportering) }

fun List<SummertÅrsinntekt>.filtrerSkattbareInntekter() = filter { !inntektstyperYtelser.contains(it.inntektRapportering) }
