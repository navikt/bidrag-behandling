package no.nav.bidrag.behandling

import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun behandlingNotFoundException(behandlingId: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.NOT_FOUND,
        "Fant ikke behandling med id $behandlingId",
    )

fun aktiveringAvGrunnlagFeiletException(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Aktivering av grunnlag feilet for behandling $behandlingsid",
    )

fun inntektIkkeFunnetException(idInntekt: Long): Nothing =
    throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Fant ikke inntekt med id $idInntekt")

fun husstandsbarnIkkeFunnetException(
    idHusstandsbarn: Long,
    behandlingsid: Long,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.NOT_FOUND,
        "Fant ikke husstandsbarn med id $idHusstandsbarn knyttet " +
            "til behandling $behandlingsid",
    )

fun ressursIkkeFunnetException(feilmelding: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.NOT_FOUND,
        feilmelding,
    )

fun ressursIkkeTilknyttetBehandling(feilmelding: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        feilmelding,
    )

fun ressursHarFeilKildeException(feilmelding: String): Nothing = throw HttpClientErrorException(HttpStatus.BAD_REQUEST, feilmelding)

fun oppdateringAvBoforholdFeiletException(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Oppdatering av boforhold feilet for behandling $behandlingsid",
    )

fun manglerForrigePeriode(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Angre siste endringer av boforhold feilet for behandling $behandlingsid. Mangler forrige periode",
    )

fun requestManglerDataException(
    behandlingsid: Long,
    ressurstype: Ressurstype,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Forespørselen om å oppdatere ${ressurstype.toString().lowercase()} for behandling $behandlingsid " +
            "inneholdt ingen data.",
    )

fun lagringAvGrunnlagFeiletException(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Lagring av grunnlag feilet for behandling $behandlingsid",
    )

fun aktiveringAvGrunnlagstypeIkkeStøttetException(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Feil grunnlagstype oppgitt i aktiveringsforespørsel for behandling $behandlingsid",
    )

fun finnesFraFørException(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.CONFLICT,
        "Forsøk på å oppdatere behandling $behandlingsid feilet pga duplikate data.",
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

fun fantIkkeRolleISak(
    saksnummer: String,
    ident: String,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Fant ikke rolle med ident $ident i sak med saksnummer $saksnummer",
    )

fun rolleManglerIdent(
    rolletype: Rolletype,
    behandlingId: Long,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Manger personident for rolle $rolletype i behandling $behandlingId",
    )

fun fantIkkeFødselsdatoTilSøknadsbarn(behandlingsid: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Fant ikke fødselsdato til søknadsbarn i behandling med id $behandlingsid",
    )

fun rolleManglerFødselsdato(rolletype: Rolletype): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Rolle med type $rolletype mangler fødselsdato",
    )

fun vedtakmappingFeilet(melding: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        melding,
    )

enum class Ressurstype {
    BOFORHOLD,
    INNTEKT,
    SIVILSTAND,
}
