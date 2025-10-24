package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningRequestDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderResponse
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlerenhetRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknaderResponse
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
    fun lagreBehandlingsid(request: OppdaterBehandlingsidRequest) =
        postForEntity<Unit>(
            bidragBBMUri.pathSegment("settbehandlingsid").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun leggTilBarnISøknad(request: LeggTilBarnIFFSøknadRequest) =
        postForEntity<Unit>(
            bidragBBMUri.pathSegment("leggtilbarniffsoknad").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun feilregistrerSøknad(request: FeilregistrerSøknadRequest) =
        postForEntity<Unit>(
            bidragBBMUri.pathSegment("feilregistrersoknad").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun lagreBehandlerEnhet(request: OppdaterBehandlerenhetRequest) =
        postForEntity<Unit>(
            bidragBBMUri.pathSegment("oppdaterbehandlerenhet").build().toUri(),
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
