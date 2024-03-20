package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

private val inntekstrapporteringerSomKreverGjelderBarn =
    listOf(Inntektsrapportering.BARNETILLEGG, Inntektsrapportering.KONTANTSTØTTE, Inntektsrapportering.BARNETILSYN)
private val inntekstrapporteringerSomKreverInnteksttype = listOf(Inntektsrapportering.BARNETILLEGG)

fun OppdatereInntekterRequestV2.valider() {
    val feilListe = mutableListOf<String>()
    oppdatereManuelleInntekter.forEach { oppdaterInntekt ->
        if (inntekstrapporteringerSomKreverGjelderBarn.contains(oppdaterInntekt.type)) oppdaterInntekt.validerHarGjelderBarn(feilListe)
        if (inntekstrapporteringerSomKreverInnteksttype.contains(oppdaterInntekt.type)) oppdaterInntekt.validerHarInnteksttype(feilListe)
    }

    if (feilListe.isNotEmpty()) {
        throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Ugyldig data ved oppdatering av inntekter: ${feilListe.joinToString(", ")}")
    }
}

fun OppdatereManuellInntekt.validerHarGjelderBarn(feilListe: MutableList<String>) {
    if (gjelderBarn == null || gjelderBarn.verdi.isEmpty()) {
        feilListe.add(
            "$type må ha en gyldig barnident",
        )
    }
}

fun OppdatereManuellInntekt.validerHarInnteksttype(feilListe: MutableList<String>) {
    if (inntektstype == null) {
        feilListe.add(
            "Barnetillegg må ha en gyldig inntektstype",
        )
    }
}
