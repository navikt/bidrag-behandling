package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertNotNull

@Suppress("NonAsciiCharacters")
class OppretteBehandlingFraVedtakTest : BehandlingControllerTest() {
    @Test
    @Transactional
    fun `skal opprette en behandling fra vedtak`() {
        stubUtils.stubHenteGrunnlagOk(
            navnResponsfil = "grunnlagresponse.json",
            rolle = testdataBM.tilRolle(),
        )
        stubUtils.stubHenteGrunnlagOk(
            tomRespons = true,
            rolle = testdataBarn1.tilRolle(),
        )
        stubUtils.stubHenteGrunnlagOk(
            tomRespons = true,
            rolle = testdataBarn2.tilRolle(),
        )
        // hvis
        stubUtils.stubHenteVedtak()
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/vedtak/12333",
                HttpMethod.POST,
                HttpEntity(
                    OpprettBehandlingFraVedtakRequest(
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
            inntekter shouldHaveSize 15
            grunnlag shouldHaveSize 31
            refVedtaksid shouldBe 12333
            grunnlag.filter { it.aktiv == null }.shouldHaveSize(10)
            sivilstand shouldHaveSize 2
            husstandsbarn shouldHaveSize 4
            søktFomDato shouldBe LocalDate.parse("2020-01-01")
            vedtakstype shouldBe Vedtakstype.KLAGE
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
            soknadRefId shouldBe 111
            soknadsid shouldBe 12323
        }

        stubUtils.Verify().hentGrunnlagKalt(1, testdataBM.tilRolle(behandling))
        stubUtils.Verify().hentGrunnlagKalt(1, testdataBarn2.tilRolle(behandling))
        stubUtils.Verify().hentGrunnlagKalt(1, testdataBarn1.tilRolle(behandling))
    }

    @Test
    fun `Skal ikke opprette behandling hvis vedtak ikke finnes`() {
        stubUtils.stubHenteVedtak(status = HttpStatus.NOT_FOUND)
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/vedtak/12333",
                HttpMethod.POST,
                HttpEntity(
                    OpprettBehandlingFraVedtakRequest(
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

        behandlingRes.statusCode shouldBe HttpStatus.NOT_FOUND
    }
}
