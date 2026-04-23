package no.nav.bidrag.behandling.service

import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.dto.FinnSammenknytningerHovedsøknadResponse
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragPeriodeResponse
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderResponse
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForholdsmessigFordelingServiceSynkroniseringTest {
    @MockK
    lateinit var sakConsumer: BidragSakConsumer

    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @MockK
    lateinit var behandlingService: BehandlingService

    @MockK
    lateinit var beløpshistorikkConsumer: BidragBeløpshistorikkConsumer

    @MockK
    lateinit var grunnlagService: GrunnlagService

    @MockK
    lateinit var bbmConsumer: BidragBBMConsumer

    @MockK
    lateinit var forsendelseService: ForsendelseService

    @MockK
    lateinit var beregningService: BeregningService

    @MockK
    lateinit var virkningstidspunktService: VirkningstidspunktService

    @MockK
    lateinit var underholdService: UnderholdService

    private lateinit var service: ForholdsmessigFordelingService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            ForholdsmessigFordelingService(
                sakConsumer,
                behandlingRepository,
                behandlingService,
                beløpshistorikkConsumer,
                grunnlagService,
                bbmConsumer,
                forsendelseService,
                beregningService,
                virkningstidspunktService,
                underholdService,
            )

        every { grunnlagService.lagreBeløpshistorikkGrunnlag(any()) } returns emptyMap<Grunnlagsdatatype, GrunnlagFeilDto>()
        every { grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(any()) } returns
            emptyMap<Grunnlagsdatatype, GrunnlagFeilDto>()
        every { bbmConsumer.hentÅpneSøknaderForBp(any()) } returns
            mockk<HentBPsÅpneSøknaderResponse> {
                every { åpneSøknader } returns emptyList()
            }
        every { bbmConsumer.hentSøknad(any()) } returns null
        every { bbmConsumer.fjernSammeknytning(any()) } returns Unit
        every { bbmConsumer.endreSammenknytningSøknad(any(), any()) } returns null
        every { bbmConsumer.sammeknyttSøknader(any(), any()) } returns null
        every { beløpshistorikkConsumer.hentAlleLøpendeStønaderIPeriode(any()) } returns
            mockk<LøpendeBidragPeriodeResponse> {
                every { bidragListe } returns emptyList()
            }
    }

    @Test
    fun `skal fjerne gamle sammenknytninger og legge til manglende hovedsoknad for ff behandling`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.forholdsmessigFordeling = ForholdsmessigFordeling(erHovedbehandling = true)

        val annenSøknad = mockk<HentSøknad> { every { søknadsid } returns 999L }
        every { bbmConsumer.finnSammenknytningerHovedsøknad(behandling.soknadsid!!) } returns
            FinnSammenknytningerHovedsøknadResponse(søknader = listOf(annenSøknad))

        service.synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling)

        verify(exactly = 1) { grunnlagService.lagreBeløpshistorikkGrunnlag(behandling) }
        verify(exactly = 1) { grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(behandling) }
        verify(exactly = 1) { bbmConsumer.fjernSammeknytning(999L) }
        verify(exactly = 1) { bbmConsumer.endreSammenknytningSøknad(behandling.soknadsid!!, behandling.soknadsid!!) }
    }

    @Test
    fun `skal ikke synkronisere sammenknytninger nar behandling ikke er ff`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.forholdsmessigFordeling = null

        service.synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling)

        verify(exactly = 0) { bbmConsumer.finnSammenknytningerHovedsøknad(any()) }
        verify(exactly = 0) { bbmConsumer.fjernSammeknytning(any()) }
        verify(exactly = 0) { bbmConsumer.endreSammenknytningSøknad(any(), any()) }
    }

    @Test
    fun `skal ikke endre hovedsoknadsknytning nar hovedsoknad finnes`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.forholdsmessigFordeling = ForholdsmessigFordeling(erHovedbehandling = true)

        val hovedSøknad = mockk<HentSøknad> { every { søknadsid } returns behandling.soknadsid!! }
        every { bbmConsumer.finnSammenknytningerHovedsøknad(behandling.soknadsid!!) } returns
            FinnSammenknytningerHovedsøknadResponse(søknader = listOf(hovedSøknad))

        service.synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling)

        verify(exactly = 0) { bbmConsumer.endreSammenknytningSøknad(any(), any()) }
        verify(exactly = 0) { bbmConsumer.fjernSammeknytning(any()) }
        behandling.soknadsid shouldBe behandling.soknadsid
    }
}
