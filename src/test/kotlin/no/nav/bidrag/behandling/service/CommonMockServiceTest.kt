package no.nav.bidrag.behandling.service

import io.getunleash.FakeUnleash
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkClass
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.controller.v2.BehandlingControllerV2
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
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.inntekt.InntektApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import stubHusstandrepository
import stubInntektRepository
import stubPersonConsumer
import stubPersonRepository

@ExtendWith(MockKExtension::class)
abstract class CommonMockServiceTest {
    lateinit var virkningstidspunktService: VirkningstidspunktService

    @MockK
    lateinit var underholdService: UnderholdService
    val notatService = NotatService()

    lateinit var behandlingControllerV2: BehandlingControllerV2

    @MockK
    lateinit var grunnlagConsumer: BidragGrunnlagConsumer

    @MockK
    lateinit var vedtakConsumer: BidragVedtakConsumer

//    @MockK
//    lateinit var vedtakLocalConsumer: BidragVedtakConsumerLocal

    lateinit var boforholdService: BoforholdService

    @MockK
    lateinit var grunnlagRepository: GrunnlagRepository

    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @MockK
    lateinit var inntektService: InntektService

    @MockK
    lateinit var behandlingService: BehandlingService

    @MockK
    lateinit var grunnlagService: GrunnlagService

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @MockK
    lateinit var sivilstandRepository: SivilstandRepository

    @MockK
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    @MockK
    lateinit var utgiftService: UtgiftService

    lateinit var vedtakService: VedtakService

    @MockK
    lateinit var gebyrService: GebyrService

    @MockK
    lateinit var samværRepository: SamværRepository
    lateinit var samværService: SamværService
    lateinit var inntektRepository: InntektRepository

    lateinit var personRepository: PersonRepository
    lateinit var personConsumer: BidragPersonConsumer
    lateinit var dtomapper: Dtomapper
    lateinit var husstandsmedlemRepository: HusstandsmedlemRepository

    @BeforeEach
    fun init() {
        inntektRepository = stubInntektRepository()
        personConsumer = stubPersonConsumer()
        personRepository = stubPersonRepository()
        husstandsmedlemRepository = stubHusstandrepository()
        stubSjablonService()
        stubSjablonProvider()
        samværService = SamværService(samværRepository, behandlingRepository, notatService, BeregnSamværsklasseApi(stubSjablonService()))
        val validerBeregning = ValiderBeregning()
        val personService = PersonService(personConsumer)
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
        dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validerBeregning,
                validerBehandlingService,
                vedtakGrunnlagMapper,
                BeregnBarnebidragApi(),
            )
        boforholdService = BoforholdService(behandlingRepository, husstandsmedlemRepository, notatService, sivilstandRepository, dtomapper)
        underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                personService,
            )
        val unleash = FakeUnleash()
        unleash.enableAll()

        grunnlagService =
            GrunnlagService(grunnlagConsumer, boforholdService, grunnlagRepository, InntektApi(""), inntektService, dtomapper, underholdService, barnebidragGrunnlagInnhenting, vedtakConsumer)
        inntektService = InntektService(behandlingRepository, inntektRepository, notatService)
        boforholdService = BoforholdService(behandlingRepository, husstandsmedlemRepository, notatService, sivilstandRepository, dtomapper)
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
        vedtakService = mockkClass(VedtakService::class)
        behandlingControllerV2 =
            BehandlingControllerV2(
                vedtakService,
                behandlingService,
                gebyrService,
                boforholdService,
                inntektService,
                utgiftService,
                validerBehandlingService,
                dtomapper,
                virkningstidspunktService,
            )
    }
}
