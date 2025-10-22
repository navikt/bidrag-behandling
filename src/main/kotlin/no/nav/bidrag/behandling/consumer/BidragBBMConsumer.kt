package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.consumer.dto.HentBPsÅpneSøknaderRequest
import no.nav.bidrag.behandling.consumer.dto.HentBPsÅpneSøknaderResponse
import no.nav.bidrag.behandling.consumer.dto.OppdaterBehandlingsidRequest
import no.nav.bidrag.behandling.consumer.dto.OpprettSøknadRequest
import no.nav.bidrag.behandling.consumer.dto.OpprettSøknaderRequest
import no.nav.bidrag.behandling.consumer.dto.OpprettSøknaderResponse
import no.nav.bidrag.behandling.consumer.dto.ÅpenSøknadDto
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningRequestDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Component
class BidragBBMConsumer(
    @Value("\${BIDRAG_BBM_URL}") private val bidragBBMurl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-bbm") {
    private val bidragBBMUri
        get() = UriComponentsBuilder.fromUri(bidragBBMurl).pathSegment("api", "beregning")

    //    @BrukerCacheable(BBM_BEREGNING_CACHE)
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun hentBeregning(request: BidragBeregningRequestDto): BidragBeregningResponsDto =
        postForNonNullEntity(
            bidragBBMUri.build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun opprettSøknader(request: OpprettSøknadRequest): OpprettSøknaderResponse =
        postForNonNullEntity(
            bidragBBMUri.pathSegment("opprettsoknad").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun lagreBehandlingsid(request: OppdaterBehandlingsidRequest): Unit =
        postForNonNullEntity(
            bidragBBMUri.pathSegment("settbehandlingsid").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun hentÅpneSøknaderForBp(bidragspliktig: String): HentBPsÅpneSøknaderResponse =
        postForNonNullEntity(
            bidragBBMUri.pathSegment("apnesoknader").build().toUri(),
            HentBPsÅpneSøknaderRequest(bidragspliktig),
        )
}
