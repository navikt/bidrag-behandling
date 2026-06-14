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
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.service.forholdsmessigfordeling.ForholdsmessigFordelingService
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragPeriodeResponse
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderResponse
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
        service.grenseSynkroniserFF = "60"

        every { grunnlagService.lagreBeløpshistorikkGrunnlag(any()) } returns emptyMap<Grunnlagsdatatype, GrunnlagFeilDto>()
        every { grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(any()) } returns
            emptyMap<Grunnlagsdatatype, GrunnlagFeilDto>()
        every { bbmConsumer.hentÅpneSøknaderForBp(any()) } returns
            mockk<HentBPsÅpneSøknaderResponse> {
                every { åpneSøknader } returns emptyList()
            }
        every { bbmConsumer.hentSøknad(any()) } returns null
        every { bbmConsumer.feilregistrerSøknad(any()) } returns Unit
        every { bbmConsumer.fjernSammenknytning(any()) } returns Unit
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
        verify(exactly = 1) { bbmConsumer.fjernSammenknytning(999L) }
        verify(exactly = 1) { bbmConsumer.endreSammenknytningSøknad(behandling.soknadsid!!, behandling.soknadsid!!) }
    }

    @Test
    fun `skal ikke synkronisere sammenknytninger nar behandling ikke er ff`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.forholdsmessigFordeling = null

        service.synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling)

        verify(exactly = 0) { bbmConsumer.finnSammenknytningerHovedsøknad(any()) }
        verify(exactly = 0) { bbmConsumer.fjernSammenknytning(any()) }
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
        verify(exactly = 0) { bbmConsumer.fjernSammenknytning(any()) }
        behandling.soknadsid shouldBe behandling.soknadsid
    }

    @Test
    fun `skal feilregistrere duplicate apne ff soknader og beholde eldste`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val rolle = behandling.søknadsbarn.first()

        val eldste =
            opprettSøknad(
                søknadsid = 1001L,
                søknadFomDato = LocalDate.parse("2024-01-01"),
                mottattDato = LocalDate.parse("2024-01-10"),
                behandlingstype = behandling.behandlingstypeForFF,
            )
        val duplikat1 =
            opprettSøknad(
                søknadsid = 1002L,
                søknadFomDato = LocalDate.parse("2024-02-01"),
                mottattDato = LocalDate.parse("2024-02-10"),
                behandlingstype = behandling.behandlingstypeForFF,
            )
        val duplikat2 =
            opprettSøknad(
                søknadsid = 1003L,
                søknadFomDato = LocalDate.parse("2024-03-01"),
                mottattDato = LocalDate.parse("2024-03-10"),
                behandlingstype = behandling.behandlingstypeForFF,
            )

        rolle.forholdsmessigFordeling =
            ForholdsmessigFordelingRolle(
                tilhørerSak = behandling.saksnummer,
                behandlerenhet = behandling.behandlerEnhet,
                delAvOpprinneligBehandling = true,
                erRevurdering = true,
                bidragsmottaker = behandling.bidragsmottaker?.ident,
                søknader = mutableSetOf(eldste, duplikat1, duplikat2),
            )

        service.slettDuplikatForholdsmessigFordelingSøknader(behandling)

        verify(exactly = 1) { bbmConsumer.feilregistrerSøknad(match { it.søknadsid == 1002L }) }
        verify(exactly = 1) { bbmConsumer.feilregistrerSøknad(match { it.søknadsid == 1003L }) }
        verify(exactly = 1) { bbmConsumer.fjernSammenknytning(1002L) }
        verify(exactly = 1) { bbmConsumer.fjernSammenknytning(1003L) }

        eldste.status shouldBe Behandlingstatus.UNDER_BEHANDLING
        duplikat1.status shouldBe Behandlingstatus.FEILREGISTRERT
        duplikat2.status shouldBe Behandlingstatus.FEILREGISTRERT
    }

    @Test
    fun `skal ikke feilregistrere nar det bare finnes en apen ff soknad`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val rolle = behandling.søknadsbarn.first()

        rolle.forholdsmessigFordeling =
            ForholdsmessigFordelingRolle(
                tilhørerSak = behandling.saksnummer,
                behandlerenhet = behandling.behandlerEnhet,
                delAvOpprinneligBehandling = true,
                erRevurdering = true,
                bidragsmottaker = behandling.bidragsmottaker?.ident,
                søknader =
                    mutableSetOf(
                        opprettSøknad(
                            søknadsid = 1001L,
                            søknadFomDato = LocalDate.parse("2024-01-01"),
                            mottattDato = LocalDate.parse("2024-01-10"),
                            behandlingstype = behandling.behandlingstypeForFF,
                        ),
                        opprettSøknad(
                            søknadsid = 2001L,
                            søknadFomDato = LocalDate.parse("2024-01-01"),
                            mottattDato = LocalDate.parse("2024-01-10"),
                            behandlingstype = Behandlingstype.ALDERSJUSTERING,
                        ),
                        opprettSøknad(
                            søknadsid = 2002L,
                            søknadFomDato = LocalDate.parse("2024-01-01"),
                            mottattDato = LocalDate.parse("2024-01-10"),
                            behandlingstype = behandling.behandlingstypeForFF,
                            status = Behandlingstatus.FEILREGISTRERT,
                        ),
                    ),
            )

        service.slettDuplikatForholdsmessigFordelingSøknader(behandling)

        verify(exactly = 0) { bbmConsumer.feilregistrerSøknad(any()) }
        verify(exactly = 0) { bbmConsumer.fjernSammenknytning(any()) }
    }

    @Test
    fun `skal beholde apen status for duplikat nar feilregistrering i bbm feiler`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val rolle = behandling.søknadsbarn.first()

        val eldste =
            opprettSøknad(
                søknadsid = 1001L,
                søknadFomDato = LocalDate.parse("2024-01-01"),
                mottattDato = LocalDate.parse("2024-01-10"),
                behandlingstype = behandling.behandlingstypeForFF,
            )
        val vellykketDuplikat =
            opprettSøknad(
                søknadsid = 1002L,
                søknadFomDato = LocalDate.parse("2024-02-01"),
                mottattDato = LocalDate.parse("2024-02-10"),
                behandlingstype = behandling.behandlingstypeForFF,
            )
        val feiletDuplikat =
            opprettSøknad(
                søknadsid = 1003L,
                søknadFomDato = LocalDate.parse("2024-03-01"),
                mottattDato = LocalDate.parse("2024-03-10"),
                behandlingstype = behandling.behandlingstypeForFF,
            )

        rolle.forholdsmessigFordeling =
            ForholdsmessigFordelingRolle(
                tilhørerSak = behandling.saksnummer,
                behandlerenhet = behandling.behandlerEnhet,
                delAvOpprinneligBehandling = true,
                erRevurdering = true,
                bidragsmottaker = behandling.bidragsmottaker?.ident,
                søknader = mutableSetOf(eldste, vellykketDuplikat, feiletDuplikat),
            )

        every { bbmConsumer.feilregistrerSøknad(match { it.søknadsid == 1003L }) } throws RuntimeException("BBM feil")

        service.slettDuplikatForholdsmessigFordelingSøknader(behandling)

        verify(exactly = 1) { bbmConsumer.feilregistrerSøknad(match { it.søknadsid == 1002L }) }
        verify(exactly = 1) { bbmConsumer.feilregistrerSøknad(match { it.søknadsid == 1003L }) }
        verify(exactly = 1) { bbmConsumer.fjernSammenknytning(1002L) }
        verify(exactly = 0) { bbmConsumer.fjernSammenknytning(1003L) }

        eldste.status shouldBe Behandlingstatus.UNDER_BEHANDLING
        vellykketDuplikat.status shouldBe Behandlingstatus.FEILREGISTRERT
        feiletDuplikat.status shouldBe Behandlingstatus.UNDER_BEHANDLING
    }

    private fun opprettSøknad(
        søknadsid: Long,
        søknadFomDato: LocalDate,
        mottattDato: LocalDate,
        behandlingstype: Behandlingstype,
        status: Behandlingstatus = Behandlingstatus.UNDER_BEHANDLING,
    ) = ForholdsmessigFordelingSøknadBarn(
        mottattDato = mottattDato,
        søknadFomDato = søknadFomDato,
        søktAvType = SøktAvType.NAV_BIDRAG,
        søknadsid = søknadsid,
        behandlingstype = behandlingstype,
        behandlingstema = Behandlingstema.BIDRAG,
        innkreving = true,
        status = status,
    )
}
