package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.dto.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.commons.web.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragForsendelseConsumer(
    @Value("\${BIDRAG_FORSENDELSE_URL}") bidragForsnendelseUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-dokument-forsendelse") {

    private val bidragForsendelsedUri =
        UriComponentsBuilder.fromUri(bidragForsnendelseUrl).pathSegment("api").pathSegment("forsendelse")

    fun opprettForsendelse(payload: OpprettForsendelseForespørsel): OpprettForsendelseRespons =
        postForNonNullEntity(bidragForsendelsedUri.build().toUri(), payload)
}


data class OpprettForsendelseRespons(
    var forsendelseId: String? = null
)
