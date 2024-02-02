package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.controller.v1.KontrollerTestRunner
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereInntekterRequest
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InntekterControllerTest : KontrollerTestRunner() {
    @Autowired
    override lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
    }

    @Nested
    @DisplayName("Tester endepunkt for henting av inntekter")
    open inner class HenteInntekter {
        @Test
        open fun `skal hente inntekter for behandling`() {
            // given
            val behandling = testdataManager.opprettBehandling()

            // when
            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV1()}/behandling/${behandling.id}",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    BehandlingDto::class.java,
                )

            // then
            assertSoftly {
                r1 shouldNotBe null
                r1.statusCode shouldBe HttpStatus.OK
                r1.body shouldNotBe null
                r1.body!!.inntekter.inntekter.size shouldBeExactly 3
            }
        }

        @Test
        fun `skal returnere tom liste av inntekter for behandling som mangler inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)

            // when
            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV1()}/behandling/${behandling.id}",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    BehandlingDto::class.java,
                )

            // then
            assertSoftly {
                r1 shouldNotBe null
                r1.statusCode shouldBe HttpStatus.OK
                r1.body shouldNotBe null
                r1.body!!.inntekter.inntekter.size shouldBeExactly 0
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
                    "${rootUriV1()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequest(
                            inntekter =
                                OppdatereInntekterRequest(
                                    inntekter = setOf(inn),
                                ),
                        ),
                    ),
                    BehandlingDto::class.java,
                )

            // then
            assertEquals(HttpStatus.OK, r.statusCode)
            assertEquals(1, r.body!!.inntekter.inntekter.size)
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
                    inntektsposter =
                        setOf(
                            InntektPost("ABC1", beløp = BigDecimal.TEN, visningsnavn = "ABC1"),
                            InntektPost("ABC2", visningsnavn = "ABC2", beløp = BigDecimal.TEN),
                        ),
                )

            val inntekt2 =
                testInntektDto().copy(
                    datoFom = LocalDate.now().minusMonths(5),
                    inntektstype = Inntektsrapportering.LIGNINGSINNTEKT,
                )

            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV1()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequest(
                            inntekter =
                                OppdatereInntekterRequest(
                                    setOf(inntekt1, inntekt2),
                                    setOf(),
                                    setOf(),
                                ),
                        ),
                    ),
                    BehandlingDto::class.java,
                )

            val inntekter = r1.body!!.inntekter!!
            // then
            assertEquals(HttpStatus.OK, r1.statusCode)
            assertEquals(2, inntekter.inntekter.size)
            assertNotNull(inntekter.inntekter.find { it.inntektstype == Inntektsrapportering.DAGPENGER && it.inntektsposter.size == 2 })
            assertNotNull(
                inntekter.inntekter.find {
                    it.inntektstype == Inntektsrapportering.LIGNINGSINNTEKT &&
                        it.inntektsposter.size == 1
                },
            )
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
                    "${rootUriV1()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequest(
                            inntekter =
                                OppdatereInntekterRequest(
                                    emptySet(),
                                    emptySet(),
                                    emptySet(),
                                ),
                        ),
                    ),
                    BehandlingDto::class.java,
                )

            // then
            assertEquals(HttpStatus.OK, r.statusCode)
            assertEquals(0, r.body!!.inntekter.inntekter.size)
        }
    }

    private fun testInntektDto() =
        InntektDto(
            null,
            true,
            Inntektsrapportering.DAGPENGER,
            BigDecimal.valueOf(305203),
            LocalDate.now().minusYears(1).withDayOfYear(1),
            LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31),
            "blablabla",
            true,
            setOf(InntektPost(kode = "ABC", visningsnavn = "ABC", beløp = BigDecimal.TEN)),
        )
}
