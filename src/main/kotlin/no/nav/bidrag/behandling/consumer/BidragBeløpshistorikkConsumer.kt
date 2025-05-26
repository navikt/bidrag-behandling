package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.config.CacheConfig.Companion.STØNAD_HISTORIKK_CACHE
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.SkyldnerStønaderRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssakerResponse
import no.nav.bidrag.transport.behandling.belopshistorikk.response.SkyldnerStønaderResponse
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Component
class BidragBeløpshistorikkConsumer(
    @Value("\${BIDRAG_BELOPSHISTORIKK_URL}") private val bidragStønadUrl: URI,
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

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun hentAlleStønaderForBidragspliktig(personidentBidragspliktig: Personident): SkyldnerStønaderResponse =
        postForNonNullEntity(
            bidragsStønadUri.pathSegment("hent-alle-stonader-for-skyldner").build().toUri(),
            SkyldnerStønaderRequest(personidentBidragspliktig),
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @BrukerCacheable(STØNAD_HISTORIKK_CACHE)
    fun hentHistoriskeStønader(request: HentStønadHistoriskRequest): StønadDto? =
        postForEntity(
            bidragsStønadUri.pathSegment("hent-stonad-historisk/").build().toUri(),
            request,
        )
}
