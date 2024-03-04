package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.controller.behandling.BehandlingControllerTest
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.utils.testdata.TestDataPerson
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettSivilstand
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import stubPersonConsumer
import java.time.LocalDate

class VerdikjedeTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
        grunnlagRepository.deleteAll()
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
    }

    @Test
    fun `skal opprette behandling og fatte vedtak`() {
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

        val opprettetBehandling = opprettOgVerifiserBehandling()

        // Beregn forskudd
        val responseBeregning =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${opprettetBehandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity(""),
                object : ParameterizedTypeReference<List<ResultatBeregningBarnDto>>() {},
            )

        responseBeregning.statusCode shouldBe HttpStatus.OK
        assertSoftly(responseBeregning.body!!) {
            shouldHaveSize(2)
        }

        // Fatte vedtak
        stubUtils.stubHentSak(opprettSakForBehandling(opprettetBehandling))
        stubUtils.stubFatteVedtak()
        val responseVedtak =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${opprettetBehandling.id}",
                HttpMethod.POST,
                null,
                Int::class.java,
            )
        responseVedtak.statusCode shouldBe HttpStatus.OK
        responseVedtak.body shouldBe 1

        val behandlingEtter =
            behandlingRepository.findBehandlingById(opprettetBehandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe 1
        stubUtils.Verify().fatteVedtakKalt()
        stubUtils.Verify().hentSakKalt(behandlingEtter.saksnummer)
    }

    private fun Behandling.taMedInntekt(
        type: Inntektsrapportering,
        gjelder: TestDataPerson,
    ) {
        val inntekt = inntekter.find { it.type == type && it.ident == gjelder.ident }!!
        inntekt.taMed = true
    }

    private fun opprettOgVerifiserBehandling(): Behandling {
        val personidentBm = Personident(testdataBM.ident)
        val personidentBarn1 = Personident(testdataBarn1.ident)
        val personidentBarn2 = Personident(testdataBarn2.ident)

        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    personidentBarn1,
                    fødselsdato = testdataBarn1.foedselsdato,
                ),
                OpprettRolleDto(
                    Rolletype.BARN,
                    personidentBarn2,
                    fødselsdato = testdataBarn2.foedselsdato,
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    personidentBm,
                    fødselsdato = testdataBM.foedselsdato,
                ),
            )
        val behandlingReq =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "4806", roller)
                .copy(
                    søktFomDato = LocalDate.parse("2023-01-01"),
                    mottattdato = LocalDate.parse("2023-01-01"),
                )

        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling",
                HttpMethod.POST,
                HttpEntity(behandlingReq),
                OpprettBehandlingResponse::class.java,
            )
        response.statusCode shouldBe HttpStatus.OK

        val opprettetBehandling =
            behandlingRepository.findBehandlingById(response.body?.id!!).get()
        opprettetBehandling.årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
        opprettetBehandling.virkningstidspunkt = opprettetBehandling.søktFomDato
        opprettetBehandling.taMedInntekt(
            Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
            testdataBM,
        )
        opprettetBehandling.husstandsbarn.add(
            opprettHusstandsbarn(
                opprettetBehandling,
                testdataBarn1,
            ),
        )
        opprettetBehandling.husstandsbarn.add(
            opprettHusstandsbarn(
                opprettetBehandling,
                testdataBarn2,
            ),
        )
        opprettetBehandling.sivilstand.add(opprettSivilstand(opprettetBehandling))
        return behandlingRepository.save(opprettetBehandling)
    }
}
