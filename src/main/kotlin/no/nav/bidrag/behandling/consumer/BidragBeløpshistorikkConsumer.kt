package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.config.CacheConfig.Companion.HENT_ALLE_STØNADER_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.STØNAD_HISTORIKK_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.STØNAD_HISTORIKK_CACHE_2
import no.nav.bidrag.behandling.config.CacheConfig.Companion.STØNAD_LØPENDE_BIDRAG_CACHE
import no.nav.bidrag.beregn.barnebidrag.service.external.BeregningBeløpshistorikkConsumer
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragPeriodeRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.SkyldnerStønaderRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragPeriodeResponse
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
    @Value("\${BIDRAG_BELOPSHISTORIKK_URL}") private val bidragBeløpshistorikkUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-beløpshistorikk"),
    BeregningBeløpshistorikkConsumer {
    private val bidragBeløpshistorikkUri
        get() = UriComponentsBuilder.fromUri(bidragBeløpshistorikkUrl)

    @BrukerCacheable(STØNAD_LØPENDE_BIDRAG_CACHE)
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    override fun hentLøpendeBidrag(request: LøpendeBidragssakerRequest): LøpendeBidragssakerResponse =
        postForNonNullEntity(
            bidragBeløpshistorikkUri.pathSegment("hent-lopende-bidragssaker-for-skyldner").build().toUri(),
            request,
        )

    override fun hentAlleLøpendeStønaderIPeriode(request: LøpendeBidragPeriodeRequest): LøpendeBidragPeriodeResponse =
        postForNonNullEntity(
            bidragBeløpshistorikkUri.pathSegment("hent-stonader-i-periode/").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @BrukerCacheable(HENT_ALLE_STØNADER_CACHE)
    fun hentAlleStønaderForBidragspliktig(personidentBidragspliktig: Personident): SkyldnerStønaderResponse =
        postForNonNullEntity(
            bidragBeløpshistorikkUri.pathSegment("hent-alle-stonader-for-skyldner").build().toUri(),
            SkyldnerStønaderRequest(personidentBidragspliktig),
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @BrukerCacheable(STØNAD_HISTORIKK_CACHE)
    override fun hentHistoriskeStønader(request: HentStønadHistoriskRequest): StønadDto? =
        postForEntity(
            bidragBeløpshistorikkUri.pathSegment("hent-stonad-historisk/").build().toUri(),
            request,
        )

    @BrukerCacheable(STØNAD_HISTORIKK_CACHE_2)
    override fun hentLøpendeStønad(hentStønadRequest: HentStønadRequest): StønadDto? =
        postForEntity(
            bidragBeløpshistorikkUri.pathSegment("hent-stonad-historisk/").build().toUri(),
            hentStønadRequest,
        )
}
