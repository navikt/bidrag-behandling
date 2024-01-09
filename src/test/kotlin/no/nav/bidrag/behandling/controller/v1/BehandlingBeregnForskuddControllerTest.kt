package no.nav.bidrag.behandling.controller.v1

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning
import no.nav.bidrag.behandling.utils.opprettGyldigBehandlingForBeregning
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.oppretteBehandlingRoller
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class BehandlingBeregnForskuddControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
    }

    @Test
    fun `skal beregne forskudd for validert behandling`() {
        stubUtils.stubBeregneForskudd()

        // given
        val behandling = opprettGyldigBehandlingForBeregning()

        try {
            behandlingRepository.save(behandling)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatForskuddsberegning::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.OK
            returnert.body shouldNotBe null
        }
    }

    @Test
    fun `skal returnere httpkode 400 dersom behandling mangler informasjon om husstandsbarn`() {
        // given
        val behandling = lagreBehandlingMedRoller()

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatForskuddsberegning::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldBe null
            returnert.headers["Warning"]?.shouldBe(
                listOf(
                    "Sivilstand mangler i behandling",
                    "Mangler inntekter for bidragsmottaker",
                    "Mangler informasjon om husstandsbarn",
                ),
            )
        }
    }

    @Test
    fun `skal videreføre BAD_REQUEST fra bidrag-beregn-forskudd-rest`() {
        // given
        stubUtils.stubBeregneForskudd(
            HttpStatus.BAD_REQUEST,
            mapOf("error" to "Some error"),
        )
        val behandling = behandlingRepository.save(opprettGyldigBehandlingForBeregning())

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatForskuddsberegning::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldBe null
            returnert.headers["Warning"]?.shouldBe(
                listOf(
                    "Some error",
                    "Some error",
                ),
            )
        }
    }

    private fun lagreBehandlingMedRoller(): Behandling {
        val behandling = oppretteBehandling()
        behandling.roller = oppretteBehandlingRoller(behandling)
        return behandlingRepository.save(behandling)
    }
}
