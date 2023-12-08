package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntekterResponse
import no.nav.bidrag.behandling.dto.inntekt.UtvidetbarnetrygdDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class TestInntektRequest(
    val inntekter: Set<TestInntektDto>,
    val barnetillegg: Set<BarnetilleggDto>,
    val utvidetbarnetrygd: Set<UtvidetbarnetrygdDto>,
)

data class TestInntektDto(
    val id: Long?,
    val taMed: Boolean,
    val inntektType: String?,
    val belop: String,
    val datoTom: String?,
    val datoFom: String?,
    val ident: String,
    val inntektPostListe: Set<InntektPost>,
)

class InntekterControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var behandlingService: BehandlingService

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
    }

    @Nested
    @DisplayName("Tester endepunkt for henting av inntekter")
    open inner class HenteInntekter {
        @Test
        fun `skal hente inntekter for behandling`() {
            // given
            val behandling = testdataManager.opprettBehandling()

            // when
            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUri()}/behandling/${behandling.id}/inntekter",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    InntekterResponse::class.java,
                )

            // then
            assertSoftly {
                r1 shouldNotBe null
                r1.statusCode shouldBe HttpStatus.OK
                r1.body shouldNotBe null
                r1.body!!.inntekter.size shouldBeExactly 3
            }
        }

        @Test
        fun `skal returnere tom liste av inntekter for behandling som mangler inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)

            // when
            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUri()}/behandling/${behandling.id}/inntekter",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    InntekterResponse::class.java,
                )

            // then
            assertSoftly {
                r1 shouldNotBe null
                r1.statusCode shouldBe HttpStatus.OK
                r1.body shouldNotBe null
                r1.body!!.inntekter.size shouldBeExactly 0
            }
        }
    }

    @Nested
    @DisplayName("Tester endepunkt for oppdatering av inntekter")
    open inner class OppdatereInntekter {
        @Test
        @Transactional
        open fun `skal opprette inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)

            assert(behandling.inntekter.size == 0)

            val inn = testInntektDto()

            // when
            val r =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUri()}/behandling/${behandling.id}/inntekter",
                    HttpMethod.PUT,
                    HttpEntity(TestInntektRequest(setOf(inn), emptySet(), emptySet())),
                    InntekterResponse::class.java,
                )

            // then
            assertEquals(HttpStatus.OK, r.statusCode)
            assertEquals(1, r.body!!.inntekter.size)
        }

        @Test
        @Transactional
        open fun `skal oppdatere eksisterende inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(true)

            assert(behandling.inntekter.size > 0)

            // when
            val inntekt1 =
                testInntektDto().copy(
                    id = null,
                    inntektPostListe =
                        setOf(
                            InntektPost("ABC1", "ABC1", BigDecimal.TEN),
                            InntektPost("ABC2", "ABC2", BigDecimal.TEN),
                        ),
                )

            val inntekt2 = testInntektDto().copy(datoFom = null, inntektType = "null")

            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUri()}/behandling/${behandling.id}/inntekter",
                    HttpMethod.PUT,
                    HttpEntity(TestInntektRequest(setOf(inntekt1, inntekt2), setOf(), setOf())),
                    InntekterResponse::class.java,
                )

            // then
            assertEquals(HttpStatus.OK, r1.statusCode)
            assertEquals(2, r1.body!!.inntekter.size)
            assertNotNull(r1.body!!.inntekter.find { it.inntektType == "some0" && it.inntektPostListe.size == 2 })
            assertNotNull(r1.body!!.inntekter.find { it.inntektType == "null" && it.inntektPostListe.size == 1 })
        }

        @Test
        @Transactional
        open fun `skal slette inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(true)

            assert(behandling.inntekter.size > 0)

            // when
            val r =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUri()}/behandling/${behandling.id}/inntekter",
                    HttpMethod.PUT,
                    HttpEntity(TestInntektRequest(emptySet(), emptySet(), emptySet())),
                    InntekterResponse::class.java,
                )

            // then
            assertEquals(HttpStatus.OK, r.statusCode)
            assertEquals(0, r.body!!.inntekter.size)
        }
    }

    private fun testInntektDto() =
        TestInntektDto(
            null,
            true,
            "some0",
            "1.123",
            "2022-10-10",
            "2022-10-10",
            "blablabla",
            setOf(InntektPost("ABC", "ABC", BigDecimal.TEN)),
        )
}
