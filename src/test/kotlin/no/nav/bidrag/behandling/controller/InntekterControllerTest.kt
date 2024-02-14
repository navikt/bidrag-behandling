package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
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
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    BehandlingDtoV2::class.java,
                )

            // then
            assertSoftly {
                r1 shouldNotBe null
                r1.statusCode shouldBe HttpStatus.OK
                r1.body shouldNotBe null
                r1.body?.inntekter?.årsinntekter?.size shouldBe 3
            }
        }

        @Test
        fun `skal returnere tom liste av inntekter for behandling som mangler inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)

            // when
            val r1 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    BehandlingDtoV2::class.java,
                )

            // then
            assertSoftly {
                r1 shouldNotBe null
                r1.statusCode shouldBe HttpStatus.OK
                r1.body shouldNotBe null
                r1.body?.inntekter?.årsinntekter?.size shouldBe 0
                r1.body?.inntekter?.barnetillegg?.size shouldBe 0
                r1.body?.inntekter?.barnetilsyn?.size shouldBe 0
                r1.body?.inntekter?.kontantstøtte?.size shouldBe 0
                r1.body?.inntekter?.månedsinntekter?.size shouldBe 0
            }
        }
    }

    @Nested
    @DisplayName("Tester endepunkt for oppdatering av inntekter")
    open inner class OppdatereInntekter {
        @Test
        open fun `skal opprette inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)

            assert(behandling.inntekter.size == 0)

            val kontantstøtte = oppretteRequestForOppdateringAvManuellInntekt()

            // when
            val r =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequestV2(
                            inntekter =
                                OppdatereInntekterRequestV2(
                                    oppdatereManuelleInntekter = setOf(kontantstøtte),
                                ),
                        ),
                    ),
                    BehandlingDtoV2::class.java,
                )

            // then
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                r.statusCode shouldBe HttpStatus.CREATED
                oppdatertBehandling.isPresent
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling.get().inntekter.filter { i ->
                    i.inntektsrapportering == Inntektsrapportering.KONTANTSTØTTE
                }.size shouldBe 1
            }
        }

        /*
        @Test
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
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequestV2(
                            inntekter =
                            OppdatereInntekterRequestV2(
                                oppdatereManuelleInntekter = setOf(inntekt2)
                            ),
                        ),
                    ),
                    Void::class.java,
                )

            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            // then
            assertSoftly {
                r1.statusCode shouldBe HttpStatus.CREATED
                oppdatertBehandling.isPresent
                oppdatertBehandling.get().inntekter.size shouldBeExactly 2
                oppdatertBehandling.get().inntekter.find {
                    it.inntektsrapportering == Inntektsrapportering.DAGPENGER && it.inntektsposter.size == 2
                } shouldNotBe null
                oppdatertBehandling.get().inntekter.find {
                    it.inntektsrapportering == Inntektsrapportering.LIGNINGSINNTEKT && it.inntektsposter.size == 1
                } shouldNotBe null
            }
        }

*/
        @Test
        @Transactional
        open fun `skal slette inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(true)

            assert(behandling.inntekter.size > 0)

            // when
            val respons =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequestV2(
                            inntekter =
                                OppdatereInntekterRequestV2(
                                    sletteInntekter = behandling.inntekter.map { it.id!! }.toSet(),
                                ),
                        ),
                    ),
                    BehandlingDtoV2::class.java,
                )

            // then
            assertSoftly {
                respons shouldNotBe null
                respons.statusCode shouldBe HttpStatus.CREATED
                respons.body shouldNotBe null
                respons.body?.inntekter?.årsinntekter?.size shouldBe 0
                respons.body?.inntekter?.barnetillegg?.size shouldBe 0
                respons.body?.inntekter?.barnetilsyn?.size shouldBe 0
                respons.body?.inntekter?.kontantstøtte?.size shouldBe 0
                respons.body?.inntekter?.månedsinntekter?.size shouldBe 0
            }
        }
    }

    /*
        private fun testInntektDto() =


            InntektDtoV2(
                taMed = true,
                rapporteringstype = Inntektsrapportering.KONTANTSTØTTE,
                beløp = BigDecimal.valueOf(305203),
                datoFom = LocalDate.now().minusYears(1).withDayOfYear(1),
                datoTom = LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31),
                opprinneligFom = LocalDate.now().minusYears(1).withDayOfYear(1),
                opprinneligTom = LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31),
                ident = Personident("12345678910"),
                gjelderBarn = Personident("012345678912"),
                inntektsposter = setOf(InntektspostDtoV2(kode = "ABC", visningsnavn = "ABC", beløp = BigDecimal.TEN)),
            )
     */
    private fun oppretteRequestForOppdateringAvManuellInntekt() =
        OppdatereManuellInntekt(
            type = Inntektsrapportering.KONTANTSTØTTE,
            beløp = BigDecimal(305203),
            datoFom = LocalDate.now().minusYears(1).withDayOfYear(1),
            datoTom = LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31),
            ident = Personident("12345678910"),
            gjelderBarn = Personident("01234567891"),
        )
}
