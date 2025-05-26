package no.nav.bidrag.behandling.service

import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkClass
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class BehandlingServiceMockTest : CommonMockServiceTest() {
    @BeforeEach
    fun init2() {
        behandlingService =
            BehandlingService(
                behandlingRepository,
                mockkClass(ForsendelseService::class),
                virkningstidspunktService,
                tilgangskontrollService,
                grunnlagService,
                dtomapper,
                validerBehandlingService,
                underholdService,
            )
    }

    @Test
    fun `skal hente behandling`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)

        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        val dto = behandlingControllerV2.henteBehandlingV2(behandling.id!!)

        dto shouldNotBe null
    }
}
