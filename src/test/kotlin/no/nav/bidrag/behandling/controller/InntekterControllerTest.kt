package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.fødselsnummerBarn1
import no.nav.bidrag.behandling.utils.testdata.fødselsnummerBm
import no.nav.bidrag.behandling.utils.testdata.oppretteRequestForOppdateringAvManuellInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
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
import java.time.YearMonth
import kotlin.test.Ignore

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
    @DisplayName("Tester henting av inntekter")
    open inner class HenteInntekter {
        @Test
        @Ignore("Wiremock-problem kun på GCP")
        open fun `skal hente inntekter for behandling`() {
            // given
            val behandling = testdataManager.opprettBehandling(true)

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
                r1.body?.inntekter?.årsinntekter?.size shouldBe 9
            }
        }

        @Test
        @Ignore("Wiremock-problem kun på GCP")
        fun `skal oppdater inntektstabell med sammenstilte inntekter fra grunnlagsinnhenting`() {
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
                r1.body?.inntekter?.årsinntekter?.size shouldBe 8
                r1.body?.inntekter?.barnetillegg?.size shouldBe 0
                r1.body?.inntekter?.barnetilsyn?.size shouldBe 0
                r1.body?.inntekter?.kontantstøtte?.size shouldBe 1
                r1.body?.inntekter?.månedsinntekter?.size shouldBe 2
            }
        }
    }

    @Nested
    @DisplayName("Tester oppdatering av inntekter")
    open inner class OppdatereInntekter {
        @Test
        open fun `skal opprette inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)

            assert(behandling.inntekter.size == 0)

            val endreKontantstøtte =
                oppretteRequestForOppdateringAvManuellInntekt()

            // when
            val r =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequestV2(
                            inntekter =
                                OppdatereInntekterRequestV2(
                                    oppdatereManuelleInntekter = setOf(endreKontantstøtte),
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
                    i.type == Inntektsrapportering.KONTANTSTØTTE
                }.size shouldBe 1
            }
        }

        @Test
        open fun `skal oppdatere eksisterende inntekter`() {
            // given
            val behandling = testdataManager.opprettBehandling()

            behandling.inntekter =
                mutableSetOf(
                    Inntekt(
                        behandling = behandling,
                        type = Inntektsrapportering.KONTANTSTØTTE,
                        belop = BigDecimal(14000),
                        datoFom = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                        datoTom = YearMonth.now().minusYears(1).withMonth(12).atDay(31),
                        ident = fødselsnummerBm,
                        gjelderBarn = fødselsnummerBarn1,
                        kilde = Kilde.MANUELL,
                        taMed = true,
                    ),
                )

            behandlingRepository.save(behandling)

            assert(behandling.inntekter.size > 0)

            val endreInntektForespørsel =
                oppretteRequestForOppdateringAvManuellInntekt(idInntekt = behandling.inntekter.first().id!!)

            // when
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdaterBehandlingRequestV2(
                            inntekter =
                                OppdatereInntekterRequestV2(
                                    oppdatereManuelleInntekter = setOf(endreInntektForespørsel),
                                ),
                        ),
                    ),
                    BehandlingDtoV2::class.java,
                )

            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            // then
            assertSoftly {
                svar.statusCode shouldBe HttpStatus.CREATED
                oppdatertBehandling.isPresent
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling.get().inntekter.first().type shouldBe Inntektsrapportering.KONTANTSTØTTE
                oppdatertBehandling.get().inntekter.first().belop shouldBe endreInntektForespørsel.beløp
            }
        }

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
}
