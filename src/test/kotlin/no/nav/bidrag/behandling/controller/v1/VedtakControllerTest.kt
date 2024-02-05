package no.nav.bidrag.behandling.controller.v1

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.utils.opprettGyldigBehandlingForBeregning
import no.nav.bidrag.behandling.utils.opprettSakForBehandling
import no.nav.bidrag.commons.service.sjablon.SjablonProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class VedtakControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
        mockkObject(SjablonProvider)
        every {
            SjablonProvider.hentSjablontall()
        } returns emptyList()
    }

    @Test
    fun `Skal fatte vedtak`() {
        val behandling = opprettGyldigBehandlingForBeregning(false)
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        behandling.boforholdsbegrunnelseKunINotat = "Boforhold"
        behandling.boforholdsbegrunnelseIVedtakOgNotat = "Boforhold kun i notat"
        try {
            behandlingRepository.save(behandling)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/vedtak",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe 1

        val behandlingEtter = behandlingRepository.findBehandlingById(behandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe 1
        stubUtils.Verify().fatteVedtakKalt()
        stubUtils.Verify().hentSakKalt(behandling.saksnummer)
    }

    @Test
    fun `Skal ikke fatte vedtak hvis behandling har vedtakId`() {
        val behandling = opprettGyldigBehandlingForBeregning(false)
        behandling.vedtaksid = 1L
        try {
            behandlingRepository.save(behandling)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/vedtak",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
    }
}
