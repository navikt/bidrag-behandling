package no.nav.bidrag.behandling.transformers.grunnlag

import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun innhentetGrunnlagHarFlereRelatertePersonMedSammeId(): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Innhentet grunnlag for husstandsmedlemmer har flere relaterte personer med samme personId",
    )

fun inntektManglerSøknadsbarn(inntektsrapportering: Inntektsrapportering): Nothing =
    grunnlagByggingFeilet(
        "Mangler søknadsbarn for inntektsrapportering $inntektsrapportering",
    )

fun manglerRolleIGrunnlag(
    rolletype: Rolletype,
    behandlingId: Long? = null,
    fødselsnummer: String? = null,
): Nothing =
    grunnlagByggingFeilet(
        "Mangler rolle $rolletype" +
            "${behandlingId?.let { " i behandling $it" } ?: ""}${fødselsnummer?.let { " med fødselsnummer $it" } ?: ""}",
    )

fun grunnlagByggingFeilet(message: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Kunne ikke bygge grunnlag: $message",
    )
