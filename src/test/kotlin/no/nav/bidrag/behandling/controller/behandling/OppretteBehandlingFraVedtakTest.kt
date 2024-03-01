package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertNotNull

@Suppress("NonAsciiCharacters")
class OppretteBehandlingFraVedtakTest : BehandlingControllerTest() {
    @Test
    fun `skal opprette en behandling med null opprettetDato og så hente den`() {
        // hvis
        stubUtils.stubHenteVedtak()
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/vedtak",
                HttpMethod.POST,
                HttpEntity(
                    OpprettBehandlingFraVedtakRequest(
                        vedtakId = 1,
                        vedtakstype = Vedtakstype.KLAGE,
                        søknadFra = SøktAvType.BIDRAGSMOTTAKER,
                        søktFomDato = LocalDate.parse("2020-01-01"),
                        mottattdato = LocalDate.parse("2024-01-01"),
                        behandlerenhet = "4444",
                        saksnummer = "1234567",
                        søknadsreferanseid = 111,
                        søknadsid = 12323,
                    ),
                ),
                OpprettBehandlingResponse::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val behandling = behandlingRepository.findBehandlingById(behandlingRes.body!!.id).get()
        assertNotNull(behandling)
        assertSoftly(behandling) {
            roller shouldHaveSize 3
            inntekter shouldHaveSize 14
            grunnlag shouldHaveSize 13
            sivilstand shouldHaveSize 2
            husstandsbarn shouldHaveSize 6
            søktFomDato shouldBe LocalDate.parse("2020-01-01")
            vedtakstype shouldBe Vedtakstype.KLAGE
            årsak shouldBe null
            avslag shouldBe null
            omgjørVedtaksid shouldBe null
            soknadRefId shouldBe 111
            soknadsid shouldBe 12323
        }
    }
}
