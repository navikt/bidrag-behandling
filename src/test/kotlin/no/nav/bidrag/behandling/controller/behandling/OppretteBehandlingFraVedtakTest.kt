package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertNotNull

@Suppress("NonAsciiCharacters")
class OppretteBehandlingFraVedtakTest : BehandlingControllerTest() {
    @Test
    @Transactional
    fun `skal opprette en behandling fra vedtak`() {
        stubUtils.stubHenteGrunnlag(
            navnResponsfil = "grunnlagresponse.json",
            rolleIdent = testdataBM.ident,
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolleIdent = testdataBarn1.ident,
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolleIdent = testdataBarn2.ident,
        )
        stubUtils.stubHenteVedtak()

        // hvis
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
            innkrevingstype shouldBe Innkrevingstype.MED_INNKREVING
            klagedetaljer!!.opprinneligVedtakstidspunkt shouldHaveSize 1
            klagedetaljer!!.opprinneligVedtakstidspunkt shouldContain LocalDateTime.parse("2024-02-23T15:34:27.275019")
            klagedetaljer?.refVedtaksid shouldBe 12333
            grunnlag.filter { it.aktiv == null }.shouldHaveSize(8)
            sivilstand shouldHaveSize 2
            // TODO: Boforhold grunnlag inneholder sju unike husstandsmedlemmer - fikse stub-vedtaksdata slik at tallene stemmer
            husstandsmedlem shouldHaveSize 7
            søktFomDato shouldBe LocalDate.parse("2020-01-01")
            vedtakstype shouldBe Vedtakstype.KLAGE
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
            klagedetaljer?.soknadRefId shouldBe 111
            soknadsid shouldBe 12323
            notater shouldHaveSize 5
            notater.filter { it.erDelAvBehandlingen == false }.shouldHaveSize(5)
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
