package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForholdsmessigFordelingKravhaverServiceTest {
    @MockK
    lateinit var sakConsumer: BidragSakConsumer

    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @MockK
    lateinit var beløpshistorikkConsumer: BidragBeløpshistorikkConsumer

    @MockK
    lateinit var bbmConsumer: BidragBBMConsumer

    private lateinit var service: ForholdsmessigFordelingKravhaverService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            spyk(
                ForholdsmessigFordelingKravhaverService(
                    sakConsumer = sakConsumer,
                    behandlingRepository = behandlingRepository,
                    beløpshistorikkConsumer = beløpshistorikkConsumer,
                    bbmConsumer = bbmConsumer,
                ),
            )
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal inkludere resultat fra andre oppslag`() {
        val behandling = mockk<Behandling>()

        val førsteOppslag =
            setOf(
                sakKravhaver("barn1", Stønadstype.BIDRAG),
                sakKravhaver("barn2", Stønadstype.BIDRAG18AAR),
            )
        val utenLøpende = listOf(sakKravhaver("barn3", Stønadstype.BIDRAG))
        val mellomresultat = førsteOppslag + utenLøpende
        val andreOppslag = setOf(sakKravhaver("barn4", Stønadstype.BIDRAG))

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        resultat.shouldContainExactlyInAnyOrder(førsteOppslag + utenLøpende + andreOppslag)
        verify(exactly = 1) { service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null) }
        verify(exactly = 1) { service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag) }
        verify(exactly = 1) { service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat) }
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal deduplisere når andre oppslag returnerer eksisterende kravhaver`() {
        val behandling = mockk<Behandling>()

        val kravhaverFraFørsteOppslag = sakKravhaver("barn1", Stønadstype.BIDRAG)
        val førsteOppslag = setOf(kravhaverFraFørsteOppslag)
        val utenLøpende = listOf(sakKravhaver("barn2", Stønadstype.BIDRAG18AAR))
        val mellomresultat = førsteOppslag + utenLøpende
        val andreOppslag = setOf(kravhaverFraFørsteOppslag)

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        resultat.shouldContainExactlyInAnyOrder(førsteOppslag + utenLøpende)
        verify(exactly = 1) { service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat) }
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal inkludere kravhavere fra åpne behandlinger`() {
        val behandling = mockk<Behandling>()
        val behandlingMedBarn1 =
            sakKravhaverMedÅpneBehandlinger(
                "barn1",
                Stønadstype.BIDRAG,
                åpneBehandlinger = setOf(mockk()),
            )

        val førsteOppslag = setOf(behandlingMedBarn1)
        val utenLøpende = emptyList<SakKravhaver>()
        val mellomresultat = førsteOppslag + utenLøpende
        val andreOppslag = emptySet<SakKravhaver>()

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        resultat.size shouldBe 1
        resultat.first().kravhaver shouldBe "barn1"
        resultat.first().åpneBehandlinger.size shouldBe 1
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal inkludere kravhavere fra åpne søknader`() {
        val behandling = mockk<Behandling>()
        val behandlingMedBarn2 =
            sakKravhaverMedÅpneSøknader(
                "barn2",
                Stønadstype.BIDRAG18AAR,
                åpneSøknader = setOf(mockk<HentSøknad>()),
            )

        val førsteOppslag = setOf(behandlingMedBarn2)
        val utenLøpende = emptyList<SakKravhaver>()
        val mellomresultat = førsteOppslag + utenLøpende
        val andreOppslag = emptySet<SakKravhaver>()

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        resultat.size shouldBe 1
        resultat.first().kravhaver shouldBe "barn2"
        resultat.first().åpneSøknader.size shouldBe 1
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal inkludere kravhavere fra privat avtale`() {
        val behandling = mockk<Behandling>()
        val privatAvtale = mockk<PrivatAvtale>()
        val behandlingMedPrivatAvtale =
            sakKravhaverMedPrivatAvtale("barn3", Stønadstype.BIDRAG, privatAvtale)

        val førsteOppslag = emptySet<SakKravhaver>()
        val utenLøpende = listOf(behandlingMedPrivatAvtale)
        val mellomresultat = førsteOppslag + utenLøpende
        val andreOppslag = emptySet<SakKravhaver>()

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        resultat.size shouldBe 1
        resultat.first().kravhaver shouldBe "barn3"
        resultat.first().privatAvtale shouldBe privatAvtale
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal håndtere blanding av behandling, søknad og privat avtale`() {
        val behandling = mockk<Behandling>()
        val privatAvtale = mockk<PrivatAvtale>()

        val fraBehandling =
            sakKravhaverMedÅpneBehandlinger("barn1", Stønadstype.BIDRAG, setOf(mockk()))
        val fraSøknad =
            sakKravhaverMedÅpneSøknader("barn2", Stønadstype.BIDRAG18AAR, setOf(mockk<HentSøknad>()))

        val førsteOppslag = setOf(fraBehandling, fraSøknad)
        val fraPrivatAvtale = sakKravhaverMedPrivatAvtale("barn3", Stønadstype.BIDRAG, privatAvtale)
        val utenLøpende = listOf(fraPrivatAvtale)
        val mellomresultat = førsteOppslag + utenLøpende
        val andreOppslag = emptySet<SakKravhaver>()

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        resultat.size shouldBe 3
        resultat.map { it.kravhaver }.shouldContainExactlyInAnyOrder("barn1", "barn2", "barn3")
        resultat.find { it.kravhaver == "barn1" }?.åpneBehandlinger?.size shouldBe 1
        resultat.find { it.kravhaver == "barn2" }?.åpneSøknader?.size shouldBe 1
        resultat.find { it.kravhaver == "barn3" }?.privatAvtale shouldBe privatAvtale
    }

    @Test
    fun `hentAlleRelevanteKravhavere skal ikke inkludere duplikater når samme barn og stønadstype kommer fra andre oppslag`() {
        val behandling = mockk<Behandling>()

        val fraBehandling =
            sakKravhaverMedÅpneBehandlinger("barn1", Stønadstype.BIDRAG, setOf(mockk()), saksnummer = "SAK-001")

        val førsteOppslag = setOf(fraBehandling)
        val utenLøpende = emptyList<SakKravhaver>()
        val mellomresultat = førsteOppslag + utenLøpende
        // Andre oppslag returnerer samme barn1 + BIDRAG fra samme saksnummer - skal filtreres ut
        val andreOppslag = setOf(fraBehandling.copy())

        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, null)
        } returns førsteOppslag
        every {
            service.hentBarnUtenLøpendeBidrag(behandling, førsteOppslag)
        } returns utenLøpende
        every {
            service.hentAlleÅpneEllerLøpendeBidraggsakerForBP(behandling, mellomresultat)
        } returns andreOppslag

        val resultat = service.hentAlleRelevanteKravhavere(behandling)

        // distinctBy on (saksnummer, distinctKey) => same saksnummer + key = deduplicated (but kept due to distinctBy)
        resultat.size shouldBe 1
    }

    private fun sakKravhaver(
        kravhaver: String,
        stønadstype: Stønadstype,
    ) = SakKravhaver(
        saksnummer = "SAK-$kravhaver",
        kravhaver = kravhaver,
        stønadstype = stønadstype,
    )

    private fun sakKravhaverMedÅpneBehandlinger(
        kravhaver: String,
        stønadstype: Stønadstype,
        åpneBehandlinger: Set<Behandling>,
        saksnummer: String? = "SAK-$kravhaver",
    ) = SakKravhaver(
        saksnummer = saksnummer,
        kravhaver = kravhaver,
        stønadstype = stønadstype,
        åpneBehandlinger = åpneBehandlinger.toMutableSet(),
    )

    private fun sakKravhaverMedÅpneSøknader(
        kravhaver: String,
        stønadstype: Stønadstype,
        åpneSøknader: Set<HentSøknad>,
        saksnummer: String? = "SAK-$kravhaver",
    ) = SakKravhaver(
        saksnummer = saksnummer,
        kravhaver = kravhaver,
        stønadstype = stønadstype,
        åpneSøknader = åpneSøknader.toMutableSet(),
    )

    private fun sakKravhaverMedPrivatAvtale(
        kravhaver: String,
        stønadstype: Stønadstype,
        privatAvtale: PrivatAvtale,
        saksnummer: String? = null,
    ) = SakKravhaver(
        saksnummer = saksnummer,
        kravhaver = kravhaver,
        stønadstype = stønadstype,
        privatAvtale = privatAvtale,
    )
}
