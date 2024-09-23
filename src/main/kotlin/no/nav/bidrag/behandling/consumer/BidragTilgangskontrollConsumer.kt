package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.config.CacheConfig.Companion.TILGANG_PERSON_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.TILGANG_SAK_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.TILGANG_TEMA_CACHE
import no.nav.bidrag.behandling.fantIkkeSak
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragTilgangskontrollConsumer(
    @Value("\${BIDRAG_TILGANGSKONTROLL_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-tilgangskontroll") {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @BrukerCacheable(TILGANG_SAK_CACHE)
    fun sjekkTilgangPersonISak(
        personident: Personident,
        saksnummer: Saksnummer,
    ): Boolean {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            postForEntity(createUri("/api/tilgang/person/sak"), SjekkTilgangPersonISakRequest(personident, saksnummer), headers) ?: false
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            if (e.statusCode == HttpStatus.NOT_FOUND) fantIkkeSak(saksnummer.verdi)
            throw e
        }
    }

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @BrukerCacheable(TILGANG_PERSON_CACHE)
    fun sjekkTilgangPerson(personnummer: String): Boolean {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.TEXT_PLAIN
            postForEntity(createUri("/api/tilgang/person"), personnummer, headers) ?: false
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            throw e
        }
    }

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @BrukerCacheable(TILGANG_TEMA_CACHE)
    fun sjekkTilgangTema(tema: String): Boolean {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.TEXT_PLAIN
            postForEntity(createUri("/api/tilgang/tema"), tema, headers) ?: false
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            throw e
        }
    }
}

data class SjekkTilgangPersonISakRequest(
    val personident: Personident,
    val saksnummer: Saksnummer,
)
