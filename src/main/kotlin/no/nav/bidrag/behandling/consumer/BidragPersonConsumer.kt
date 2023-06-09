package no.nav.bidrag.behandling.consumer

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
import no.nav.bidrag.commons.web.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

@Service
class BidragPersonConsumer(
    @Value("\${BIDRAG_PERSON_URL}") bidragPersonUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-person") {

    private val beregnForskuddUri =
        UriComponentsBuilder.fromUri(bidragPersonUrl).pathSegment("beregn").pathSegment("forskudd").build().toUri()

    fun beregnForskudd(payload: BeregnForskuddPayload): ForskuddDto {
        return postForNonNullEntity(beregnForskuddUri, 1 /*, todo payload*/)
    }
}

@Schema(description = "Grunnlaget for en forskuddsberegning")
data class BeregnForskuddPayload(
    @Schema(description = "Beregn forskudd fra-dato") val beregnDatoFra: LocalDate? = null,
    @Schema(description = "Beregn forskudd til-dato") val beregnDatoTil: LocalDate? = null,
    @Schema(description = "Periodisert liste over grunnlagselementer") val grunnlagListe: List<Grunnlag>? = null,
)

@Schema(description = "Grunnlag")
data class Grunnlag(
    @Schema(description = "Referanse") val referanse: String? = null,
    @Schema(description = "Type") val type: String? = null,
    @Schema(description = "Innhold") val innhold: JsonNode? = null,
)
