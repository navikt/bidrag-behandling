package no.nav.bidrag.behandling.controller

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

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
                "${rootUriV2()}/behandling/${behandling.id}/vedtak",
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
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        behandling.vedtaksid = 1L
        save(behandling)
        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/vedtak",
                HttpMethod.POST,
                HttpEntity(""),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
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
