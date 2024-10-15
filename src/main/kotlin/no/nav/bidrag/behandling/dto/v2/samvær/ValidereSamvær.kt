package no.nav.bidrag.behandling.dto.v2.samvær

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

data class SamværValideringsfeilDto(
    val samværId: String,
    val gjelderBarn: String,
    val manglerBegrunnelse: Boolean,
    val ingenLøpendeSamvær: Boolean,
    val manglerSamvær: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || ingenLøpendeSamvær || manglerSamvær
}

fun OppdaterSamværDto.valider() {
    val feilliste = mutableListOf<String>()

    periode?.valider()?.also { feilliste.addAll(it) }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av samvær: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdaterSamværsperiodeDto.valider(): MutableList<String> {
    val feilliste = mutableListOf<String>()

    return feilliste
}
