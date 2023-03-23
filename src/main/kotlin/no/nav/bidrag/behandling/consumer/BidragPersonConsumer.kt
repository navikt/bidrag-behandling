package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.behandling.dto.HentPersonRequest
import no.nav.bidrag.behandling.dto.HentPersonResponse
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.domain.ident.PersonIdent
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
    fun hentPerson(personIdent: PersonIdent): HentPersonResponse {
        SECURE_LOGGER.info("Henter person med id $personIdent")
        return postForNonNullEntity(hentPersonUri, HentPersonRequest(personIdent.verdi))
    }
}
