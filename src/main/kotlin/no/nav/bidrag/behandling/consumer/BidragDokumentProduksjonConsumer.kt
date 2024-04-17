package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.dto.v1.notat.NotatDto
import no.nav.bidrag.commons.web.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Component
class BidragDokumentProduksjonConsumer(
    @Value("\${BIDRAG_DOKUMENT_PRODUKSJON_URL}") private val bidragDokumentProduksjonUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-dokument-produksjon") {
    private val bidragDokumentProduksjonUri
        get() = UriComponentsBuilder.fromUri(bidragDokumentProduksjonUrl).pathSegment("api", "notat", "pdf", "forskudd")

    fun opprettNotat(request: NotatDto): ByteArray {
        val headers = HttpHeaders()
        return postForEntity(
            bidragDokumentProduksjonUri.build().toUri(),
            request,
        )!!
    }
}
