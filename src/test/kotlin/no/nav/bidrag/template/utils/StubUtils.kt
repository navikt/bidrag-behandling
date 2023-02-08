import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import no.nav.bidrag.template.model.HentPersonResponse
import org.junit.Assert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class StubUtils {

    companion object {
        fun aClosedJsonResponse(): ResponseDefinitionBuilder {
            return aResponse()
                .withHeader(HttpHeaders.CONNECTION, "close")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        }
    }

    fun stubBidragPersonResponse(personResponse: HentPersonResponse) {
        try {
            WireMock.stubFor(
                WireMock.post("/person/informasjon").willReturn(
                    aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(ObjectMapper().writeValueAsString(personResponse))
                )
            )
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
        }
    }
}
