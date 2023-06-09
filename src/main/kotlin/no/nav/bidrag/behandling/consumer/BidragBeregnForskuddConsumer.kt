package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragBeregnForskuddConsumer(
    @Value("\${BIDRAG_BEREGN_FORSKUDD_URL}") bidragBeregnForskuddUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-person") {

    private val hentPersonUri =
        UriComponentsBuilder.fromUri(bidragBeregnForskuddUrl).pathSegment("beregn").build().toUri()
}
