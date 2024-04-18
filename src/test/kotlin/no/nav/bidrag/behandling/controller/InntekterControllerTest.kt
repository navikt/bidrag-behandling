package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteRequestForOppdateringAvManuellInntekt
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.YearMonth

class InntekterControllerTest : KontrollerTestRunner() {
    @Autowired
    override lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
    }

    @Nested
    @DisplayName("Tester henting av inntekter")
    open inner class HenteInntekter {
        @Test
        @Disabled("Gir Wiremock-problemer på Github")
        open fun `skal hente inntekter for behandling`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)

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
                r1.body?.inntekter?.årsinntekter?.size shouldBe 16
                r1.body?.inntekter?.årsinntekter
                    ?.filter { it.ident.verdi == behandling.bidragsmottaker!!.ident!! }?.size shouldBe 6
                r1.body?.inntekter?.årsinntekter?.filter { it.ident.verdi == testdataBarn1.ident }
                    ?.size shouldBe 5
                r1.body?.inntekter?.årsinntekter?.filter { it.ident.verdi == testdataBarn2.ident }
                    ?.size shouldBe 5
            }
        }

        @Test
        fun `skal oppdater inntektstabell med sammenstilte inntekter fra grunnlagsinnhenting`() {
            // given
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)

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
                r1.body?.inntekter?.årsinntekter?.size shouldBe 18
                r1.body?.inntekter?.barnetillegg?.size shouldBe 0
                r1.body?.inntekter?.utvidetBarnetrygd?.size shouldBe 1
                r1.body?.inntekter?.kontantstøtte?.size shouldBe 0
                r1.body?.inntekter?.månedsinntekter?.size shouldBe 3
                // TODO: Oppdater validering
                r1.body?.aktiveGrunnlagsdata shouldNotBe null
                r1.body!!.aktiveGrunnlagsdata.arbeidsforhold shouldHaveSize 3
                r1.body!!.aktiveGrunnlagsdata.husstandsbarn shouldHaveSize 2
                r1.body!!.aktiveGrunnlagsdata.sivilstand!!.grunnlag shouldHaveSize 2
                r1.body?.ikkeAktiverteEndringerIGrunnlagsdata shouldNotBe null
                r1.body?.ikkeAktiverteEndringerIGrunnlagsdata!!.inntekter.årsinntekter shouldHaveSize 0
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
            var behandling = testdataManager.opprettBehandling()

            behandling.inntekter =
                mutableSetOf(
                    Inntekt(
                        behandling = behandling,
                        type = Inntektsrapportering.KONTANTSTØTTE,
                        belop = BigDecimal(14000),
                        datoFom = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                        datoTom = YearMonth.now().minusYears(1).withMonth(12).atDay(31),
                        ident = testdataBM.ident,
                        gjelderBarn = testdataBarn1.ident,
                        kilde = Kilde.MANUELL,
                        taMed = true,
                    ),
                )

            val lagretBehandling = behandlingRepository.save(behandling)

            assert(behandling.inntekter.size > 0)

            val endreInntektForespørsel =
                oppretteRequestForOppdateringAvManuellInntekt(idInntekt = lagretBehandling.inntekter.first().id!!)

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
        fun `skal kun være mulig å slette inntekter med kilde manuell`() {
            // given
            val behandling = testdataManager.opprettBehandling(true)

            assertSoftly {
                behandling.inntekter.size shouldBe 3
                behandling.inntekter.filter { Kilde.MANUELL == it.kilde }.size shouldBe 1
            }

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
            assertSoftly(respons) {
                it shouldNotBe null
                it.statusCode shouldBe HttpStatus.CREATED
                it.body shouldNotBe null
                it.body?.inntekter?.årsinntekter?.size shouldBe 2
                it.body?.inntekter?.barnetillegg?.size shouldBe 0
                it.body?.inntekter?.utvidetBarnetrygd?.size shouldBe 0
                it.body?.inntekter?.kontantstøtte?.size shouldBe 0
                it.body?.inntekter?.månedsinntekter?.size shouldBe 0
            }
        }
    }
}
