package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.controller.v2.BehandlingControllerV2
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import stubInntektRepository
import stubPersonConsumer
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
    lateinit var behandlingService: BehandlingService

    lateinit var inntektRepository: InntektRepository

    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockK
    lateinit var vedtakService: VedtakService

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var boforholdService: BoforholdService

    @MockK
    lateinit var utgiftService: UtgiftService

    @MockK
    lateinit var virkningstidspunktService: VirkningstidspunktService

    @MockK
    lateinit var ffservice: ForholdsmessigFordelingService

    @MockK
    lateinit var validerBeregning: ValiderBeregning

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    lateinit var controller: BehandlingControllerV2

    lateinit var inntektService: InntektService

    @BeforeEach
    fun initMock() {
        stubSjablonService()
        stubSjablonProvider()
        stubKodeverkProvider()
        inntektRepository = stubInntektRepository()
        val personService = PersonService(stubPersonConsumer())

        every { barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(any(), any()) } returns emptySet()
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                ValiderBeregning(),
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
        val dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validerBeregning,
                validerBehandlingService,
                vedtakGrunnlagMapper,
                BeregnBarnebidragApi(),
            )
        inntektService = InntektService(behandlingRepository, inntektRepository, notatService)
        controller =
            BehandlingControllerV2(
                vedtakService = vedtakService,
                behandlingService = behandlingService,
                gebyrService = GebyrService(vedtakGrunnlagMapper),
                boforholdService = boforholdService,
                inntektService = inntektService,
                utgiftService = utgiftService,
                validerBehandlingService = validerBehandlingService,
                virkningstidspunktService = virkningstidspunktService,
                dtomapper = dtomapper,
                forholdsmessigFordelingService = ffservice,
            )
        every { inntektRepository.saveAll<Inntekt>(any()) } answers { firstArg() }
    }

    @Test
    fun test() {
        val periods =
            listOf(
                ÅrMånedsperiode(YearMonth.of(2023, 1), YearMonth.of(2023, 3)),
                ÅrMånedsperiode(YearMonth.of(2023, 4), null), // `til` is null here
                ÅrMånedsperiode(YearMonth.of(2023, 6), YearMonth.of(2023, 8)),
            )

        val result =
            periods.map { period ->
                period.til ?: YearMonth.now().plusYears(10000) // Handle null `til`
            }

        println(result)
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

        inntekter.shouldHaveSize(3)
        assertSoftly(inntekter[0]) {
            taMed shouldBe true
            datoFom shouldBe LocalDate.parse("2023-08-01")
            datoTom shouldBe LocalDate.parse("2024-07-31")
        }
        assertSoftly(inntekter[1]) {
            taMed shouldBe true
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe LocalDate.parse("2024-07-31")
        }
        assertSoftly(inntekter[2]) {
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
            datoTom shouldBe LocalDate.parse("2025-01-31")
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
            behandling,
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
            behandling,
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

    @Test
    fun `skal oppdatere gebyr ved endring av inntekter`() {
        val behandling = oppretteBehandling(1)
        val virkningstidspunkt = LocalDate.now().plusMonths(4)
        behandling.virkningstidspunkt = virkningstidspunkt
        behandling.roller = oppretteBehandlingRoller(behandling, generateId = true)

        behandling.bidragsmottaker!!.harGebyrsøknad = true
        behandling.bidragsmottaker!!.manueltOverstyrtGebyr =
            RolleManueltOverstyrtGebyr(
                overstyrGebyr = false,
                ilagtGebyr = false,
                beregnetIlagtGebyr = false,
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val forespørselOmOppdateringAvInntekter =
            OppdatereInntektRequest(
                oppdatereManuellInntekt =
                    OppdatereManuellInntekt(
                        type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                        beløp = BigDecimal(3052003),
                        datoFom = LocalDate.now().minusYears(1).withDayOfYear(1),
                        datoTom =
                            LocalDate
                                .now()
                                .minusYears(1)
                                .withMonth(12)
                                .withDayOfMonth(31),
                        ident = Personident(behandling.bidragsmottaker!!.ident!!),
                        gjelderBarn = null,
                    ),
            )

        // hvis
        val response =
            controller.oppdatereInntekt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

        response.beregnetGebyrErEndret shouldBe true
        behandling.bidragsmottaker!!.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
    }

    @Test
    fun `skal ikke oppdatere gebyr ved hvis ingen endring av beregnet gebyr`() {
        val behandling = oppretteBehandling(1)
        val virkningstidspunkt = LocalDate.now().plusMonths(4)
        behandling.virkningstidspunkt = virkningstidspunkt
        behandling.roller = oppretteBehandlingRoller(behandling, generateId = true)

        behandling.bidragsmottaker!!.harGebyrsøknad = true
        behandling.bidragsmottaker!!.manueltOverstyrtGebyr =
            RolleManueltOverstyrtGebyr(
                overstyrGebyr = true,
                ilagtGebyr = false,
                beregnetIlagtGebyr = true,
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { behandlingService.hentBehandlingById(any()) } returns behandling

        val forespørselOmOppdateringAvInntekter =
            OppdatereInntektRequest(
                oppdatereManuellInntekt =
                    OppdatereManuellInntekt(
                        type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                        beløp = BigDecimal(3052003),
                        datoFom = LocalDate.now().minusYears(1).withDayOfYear(1),
                        datoTom =
                            LocalDate
                                .now()
                                .minusYears(1)
                                .withMonth(12)
                                .withDayOfMonth(31),
                        ident = Personident(behandling.bidragsmottaker!!.ident!!),
                        gjelderBarn = null,
                    ),
            )

        // hvis
        val response =
            controller.oppdatereInntekt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

        response.beregnetGebyrErEndret shouldBe false
        behandling.bidragsmottaker!!.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
        behandling.bidragsmottaker!!.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
    }

    @Test
    fun `skal endre inntekter og legge til til og med dato på forrige periode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val virkningstidspunkt = LocalDate.now().plusMonths(4)
        behandling.virkningstidspunkt = virkningstidspunkt

        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { behandlingService.hentBehandlingById(any()) } returns behandling

        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-02"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-03"),
                    datoTom = YearMonth.parse("2024-04"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-06"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-07"),
                    datoTom = null,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragspliktig!!.ident!!,
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragspliktig!!.ident!!,
                    datoFom = YearMonth.parse("2024-08"),
                    datoTom = YearMonth.parse("2024-09"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
            )

        assertSoftly("Endring BM") {
            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(
                    oppdatereManuellInntekt =
                        OppdatereManuellInntekt(
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            beløp = BigDecimal(3052003),
                            datoFom = LocalDate.parse("2024-12-01"),
                            datoTom = null,
                            ident = Personident(behandling.bidragsmottaker!!.ident!!),
                            gjelderBarn = null,
                        ),
                )

            // hvis
            val response =
                controller.oppdatereInntekt(
                    behandling.id!!,
                    forespørselOmOppdateringAvInntekter,
                )
            response.inntekter.årsinntekter.shouldHaveSize(7)
            val inntekterBM = response.inntekter.årsinntekter.filter { it.ident.verdi == behandling.bidragsmottaker!!.ident!! }
            inntekterBM.shouldHaveSize(5)

            val siste = inntekterBM.sortedBy { it.datoFom }.last()
            siste.datoFom shouldBe LocalDate.parse("2024-12-01")
            siste.datoTom shouldBe null

            val nestSiste = findBeforeLast(inntekterBM.sortedBy { it.datoFom })!!
            nestSiste.datoFom shouldBe LocalDate.parse("2024-07-01")
            nestSiste.datoTom shouldBe LocalDate.parse("2024-11-30")
        }

        behandling.inntekter.forEach {
            it.id = it.id ?: 1
        }
        assertSoftly("Endring BP") {
            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(
                    oppdatereManuellInntekt =
                        OppdatereManuellInntekt(
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            beløp = BigDecimal(3052003),
                            datoFom = LocalDate.parse("2025-01-01"),
                            datoTom = LocalDate.parse("2025-02-01"),
                            ident = Personident(behandling.bidragspliktig!!.ident!!),
                            gjelderBarn = null,
                        ),
                )

            // hvis
            val response =
                controller.oppdatereInntekt(
                    behandling.id!!,
                    forespørselOmOppdateringAvInntekter,
                )
            response.inntekter.årsinntekter.shouldHaveSize(8)
            val inntekterBP = response.inntekter.årsinntekter.filter { it.ident.verdi == behandling.bidragspliktig!!.ident!! }
            inntekterBP.shouldHaveSize(3)

            val siste = inntekterBP.sortedBy { it.datoFom }.last()
            siste.datoFom shouldBe LocalDate.parse("2025-01-01")
            siste.datoTom shouldBe LocalDate.parse("2025-02-01")

            val nestSiste = findBeforeLast(inntekterBP.sortedBy { it.datoFom })!!
            nestSiste.datoFom shouldBe LocalDate.parse("2024-08-01")
            nestSiste.datoTom shouldBe LocalDate.parse("2024-12-31")
        }
    }

    @Test
    fun `skal endre inntekter og ikke legge til til og med dato på forrige periode for barnetillegg`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val virkningstidspunkt = LocalDate.now().plusMonths(4)
        behandling.virkningstidspunkt = virkningstidspunkt

        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { behandlingService.hentBehandlingById(any()) } returns behandling

        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-02"),
                    gjelderBarn = behandling.søknadsbarn.first().ident!!,
                    type = Inntektsrapportering.BARNETILLEGG,
                    inntektstyper = listOf(Pair(Inntektstype.BARNETILLEGG_AAP, BigDecimal(100))),
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-03"),
                    datoTom = YearMonth.parse("2024-04"),
                    gjelderBarn = behandling.søknadsbarn.first().ident!!,
                    type = Inntektsrapportering.BARNETILLEGG,
                    inntektstyper = listOf(Pair(Inntektstype.BARNETILLEGG_AAP, BigDecimal(100))),
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-06"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = behandling.søknadsbarn.first().ident!!,
                    inntektstyper = listOf(Pair(Inntektstype.BARNETILLEGG_DNB, BigDecimal(100))),
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-07"),
                    datoTom = null,
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = behandling.søknadsbarn.first().ident!!,
                    inntektstyper = listOf(Pair(Inntektstype.BARNETILLEGG_DNB, BigDecimal(100))),
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = behandling.søknadsbarn.first().ident!!,
                    inntektstyper = listOf(Pair(Inntektstype.BARNETILLEGG_DNB, BigDecimal(100))),
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-08"),
                    datoTom = YearMonth.parse("2024-09"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = behandling.søknadsbarn.first().ident!!,
                    inntektstyper = listOf(Pair(Inntektstype.BARNETILLEGG_DNB, BigDecimal(100))),
                    kilde = Kilde.MANUELL,
                ),
            )

        assertSoftly("Endring BM") {
            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(
                    oppdatereManuellInntekt =
                        OppdatereManuellInntekt(
                            type = Inntektsrapportering.BARNETILLEGG,
                            beløp = BigDecimal(3052003),
                            datoFom = LocalDate.parse("2024-12-01"),
                            datoTom = null,
                            inntektstype = Inntektstype.BARNETILLEGG_AAP,
                            ident = Personident(behandling.bidragsmottaker!!.ident!!),
                            gjelderBarn = Personident(behandling.søknadsbarn.first().ident!!),
                        ),
                )

            // hvis
            val response =
                controller.oppdatereInntekt(
                    behandling.id!!,
                    forespørselOmOppdateringAvInntekter,
                )
            response.inntekter.barnetillegg.shouldHaveSize(7)
            val inntekterBM = response.inntekter.barnetillegg.filter { it.ident.verdi == behandling.bidragsmottaker!!.ident!! }
            inntekterBM.shouldHaveSize(7)

            val siste = inntekterBM.sortedBy { it.datoFom }.filter { it.inntektstyper.contains(Inntektstype.BARNETILLEGG_AAP) }.last()
            siste.datoFom shouldBe LocalDate.parse("2024-12-01")
            siste.datoTom shouldBe null

            val nestSiste = findBeforeLast(inntekterBM.sortedBy { it.datoFom }.filter { it.inntektstyper.contains(Inntektstype.BARNETILLEGG_AAP) })!!
            nestSiste.datoFom shouldBe LocalDate.parse("2024-03-01")
            nestSiste.datoTom shouldBe LocalDate.parse("2024-11-30")
        }

        behandling.inntekter.forEach {
            it.id = it.id ?: 1
        }
    }

    @Test
    fun `skal ikke endre forrige periode hvis ny inntekt legges til mellom`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val virkningstidspunkt = LocalDate.now().plusMonths(4)
        behandling.virkningstidspunkt = virkningstidspunkt

        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { behandlingService.hentBehandlingById(any()) } returns behandling

        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-02"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-03"),
                    datoTom = YearMonth.parse("2024-04"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-06"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    datoFom = YearMonth.parse("2024-07"),
                    datoTom = null,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragspliktig!!.ident!!,
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    ident = behandling.bidragspliktig!!.ident!!,
                    datoFom = YearMonth.parse("2024-08"),
                    datoTom = null,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    kilde = Kilde.MANUELL,
                ),
            )
        val forespørselOmOppdateringAvInntekter =
            OppdatereInntektRequest(
                oppdatereManuellInntekt =
                    OppdatereManuellInntekt(
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                        beløp = BigDecimal(3052003),
                        datoFom = LocalDate.parse("2023-12-01"),
                        datoTom = LocalDate.parse("2023-12-31"),
                        ident = Personident(behandling.bidragsmottaker!!.ident!!),
                        gjelderBarn = null,
                    ),
            )

        // hvis
        val response =
            controller.oppdatereInntekt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

        assertSoftly {
            response.inntekter.årsinntekter.shouldHaveSize(7)
            val inntekterBM = response.inntekter.årsinntekter.filter { it.ident.verdi == behandling.bidragsmottaker!!.ident!! }
            inntekterBM.shouldHaveSize(5)

            val siste = inntekterBM.sortedBy { it.datoFom }.last()
            siste.datoFom shouldBe LocalDate.parse("2024-07-01")
            siste.datoTom shouldBe null

            val første = inntekterBM.sortedBy { it.datoFom }.first()
            første.datoFom shouldBe LocalDate.parse("2023-12-01")
            første.datoTom shouldBe LocalDate.parse("2023-12-31")
        }
    }
}

fun <T> findBeforeLast(list: List<T>): T? = if (list.size >= 2) list[list.size - 2] else null
