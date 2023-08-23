package no.nav.bidrag.behandling.controller

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.utils.ROLLE_BA_1
import no.nav.bidrag.behandling.utils.ROLLE_BM
import no.nav.bidrag.behandling.utils.ROLLE_BP
import no.nav.bidrag.behandling.utils.SAKSNUMMER
import no.nav.bidrag.behandling.utils.SOKNAD_ID
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.transport.dokument.BidragEnhet
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class ForsendelseControllerTest : KontrollerTestRunner() {

    @Test
    fun `Skal opprette forsendelse`() {
        stubUtils.stubOpprettForsendelse()
        stubUtils.stubTilgangskontrollTema()
        val response = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/forsendelse/init",
            HttpMethod.POST,
            HttpEntity(
                InitalizeForsendelseRequest(
                    saksnummer = SAKSNUMMER,
                    enhet = BidragEnhet.ENHET_FARSKAP,
                    behandlingInfo = BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = StonadType.FORSKUDD,
                    ),
                    roller = listOf(
                        ROLLE_BM,
                        ROLLE_BP,
                        ROLLE_BA_1,
                    ),
                ),
            ),
            List::class.java,
        )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe listOf(ROLLE_BM.fødselsnummer?.verdi)
        @Language("Json")
        val expectedRequest = """
            {
                "mottaker": {
                    "ident": "${ROLLE_BM.fødselsnummer?.verdi}"
                },
                "gjelderIdent": "${ROLLE_BM.fødselsnummer?.verdi}",
                "saksnummer": "$SAKSNUMMER",
                "enhet": "${BidragEnhet.ENHET_FARSKAP}",
                "språk": "NB",
                "tema": "FAR",
                "behandlingInfo": {
                    "vedtakId": null,
                    "behandlingId": null,
                    "soknadId": $SOKNAD_ID,
                    "erFattetBeregnet": null,
                    "erVedtakIkkeTilbakekreving": false,
                    "stonadType": "FORSKUDD",
                    "engangsBelopType": null,
                    "behandlingType": null,
                    "soknadType": null,
                    "soknadFra": null,
                    "vedtakType": null
                },
                "opprettTittel": true
            }
        """.trimIndent().replace("\n", "").replace(" ", "")
        stubUtils.Verify().opprettForsendelseKaltMed(expectedRequest)
    }
}
