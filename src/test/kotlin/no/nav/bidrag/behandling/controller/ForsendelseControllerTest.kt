package no.nav.bidrag.behandling.controller

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingStatus
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID
import no.nav.bidrag.behandling.utils.testdata.opprettForsendelseResponsUnderOpprettelse
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.dokument.BidragEnhet
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ForsendelseControllerTest : KontrollerTestRunner() {
    @Test
    fun `Skal opprette forsendelse`() {
        val forsendelseId = "213123213123"
        stubUtils.stubOpprettForsendelse(forsendelseId)
        stubUtils.stubTilgangskontrollTema()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/forsendelse/init",
                HttpMethod.POST,
                HttpEntity(
                    InitalizeForsendelseRequest(
                        saksnummer = SAKSNUMMER,
                        enhet = BidragEnhet.ENHET_FARSKAP,
                        behandlingInfo =
                            BehandlingInfoDto(
                                soknadId = SOKNAD_ID,
                                stonadType = Stønadstype.FORSKUDD,
                            ),
                        roller =
                            listOf(
                                testdataBM.tilForsendelseRolleDto(),
                                testdataBP.tilForsendelseRolleDto(),
                                testdataBarn1.tilForsendelseRolleDto(),
                            ),
                    ),
                ),
                List::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe listOf(forsendelseId)
        @Language("Json")
        val expectedRequest =
            """
            {
                "gjelderIdent": "${testdataBM.ident}",
                "mottaker": {
                    "ident": "${testdataBM.ident}",
                    "språk":null,
                    "navn":null,
                    "identType":null,
                    "adresse":null
                },
                "dokumenter":[],
                "saksnummer": "$SAKSNUMMER",
                "enhet": "${BidragEnhet.ENHET_FARSKAP}",
                "behandlingInfo": {
                    "vedtakId": null,
                    "behandlingId": null,
                    "soknadId": "$SOKNAD_ID",
                    "engangsBelopType": null,
                    "stonadType": "FORSKUDD",
                    "behandlingType": null,
                    "vedtakType": null,
                    "soknadType": null,
                    "erFattetBeregnet": null,
                    "erVedtakIkkeTilbakekreving": false,
                    "soknadFra": null,
                    "barnIBehandling":["${testdataBarn1.ident}"]
                },
                "tema": "FAR",
                "språk": "NB",
                "opprettTittel": true,
                "distribuerAutomatiskEtterFerdigstilling":false
            }
            """.trimIndent().replace("\n", "").replace(" ", "")
        stubUtils.Verify().opprettForsendelseKaltMed(expectedRequest)
        stubUtils.Verify().forsendelseHentetForSak(SAKSNUMMER, 0)
        stubUtils.Verify().forsendelseSlettet(antall = 0)
    }

    @Test
    fun `Skal opprette forsendelse med forkortet roller`() {
        val forsendelseId = "213123213123"
        stubUtils.stubOpprettForsendelse(forsendelseId)
        stubUtils.stubTilgangskontrollTema()
        val header = HttpHeaders()
        header.contentType = MediaType.APPLICATION_JSON
        val response =
            httpHeaderTestRestTemplateNoJackson
                .exchange(
                    "${rootUriV1()}/forsendelse/init",
                    HttpMethod.POST,
                    HttpEntity(
                        """
                        {
                            "saksnummer": "$SAKSNUMMER",
                            "behandlingInfo": {
                                "vedtakId": null,
                                "behandlingId": null,
                                "soknadId": 12412421414,
                                "erFattetBeregnet": null,
                                "erVedtakIkkeTilbakekreving": false,
                                "stonadType": "FORSKUDD",
                                "engangsBelopType": null,
                                "behandlingType": null,
                                "soknadType": null,
                                "soknadFra": null,
                                "vedtakType": null,
                                "barnIBehandling": []
                            },
                            "enhet": "4860",
                            "tema": null,
                            "roller": [
                                {
                                    "fødselsnummer": "${testdataBM.ident}",
                                    "type": "BM"
                                },
                                {
                                    "fødselsnummer": "${testdataBP.ident}",
                                    "type": "BP"
                                },
                                {
                                    "fødselsnummer": "${testdataBarn1.ident}",
                                    "type": "BA"
                                }
                            ]
                        }
                        """.trimIndent(),
                        header,
                    ),
                    List::class.java,
                )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe listOf(forsendelseId)
        @Language("Json")
        val expectedRequest =
            """
            {
                "gjelderIdent": "${testdataBM.ident}",
                "mottaker": {
                    "ident": "${testdataBM.ident}",
                    "språk":null,
                    "navn":null,
                    "identType":null,
                    "adresse":null
                },
                "dokumenter":[],
                "saksnummer": "$SAKSNUMMER",
                "enhet": "${BidragEnhet.ENHET_FARSKAP}",
                "behandlingInfo": {
                    "vedtakId": null,
                    "behandlingId": null,
                    "soknadId": "$SOKNAD_ID",
                    "engangsBelopType": null,
                    "stonadType": "FORSKUDD",
                    "behandlingType": null,
                    "vedtakType": null,
                    "soknadType": null,
                    "erFattetBeregnet": null,
                    "erVedtakIkkeTilbakekreving": false,
                    "soknadFra": null,
                    "barnIBehandling":["${testdataBarn1.ident}"]
                },
                "tema": "FAR",
                "språk": "NB",
                "opprettTittel": true,
                "distribuerAutomatiskEtterFerdigstilling":false
            }
            """.trimIndent().replace("\n", "").replace(" ", "")
        stubUtils.Verify().opprettForsendelseKaltMed(expectedRequest)
        stubUtils.Verify().forsendelseHentetForSak(SAKSNUMMER, 0)
        stubUtils.Verify().forsendelseSlettet(antall = 0)
    }

    @Test
    fun `Skal opprette forsendelse og slette forsendelser for varsel`() {
        val forsendelseId = "213123213123"

        stubUtils.stubOpprettForsendelse(forsendelseId)
        stubUtils.stubSlettForsendelse()
        stubUtils.stubHentForsendelserForSak(
            listOf(
                opprettForsendelseResponsUnderOpprettelse(1),
                opprettForsendelseResponsUnderOpprettelse(2),
                opprettForsendelseResponsUnderOpprettelse(3).copy(forsendelseType = ForsendelseTypeTo.NOTAT),
                opprettForsendelseResponsUnderOpprettelse(4).copy(status = ForsendelseStatusTo.UNDER_PRODUKSJON),
            ),
        )
        stubUtils.stubTilgangskontrollTema()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/forsendelse/init",
                HttpMethod.POST,
                HttpEntity(
                    InitalizeForsendelseRequest(
                        saksnummer = SAKSNUMMER,
                        enhet = BidragEnhet.ENHET_FARSKAP,
                        behandlingInfo =
                            BehandlingInfoDto(
                                soknadId = SOKNAD_ID,
                                stonadType = Stønadstype.FORSKUDD,
                                vedtakId = "1",
                            ),
                        roller =
                            listOf(
                                testdataBM.tilForsendelseRolleDto(),
                                testdataBP.tilForsendelseRolleDto(),
                                testdataBarn1.tilForsendelseRolleDto(),
                            ),
                    ),
                ),
                List::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe listOf(forsendelseId)
        stubUtils.Verify().opprettForsendelseKaltAntallGanger(1)
        stubUtils.Verify().forsendelseHentetForSak(SAKSNUMMER)
        stubUtils.Verify().forsendelseSlettet("1")
        stubUtils.Verify().forsendelseSlettet("2")
    }

    @Test
    fun `Skal slette forsendelser for varsel hvis behandling er feilregistrert`() {
        val forsendelseId = "213123213123"

        stubUtils.stubOpprettForsendelse(forsendelseId)
        stubUtils.stubSlettForsendelse()
        stubUtils.stubHentForsendelserForSak(
            listOf(
                opprettForsendelseResponsUnderOpprettelse(1),
                opprettForsendelseResponsUnderOpprettelse(2),
                opprettForsendelseResponsUnderOpprettelse(3).copy(forsendelseType = ForsendelseTypeTo.NOTAT),
                opprettForsendelseResponsUnderOpprettelse(4).copy(status = ForsendelseStatusTo.UNDER_PRODUKSJON),
            ),
        )
        stubUtils.stubTilgangskontrollTema()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/forsendelse/init",
                HttpMethod.POST,
                HttpEntity(
                    InitalizeForsendelseRequest(
                        saksnummer = SAKSNUMMER,
                        behandlingStatus = BehandlingStatus.FEILREGISTRERT,
                        enhet = BidragEnhet.ENHET_FARSKAP,
                        behandlingInfo =
                            BehandlingInfoDto(
                                soknadId = SOKNAD_ID,
                                stonadType = Stønadstype.FORSKUDD,
                                vedtakId = "1",
                            ),
                        roller =
                            listOf(
                                testdataBM.tilForsendelseRolleDto(),
                                testdataBP.tilForsendelseRolleDto(),
                                testdataBarn1.tilForsendelseRolleDto(),
                            ),
                    ),
                ),
                List::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe listOf("1", "2")
        stubUtils.Verify().opprettForsendelseKaltAntallGanger(0)
        stubUtils.Verify().forsendelseHentetForSak(SAKSNUMMER)
        stubUtils.Verify().forsendelseSlettet("1")
        stubUtils.Verify().forsendelseSlettet("2")
    }
}
