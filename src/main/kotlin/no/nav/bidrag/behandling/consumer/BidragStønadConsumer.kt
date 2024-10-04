package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.behandling.stonad.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssakerResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Component
class BidragStønadConsumer(
    @Value("\${BIDRAG_STONAD_URL}") private val bidragStønadUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-stønad") {
    private val bidragsStønadUri
        get() = UriComponentsBuilder.fromUri(bidragStønadUrl)

    //    @BrukerCacheable(STØNAD_LØPENDE_BIDRAG_CACHE)
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun hentLøpendeBidrag(request: LøpendeBidragssakerRequest): LøpendeBidragssakerResponse =
        postForNonNullEntity(
            bidragsStønadUri.pathSegment("hent-lopende-bidragssaker-for-skyldner").build().toUri(),
            request,
        )
}
