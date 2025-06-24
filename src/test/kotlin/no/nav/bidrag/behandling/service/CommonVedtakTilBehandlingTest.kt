package no.nav.bidrag.behandling.service

import io.getunleash.FakeUnleash
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.behandling.utils.testdata.opprettEngangsbeløp
import no.nav.bidrag.behandling.utils.testdata.opprettVedtakDto
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.beregn.barnebidrag.service.AldersjusteringOrchestrator
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import stubBehandlingrepository
import stubPersonConsumer
import stubPersonRepository
import stubSaksbehandlernavnProvider
import stubTokenUtils
import stubUnderholdskostnadRepository

@ExtendWith(MockKExtension::class)
abstract class CommonVedtakTilBehandlingTest : CommonMockServiceTest() {
    @MockK
    lateinit var bidragStønadConsumer: BidragBeløpshistorikkConsumer

    @MockK
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockK
    lateinit var aldersjusteringOrchestrator: AldersjusteringOrchestrator

    @MockK
    lateinit var sakConsumer: BidragSakConsumer

    lateinit var beregningService: BeregningService
    val unleash = FakeUnleash()

    @BeforeEach
    fun initMocks() {
        stubUnderholdskostnadRepository(underholdskostnadRepository)
        stubBehandlingrepository(behandlingRepository)
        val validerBeregning = ValiderBeregning()
        personRepository = stubPersonRepository()
        personConsumer = stubPersonConsumer()
        barnebidragGrunnlagInnhenting = BarnebidragGrunnlagInnhenting(bidragStønadConsumer)
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns null
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
        val underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                personService,
            )
        val vedtakTilBehandlingMapping = VedtakTilBehandlingMapping(validerBeregning, underholdService = underholdService, personRepository, behandlingRepository)
        beregningService =
            BeregningService(
                behandlingService,
                vedtakGrunnlagMapper,
                aldersjusteringOrchestrator,
            )
        val behandlingTilVedtakMapping =
            BehandlingTilVedtakMapping(
                sakConsumer,
                vedtakGrunnlagMapper,
                beregningService,
                vedtakConsumer,
            )

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
//        every { grunnlagService.oppdatereGrunnlagForBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangPersonISak(any(), any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangVedtak(any()) } returns Unit
        every { notatOpplysningerService.opprettNotat(any()) } returns "213"
        every { behandlingService.oppdaterVedtakFattetStatus(any(), any(), any()) } returns Unit
        every { validerBehandlingService.validerKanBehandlesINyLøsning(any()) } returns Unit
        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        every { vedtakConsumer.hentVedtak(any()) } returns
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        opprettEngangsbeløp(Engangsbeløptype.GEBYR_MOTTAKER).copy(
                            kravhaver = personIdentNav,
                            skyldner = Personident(testdataBM.ident),
                        ),
                        opprettEngangsbeløp(Engangsbeløptype.GEBYR_SKYLDNER).copy(
                            kravhaver = personIdentNav,
                            skyldner = Personident(testdataBP.ident),
                        ),
                        opprettEngangsbeløp(Engangsbeløptype.DIREKTE_OPPGJØR).copy(
                            kravhaver = Personident(testdataBarn1.ident),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            )
        stubSjablonProvider()
        stubPersonConsumer()
        stubTokenUtils()
        stubSaksbehandlernavnProvider()
        stubKodeverkProvider()
    }
}
