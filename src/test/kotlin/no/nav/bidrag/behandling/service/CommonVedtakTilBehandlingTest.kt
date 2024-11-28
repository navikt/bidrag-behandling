package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.mockk.every
import io.mockk.impl.annotations.MockK
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import stubPersonRepository
import stubSaksbehandlernavnProvider
import stubTokenUtils
import stubUnderholdskostnadRepository

@ExtendWith(SpringExtension::class)
abstract class CommonVedtakTilBehandlingTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockkBean
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    lateinit var personRepository: PersonRepository

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer
    lateinit var personConsumer: BidragPersonConsumer
    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService
    lateinit var dtomapper: Dtomapper
    val unleash = FakeUnleash()
    val notatService = NotatService()

    @BeforeEach
    fun initMocks() {
        stubUnderholdskostnadRepository(underholdskostnadRepository)
        val validerBeregning = ValiderBeregning()
        personRepository = stubPersonRepository()
        personConsumer = stubPersonConsumer()
        val personService = PersonService(personConsumer)
        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validerBeregning,
                validerBehandlingService,
                VedtakGrunnlagMapper(behandlingTilGrunnlagMappingV2, validerBeregning, evnevurderingService, personService),
                BeregnBarnebidragApi(),
            )
        val underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                dtomapper,
            )
        val vedtakTilBehandlingMapping = VedtakTilBehandlingMapping(validerBeregning, underholdService = underholdService)
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
        unleash.enableAll()
        every { grunnlagService.oppdatereGrunnlagForBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangPersonISak(any(), any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangVedtak(any()) } returns Unit
        every { notatOpplysningerService.opprettNotat(any()) } returns "213"
        every { behandlingService.oppdaterVedtakFattetStatus(any(), any()) } returns Unit
        every { validerBehandlingService.validerKanBehandlesINyLøsning(any()) } returns Unit
        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubPersonConsumer()
        stubTokenUtils()
        stubSaksbehandlernavnProvider()
        stubKodeverkProvider()
    }
}
