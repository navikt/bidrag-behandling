package no.nav.bidrag.behandling

import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun behandlingNotFoundException(behandlingId: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.NOT_FOUND,
        "Fant ikke behandling med id $behandlingId",
    )

class KunneIkkeLeseMeldingFraHendelse(melding: String?, throwable: Throwable) :
    RuntimeException(melding, throwable)

class BeregningAvResultatForBehandlingFeilet(val feilmeldinger: List<String>) :
    HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Beregning av resultat feilet med: ${feilmeldinger.joinToString(", ")}",
    )

fun fantIkkeSak(saksnummer: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Sak med saksnummer $saksnummer finnes ikke",
    )

fun rolleManglerIdent(
    rolletype: Rolletype,
    behandlingId: Long,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Manger personident for rolle $rolletype i behandling $behandlingId",
    )

fun manglerBosstatus(behandlingId: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Mangler bosstatus for søknadsbarn i behandling $behandlingId",
    )

fun manglerRolle(
    rolletype: Rolletype,
    behandlingId: Long? = -1,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Mangler rolle $rolletype i behandling $behandlingId",
    )

fun fantIkkeFødselsdatoTilSøknadsbarn(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Fant ikke fødselsdato til søknadsbarn i behandling med id $behandlingsid",
    )

fun valideringAvBehandlingFeilet(valideringsfeil: List<String>): Nothing = throw BeregningAvResultatForBehandlingFeilet(valideringsfeil)

fun rolleManglerFødselsdato(rolletype: Rolletype): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Rolle med type $rolletype mangler fødselsdato",
    )
