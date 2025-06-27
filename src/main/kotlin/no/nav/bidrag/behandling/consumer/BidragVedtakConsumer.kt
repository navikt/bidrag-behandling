package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.beregn.barnebidrag.service.external.BeregningVedtakConsumer
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.behandling.vedtak.request.HentVedtakForStønadRequest
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.HentVedtakForStønadResponse
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

data class OpprettVedtakConflictResponse(
    val vedtaksid: Int,
)

@Component
class BidragVedtakConsumer(
    @Value("\${BIDRAG_VEDTAK_URL}") private val bidragVedtakUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-vedtak"),
    BeregningVedtakConsumer {
    private val bidragVedtakUri
        get() = UriComponentsBuilder.fromUri(bidragVedtakUrl).pathSegment("vedtak")

    fun fatteVedtak(request: OpprettVedtakRequestDto): OpprettVedtakResponseDto =
        try {
            postForNonNullEntity(
                bidragVedtakUri.build().toUri(),
                request,
            )
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.CONFLICT) {
                val resultat = e.getResponseBodyAs(OpprettVedtakConflictResponse::class.java)!!
                secureLogger.info {
                    "Vedtak med referanse ${request.unikReferanse} finnes allerede med vedtaksid ${resultat.vedtaksid}."
                }
                OpprettVedtakResponseDto(resultat.vedtaksid, emptyList())
            } else {
                secureLogger.error(e) { "Feil ved oppretting av vedtak med referanse ${request.unikReferanse}" }
                throw e
            }
        }

//    @BrukerCacheable(VEDTAK_CACHE)
    fun hentVedtak(vedtakId: Long): VedtakDto? =
        getForEntity(
            bidragVedtakUri.pathSegment(vedtakId.toString()).build().toUri(),
        )

    override fun hentVedtak(vedtaksid: Int): VedtakDto? = hentVedtak(vedtaksid.toLong())

    //    @BrukerCacheable(VEDTAK_FOR_STØNAD_CACHE)
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    override fun hentVedtakForStønad(request: HentVedtakForStønadRequest): HentVedtakForStønadResponse =
        postForNonNullEntity(
            bidragVedtakUri.pathSegment("hent-vedtak").build().toUri(),
            request,
        )
}
