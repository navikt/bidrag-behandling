package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.dokument.AvvikType
import no.nav.bidrag.transport.dokument.Avvikshendelse
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseRespons
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragForsendelseConsumer(
    @Value("\${BIDRAG_FORSENDELSE_URL}") private val bidragForsnendelseUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-dokument-forsendelse") {
    private val bidragForsendelsedUri get() =
        UriComponentsBuilder.fromUri(bidragForsnendelseUrl).pathSegment("api").pathSegment("forsendelse")

    private fun createUri(path: String = "") = URI.create("$bidragForsnendelseUrl/$path")

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun distribuerForsendelse(forsendelseId: Long): DistribuerJournalpostResponse {
        val distribuerJournalpostRequest = DistribuerJournalpostRequest()

        try {
            return postForNonNullEntity<DistribuerJournalpostResponse>(
                createUri("api/forsendelse/journal/distribuer/$forsendelseId"),
                distribuerJournalpostRequest,
            )
        } catch (e: HttpStatusCodeException) {
            val begrunnelse = e.responseHeaders?.getOrEmpty(HttpHeaders.WARNING)?.firstOrNull()
            throw HttpClientErrorException(e.statusCode, begrunnelse ?: e.message ?: "Ukjent feil ved distribusjon av forsendelse")
        }
    }

    fun opprettForsendelse(opprettForsendelseForespørsel: OpprettForsendelseForespørsel): OpprettForsendelseRespons =
        postForNonNullEntity(bidragForsendelsedUri.build().toUri(), opprettForsendelseForespørsel)

    fun hentForsendelserISak(saksnummer: String): List<ForsendelseResponsTo> =
        getForNonNullEntity(
            bidragForsendelsedUri
                .pathSegment("sak")
                .pathSegment(saksnummer)
                .pathSegment("forsendelser")
                .build()
                .toUri(),
        )

    fun slettForsendelse(forsendelseId: Long) {
        postForEntity<Void>(
            bidragForsendelsedUri
                .pathSegment("journal")
                .pathSegment(forsendelseId.toString())
                .pathSegment("avvik")
                .build()
                .toUri(),
            Avvikshendelse(AvvikType.SLETT_JOURNALPOST),
        )
    }
}

data class ForsendelseResponsTo(
    val forsendelseId: Long,
    val saksnummer: String? = null,
    val behandlingInfo: BehandlingInfoResponseDto? = null,
    val forsendelseType: ForsendelseTypeTo? = null,
    val status: ForsendelseStatusTo? = null,
)

data class BehandlingInfoResponseDto(
    val soknadId: String? = null,
    val erFattet: Boolean,
)

enum class ForsendelseTypeTo {
    UTGÅENDE,
    NOTAT,
}

enum class ForsendelseStatusTo {
    UNDER_OPPRETTELSE,
    UNDER_PRODUKSJON,
    FERDIGSTILT,
    SLETTET,
    DISTRIBUERT,
    DISTRIBUERT_LOKALT,
}
