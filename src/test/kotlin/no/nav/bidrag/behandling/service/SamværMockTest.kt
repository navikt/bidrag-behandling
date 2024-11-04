package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværsperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.SletteSamværsperiodeElementDto
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.commons.service.sjablon.SjablonService
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorFerietype
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorNetterFrekvens
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate

class SamværMockTest : TestContainerRunner() {
    @Autowired
    lateinit var samværService: SamværService

    @Autowired
    lateinit var testdataManager: TestdataManager

    @MockkBean
    lateinit var sjablonService: SjablonService

    @BeforeEach
    fun initMock() {
        stubSjablonService()
        every { sjablonService.hentSjablonSamværsfradrag() } returns stubSjablonService().hentSjablonSamværsfradrag()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPersonISak()
    }

    @Test
    @Transactional
    fun `skal opprette en ny samværsperiode`() {
        val behandling =
            testdataManager.lagreBehandling(
                opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG),
            )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.oppdaterSamvær(
                behandling.id!!,
                OppdaterSamværDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    periode =
                        OppdaterSamværsperiodeDto(
                            periode = DatoperiodeDto(behandling.virkningstidspunkt!!, null),
                            samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                        ),
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(1)
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                gjennomsnittligSamværPerMåned shouldBe BigDecimal.ZERO
                beregning shouldBe null
            }
        }
    }

    @Test
    @Transactional
    fun `skal oppdatere samværsperiode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = behandling.virkningstidspunkt!!,
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.oppdaterSamvær(
                behandling.id!!,
                OppdaterSamværDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    periode =
                        OppdaterSamværsperiodeDto(
                            id = samvær.perioder.first().id,
                            periode = DatoperiodeDto(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(2)),
                            samværsklasse = Samværsklasse.SAMVÆRSKLASSE_2,
                        ),
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(1)
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt
                periode.tom shouldBe behandling.virkningstidspunkt!!.plusMonths(2)
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                gjennomsnittligSamværPerMåned shouldBe BigDecimal.ZERO
                beregning shouldBe null
            }
        }
    }

    @Test
    @Transactional
    fun `skal opprette ny samværsperiode og oppdater forrige tom til neste fom`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = behandling.virkningstidspunkt!!,
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.oppdaterSamvær(
                behandling.id!!,
                OppdaterSamværDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    periode =
                        OppdaterSamværsperiodeDto(
                            periode = DatoperiodeDto(behandling.virkningstidspunkt!!.plusMonths(2), null),
                            samværsklasse = Samværsklasse.SAMVÆRSKLASSE_2,
                        ),
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(2)
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt
                periode.tom shouldBe behandling.virkningstidspunkt!!.plusMonths(2).minusDays(1)
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                gjennomsnittligSamværPerMåned shouldBe BigDecimal.ZERO
                beregning shouldBe null
            }
            assertSoftly(perioder[1]) {
                periode.fom shouldBe behandling.virkningstidspunkt!!.plusMonths(2)
                periode.tom shouldBe null
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                gjennomsnittligSamværPerMåned shouldBe BigDecimal.ZERO
                beregning shouldBe null
            }
        }
    }

    @Test
    @Transactional
    fun `skal opprette ny samværsperiode og ikke oppdater forrige tom hvis ny periode er før siste periode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = LocalDate.now().withDayOfMonth(1),
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.oppdaterSamvær(
                behandling.id!!,
                OppdaterSamværDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    periode =
                        OppdaterSamværsperiodeDto(
                            periode = DatoperiodeDto(behandling.virkningstidspunkt!!.plusMonths(2), null),
                            samværsklasse = Samværsklasse.SAMVÆRSKLASSE_2,
                        ),
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(2)
            assertSoftly(perioder[1]) {
                periode.fom shouldBe LocalDate.now().withDayOfMonth(1)
                periode.tom shouldBe null
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                gjennomsnittligSamværPerMåned shouldBe BigDecimal.ZERO
                beregning shouldBe null
            }
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt!!.plusMonths(2)
                periode.tom shouldBe null
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                gjennomsnittligSamværPerMåned shouldBe BigDecimal.ZERO
                beregning shouldBe null
            }
        }
    }

    @Test
    @Transactional
    fun `skal oppdatere samværsperiode med beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = behandling.virkningstidspunkt!!,
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.oppdaterSamvær(
                behandling.id!!,
                OppdaterSamværDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    periode =
                        OppdaterSamværsperiodeDto(
                            id = samvær.perioder.first().id,
                            periode = DatoperiodeDto(behandling.virkningstidspunkt!!, null),
                            beregning =
                                SamværskalkulatorDetaljer(
                                    regelmessigSamværNetter = BigDecimal(4),
                                    ferier =
                                        listOf(
                                            SamværskalkulatorDetaljer.SamværskalkulatorFerie(
                                                type = SamværskalkulatorFerietype.SOMMERFERIE,
                                                bidragsmottakerNetter = BigDecimal(14),
                                                bidragspliktigNetter = BigDecimal(1),
                                                frekvens = SamværskalkulatorNetterFrekvens.HVERT_ÅR,
                                            ),
                                        ),
                                ),
                        ),
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(1)
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                gjennomsnittligSamværPerMåned shouldBe BigDecimal("8.42")
                beregning shouldNotBe null
            }
        }
    }

    @Test
    @Transactional
    fun `skal opprette en ny samværsperiode med beregning`() {
        val behandling =
            testdataManager.lagreBehandling(
                opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG),
            )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.oppdaterSamvær(
                behandling.id!!,
                OppdaterSamværDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    periode =
                        OppdaterSamværsperiodeDto(
                            periode = DatoperiodeDto(behandling.virkningstidspunkt!!, null),
                            beregning =
                                SamværskalkulatorDetaljer(
                                    regelmessigSamværNetter = BigDecimal(4),
                                    ferier =
                                        listOf(
                                            SamværskalkulatorDetaljer.SamværskalkulatorFerie(
                                                type = SamværskalkulatorFerietype.SOMMERFERIE,
                                                bidragsmottakerNetter = BigDecimal(14),
                                                bidragspliktigNetter = BigDecimal(1),
                                                frekvens = SamværskalkulatorNetterFrekvens.HVERT_ÅR,
                                            ),
                                        ),
                                ),
                        ),
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(1)
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                gjennomsnittligSamværPerMåned shouldBe BigDecimal("8.42")
                beregning shouldNotBe null
                beregning!!.ferier shouldHaveSize 1
                beregning.ferier[0].bidragsmottakerNetter shouldBe BigDecimal(14)
                beregning.ferier[0].type shouldBe SamværskalkulatorFerietype.SOMMERFERIE
                beregning.regelmessigSamværNetter shouldBe BigDecimal(4)
            }
        }
    }

    @Test
    @Transactional
    fun `skal slette samværsperiode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = behandling.virkningstidspunkt!!.plusMonths(2).minusDays(1),
                fom = behandling.virkningstidspunkt!!,
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = behandling.virkningstidspunkt!!.plusMonths(2),
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_2,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val søknadsbarn = behandling.søknadsbarn.first()

        val respons =
            samværService.slettPeriode(
                behandling.id!!,
                SletteSamværsperiodeElementDto(
                    gjelderBarn = søknadsbarn.ident!!,
                    samværsperiodeId = samvær.perioder.toList()[1].id!!,
                ),
            )

        assertSoftly(respons.oppdatertSamvær!!) {
            perioder.shouldHaveSize(1)
            assertSoftly(perioder[0]) {
                periode.fom shouldBe behandling.virkningstidspunkt
                periode.tom shouldBe behandling.virkningstidspunkt!!.plusMonths(2).minusDays(1)
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
            }
        }
    }

    @Test
    @Transactional
    fun `skal feile hvis ikke finner samvær i behandling`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = behandling.virkningstidspunkt!!.plusMonths(2).minusDays(1),
                fom = behandling.virkningstidspunkt!!,
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = behandling.virkningstidspunkt!!.plusMonths(2),
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_2,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val exception =
            assertThrows<HttpClientErrorException> {
                samværService.slettPeriode(
                    behandling.id!!,
                    SletteSamværsperiodeElementDto(
                        gjelderBarn = "123123",
                        samværsperiodeId = samvær.perioder.toList()[1].id!!,
                    ),
                )
            }
        exception.message shouldContain "Fant ikke samvær for barn 123123"
    }

    @Test
    @Transactional
    fun `skal feile hvis ikke finner periode i samvær i behandling`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        val samvær = behandling.samvær.first()
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = behandling.virkningstidspunkt!!.plusMonths(2).minusDays(1),
                fom = behandling.virkningstidspunkt!!,
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
            ),
        )
        samvær.perioder.add(
            Samværsperiode(
                samvær = samvær,
                tom = null,
                fom = behandling.virkningstidspunkt!!.plusMonths(2),
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_2,
            ),
        )
        testdataManager.lagreBehandling(
            behandling = behandling,
        )
        val søknadsbarn = behandling.søknadsbarn.first()

        val exception =
            assertThrows<HttpClientErrorException> {
                samværService.slettPeriode(
                    behandling.id!!,
                    SletteSamværsperiodeElementDto(
                        gjelderBarn = søknadsbarn.ident!!,
                        samværsperiodeId = 213,
                    ),
                )
            }
        exception.message shouldContain "Fant ikke samværsperiode med id"
    }
}
