package no.nav.bidrag.behandling.controller

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.toggleFatteVedtakName
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.erstattVariablerITestFil
import no.nav.bidrag.behandling.utils.testdata.initGrunnlagRespons
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.commons.service.sjablon.SjablonService
import no.nav.bidrag.commons.web.mock.sjablonSamværsfradragResponse
import no.nav.bidrag.commons.web.mock.sjablonTallResponse
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import stubPersonConsumer
import java.time.LocalDate
import java.time.LocalDateTime

class VedtakControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var entityManager: EntityManager

    @MockkBean
    lateinit var sjablonService: SjablonService

    @BeforeEach
    fun oppsett() {
        try {
            behandlingRepository.deleteAll()
            grunnlagRepository.deleteAll()
        } catch (e: Exception) {
        }

        every {
            sjablonService.hentSjablonSamværsfradrag()
        } returns sjablonSamværsfradragResponse()
        every {
            sjablonService.hentSjablontall()
        } returns sjablonTallResponse()
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        stubUtils.stubOpprettNotat()
        stubUtils.stubHentePersonInfoForTestpersoner()
        stubUtils.stubAlleBidragVedtakForStønad()
        stubUtils.stubBidraBBMHentBeregning()
        stubUtils.stubBidragBeløpshistorikkLøpendeSaker()
        stubUtils.stubHentSak(opprettSakForBehandling(oppretteBehandling()))
        stubUtils.stubOpprettJournalpost("12333")
    }

    @Test
    @Disabled
    fun `Skal ikke fatte vedtak særbidrag hvis feature toggle er av`() {
        every { unleashInstance.isEnabled(eq(toggleFatteVedtakName), any<Boolean>()) } returns false

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
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
    fun `Skal fatte vedtak for forskudd`() {
        stubUtils.stubOpprettJournalpost("12333")

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        save(behandling)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(FatteVedtakRequestDto(enhet = "4999")),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe 1

        val behandlingEtter = behandlingRepository.findBehandlingById(behandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe 1
        behandlingEtter.vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
        behandlingEtter.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        behandlingEtter.vedtakFattetAvEnhet shouldBe "4999"
        behandlingEtter.notatJournalpostId shouldBe "12333"
        stubUtils.Verify().fatteVedtakKalt()
        stubUtils.Verify().hentSakKalt(behandling.saksnummer)
        stubUtils.Verify().opprettNotatKalt()
        stubUtils.Verify().opprettJournalpostKaltMed()
    }

    @Test
    @Disabled
    fun `Skal fatte vedtak for bidrag`() {
        stubUtils.stubOpprettJournalpost("12333")

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        behandling.søktFomDato = LocalDate.parse("2024-01-01")
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = false)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = false)

        save(behandling, "grunnlagresponse_bp_bm")

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubAlleBidragVedtakForStønad()
        stubUtils.stubBidragStønaderForSkyldner()
        stubUtils.stubFatteVedtak()
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                HttpEntity(FatteVedtakRequestDto(enhet = "4999")),
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe 1

        val behandlingEtter = behandlingRepository.findBehandlingById(behandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe 1
        behandlingEtter.vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
        behandlingEtter.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        behandlingEtter.vedtakFattetAvEnhet shouldBe "4999"
        behandlingEtter.notatJournalpostId shouldBe "12333"
        stubUtils.Verify().fatteVedtakKalt()
        stubUtils.Verify().hentSakKalt(behandling.saksnummer)
        stubUtils.Verify().opprettNotatKalt()
        stubUtils.Verify().opprettJournalpostKaltMed()
    }

    @Test
    fun `Skal fatte vedtak for særbidrag`() {
        stubUtils.stubOpprettJournalpost("12333")

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.husstandsmedlem = mutableSetOf()
        testdataManager.lagreBehandling(behandling)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        behandling.initGrunnlagRespons(stubUtils)
        // Trigge hente grunnlag
        val henteBehandlingResponse =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}",
                HttpMethod.GET,
                null,
                Void::class.java,
            )
        henteBehandlingResponse.statusCode shouldBe HttpStatus.OK
        val response =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/fattevedtak/${behandling.id}",
                HttpMethod.POST,
                null,
                Int::class.java,
            )

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe 1

        val behandlingEtter = behandlingRepository.findBehandlingById(behandling.id!!).get()
        behandlingEtter.vedtaksid shouldBe 1
        behandlingEtter.vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
        behandlingEtter.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        // TODO endre dette når notat er klart
        behandlingEtter.notatJournalpostId shouldBe "12333"
        stubUtils.Verify().fatteVedtakKalt()
        stubUtils.Verify().hentSakKalt(behandling.saksnummer, 3)
        stubUtils.Verify().opprettNotatKalt()
        stubUtils.Verify().opprettJournalpostKaltMed()
    }

    @Test
    fun `Skal ikke fatte vedtak hvis behandling har vedtakId for særbidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.vedtaksid = 1
        behandling.husstandsmedlem = mutableSetOf()
        testdataManager.lagreBehandling(behandling)

        stubUtils.stubHentSak(opprettSakForBehandling(behandling))
        stubUtils.stubFatteVedtak()
        behandling.initGrunnlagRespons(stubUtils)
        // Trigge hente grunnlag
        val henteBehandlingResponse =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}",
                HttpMethod.GET,
                null,
                Void::class.java,
            )
        henteBehandlingResponse.statusCode shouldBe HttpStatus.OK
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
    fun `Skal ikke fatte vedtak hvis behandling har vedtakId`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false)
        behandling.vedtaksid = 1
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
                null,
                BeregningValideringsfeil::class.java,
            )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.headers[HttpHeaders.WARNING]!!.first() shouldContain "Validering feilet - Feil ved validering av behandling for beregning"
        response.body!!.måBekrefteNyeOpplysninger.map { it.type } shouldContainAll listOf(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER)
    }

    @Test
    fun `Skal ikke fatte vedtak hvis nyeste opplysninger ikke er aktivert for klage`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, Vedtakstype.KLAGE)
        behandling.omgjøringsdetaljer?.omgjørVedtakId = 1
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
                null,
                BeregningValideringsfeil::class.java,
            )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.headers[HttpHeaders.WARNING]!!.first() shouldContain "Validering feilet - Feil ved validering av behandling for beregning"
        response.body!!.måBekrefteNyeOpplysninger.map { it.type } shouldContainAll listOf(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER)
    }

    private fun save(
        behandling: Behandling,
        fil: String = "grunnlagresponse",
    ) {
        try {
            behandlingRepository.save(behandling)
            val grunnlag =
                opprettAlleAktiveGrunnlagFraFil(
                    behandling,
                    erstattVariablerITestFil(fil),
                )
            grunnlagRepository.saveAll(grunnlag)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
