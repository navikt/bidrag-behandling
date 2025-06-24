package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.PERSON_FØDSELSDATO_CACHE
import no.nav.bidrag.beregn.barnebidrag.service.external.BeregningPersonConsumer
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.person.NavnFødselDødDto
import no.nav.bidrag.transport.person.PersonDto
import no.nav.bidrag.transport.person.PersonRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

@Service
class BidragPersonConsumer(
    @Value("\${BIDRAG_PERSON_URL}") bidragPersonUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-person"),
    BeregningPersonConsumer {
    private val hentPersonUri =
        UriComponentsBuilder
            .fromUri(bidragPersonUrl)
            .pathSegment("informasjon")
            .build()
            .toUri()
    private val hentFødselsnummerUri =
        UriComponentsBuilder
            .fromUri(bidragPersonUrl)
            .pathSegment("navnfoedseldoed")
            .build()
            .toUri()

    @BrukerCacheable(PERSON_CACHE)
    fun hentPerson(ident: String): PersonDto = postForNonNullEntity(hentPersonUri, PersonDto(Personident(ident)))

    @BrukerCacheable(PERSON_FØDSELSDATO_CACHE)
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    override fun hentFødselsdatoForPerson(personident: Personident): LocalDate? {
        try {
            val response = postForNonNullEntity<NavnFødselDødDto>(hentFødselsnummerUri, PersonRequest(personident))
            return response.fødselsdato ?: response.fødselsår?.let { opprettFødselsdatoFraFødselsår(it) }
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode.value() == HttpStatus.NOT_FOUND.value()) {
                return null
            }
            throw e
        }
    }

    private fun opprettFødselsdatoFraFødselsår(fødselsår: Int): LocalDate {
        // Fødselsår finnes for alle i PDL, mens noen ikke har utfyllt fødselsdato. I disse tilfellene settes 1. januar.
        return LocalDate.of(fødselsår, 1, 1)
    }
}
