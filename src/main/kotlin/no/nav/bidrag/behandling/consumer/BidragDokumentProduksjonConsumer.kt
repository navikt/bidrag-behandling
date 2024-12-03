package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.notat.VedtakNotatDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
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
        get() = UriComponentsBuilder.fromUri(bidragDokumentProduksjonUrl).pathSegment("api", "v2", "notat", "pdf")

    fun opprettNotat(request: VedtakNotatDto): ByteArray =
        postForEntity(
            bidragDokumentProduksjonUri.build().toUri(),
            request,
        )!!
}
