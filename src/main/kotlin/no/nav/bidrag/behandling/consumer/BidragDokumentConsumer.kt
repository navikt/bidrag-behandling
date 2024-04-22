package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.dokument.OpprettJournalpostRequest
import no.nav.bidrag.transport.dokument.OpprettJournalpostResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragDokumentConsumer(
    @Value("\${BIDRAG_DOKUMENT_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-dokument") {
    private fun createUri(path: String?) =
        UriComponentsBuilder.fromUri(url)
            .path(path ?: "").build().toUri()

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun opprettJournalpost(opprettJournalpostRequest: OpprettJournalpostRequest): OpprettJournalpostResponse {
        return postForNonNullEntity(createUri("/journalpost/JOARK"), opprettJournalpostRequest)
    }
}
