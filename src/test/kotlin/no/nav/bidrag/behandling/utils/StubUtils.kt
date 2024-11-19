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
import com.github.tomakehurst.wiremock.matching.MatchResult
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
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.utils.testdata.BP_BARN_ANNEN_IDENT
import no.nav.bidrag.behandling.utils.testdata.BP_BARN_ANNEN_IDENT_2
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.erstattVariablerITestFil
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
import no.nav.bidrag.transport.behandling.stonad.response.SkyldnerStønaderResponse
import no.nav.bidrag.transport.behandling.vedtak.request.HentVedtakForStønadRequest
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import no.nav.bidrag.transport.dokument.OpprettJournalpostResponse
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
    every { TokenUtils.hentSaksbehandlerIdent() } returns SAKSBEHANDLER_IDENT
}

fun createPersonServiceMock(): PersonService = PersonService(stubPersonConsumer())

fun stubPersonRepository(): PersonRepository {
    val personRepositoryMock = mockkClass(PersonRepository::class)
    every { personRepositoryMock.findFirstByIdent(any<String>()) }.answers {
        val personId = firstArg<String>()
        val personer =
            listOf(testdataBM, testdataBarn1, testdataBarn2, testdataBP, testdataHusstandsmedlem1)
        personer.find { it.ident == personId }?.tilPerson() ?: Person(
            ident = firstArg<String>(),
            fødselsdato = LocalDate.parse("2015-05-01"),
        )
    }
    return personRepositoryMock
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
            fødselsdato = LocalDate.parse("2015-05-01"),
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
        fun aClosedJsonResponse(): ResponseDefinitionBuilder =
            aResponse()
                .withHeader(HttpHeaders.CONNECTION, "close")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")

        private fun createGenericResponse() =
            aResponse()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                .withStatus(HttpStatus.OK.value())
    }

    fun stubUnleash() {
        WireMock.stubFor(
            WireMock
                .get(urlMatching("/unleash/api/client/features"))
                .willReturn(
                    aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBodyFile("unleash-response.json"),
                ),
        )
    }

    fun stubBidragVedtakForStønad(
        kravhaverIdent: String,
        filnavn: String,
    ) {
        WireMock.stubFor(
            WireMock
                .post(urlMatching("/vedtak/vedtak/hent-vedtak"))
                .andMatching {
                    try {
                        val request = commonObjectmapper.readValue<HentVedtakForStønadRequest>(it.bodyAsString)
                        if (request.kravhaver.verdi == kravhaverIdent) {
                            MatchResult.exactMatch()
                        } else {
                            MatchResult.noMatch()
                        }
                    } catch (e: Exception) {
                        MatchResult.noMatch()
                    }
                }.willReturn(
                    aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(erstattVariablerITestFil("vedtak/$filnavn")),
                ),
        )
    }

    fun stubAlleBidragVedtakForStønad() {
        stubBidragVedtakForStønad(testdataBarn1.ident, "vedtak-for-stønad-barn1")
        stubBidragVedtakForStønad(testdataBarn2.ident, "vedtak-for-stønad-barn2")
        stubBidragVedtakForStønad(testdataHusstandsmedlem1.ident, "vedtak-for-stønad-barn3")
        stubBidragVedtakForStønad(BP_BARN_ANNEN_IDENT, "vedtak-for-stønad-barn_annen")
        stubBidragVedtakForStønad(BP_BARN_ANNEN_IDENT_2, "vedtak-for-stønad-barn_annen_2")
    }

    fun stubBidragStonadLøpendeSaker(
        filnavn: String = "løpende-bidragssaker-bp",
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/stonad/hent-lopende-bidragssaker-for-skyldner")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(erstattVariablerITestFil("stonad/$filnavn")),
            ),
        )
    }

    fun stubAlleStønaderBp(
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/stonad/hent-alle-stonader-for-skyldner")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(SkyldnerStønaderResponse())),
            ),
        )
    }

    fun stubBidraBBMHentBeregning(
        filnavn: String = "bbm-beregning",
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/bbm/api/beregning")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(erstattVariablerITestFil("bidragbbm/$filnavn")),
            ),
        )
    }

    fun stubTilgangskontrollSak(
        result: Boolean = true,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/tilgangskontroll/api/tilgang/sak")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString()),
            ),
        )
    }

    fun stubTilgangskontrollPerson(
        result: Boolean = true,
        status: HttpStatus = HttpStatus.OK,
        personIdent: String? = null,
    ) {
        val stub = WireMock.post(urlMatching("/tilgangskontroll/api/tilgang/person"))
        if (!personIdent.isNullOrEmpty()) {
            stub.withRequestBody(ContainsPattern(personIdent))
        }
        stub.willReturn(
            aClosedJsonResponse()
                .withStatus(status.value())
                .withBody(result.toString()),
        )
        WireMock.stubFor(stub)
    }

    fun stubTilgangskontrollPersonISak(
        result: Boolean = true,
        status: HttpStatus = HttpStatus.OK,
        personIdent: String? = null,
    ) {
        val stub = WireMock.post(urlMatching("/tilgangskontroll/api/tilgang/person/sak"))
        if (!personIdent.isNullOrEmpty()) {
            stub.withRequestBody(ContainsPattern(personIdent))
        }
        stub.willReturn(
            aClosedJsonResponse()
                .withStatus(status.value())
                .withBody(result.toString()),
        )
        WireMock.stubFor(stub)
    }

    fun stubOpprettForsendelse(
        forsendelseId: String = "12312321",
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/forsendelse/api/forsendelse")).willReturn(
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
            WireMock.get(urlMatching("/sak/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(sakResponse)),
            ),
        )
    }

    fun stubFatteVedtak(vedtaksid: Int = 1) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/vedtak/vedtak")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(toJsonString(OpprettVedtakResponseDto(vedtaksid, emptyList()))),
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
            WireMock.get(urlMatching("/vedtak/vedtak(.*)")).willReturn(
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
            WireMock.get(urlMatching("/forsendelse/api/forsendelse/sak/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(response)),
            ),
        )
    }

    fun stubOpprettNotat(status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/dokumentproduksjon/api/v2/notat/pdf")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                    .withBody("DOK".toByteArray()),
            ),
        )
    }

    fun stubOpprettJournalpost(
        nyJournalpostId: String,
        dokumenter: List<OpprettDokumentDto> = emptyList(),
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.post(urlMatching("/dokument/journalpost/JOARK")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(toJsonString(nyOpprettJournalpostResponse(nyJournalpostId, dokumenter))),
            ),
        )
    }

    fun stubSlettForsendelse(status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock
                .post(urlMatching("/forsendelse/api/forsendelse/journal/(.*)/avvik"))
                .willReturn(
                    aClosedJsonResponse()
                        .withStatus(status.value())
                        .withBody(toJsonString(OpprettForsendelseRespons("123213"))),
                ),
        )
    }

    fun stubPerson(
        status: HttpStatus = HttpStatus.OK,
        personident: String,
        navn: String = "Navn Navnesen",
        shouldContaintPersonIdent: Boolean = false,
    ) {
        var postRequest = WireMock.post(urlMatching("/bidrag-person/informasjon"))

        if (shouldContaintPersonIdent) {
            postRequest = postRequest.withRequestBody(ContainsPattern(personident))
        }

        WireMock.stubFor(
            WireMock
                .post(urlMatching("/bidrag-person/informasjon"))
                .willReturn(
                    aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withTransformers("example"),
                ),
        )
    }

    fun stubHentePersonInfoForTestpersoner() {
        stubHentePersoninfo(
            shouldContaintPersonIdent = true,
            personident = testdataBM.ident,
            responseBody = testdataBM.tilPersonDto(),
        )
        stubHentePersoninfo(
            shouldContaintPersonIdent = true,
            personident = testdataBarn1.ident,
            responseBody = testdataBarn1.tilPersonDto(),
        )
        stubHentePersoninfo(
            shouldContaintPersonIdent = true,
            personident = testdataBarn2.ident,
            responseBody = testdataBarn2.tilPersonDto(),
        )
        stubHentePersoninfo(
            shouldContaintPersonIdent = true,
            personident = testdataBP.ident,
            responseBody = testdataBP.tilPersonDto(),
        )
        stubHentePersoninfo(
            shouldContaintPersonIdent = true,
            personident = testdataHusstandsmedlem1.ident,
            responseBody = testdataHusstandsmedlem1.tilPersonDto(),
        )
    }

    fun stubHentePersoninfo(
        status: HttpStatus = HttpStatus.OK,
        personident: String,
        navn: String = "Navn Navnesen",
        shouldContaintPersonIdent: Boolean = false,
        responseBody: PersonDto? = null,
    ) {
        var postRequest = WireMock.post(urlMatching("/bidrag-person/informasjon"))

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
                                responseBody ?: PersonDto(
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

    fun stubKodeverkSpesifisertSummertSkattegrunnlag(
        response: KodeverkKoderBetydningerResponse? = null,
        status: HttpStatus = HttpStatus.OK,
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*/kodeverk/SpesifisertSummertSkattegrunnlag.*")).willReturn(
                if (response != null) {
                    aClosedJsonResponse().withStatus(status.value()).withBody(
                        ObjectMapper().findAndRegisterModules().writeValueAsString(response),
                    )
                } else {
                    aClosedJsonResponse().withBodyFile("respons_kodeverk_spesifisertsummertskattegrunnlag.json")
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
            WireMock
                .get(WireMock.urlPathMatching(".*/kodeverk/YtelseFraOffentligeBeskrivelse.*"))
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
            WireMock
                .get(WireMock.urlPathMatching(".*/kodeverk/PensjonEllerTrygdeBeskrivelse.*"))
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
            WireMock
                .get(WireMock.urlPathMatching(".*/kodeverk/Naeringsinntektsbeskrivelse.*"))
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
            WireMock.post(urlMatching("/tilgangskontroll/api/tilgang/tema")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString()),
            ),
        )
    }

    fun stubHentSaksbehandler() {
        WireMock.stubFor(
            WireMock.get(urlMatching("/organisasjon/saksbehandler/info/(.*)")).willReturn(
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

    fun stubHenteGrunnlag(
        rolle: Rolle? = null,
        tomRespons: Boolean = false,
        navnResponsfil: String = "hente-grunnlagrespons.json",
        responsobjekt: HentGrunnlagDto? = null,
        grunnlagNede: Boolean = false,
    ): StubMapping {
        val wiremock =
            if (rolle == null) {
                WireMock.post(WireMock.urlEqualTo("/hentgrunnlag"))
            } else {
                WireMock
                    .post(
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
                emptyList(),
                LocalDateTime.now(),
            )

        val respons =
            if (grunnlagNede) {
                aClosedJsonResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                    .withBody("{}")
            } else if (tomRespons && responsobjekt == null) {
                aClosedJsonResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBody(tilJson(hentGrunnlagDto))
            } else if (!tomRespons && responsobjekt != null) {
                aClosedJsonResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBody(tilJson(responsobjekt))
            } else {
                aClosedJsonResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withStatus(HttpStatus.OK.value())
                    .withBodyFile(navnResponsfil)
            }

        return WireMock.stubFor(wiremock.willReturn(respons))
    }

    fun stubbeGrunnlagsinnhentingForBehandling(
        behandling: Behandling,
        grunnlagNede: Boolean = false,
    ) {
        var barnNummer = 1
        behandling.roller.forEach {
            when (it.rolletype) {
                Rolletype.BIDRAGSMOTTAKER -> stubHenteGrunnlag(it, grunnlagNede)
                Rolletype.BIDRAGSPLIKTIG ->
                    stubHenteGrunnlag(
                        rolle = it,
                        navnResponsfil = "hente-grunnlagrespons-sb-bp.json",
                        grunnlagNede = grunnlagNede,
                    )

                Rolletype.BARN -> {
                    stubHenteGrunnlag(
                        rolle = it,
                        navnResponsfil = "hente-grunnlagrespons-barn${barnNummer++}.json",
                        grunnlagNede = grunnlagNede,
                    )
                }

                else -> {
                    stubHenteGrunnlag(tomRespons = true, grunnlagNede = grunnlagNede)
                }
            }
        }
    }

    inner class Verify {
        fun opprettForsendelseKaltMed(vararg contains: String) {
            val verify =
                postRequestedFor(
                    urlMatching("/forsendelse/api/forsendelse"),
                )
            verifyContains(verify, *contains)
        }

        fun forsendelseHentetForSak(
            saksnummer: String,
            antall: Int = -1,
        ) {
            val verify =
                WireMock.getRequestedFor(
                    urlMatching("/forsendelse/api/forsendelse/sak/$saksnummer/forsendelser"),
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
                postRequestedFor(
                    urlMatching("/forsendelse/api/forsendelse/journal/$forsendelseId/avvik"),
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
                postRequestedFor(
                    urlMatching("/forsendelse/api/forsendelse"),
                )
            WireMock.verify(antall, verify)
        }

        fun fatteVedtakKalt(antallGanger: Int = 1) {
            val verify =
                postRequestedFor(
                    urlMatching("/vedtak/vedtak"),
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
                    urlMatching("/sak/sak/$saksnummer"),
                )
            WireMock.verify(1, verify)
        }

        fun hentGrunnlagKalt(
            antallGanger: Int = 1,
            rolle: Rolle? = null,
        ) {
            val verify =
                if (rolle == null) {
                    postRequestedFor(WireMock.urlEqualTo("/hentgrunnlag"))
                } else {
                    postRequestedFor(
                        WireMock.urlEqualTo("/hentgrunnlag"),
                    ).withRequestBody(WireMock.containing(rolle.ident))
                }
            WireMock.verify(antallGanger, verify)
        }

        fun opprettNotatKalt() {
            WireMock.verify(postRequestedFor(urlMatching("/dokumentproduksjon/api/v2/notat/pdf")))
        }

        fun opprettJournalpostKaltMed(vararg contains: String) {
            val verify =
                WireMock.postRequestedFor(
                    urlMatching("/dokument/journalpost/JOARK"),
                )
            verifyContains(verify, *contains)
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

    fun toJsonString(data: Any): String =
        try {
            ObjectMapper().findAndRegisterModules().writeValueAsString(data)
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
            ""
        }
}

fun nyOpprettJournalpostResponse(
    journalpostId: String = "123123123",
    dokumenter: List<OpprettDokumentDto> =
        listOf(OpprettDokumentDto(tittel = "Tittel på dokument", dokumentreferanse = "dokref1")),
): OpprettJournalpostResponse =
    OpprettJournalpostResponse(
        dokumenter = dokumenter,
        journalpostId = journalpostId,
    )
