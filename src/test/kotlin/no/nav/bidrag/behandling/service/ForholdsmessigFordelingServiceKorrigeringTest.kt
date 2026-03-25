package no.nav.bidrag.behandling.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragPeriodeResponse
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentBPsÅpneSøknaderResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ForholdsmessigFordelingServiceKorrigeringTest {
    @MockK
    private lateinit var sakConsumer: BidragSakConsumer

    @MockK
    private lateinit var behandlingRepository: BehandlingRepository

    @MockK
    private lateinit var behandlingService: BehandlingService

    @MockK
    private lateinit var belopshistorikkConsumer: BidragBeløpshistorikkConsumer

    @MockK
    private lateinit var grunnlagService: GrunnlagService

    @MockK(relaxed = true)
    private lateinit var bbmConsumer: BidragBBMConsumer

    @MockK
    private lateinit var forsendelseService: ForsendelseService

    @MockK
    private lateinit var beregningService: BeregningService

    @MockK
    private lateinit var virkningstidspunktService: VirkningstidspunktService

    @MockK
    private lateinit var underholdService: UnderholdService

    private lateinit var service: ForholdsmessigFordelingService

    @BeforeEach
    fun setup() {
        service =
            ForholdsmessigFordelingService(
                sakConsumer = sakConsumer,
                behandlingRepository = behandlingRepository,
                behandlingService = behandlingService,
                beløpshistorikkConsumer = belopshistorikkConsumer,
                grunnlagService = grunnlagService,
                bbmConsumer = bbmConsumer,
                forsendelseService = forsendelseService,
                beregningService = beregningService,
                virkningstidspunktService = virkningstidspunktService,
                underholdService = underholdService,
            )
        every { belopshistorikkConsumer.hentAlleLøpendeStønaderIPeriode(any()) }.returns(LøpendeBidragPeriodeResponse())
        every { grunnlagService.lagreBeløpshistorikkGrunnlag(any()) }.returns(emptyMap())
        every { grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(any()) }.returns(emptyMap())
        every { behandlingRepository.finnÅpneBidragsbehandlingerForBp(any(), any()) }.returns(emptyList())
        every { sakConsumer.hentSakerPerson(any()) }.returns(emptyList())
    }

    @Test
    fun `skal opprette ny ff soknad nar barn mangler ff soknad`() {
        val behandling = lagBehandling()
        val barn = behandling.søknadsbarn.first()
        barn.forholdsmessigFordeling = lagFfDetaljer(erRevurdering = false)

        val serviceSpy = spyk(service)
        every { serviceSpy.opprettEllerOppdaterForholdsmessigFordeling(any(), any()) } answers { }
        stubApneSoknaderTom()

        serviceSpy.`synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling`(behandling)

        verify(exactly = 1) {
            serviceSpy.opprettEllerOppdaterForholdsmessigFordeling(behandling.id!!, reevaluerSøkndasbarn = Pair(barn.ident!!, barn.stønadstype))
        }
    }

    @Test
    fun `skal ikke sette feilregistrert lokalt nar feilregistrering mot bbm feiler`() {
        val behandling = lagBehandling()
        val barn = behandling.søknadsbarn.first()
        val soknadSomSkalBeholdes = lagFfSoknad(soknadsid = AKTIV_SOKNAD_ID)
        val soknadSomFeiler = lagFfSoknad(soknadsid = SOKNAD_SOM_SKAL_FEILREGISTRERES)

        barn.forholdsmessigFordeling =
            lagFfDetaljer(
                erRevurdering = false,
                soknader = mutableSetOf(soknadSomSkalBeholdes, soknadSomFeiler),
            )

        stubApneSoknaderTom()
        every { bbmConsumer.hentSøknad(any()) } throws RuntimeException("BBM utilgjengelig")
        every {
            bbmConsumer.feilregistrerSøknad(
                match { request -> request == FeilregistrerSøknadRequest(SOKNAD_SOM_SKAL_FEILREGISTRERES) },
            )
        } throws RuntimeException("Feilet feilregistrering")

        service.`synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling`(behandling)

        soknadSomFeiler.status shouldBe Behandlingstatus.UNDER_BEHANDLING
    }

    @Test
    fun `skal sette feilregistrert lokalt nar feilregistrering mot bbm lykkes`() {
        val behandling = lagBehandling()
        val barn = behandling.søknadsbarn.first()
        val soknadSomSkalBeholdes = lagFfSoknad(soknadsid = AKTIV_SOKNAD_ID)
        val soknadSomSkalFeilregistreres = lagFfSoknad(soknadsid = SOKNAD_SOM_SKAL_FEILREGISTRERES)

        barn.forholdsmessigFordeling =
            lagFfDetaljer(
                erRevurdering = false,
                soknader = mutableSetOf(soknadSomSkalBeholdes, soknadSomSkalFeilregistreres),
            )

        stubApneSoknaderTom()
        every { bbmConsumer.hentSøknad(any()) } throws RuntimeException("BBM utilgjengelig")
        every {
            bbmConsumer.feilregistrerSøknad(
                match { request -> request == FeilregistrerSøknadRequest(SOKNAD_SOM_SKAL_FEILREGISTRERES) },
            )
        } answers { }

        service.`synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling`(behandling)

        soknadSomSkalFeilregistreres.status shouldBe Behandlingstatus.FEILREGISTRERT
    }

    @Test
    fun `skal beholde status nar oppslag mot bbm feiler`() {
        val behandling = lagBehandling()
        val barn = behandling.søknadsbarn.first()
        val lagretSoknad = lagFfSoknad(soknadsid = AKTIV_SOKNAD_ID)

        barn.forholdsmessigFordeling =
            lagFfDetaljer(
                erRevurdering = true,
                soknader = mutableSetOf(lagretSoknad),
            )

        stubApneSoknaderTom()
        every { bbmConsumer.hentSøknad(any()) } throws RuntimeException("BBM utilgjengelig")

        service.synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling)

        lagretSoknad.status shouldBe Behandlingstatus.UNDER_BEHANDLING
    }

    private fun stubApneSoknaderTom() {
        every { bbmConsumer.hentÅpneSøknaderForBp(any()) } returns
            mockk<HentBPsÅpneSøknaderResponse> {
                every { åpneSøknader } returns emptyList()
            }
    }

    private fun lagBehandling(): Behandling {
        val behandling = oppretteBehandling(id = BEHANDLING_ID)
        behandling.roller = oppretteBehandlingRoller(behandling, medBp = true, typeBehandling = TypeBehandling.BIDRAG)
        return behandling
    }

    private fun lagFfDetaljer(
        erRevurdering: Boolean,
        soknader: MutableSet<ForholdsmessigFordelingSøknadBarn> = mutableSetOf(),
    ) = ForholdsmessigFordelingRolle(
        tilhørerSak = SAKSNUMMER,
        behandlerenhet = BEHANDLERENHET,
        delAvOpprinneligBehandling = true,
        erRevurdering = erRevurdering,
        bidragsmottaker = BIDRAGSMOTTAKER_IDENT,
        søknader = soknader,
    )

    private fun lagFfSoknad(soknadsid: Long) =
        ForholdsmessigFordelingSøknadBarn(
            mottattDato = MOTATT_DATO,
            søknadFomDato = SOKNAD_FOM_DATO,
            søktAvType = SøktAvType.NAV_BIDRAG,
            søknadsid = soknadsid,
            behandlingstype = Behandlingstype.FORHOLDSMESSIG_FORDELING,
            behandlingstema = Behandlingstema.BIDRAG,
            innkreving = true,
            saksnummer = SAKSNUMMER,
            status = Behandlingstatus.UNDER_BEHANDLING,
        )

    private companion object {
        const val BEHANDLING_ID = 1L
        const val AKTIV_SOKNAD_ID = 20L
        const val SOKNAD_SOM_SKAL_FEILREGISTRERES = 10L
        const val SAKSNUMMER = "1000001"
        const val BEHANDLERENHET = "4806"
        const val BIDRAGSMOTTAKER_IDENT = "01010112345"

        val MOTATT_DATO = java.time.LocalDate.parse("2026-01-15")
        val SOKNAD_FOM_DATO = java.time.LocalDate.parse("2026-02-01")
    }
}
