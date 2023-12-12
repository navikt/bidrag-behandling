package no.nav.bidrag.behandling.controller.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.domene.ident.Personident
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity

private val log = KotlinLogging.logger {}

data class ArbeidOgInntektLenkeRequest(
    val behandlingId: Long,
    val ident: String,
)

@BehandlingRestControllerV1
class ArbeidOgInntektController(
    @Value("\${ARBEID_OG_INNTEKT_URL}") private val ainntektUrl: String,
    private val behandlingService: BehandlingService,
) {
    @PostMapping("/arbeidoginntekt/ainntekt")
    @Operation(
        description = "Generer lenke for ainntekt-søk med filter for behandling og personident oppgitt i forespørsel",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun genererAinntektLenke(
        @RequestBody request: ArbeidOgInntektLenkeRequest,
    ): String {
        val behandling = behandlingService.hentBehandlingById(request.behandlingId)
        val kodeverkContext =
            "$ainntektUrl/redirect/sok/a-inntekt"
        val restTemplate: RestTemplate =
            RestTemplateBuilder()
                .defaultHeader(
                    "Nav-A-inntekt-Filter",
                    if (behandling.behandlingstype == Behandlingstype.FORSKUDD) "BidragsforskuddA-Inntekt" else "BidragA-Inntekt",
                )
                .defaultHeader("Nav-Enhet", behandling.behandlerEnhet)
                .defaultHeader("Nav-FagsakId", behandling.saksnummer)
                .defaultHeader("Nav-Personident", request.ident)
                .build()
        return restTemplate.getForEntity<String>(kodeverkContext).body!!
    }

    @PostMapping("/arbeidoginntekt/aareg")
    @Operation(
        description = "Generer lenke for aareg-søk for personident oppgitt i forespørsel",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun genererAaregLenke(
        @RequestBody request: Personident,
    ): String {
        val kodeverkContext =
            "$ainntektUrl/redirect/sok/arbeidstaker"
        val restTemplate: RestTemplate =
            RestTemplateBuilder()
                .defaultHeader("Nav-Personident", request.verdi)
                .build()
        return restTemplate.getForEntity<String>(kodeverkContext).body!!
    }
}
