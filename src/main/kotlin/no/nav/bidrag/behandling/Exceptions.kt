package no.nav.bidrag.behandling

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun behandlingNotFoundException(behandlingId: Long): Nothing =
    throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Fant ikke behandling med id $behandlingId")

class KunneIkkeLeseMeldingFraHendelse(melding: String?, throwable: Throwable) : RuntimeException(melding, throwable)

fun fantIkkeSak(saksnummer: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Sak med saksnummer $saksnummer finnes ikke",
    )

fun fantIkkeFødselsdatoTilSøknadsbarn(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Fant ikke fødselsdato til søknadsbarn i behandling med id $behandlingsid",
    )

fun valideringAvBehandlingFeilet(valideringsfeil: List<String>): Nothing =
    throw HttpClientErrorException(HttpStatus.BAD_REQUEST, valideringsfeil.toString())
