import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.POJONode
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.OpprettForsendelseRespons
import no.nav.bidrag.behandling.utils.opprettForsendelseResponsUnderOpprettelse
import no.nav.bidrag.behandling.utils.testdataBM
import no.nav.bidrag.behandling.utils.testdataBP
import no.nav.bidrag.behandling.utils.testdataBarn1
import no.nav.bidrag.behandling.utils.testdataBarn2
import no.nav.bidrag.behandling.utils.testdataHusstandsmedlem1
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlerInfoResponse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.person.PersonDto
import no.nav.bidrag.transport.sak.BidragssakDto
import org.junit.Assert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.Arrays

fun stubPersonConsumer(): BidragPersonConsumer {
    val personConsumerMock = mockkClass(BidragPersonConsumer::class)
    every { personConsumerMock.hentPerson(any<String>()) }.answers {
        val personId = firstArg<String>()
        val personer =
            listOf(testdataBM, testdataBarn1, testdataBarn2, testdataBP, testdataHusstandsmedlem1)
        personer.find { it.ident == personId }?.tilPersonDto() ?: PersonDto(
            Personident(firstArg<String>()),
        )
    }
    mockkObject(AppContext)
    every {
        AppContext.getBean<BidragPersonConsumer>(any())
    } returns personConsumerMock
    return personConsumerMock
}

fun stubHentPersonNyIdent(
    gammelIdent: String,
    nyIdent: String,
    personConsumerMock: BidragPersonConsumer = stubPersonConsumer(),
): BidragPersonConsumer {
    every { personConsumerMock.hentPerson(eq(gammelIdent)) } returns
        PersonDto(
            Personident(
                nyIdent,
            ),
            navn = "Ola Nordmann",
            fødselsdato = LocalDate.parse("2020-02-02"),
        )
    mockkObject(AppContext)
    every {
        AppContext.getBean<BidragPersonConsumer>(any())
    } returns personConsumerMock

    return personConsumerMock
}

class StubUtils {
    companion object {
        fun aClosedJsonResponse(): ResponseDefinitionBuilder {
            return aResponse()
                .withHeader(HttpHeaders.CONNECTION, "close")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        }
    }

    fun <R> stubResponse(
        url: String,
        personResponse: R,
    ) {
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

    fun stubUnleash() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/unleash/api/client/features"))
                .willReturn(
                    aClosedJsonResponse().withStatus(HttpStatus.OK.value())
                        .withBodyFile("unleash-response.json"),
                ),
        )
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

    fun stubHentSak(
        sakResponse: BidragssakDto,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/sak/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(sakResponse)),
            ),
        )
    }

    fun stubFatteVedtak(status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/vedtak/vedtak")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(OpprettVedtakResponseDto(1, emptyList()))),
            ),
        )
    }

    fun stubHentForsendelserForSak(
        response: List<ForsendelseResponsTo> =
            listOf(
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

    fun stubHentePersoninfo(
        status: HttpStatus = HttpStatus.OK,
        personident: String,
        navn: String = "Navn Navnesen",
        shouldContaintPersonIdent: Boolean = false,
    ) {
        var postRequest = WireMock.post(WireMock.urlMatching("/bidrag-person/informasjon"))

        if (shouldContaintPersonIdent) {
            postRequest = postRequest.withRequestBody(ContainsPattern(personident))
        }

        WireMock.stubFor(
            postRequest
                .willReturn(
                    aClosedJsonResponse()
                        .withStatus(status.value())
                        .withBody(
                            toJsonString(
                                PersonDto(
                                    ident = Personident(personident),
                                    fødselsdato = LocalDate.now().minusMonths(500),
                                    visningsnavn = navn,
                                ),
                            ),
                        ),
                ),
        )
    }

    fun stubBeregneForskudd(
        status: HttpStatus = HttpStatus.OK,
        headers: Map<String, String> = emptyMap(),
    ) {
        val response =
            aClosedJsonResponse()
                .withStatus(status.value())
                .withBody(
                    toJsonString(
                        BeregnGrunnlag(
                            periode =
                                ÅrMånedsperiode(
                                    LocalDate.now().minusMonths(6),
                                    LocalDate.now().plusMonths(6),
                                ),
                            søknadsbarnReferanse = "123",
                            grunnlagListe =
                                listOf(
                                    GrunnlagDto(
                                        referanse = "abra_cadabra",
                                        type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                                        grunnlagsreferanseListe = listOf("123"),
                                        innhold = POJONode(""),
                                    ),
                                ),
                        ),
                    ),
                )
        headers.forEach {
            response.withHeader(it.key, it.value)
        }
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/beregn/forskudd"))
                .willReturn(response),
        )
    }

    fun stubTilgangskontrollTema(
        result: Boolean = true,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/tilgangskontroll/api/tilgang/tema")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString()),
            ),
        )
    }

    fun stubHentSaksbehandler() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/organisasjon/saksbehandler/info/(.*)")).willReturn(
                aResponse()
                    .withHeader(HttpHeaders.CONNECTION, "close")
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBody(
                        toJsonString(
                            SaksbehandlerInfoResponse(
                                "Z99999",
                                "Fornavn Etternavn",
                            ),
                        ),
                    ),
            ),
        )
    }

    inner class Verify {
        fun opprettForsendelseKaltMed(vararg contains: String) {
            val verify =
                WireMock.postRequestedFor(
                    WireMock.urlMatching("/forsendelse/api/forsendelse"),
                )
            verifyContains(verify, *contains)
        }

        fun forsendelseHentetForSak(
            saksnummer: String,
            antall: Int = -1,
        ) {
            val verify =
                WireMock.getRequestedFor(
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

        fun forsendelseSlettet(
            forsendelseId: String = "(.*)",
            antall: Int = -1,
        ) {
            val verify =
                WireMock.postRequestedFor(
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
            val verify =
                WireMock.postRequestedFor(
                    WireMock.urlMatching("/forsendelse/api/forsendelse"),
                )
            WireMock.verify(antall, verify)
        }

        fun fatteVedtakKalt() {
            val verify =
                WireMock.postRequestedFor(
                    WireMock.urlMatching("/vedtak/vedtak"),
                )
            WireMock.verify(1, verify)
        }

        fun hentSakKalt(saksnummer: String) {
            val verify =
                WireMock.getRequestedFor(
                    WireMock.urlMatching("/sak/sak/$saksnummer"),
                )
            WireMock.verify(1, verify)
        }

        fun opprettForsendelseIkkeKalt() {
            opprettForsendelseKaltAntallGanger(0)
        }

        private fun verifyContains(
            verify: RequestPatternBuilder,
            vararg contains: String,
        ) {
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
