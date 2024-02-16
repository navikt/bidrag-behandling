package no.nav.bidrag.behandling.controller.v1

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkObject
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning
import no.nav.bidrag.behandling.utils.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.oppretteBehandlingRoller
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.service.sjablon.SjablonProvider
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class BehandlingBeregnForskuddControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @MockkBean
    lateinit var forskuddBeregning: BeregnForskuddApi

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
        every { forskuddBeregning.beregn(any()) } returns BeregnetForskuddResultat()
        mockkObject(SjablonProvider)
        every {
            SjablonProvider.hentSjablontall()
        } returns emptyList()
    }

    @Test
    fun `skal beregne forskudd for validert behandling`() {
        // given
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak()

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
    fun `skal videreføre feil fra bidrag-beregn-forskudd-rest`() {
        // given
        val errorMessage = "Feil input"
        every { forskuddBeregning.beregn(any()) } throws IllegalArgumentException(errorMessage)
        val behandling = behandlingRepository.save(opprettGyldigBehandlingForBeregningOgVedtak())

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
                    errorMessage,
                    errorMessage,
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
