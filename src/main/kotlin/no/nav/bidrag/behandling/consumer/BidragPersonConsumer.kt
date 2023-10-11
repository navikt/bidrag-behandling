package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.behandling.dto.HentPersonRequest
import no.nav.bidrag.behandling.dto.HentPersonResponse
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragPersonConsumer(
    @Value("\${BIDRAG_PERSON_URL}") bidragPersonUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-person") {

    private val hentPersonUri =
        UriComponentsBuilder.fromUri(bidragPersonUrl).pathSegment("informasjon").build().toUri()

    @BrukerCacheable(PERSON_CACHE)
    fun hentPerson(ident: String): HentPersonResponse {
        return postForNonNullEntity(hentPersonUri, HentPersonRequest(ident))
    }
}