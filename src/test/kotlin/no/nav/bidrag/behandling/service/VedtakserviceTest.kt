package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.SivilstandRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.initGrunnlagRespons
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import stubPersonConsumer
import stubTokenUtils
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
class VedtakserviceTest : TestContainerRunner() {
    protected val testNotatJournalpostId = "123123123"
    protected val testVedtakResponsId = 1

    @Autowired
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @SpykBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @Autowired
    lateinit var sivilstandRepository: SivilstandRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockBean
    lateinit var bidragPersonConsumer: BidragPersonConsumer

    @Autowired
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @Autowired
    lateinit var entityManager: EntityManager

    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService

    @MockK
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @MockK
    lateinit var personRepository: PersonRepository

    val unleash = FakeUnleash()
    val notatService = NotatService()

    @BeforeEach
    fun initMocks() {
        clearAllMocks()
        stubTokenUtils()
        unleash.enableAll()
        bidragPersonConsumer = stubPersonConsumer()
        val personService = PersonService(bidragPersonConsumer)
        val validerBeregning = ValiderBeregning()
        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val dtomapper =
            Dtomapper(tilgangskontrollService, validerBeregning, validerBehandlingService, behandlingTilGrunnlagMappingV2)
        val underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                dtomapper,
            )
        val vedtakTilBehandlingMapping = VedtakTilBehandlingMapping(validerBeregning, underholdService)
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                validerBeregning,
                evnevurderingService,
                personService,
            )

        beregningService =
            BeregningService(
                behandlingService,
                vedtakGrunnlagMapper,
            )
        val behandlingTilVedtakMapping = BehandlingTilVedtakMapping(sakConsumer, vedtakGrunnlagMapper, beregningService)
        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                tilgangskontrollService,
                vedtakConsumer,
                unleash,
                validerBeregning,
                vedtakTilBehandlingMapping,
                behandlingTilVedtakMapping,
                validerBehandlingService,
            )
        every { notatOpplysningerService.opprettNotat(any()) } returns testNotatJournalpostId
        every { tilgangskontrollService.sjekkTilgangPersonISak(any(), any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangVedtak(any()) } returns Unit
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        stubUtils.stubFatteVedtak()
        stubUtils.stubAlleBidragVedtakForStønad()
        stubUtils.stubBidraBBMHentBeregning()
        stubUtils.stubBidragStonadLøpendeSaker()
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak for forskudd`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.FORSKUDD)
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.engangsbeloptype = null
        behandling.stonadstype = Stønadstype.FORSKUDD
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        behandling.initGrunnlagRespons(stubUtils)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe shouldHaveSize 0
            request.stønadsendringListe[0].type shouldBe Stønadstype.FORSKUDD
            request.stønadsendringListe[1].type shouldBe Stønadstype.FORSKUDD
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak for særbidrag`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        behandling.initGrunnlagRespons(stubUtils)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            request.engangsbeløpListe.first().type shouldBe Engangsbeløptype.SÆRBIDRAG
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }
}

fun Behandling.taMedInntekt(
    rolle: Rolle,
    type: Inntektsrapportering,
) {
    val inntekt = inntekter.find { it.ident == rolle.ident && type == it.type }!!
    inntekt.taMed = true
    inntekt.datoFom = virkningstidspunkt
}
