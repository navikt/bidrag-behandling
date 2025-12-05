package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.config.CacheConfig.Companion.BBM_ALLE_BEREGNINGER_CACHE
import no.nav.bidrag.behandling.config.CacheConfig.Companion.BBM_BEREGNING_CACHE
import no.nav.bidrag.beregn.barnebidrag.service.external.BeregningBBMConsumer
import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningRequestDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadsBarnRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderResponse
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknadResponse
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlerenhetRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterReferanseGebyrRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Component
class BidragBBMConsumer(
    @Value("\${BIDRAG_BBM_URL}") private val bidragBBMurl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-bbm"),
    BeregningBBMConsumer {
    private val bidragBBMUri
        get() = UriComponentsBuilder.fromUri(bidragBBMurl).pathSegment("api", "beregning")

    @BrukerCacheable(BBM_BEREGNING_CACHE)
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    override fun hentBeregning(request: BidragBeregningRequestDto): BidragBeregningResponsDto =
        postForNonNullEntity(
            bidragBBMUri.build().toUri(),
            request,
        )

    @BrukerCacheable(BBM_ALLE_BEREGNINGER_CACHE)
    override fun hentAlleBeregninger(request: BidragBeregningRequestDto): BidragBeregningResponsDto =
        postForNonNullEntity(
            bidragBBMUri.pathSegment("alleberegningerogsamvar").build().toUri(),
            request,
        )

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun opprettSøknader(request: OpprettSøknadRequest): OpprettSøknadResponse =
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
        try {
            postForEntity<Unit>(
                bidragBBMUri.pathSegment("settbehandlingsid").build().toUri(),
                request,
            )
        } catch (e: RestClientResponseException) {
            secureLogger.error(e) { "Feil ved oppdatering av behandlingsid av request=$request" }
        }

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
    fun feilregistrerSøknadsbarn(request: FeilregistrerSøknadsBarnRequest) =
        postForEntity<Unit>(
            bidragBBMUri.pathSegment("feilregistrersoknadsbarn").build().toUri(),
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

    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    fun hentSøknad(søknadsid: Long): HentSøknadResponse =
        postForNonNullEntity(
            bidragBBMUri.pathSegment("hentsoknad").build().toUri(),
            HentSøknadRequest(søknadsid),
        )
}
