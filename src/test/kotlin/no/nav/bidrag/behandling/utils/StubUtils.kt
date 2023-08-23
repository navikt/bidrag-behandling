import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import no.nav.bidrag.behandling.consumer.OpprettForsendelseRespons
import org.junit.Assert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.util.*

class StubUtils {

    companion object {
        fun aClosedJsonResponse(): ResponseDefinitionBuilder {
            return aResponse()
                .withHeader(HttpHeaders.CONNECTION, "close")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        }
    }

    fun <R>stubResponse(url: String, personResponse: R) {
        try {
            WireMock.stubFor(
                WireMock.post(url).willReturn(
                    aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(ObjectMapper().writeValueAsString(personResponse)),
                ),
            )
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
        }
    }

    fun stubOpprettForsendelse(status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/forsendelse/api/forsendelse")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(OpprettForsendelseRespons("123213"))),
            ),
        )
    }
    fun stubTilgangskontrollTema(result: Boolean = true, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/tilgangskontroll/api/tilgang/tema")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString()),
            ),
        )
    }

    inner class Verify {
        fun opprettForsendelseKaltMed(vararg contains: String) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/forsendelse/api/forsendelse"),
            )
            verifyContains(verify, *contains)
        }

        fun opprettForsendelseKaltAntallGanger(antall: Int) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/forsendelse/api/forsendelse"),
            )
            WireMock.verify(antall, verify)
        }

        fun opprettForsendelseIkkeKalt() {
            opprettForsendelseKaltAntallGanger(0)
        }

        private fun verifyContains(verify: RequestPatternBuilder, vararg contains: String) {
            Arrays.stream(contains).forEach { verify.withRequestBody(ContainsPattern(it)) }
            WireMock.verify(verify)
        }
    }
    fun toJsonString(data: Any): String {
        return try {
            ObjectMapper().findAndRegisterModules().writeValueAsString(data)
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
            ""
        }
    }
}
