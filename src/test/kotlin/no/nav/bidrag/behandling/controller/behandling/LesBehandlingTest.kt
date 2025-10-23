package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertNotNull

@Suppress("NonAsciiCharacters")
class LesBehandlingTest : BehandlingControllerTest() {
    @Test
    fun `skal opprette en behandling fra vedtak`() {
        // hvis
        stubUtils.stubHenteVedtak()
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/vedtak/1",
                HttpMethod.GET,
                null,
                BehandlingDtoV2::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val behandling = behandlingRes.body
        assertNotNull(behandling)
        assertSoftly(behandling) {
            roller shouldHaveSize 3
            vedtakstype shouldBe Vedtakstype.FASTSETTELSE
            stønadstype shouldBe Stønadstype.FORSKUDD
            søktFomDato shouldBe LocalDate.parse("2022-01-01")
            mottattdato shouldBe LocalDate.parse("2023-01-01")
            søktAv shouldBe SøktAvType.BIDRAGSMOTTAKER
            saksnummer shouldBe "1233333"
            søknadsid shouldBe 101
            behandlerenhet shouldBe "4806"
            roller shouldHaveSize 3
        }

        assertSoftly(behandling.virkningstidspunktV3.barn.first()) {
            virkningstidspunkt shouldBe LocalDate.parse("2022-11-01")
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            notat.kunINotat shouldBe "Notat virkningstidspunkt"
        }

        assertSoftly(behandling.inntekter) {
            årsinntekter shouldHaveSize 7
            årsinntekter
                .filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT }
                .shouldBeEmpty()
            årsinntekter
                .filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT }
                .shouldBeEmpty()
            månedsinntekter shouldHaveSize 25
            notat.kunINotat shouldBe "Notat inntekt"
        }

        assertSoftly(behandling.boforhold) {
            husstandsmedlem shouldHaveSize 6
            sivilstand shouldHaveSize 2
            begrunnelse.kunINotat shouldBe "Notat boforhold"
        }

        val aktiveGrunnlagsdata = behandling.aktiveGrunnlagsdata
        aktiveGrunnlagsdata shouldNotBe null
        aktiveGrunnlagsdata.arbeidsforhold shouldHaveSize 3
        aktiveGrunnlagsdata.husstandsmedlem shouldHaveSize 5
        aktiveGrunnlagsdata.sivilstand!!.grunnlag shouldHaveSize 2
        aktiveGrunnlagsdata.stønadTilBarnetilsyn!!.grunnlag shouldHaveSize 2
    }

    @Test
    fun `Skal feile hvis vedtak ikke finnes`() {
        stubUtils.stubHenteVedtak(status = HttpStatus.NOT_FOUND)
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/vedtak/1",
                HttpMethod.GET,
                null,
                Void::class.java,
            )

        behandlingRes.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `skal ikke hente vedtak hvis ingen tilgang til sak`() {
        // gitt
        stubUtils.stubHenteVedtak()
        stubUtils.stubTilgangskontrollSak(false)
        stubUtils.stubTilgangskontrollPersonISak(false)
        testdataManager.oppretteBehandling(true)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/vedtak/1",
                HttpMethod.GET,
                null,
                Void::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.FORBIDDEN, behandlingRes.statusCode)
    }
}
