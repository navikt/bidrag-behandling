package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.dto.v1.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.dokument.AvvikType
import no.nav.bidrag.transport.dokument.Avvikshendelse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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

    fun opprettForsendelse(payload: no.nav.bidrag.behandling.dto.v1.forsendelse.OpprettForsendelseForespørsel): OpprettForsendelseRespons =
        postForNonNullEntity(bidragForsendelsedUri.build().toUri(), payload)

    fun hentForsendelserISak(saksnummer: String): List<ForsendelseResponsTo> =
        getForNonNullEntity(bidragForsendelsedUri.pathSegment("sak").pathSegment(saksnummer).pathSegment("forsendelser").build().toUri())

    fun slettForsendelse(forsendelseId: Long) {
        postForEntity<Void>(
            bidragForsendelsedUri.pathSegment("journal").pathSegment(forsendelseId.toString()).pathSegment("avvik").build().toUri(),
            Avvikshendelse(AvvikType.SLETT_JOURNALPOST),
        )
    }
}

data class OpprettForsendelseRespons(
    var forsendelseId: String? = null,
)

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
