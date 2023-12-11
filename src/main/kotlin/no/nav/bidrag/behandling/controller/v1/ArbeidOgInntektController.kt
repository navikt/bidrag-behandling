package no.nav.bidrag.behandling.controller.v1

import mu.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.service.BehandlingService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity

private val log = KotlinLogging.logger {}

data class ArbeidOgInntektLenkeRequest(
    val behandlingId: Long,
    val ident: String
)

@BehandlingRestControllerV1
class ArbeidOgInntektController(
    @Value("\${ARBEID_OG_INNTEKT_URL}") private val ainntektUrl: String,
    private val behandlingService: BehandlingService,
) {
    @PostMapping("/arbeidOgInntekt/ainntekt")
    fun ainntektLenke(
        @RequestBody request: ArbeidOgInntektLenkeRequest,
    ): String {
        return hentAinntektLenke(request)
    }

    @PostMapping("/arbeidOgInntekt/arbeidsforhold")
    fun arbeidsforholdLenke(
        @RequestBody request: ArbeidOgInntektLenkeRequest,
    ): String {
        return hentArbeidsforholdLenke(request)
    }

    private fun hentAinntektLenke(request: ArbeidOgInntektLenkeRequest): String {
        val behandling = behandlingService.hentBehandlingById(request.behandlingId)
        val kodeverkContext =
            "${ainntektUrl}/redirect/sok/a-inntekt"
        val restTemplate: RestTemplate = RestTemplateBuilder()
            .defaultHeader(
                "Nav-A-inntekt-Filter",
                if (behandling.behandlingstype == Behandlingstype.FORSKUDD) "BidragsforskuddA-Inntekt" else "BidragA-Inntekt"
            )
            .defaultHeader("Nav-Enhet", behandling.behandlerEnhet)
            .defaultHeader("Nav-FagsakId", behandling.saksnummer)
            .defaultHeader("Nav-Personident", request.ident)
            .build()
        return restTemplate.getForEntity<String>(kodeverkContext).body!!
    }

    private fun hentArbeidsforholdLenke(request: ArbeidOgInntektLenkeRequest): String {
        val kodeverkContext =
            "${ainntektUrl}/redirect/sok/arbeidstaker"
        val restTemplate: RestTemplate = RestTemplateBuilder()
            .defaultHeader("Nav-Personident", request.ident)
            .build()
        return restTemplate.getForEntity<String>(kodeverkContext).body!!
    }
}
