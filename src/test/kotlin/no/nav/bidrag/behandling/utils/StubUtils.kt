import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.findAll
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.OpprettForsendelseRespons
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.utils.testdata.opprettForsendelseResponsUnderOpprettelse
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.service.KodeverkKoderBetydningerResponse
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlerInfoResponse
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.person.PersonDto
import no.nav.bidrag.transport.sak.BidragssakDto
import org.junit.Assert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Arrays

fun stubSaksbehandlernavnProvider() {
    mockkObject(SaksbehandlernavnProvider)
    every { SaksbehandlernavnProvider.hentSaksbehandlernavn(any()) } returns "Fornavn Etternavn"
}

fun stubTokenUtils() {
    mockkStatic(TokenUtils::hentApplikasjonsnavn)
    mockkStatic(TokenUtils::hentSaksbehandlerIdent)
    every { TokenUtils.hentApplikasjonsnavn() } returns "bidrag-behandling"
    every { TokenUtils.hentSaksbehandlerIdent() } returns "Z99999"
}

fun stubPersonConsumer(): BidragPersonConsumer {
    try {
        clearMocks(BidragPersonConsumer::class)
    } catch (e: Exception) {
        // Ignore
    }
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

        private fun createGenericResponse() =
            WireMock.aResponse()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                .withStatus(HttpStatus.OK.value())
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

    fun stubHenteVedtak(
        responseObjekt: VedtakDto? = null,
        navnResponsfil: String = "vedtak_response.json",
        status: HttpStatus = HttpStatus.OK,
    ) {
        val response =
            if (responseObjekt != null) {
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(
                        commonObjectmapper.writeValueAsString(
                            responseObjekt,
                        ),
                    )
            } else {
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBodyFile(navnResponsfil)
            }
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/vedtak/vedtak(.*)")).willReturn(
                response,
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

    fun stubKodeverkSkattegrunnlag(
        response: KodeverkKoderBetydningerResponse? = null,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*/kodeverk/Summert.*")).willReturn(
                if (response != null) {
                    aClosedJsonResponse().withStatus(status.value()).withBody(
                        ObjectMapper().findAndRegisterModules().writeValueAsString(response),
                    )
                } else {
                    aClosedJsonResponse().withBodyFile("respons_kodeverk_summert_skattegrunnlag.json")
                },
            ),
        )
    }

    fun stubKodeverkLønnsbeskrivelse(
        response: KodeverkKoderBetydningerResponse? = null,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*/kodeverk/Loennsbeskrivelse.*")).willReturn(
                if (response != null) {
                    aClosedJsonResponse().withStatus(status.value()).withBody(
                        ObjectMapper().findAndRegisterModules().writeValueAsString(response),
                    )
                } else {
                    aClosedJsonResponse().withBodyFile("respons_kodeverk_loennsbeskrivelser.json")
                },
            ),
        )
    }

    fun stubKodeverkYtelsesbeskrivelser(
        response: KodeverkKoderBetydningerResponse? = null,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*/kodeverk/YtelseFraOffentligeBeskrivelse.*"))
                .willReturn(
                    if (response != null) {
                        aClosedJsonResponse().withStatus(status.value()).withBody(
                            ObjectMapper().findAndRegisterModules().writeValueAsString(response),
                        )
                    } else {
                        aClosedJsonResponse()
                            .withBodyFile("respons_kodeverk_ytelserbeskrivelser.json")
                    },
                ),
        )
    }

    fun stubKodeverkPensjonsbeskrivelser(
        response: KodeverkKoderBetydningerResponse? = null,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*/kodeverk/PensjonEllerTrygdeBeskrivelse.*"))
                .willReturn(
                    if (response != null) {
                        createGenericResponse().withStatus(status.value()).withBody(
                            ObjectMapper().findAndRegisterModules().writeValueAsString(response),
                        )
                    } else {
                        createGenericResponse()
                            .withBodyFile("respons_kodeverk_ytelserbeskrivelser.json")
                    },
                ),
        )
    }

    fun stubKodeverkNaeringsinntektsbeskrivelser(
        response: KodeverkKoderBetydningerResponse? = null,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*/kodeverk/Naeringsinntektsbeskrivelse.*"))
                .willReturn(
                    if (response != null) {
                        aClosedJsonResponse().withStatus(status.value()).withBody(
                            ObjectMapper().findAndRegisterModules().writeValueAsString(response),
                        )
                    } else {
                        aClosedJsonResponse()
                            .withBodyFile("respons_kodeverk_naeringsinntektsbeskrivelse.json")
                    },
                ),
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

    fun stubHenteGrunnlagOk(
        rolle: Rolle? = null,
        tomRespons: Boolean = false,
        navnResponsfil: String = "hente-grunnlagrespons.json",
        responsobjekt: HentGrunnlagDto? = null,
    ): StubMapping {
        val wiremock =
            if (rolle == null) {
                WireMock.post(WireMock.urlEqualTo("/hentgrunnlag"))
            } else {
                WireMock.post(
                    WireMock.urlEqualTo("/hentgrunnlag"),
                ).withRequestBody(WireMock.containing(rolle.ident))
            }

        val hentGrunnlagDto =
            HentGrunnlagDto(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                LocalDateTime.now(),
            )

        val respons =
            if (tomRespons && responsobjekt == null) {
                aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBody(tilJson(hentGrunnlagDto))
            } else if (!tomRespons && responsobjekt != null) {
                aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBody(tilJson(responsobjekt))
            } else {
                aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBodyFile(navnResponsfil)
            }

        return WireMock.stubFor(wiremock.willReturn(respons))
    }

    fun stubbeGrunnlagsinnhentingForBehandling(behandling: Behandling) {
        var barnNummer = 1
        behandling.roller.forEach {
            when (it.rolletype) {
                Rolletype.BIDRAGSMOTTAKER -> stubHenteGrunnlagOk(it)
                Rolletype.BARN -> {
                    stubHenteGrunnlagOk(
                        rolle = it,
                        navnResponsfil = "hente-grunnlagrespons-barn${barnNummer++}.json",
                    )
                }

                else -> {
                    stubHenteGrunnlagOk(tomRespons = true)
                }
            }
        }
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

        fun fatteVedtakKalt(antallGanger: Int = 1) {
            val verify =
                WireMock.postRequestedFor(
                    WireMock.urlMatching("/vedtak/vedtak"),
                )
            WireMock.verify(antallGanger, verify)
        }

        fun hentFatteVedtakRequest(): OpprettVedtakRequestDto {
            val requests: List<LoggedRequest> =
                findAll(postRequestedFor(urlMatching("/vedtak/vedtak")))
            return commonObjectmapper.readValue(requests.first().bodyAsString)
        }

        fun hentSakKalt(saksnummer: String) {
            val verify =
                WireMock.getRequestedFor(
                    WireMock.urlMatching("/sak/sak/$saksnummer"),
                )
            WireMock.verify(1, verify)
        }

        fun hentGrunnlagKalt(
            antallGanger: Int = 1,
            rolle: Rolle? = null,
        ) {
            val verify =
                if (rolle == null) {
                    WireMock.postRequestedFor(WireMock.urlEqualTo("/hentgrunnlag"))
                } else {
                    WireMock.postRequestedFor(
                        WireMock.urlEqualTo("/hentgrunnlag"),
                    ).withRequestBody(WireMock.containing(rolle.ident))
                }
            WireMock.verify(antallGanger, verify)
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
