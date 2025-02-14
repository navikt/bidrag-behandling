package no.nav.bidrag.kalkulator.consumer

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.kalkulator.config.CacheConfig.Companion.FAMILIE_CACHE
import no.nav.bidrag.kalkulator.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.transport.person.MotpartBarnRelasjonDto
import no.nav.bidrag.transport.person.PersonDto
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
        UriComponentsBuilder
            .fromUri(bidragPersonUrl)
            .pathSegment("informasjon")
            .build()
            .toUri()

    private val hentFamilieUri =
        UriComponentsBuilder
            .fromUri(bidragPersonUrl)
            .pathSegment("motpartbarnrelasjon")
            .build()
            .toUri()

    @BrukerCacheable(PERSON_CACHE)
    fun hentPerson(ident: String): PersonDto = postForNonNullEntity(hentPersonUri, PersonDto(Personident(ident)))

    @BrukerCacheable(FAMILIE_CACHE)
    fun hentFamilie(ident: String): MotpartBarnRelasjonDto =
        postForNonNullEntity(hentFamilieUri, MotpartBarnRelasjonDto(PersonDto(Personident(ident))))
}
