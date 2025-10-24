package no.nav.bidrag.behandling.consumer

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.CacheConfig.Companion.SAK_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.SAK_PERSON_CACHE
import no.nav.bidrag.beregn.barnebidrag.service.external.BeregningSakConsumer
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.service.consumers.FellesSakConsumer
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.FjernMidlertidligTilgangRequest
import no.nav.bidrag.transport.sak.OpprettMidlertidligTilgangRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

private val LOGGER = KotlinLogging.logger {}

@Service
class BidragSakConsumer(
    @Value("\${BIDRAG_SAK_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-sak"),
    BeregningSakConsumer,
    FellesSakConsumer {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @BrukerCacheable(SAK_CACHE)
    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    override fun hentSak(saksnr: String): BidragssakDto {
        try {
            return getForNonNullEntity(createUri("/bidrag-sak/sak/$saksnr"))
        } catch (e: HttpStatusCodeException) {
            LOGGER.warn(e) { "Det skjedde en feil ved henting av sak $saksnr" }
            throw e
        }
    }

    @BrukerCacheable(SAK_PERSON_CACHE)
    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    fun hentSakerPerson(personident: String): List<BidragssakDto> {
        try {
            return postForNonNullEntity(createUri("/person/sak"), Personident(personident))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return emptyList()
            }
            LOGGER.warn(e) { "Det skjedde en feil ved henting av sak for $personident" }
            throw e
        }
    }

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    fun opprettMidlertidligTilgang(request: OpprettMidlertidligTilgangRequest) {
        try {
            return postForNonNullEntity(createUri("/sak/tilgang/opprett"), request)
        } catch (e: HttpStatusCodeException) {
            LOGGER.warn(e) { "Det skjedde en feil opprettelse av midlertlidlig tilgang for $request" }
            throw e
        }
    }

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    fun fjernMidlertidligTilgang(request: FjernMidlertidligTilgangRequest) {
        try {
            return postForNonNullEntity(createUri("/sak/tilgang/fjern"), request)
        } catch (e: HttpStatusCodeException) {
            LOGGER.warn(e) { "Det skjedde en feil fjerning av midlertidlig tilgang for sak $request" }
            throw e
        }
    }
}
