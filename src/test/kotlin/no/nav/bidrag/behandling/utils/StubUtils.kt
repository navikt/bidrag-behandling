import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.OpprettForsendelseRespons
import no.nav.bidrag.behandling.utils.opprettForsendelseResponsUnderOpprettelse
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

    fun <R> stubResponse(url: String, personResponse: R) {
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

    fun stubOpprettForsendelse(
        forsendelseId: String = "12312321",
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/forsendelse/api/forsendelse")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(OpprettForsendelseRespons(forsendelseId))),
            ),
        )
    }

    fun stubHentForsendelserForSak(
        response: List<ForsendelseResponsTo> = listOf(
            opprettForsendelseResponsUnderOpprettelse(1),
            opprettForsendelseResponsUnderOpprettelse(2),
        ),
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/forsendelse/api/forsendelse/sak/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(response)),
            ),
        )
    }

    fun stubSlettForsendelse(status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/forsendelse/api/forsendelse/journal/(.*)/avvik"))
                .willReturn(
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

        fun forsendelseHentetForSak(saksnummer: String, antall: Int = -1) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/forsendelse/api/forsendelse/sak/$saksnummer/forsendelser"),
            )
            WireMock.verify(
                if (antall == -1) {
                    CountMatchingStrategy(
                        CountMatchingStrategy.GREATER_THAN_OR_EQUAL,
                        1,
                    )
                } else {
                    CountMatchingStrategy(CountMatchingStrategy.EQUAL_TO, antall)
                },
                verify,
            )
        }

        fun forsendelseSlettet(forsendelseId: String = "(.*)", antall: Int = -1) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/forsendelse/api/forsendelse/journal/$forsendelseId/avvik"),
            )
            WireMock.verify(
                if (antall == -1) {
                    CountMatchingStrategy(
                        CountMatchingStrategy.GREATER_THAN_OR_EQUAL,
                        1,
                    )
                } else {
                    CountMatchingStrategy(CountMatchingStrategy.EQUAL_TO, antall)
                },
                verify,
            )
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
