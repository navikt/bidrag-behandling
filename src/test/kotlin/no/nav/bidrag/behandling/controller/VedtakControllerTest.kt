package no.nav.bidrag.behandling.controller

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.toggleFatteVedtakName
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import stubPersonConsumer
import java.time.LocalDateTime

class VedtakControllerTest : KontrollerTestRunner() {
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
    fun `Skal ikke fatte vedtak hvis feature toggle er av`() {
        every { unleashInstance.isEnabled(eq(toggleFatteVedtakName), any<Boolean>()) } returns false

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        save(behandling)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.PRECONDITION_FAILED
        response.headers["warning"]!! shouldContain "Fattevedtak er ikke aktivert"

        val behandlingEtter = behandlingRepository.findBehandlingById(behandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe null
        stubUtils.Verify().fatteVedtakKalt(0)
    }

    @Test
    fun `Skal fatte vedtak`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        behandling.boforholdsbegrunnelseKunINotat = "Boforhold"
        behandling.boforholdsbegrunnelseIVedtakOgNotat = "Boforhold kun i notat"
        save(behandling)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe 1

        val behandlingEtter = behandlingRepository.findBehandlingById(behandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe 1
        behandlingEtter.vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
        behandlingEtter.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        stubUtils.Verify().fatteVedtakKalt()
        stubUtils.Verify().hentSakKalt(behandling.saksnummer)
    }

    @Test
    fun `Skal ikke fatte vedtak hvis behandling har vedtakId`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        behandling.vedtaksid = 1L
        save(behandling)
        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `Skal ikke fatte vedtak hvis nyeste opplysninger ikke er aktivert`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        behandlingRepository.save(behandling)
        val grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ) +
                Grunnlag(
                    type = Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    behandling = behandling,
                    data = "{}",
                    aktiv = null,
                    innhentet = LocalDateTime.now(),
                    rolle = testdataBM.tilRolle(behandling),
                )
        grunnlagRepository.saveAll(grunnlag)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.headers[HttpHeaders.WARNING]!!.first() shouldContain "Kan ikke fatte vedtak fordi nyeste opplysninger ikke er hentet inn"
    }

    @Test
    fun `Skal fatte vedtak hvis nyeste opplysninger ikke er aktivert for klage`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, Vedtakstype.KLAGE)
        behandlingRepository.save(behandling)
        val grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ) +
                Grunnlag(
                    type = Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    behandling = behandling,
                    data = "{}",
                    aktiv = null,
                    innhentet = LocalDateTime.now(),
                    rolle = testdataBM.tilRolle(behandling),
                )
        grunnlagRepository.saveAll(grunnlag)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
    }

    private fun save(behandling: Behandling) {
        try {
            behandlingRepository.save(behandling)
            val grunnlag =
                opprettAlleAktiveGrunnlagFraFil(
                    behandling,
                    "grunnlagresponse.json",
                )
            grunnlagRepository.saveAll(grunnlag)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
