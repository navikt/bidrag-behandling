package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.HusstandsmedlemRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.database.repository.SivilstandRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.disableUnleashFeature
import no.nav.bidrag.behandling.utils.enableUnleashFeature
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.stubTokenUtils
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.initGrunnlagRespons
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilSkatteprosentPåBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettEngangsbeløp
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettVedtakDto
import no.nav.bidrag.behandling.utils.testdata.synkSøknadsbarnVirkningstidspunkt
import no.nav.bidrag.behandling.utils.testdata.taMedInntekt
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.AldersjusteringOrchestrator
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.BidragsberegningOrkestrator
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.HentLøpendeBidragService
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.OmgjøringOrkestrator
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.OmgjøringOrkestratorV2
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class VedtakserviceTest : TestContainerRunner() {
    protected val testNotatJournalpostId = "123123123"
    protected val testVedtakResponsId = 1

    @Autowired
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var aldersjusteringOrchestrator: AldersjusteringOrchestrator

    @MockkBean
    lateinit var bidragsberegningOrkestrator: BidragsberegningOrkestrator

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @SpykBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

//    @SpykBean
//    lateinit var vedtakLocalConsumer: BidragVedtakConsumerLocal

    @MockkBean
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var husstandsmedlemRepository: HusstandsmedlemRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @Autowired
    lateinit var inntektRepository: InntektRepository

    @Autowired
    lateinit var samværRepository: SamværRepository

    @Autowired
    lateinit var sivilstandRepository: SivilstandRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockkBean
    lateinit var bidragPersonConsumer: BidragPersonConsumer

    @MockkBean
    lateinit var vedtakService2: no.nav.bidrag.beregn.barnebidrag.service.external.VedtakService

    @Autowired
    lateinit var bidragStønadConsumer: BidragBeløpshistorikkConsumer

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

    @MockK
    lateinit var forsendelseService: ForsendelseService

    @MockK
    lateinit var hentLøpendeBidragService: HentLøpendeBidragService

    @MockkBean
    lateinit var klageOrkestrator: OmgjøringOrkestrator

    @MockkBean
    lateinit var klageOrkestratorV2: OmgjøringOrkestratorV2

    @MockkBean
    lateinit var virkningstidspunktService: VirkningstidspunktService
    val notatService = NotatService()

    @BeforeEach
    fun initMocks() {
        clearAllMocks()
        stubTokenUtils()
        enableUnleashFeature(UnleashFeatures.BIDRAG_KLAGE)
        enableUnleashFeature(UnleashFeatures.BEGRENSET_REVURDERING)
        disableUnleashFeature(UnleashFeatures.VEDTAKSSPERRE)
        bidragPersonConsumer = stubPersonConsumer()
        bidragsberegningOrkestrator = BidragsberegningOrkestrator(BeregnBarnebidragApi(), klageOrkestrator, klageOrkestratorV2, hentLøpendeBidragService, bidragPersonConsumer, sakConsumer)
        every { barnebidragGrunnlagInnhenting.hentBeløpshistorikk(any(), any(), any(), any()) } returns null
        every { barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(any(), any()) } returns emptySet()
        val personService = PersonService(bidragPersonConsumer)
        val validerBeregning = ValiderBeregning()
        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                behandlingTilGrunnlagMappingV2,
                validerBeregning,
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
        val dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validerBeregning,
                validerBehandlingService,
                vedtakGrunnlagMapper,
                BeregnBarnebidragApi(),
            )
        val boforholdService = BoforholdService(behandlingRepository, husstandsmedlemRepository, notatService, sivilstandRepository, dtomapper)

        val inntektService = InntektService(behandlingRepository, inntektRepository, notatService)

        val underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                personService,
            )
        val samværService = SamværService(samværRepository, behandlingRepository, notatService, BeregnSamværsklasseApi(stubSjablonService()))
        virkningstidspunktService =
            VirkningstidspunktService(
                behandlingRepository,
                boforholdService,
                notatService,
                grunnlagService,
                inntektService,
                samværService,
                underholdService,
                GebyrService(vedtakGrunnlagMapper),
            )
        beregningService =
            BeregningService(
                behandlingService,
                vedtakGrunnlagMapper,
                aldersjusteringOrchestrator,
                bidragsberegningOrkestrator,
            )

        val vedtakTilBehandlingMapping = VedtakTilBehandlingMapping(validerBeregning, underholdService, behandlingRepository)

        val behandlingTilVedtakMapping =
            BehandlingTilVedtakMapping(
                sakConsumer,
                vedtakGrunnlagMapper,
                beregningService,
                vedtakConsumer,
                vedtakService2,
            )
        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                tilgangskontrollService,
                vedtakConsumer,
                validerBeregning,
                vedtakTilBehandlingMapping,
                behandlingTilVedtakMapping,
                validerBehandlingService,
                forsendelseService,
                virkningstidspunktService,
            )
        every { notatOpplysningerService.opprettNotat(any()) } returns testNotatJournalpostId
        every { tilgangskontrollService.sjekkTilgangPersonISak(any(), any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangVedtak(any()) } returns Unit
        every { vedtakService2.hentAlleVedtakForStønad(any(), any(), any()) } returns emptyList()
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        stubUtils.stubFatteVedtak()
        stubUtils.stubHenteVedtak(
            opprettVedtakDto().copy(
                engangsbeløpListe = listOf(opprettEngangsbeløp()),
            ),
        )
        stubUtils.stubAlleBidragVedtakForStønad()
        stubUtils.stubBidraBBMHentBeregning()
        stubUtils.stubBidragBeløpshistorikkLøpendeSaker()
        stubUtils.stubBidragStønaderForSkyldner()
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak for forskudd`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.FORSKUDD)
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.synkSøknadsbarnVirkningstidspunkt()
        behandling.engangsbeloptype = null
        behandling.stonadstype = Stønadstype.FORSKUDD
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        behandling.initGrunnlagRespons(stubUtils)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        behandling.leggTilSkatteprosentPåBarnetillegg()
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
    fun `Skal fatte vedtak for bidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilNotat(
            "Notat samvær BARN",
            NotatGrunnlag.NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Notat underholdskostnad BARN",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Notat underholdskostnad Andre barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.søknadsbarn.first()!!,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.søktFomDato = LocalDate.parse("2023-03-01")
        behandling.virkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.søknadsbarn.first().innbetaltBeløp = BigDecimal(500)
        behandling.søknadsbarn.first().virkningstidspunkt = behandling.virkningstidspunkt
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null))
        behandling.leggTilTillegsstønad(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(4), null))
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), testdataHusstandsmedlem1)
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!, null))
        behandling.leggTilBarnetilsyn(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null))
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!)

        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        stubUtils.stubBidragBeløpshistorikkHistoriskeSaker()
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.leggTilSkatteprosentPåBarnetillegg()
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_12MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_12MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldHaveSize(1)
            request.engangsbeløpListe shouldHaveSize 3
//            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
//                request.grunnlagListe shouldHaveSize 200
//            }
        }

        assertSoftly(opprettVedtakRequest) {
            hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 7
            hentGrunnlagstyper(Grunnlagstype.TILLEGGSSTØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.FAKTISK_UTGIFT_PERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSPERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSKALKULATOR) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 28
            hentGrunnlagstyper(Grunnlagstype.SJABLON_BIDRAGSEVNE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.SJABLON_MAKS_FRADRAG) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SJABLON_MAKS_TILSYN) shouldHaveSize 4
            hentGrunnlagstyper(Grunnlagstype.SJABLON_FORBRUKSUTGIFTER) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SAMVARSFRADRAG) shouldHaveSize 8
            hentGrunnlagstyper(Grunnlagstype.SJABLON_TRINNVIS_SKATTESATS) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 8
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_TILLEGGSSTØNAD_BEGRENSET) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 12
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }
}
