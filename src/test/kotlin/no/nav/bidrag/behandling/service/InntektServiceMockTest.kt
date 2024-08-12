package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional

@ExtendWith(MockKExtension::class)
class InntektServiceMockTest {
    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @MockK
    lateinit var notatService: NotatService

    @MockK
    lateinit var inntektRepository: InntektRepository

    lateinit var inntektService: InntektService

    @BeforeEach
    fun initMock() {
        inntektService = InntektService(behandlingRepository, inntektRepository, notatService)
        every { inntektRepository.saveAll<Inntekt>(any()) } answers { firstArg() }
    }

    @Test
    fun `skal oppdatere periode på inntekter etter endring i virkningstidspunkt`() {
        val behandling = oppretteBehandling(1)
        val virkningstidspunkt = LocalDate.parse("2023-07-01")
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-01"),
                    datoTom = YearMonth.parse("2023-06"),
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-08"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-01"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = null,
                    type = Inntektsrapportering.AINNTEKT,
                ),
            )

        behandling.virkningstidspunkt = virkningstidspunkt
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        inntektService.rekalkulerPerioderInntekter(behandling.id!!)

        val inntekter = behandling.inntekter.toList()
        assertSoftly(inntekter[0]) {
            taMed shouldBe false
            datoFom shouldBe null
            datoTom shouldBe null
        }
        assertSoftly(inntekter[1]) {
            taMed shouldBe true
            datoFom shouldBe LocalDate.parse("2023-08-01")
            datoTom shouldBe LocalDate.parse("2024-07-31")
        }
        assertSoftly(inntekter[2]) {
            taMed shouldBe true
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe LocalDate.parse("2024-07-31")
        }
        assertSoftly(inntekter[3]) {
            taMed shouldBe true
            datoFom shouldBe LocalDate.parse("2024-01-01")
            datoTom shouldBe null
        }
    }

    @Test
    fun `skal oppdatere periode på offentlige ytelser etter endring i virkningstidspunkt`() {
        val behandling = oppretteBehandling(1)
        val virkningstidspunkt = LocalDate.parse("2023-07-01")
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = null,
                    opprinneligFom = YearMonth.parse("2024-01"),
                    opprinneligTom = YearMonth.parse("2025-01"),
                    type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    kilde = Kilde.OFFENTLIG,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = null,
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2024-01"),
                    type = Inntektsrapportering.KONTANTSTØTTE,
                    kilde = Kilde.OFFENTLIG,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-10"),
                    datoTom = YearMonth.parse("2023-12"),
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.SMÅBARNSTILLEGG,
                    kilde = Kilde.OFFENTLIG,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-10"),
                    datoTom = YearMonth.parse("2023-12"),
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    kilde = Kilde.OFFENTLIG,
                ),
            )

        behandling.virkningstidspunkt = virkningstidspunkt
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        inntektService.rekalkulerPerioderInntekter(behandling.id!!)

        val inntekter = behandling.inntekter.toList()
        assertSoftly(inntekter[0]) {
            taMed shouldBe true
            datoFom shouldBe LocalDate.parse("2024-01-01")
            datoTom shouldBe null
        }
        assertSoftly(inntekter[1]) {
            taMed shouldBe true
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe LocalDate.parse("2024-01-31")
        }
        assertSoftly(inntekter[2]) {
            taMed shouldBe true
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe LocalDate.parse("2023-12-31")
        }
        assertSoftly(inntekter[3]) {
            taMed shouldBe true
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe LocalDate.parse("2023-12-31")
        }
    }

    @Test
    fun `skal lagre inntekter og automatisk ta med offentlige ytelser fra NAV`() {
        val behandling = oppretteBehandling(1)
        behandling.virkningstidspunkt = LocalDate.parse("2023-02-01")
        behandling.roller = oppretteBehandlingRoller(behandling)
        val summerteInntekter =
            SummerteInntekter(
                versjon = "xyz",
                inntekter =
                    listOf(
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.BARNETILLEGG,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2024-01"),
                                    YearMonth.parse("2035-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.SMÅBARNSTILLEGG,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.SMÅBARNSTILLEGG,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    null,
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.AINNTEKT,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.LIGNINGSINNTEKT,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.KONTANTSTØTTE,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                    ),
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        // hvis
        inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
            1,
            personident = Personident(behandling.bidragsmottaker?.ident!!),
            summerteÅrsinntekter = summerteInntekter.inntekter,
        )

        val inntekter = behandling.inntekter
        val inntekterIkkeValgt = inntekter.filter { !it.taMed }
        val inntekterValgt = inntekter.filter { it.taMed }
        assertSoftly {
            inntekterValgt shouldHaveSize 6
            inntekterIkkeValgt shouldHaveSize 3
        }
        assertSoftly(inntekterValgt) {
            map { it.type }.toSet() shouldContainAnyOf
                listOf(
                    Inntektsrapportering.BARNETILLEGG,
                    Inntektsrapportering.UTVIDET_BARNETRYGD,
                    Inntektsrapportering.SMÅBARNSTILLEGG,
                    Inntektsrapportering.KONTANTSTØTTE,
                )
            forEach {
                it.datoFom shouldBe maxOf(behandling.virkningstidspunkt!!, it.opprinneligFom!!)
            }
            filter { it.datoTom != null }.forEach {
                it.datoTom shouldBe it.opprinneligTom
            }
            val utvidetBarnetrygd =
                filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }.sortedBy { it.datoFom }
            utvidetBarnetrygd.last().datoTom shouldBe null
        }
        assertSoftly(inntekterIkkeValgt) {
            map { it.type } shouldContainAnyOf
                listOf(
                    Inntektsrapportering.AINNTEKT,
                    Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                    Inntektsrapportering.LIGNINGSINNTEKT,
                )
            forEach {
                it.datoFom shouldBe null
                it.datoTom shouldBe null
            }
        }
    }

    @Test
    fun `skal lagre inntekter og automatisk ta med offentlige ytelser fra NAV og sette datoTom til null hvis etter virkningstidspunkt`() {
        val behandling = oppretteBehandling(1)
        val virkningstidspunkt = LocalDate.now().plusMonths(4)
        behandling.virkningstidspunkt = virkningstidspunkt
        behandling.roller = oppretteBehandlingRoller(behandling)
        val summerteInntekter =
            SummerteInntekter(
                versjon = "xyz",
                inntekter =
                    listOf(
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2023-01"),
                                    YearMonth.parse("2024-01"),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
                            periode =
                                ÅrMånedsperiode(
                                    virkningstidspunkt.minusMonths(1),
                                    virkningstidspunkt.plusMonths(5),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                        SummertÅrsinntekt(
                            inntektRapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
                            periode =
                                ÅrMånedsperiode(
                                    YearMonth.parse("2024-01").atDay(1),
                                    virkningstidspunkt.plusYears(5),
                                ),
                            sumInntekt = BigDecimal(500),
                        ),
                    ),
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        // hvis
        inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
            1,
            personident = Personident(behandling.bidragsmottaker?.ident!!),
            summerteÅrsinntekter = summerteInntekter.inntekter,
        )

        val inntekter = behandling.inntekter
        val inntekterValgt = inntekter.filter { it.taMed }.sortedBy { it.datoFom }

        assertSoftly {
            inntekterValgt shouldHaveSize 2
        }
        assertSoftly(inntekterValgt[0]) {
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe null
        }
        assertSoftly(inntekterValgt[1]) {
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe null
        }
    }
}
