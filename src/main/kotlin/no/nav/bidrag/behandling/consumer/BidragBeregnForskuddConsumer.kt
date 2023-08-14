package no.nav.bidrag.behandling.consumer

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.transport.behandling.vedtak.reponse.GrunnlagDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragBeregnForskuddConsumer(
    @Value("\${BIDRAG_BEREGN_FORSKUDD_URL}") bidragBeregnForskuddUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-beregn-forskudd-rest") {

    private val beregnForskuddUri =
        UriComponentsBuilder.fromUri(bidragBeregnForskuddUrl).pathSegment("beregn").pathSegment("forskudd").build().toUri()

    fun beregnForskudd(payload: BeregnForskuddPayload): ForskuddDto =
        postForNonNullEntity(beregnForskuddUri, payload)
}

@Schema(description = "Grunnlaget for en forskuddsberegning")
data class BeregnForskuddPayload(
    @Schema(description = "Beregn forskudd fra-dato") val beregnDatoFra: String? = null,
    @Schema(description = "Beregn forskudd til-dato") val beregnDatoTil: String? = null,
    @Schema(description = "Periodisert liste over grunnlagselementer") val grunnlagListe: List<Grunnlag>? = null,
)

typealias Grunnlag = GrunnlagDto
