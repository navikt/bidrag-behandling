package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdaterePeriodeInntekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner
import no.nav.bidrag.behandling.transformers.inntekt.TransformerInntekterRequestBuilder
import no.nav.bidrag.behandling.transformers.inntekt.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.inntekt.tilBarnetillegg
import no.nav.bidrag.behandling.transformers.inntekt.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.inntekt.tilSmåbarnstillegg
import no.nav.bidrag.behandling.transformers.inntekt.tilUtvidetBarnetrygd
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteRequestForOppdateringAvManuellInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.tilAinntektspostDto
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.assertj.core.error.OptionalShouldBePresent.shouldBePresent
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@RunWith(Enclosed::class)
class InntektServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var inntektRepository: InntektRepository

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var inntektService: InntektService

    @Autowired
    lateinit var inntektApi: InntektApi

    @Autowired
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setup() {
        behandlingRepository.deleteAll()
        inntektRepository.deleteAll()
        stubSjablonProvider()
        stubKodeverkProvider()
    }

    @Nested
    @DisplayName("Teste oppdatering av sammenstilte inntekter")
    open inner class OppdatereSammenstilte {
        @Test
        @Transactional
        open fun `skal lagre innnhentede inntekter for gyldige inntektsrapporteringstyper`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling()

            val summerteInntekter =
                SummerteInntekter(
                    versjon = "xyz",
                    inntekter =
                        listOf(
                            SummertÅrsinntekt(
                                inntektRapportering = Inntektsrapportering.LIGNINGSINNTEKT,
                                inntektPostListe =
                                    listOf(
                                        InntektPost(
                                            kode = "samletLoennsinntektUtenTrygdeavgiftspliktOgMedTrekkplikt",
                                            beløp = BigDecimal(500000),
                                        ),
                                    ),
                                periode =
                                    ÅrMånedsperiode(
                                        YearMonth
                                            .now()
                                            .minusYears(1)
                                            .withMonth(1)
                                            .atDay(1),
                                        YearMonth.now().withMonth(1).atDay(1),
                                    ),
                                sumInntekt = BigDecimal(500000),
                            ),
                        ),
                )

            // hvis
            inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
                behandling,
                personident = Personident(behandling.bidragsmottaker?.ident!!),
                summerteÅrsinntekter = summerteInntekter.inntekter,
            )
            entityManager.merge(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                    .belop shouldBe
                    summerteInntekter.inntekter
                        .first { Inntektsrapportering.LIGNINGSINNTEKT == it.inntektRapportering }
                        .sumInntekt
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                    .inntektsposter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                    .inntektsposter
                    .first()
                    .kode shouldBe
                    summerteInntekter.inntekter
                        .first {
                            Inntektsrapportering.LIGNINGSINNTEKT == it.inntektRapportering
                        }.inntektPostListe
                        .first()
                        .kode
            }
        }

        @Test
        @Transactional
        open fun `skal lagre innnhentede ytelser og automatisk ta med for beregning`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling()

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

            // hvis
            inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
                behandling,
                personident = Personident(behandling.bidragsmottaker?.ident!!),
                summerteÅrsinntekter = summerteInntekter.inntekter,
            )

            entityManager.merge(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            val inntekter = oppdatertBehandling.get().inntekter
            val inntekterIkkeValgt = inntekter.filter { !it.taMed }
            val inntekterValgt = inntekter.filter { it.taMed }
            assertSoftly {
                inntekterValgt shouldHaveSize 6
                inntekterIkkeValgt shouldHaveSize 3
            }
        }

        @Test
        @Transactional
        open fun `skal lagre innnhentede inntekter for BARNETILLEGG`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling()

            val summerteInntekter =
                SummerteInntekter(
                    versjon = "xyz",
                    inntekter =
                        listOf(
                            SummertÅrsinntekt(
                                inntektRapportering = Inntektsrapportering.BARNETILLEGG,
                                periode =
                                    ÅrMånedsperiode(
                                        YearMonth
                                            .now()
                                            .minusYears(1)
                                            .withMonth(1)
                                            .atDay(1),
                                        YearMonth.now().withMonth(1).atDay(1),
                                    ),
                                sumInntekt = BigDecimal(500),
                            ),
                        ),
                )

            // hvis
            inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
                behandling,
                personident = Personident(behandling.bidragsmottaker?.ident!!),
                summerteÅrsinntekter = summerteInntekter.inntekter,
            )

            entityManager.merge(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.get().inntekter.size shouldBe 1
                val barnetillegg =
                    oppdatertBehandling.get().inntekter.first { Inntektsrapportering.BARNETILLEGG == it.type }
                barnetillegg.belop shouldBe (500).toBigDecimal()
                barnetillegg.taMed shouldBe true
                barnetillegg.datoFom shouldBe behandling.virkningstidspunkt
                barnetillegg.datoTom shouldBe barnetillegg.opprinneligTom
                barnetillegg.inntektsposter.size shouldBe 1
                barnetillegg.inntektsposter.first().inntektstype shouldBe Inntektstype.BARNETILLEGG_PENSJON
                barnetillegg.inntektsposter.first().beløp shouldBe (500).toBigDecimal()
                barnetillegg.inntektsposter.first().kode shouldBe Inntektsrapportering.BARNETILLEGG.name
            }
        }
    }

    @Nested
    @DisplayName("Teste automatisk oppdatering av offisielle inntekter")
    open inner class OppdatereAutomatiskInnhentaOffentligeInntekter {
        @Test
        @Transactional
        open fun `skal slette duplikate inntekter med samme type og periode,ved oppdatering av grunnlag`() {
            // gitt
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            testdataManager.oppretteOgLagreGrunnlag(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentet = LocalDate.of(YearMonth.now().minusYears(1).year, 1, 1).atStartOfDay(),
                grunnlagsdata =
                    SkattepliktigeInntekter(
                        listOf(
                            AinntektGrunnlagDto(
                                personId = behandling.bidragsmottaker!!.ident!!,
                                periodeFra = LocalDate.now().minusMonths(5).withDayOfMonth(1),
                                periodeTil = LocalDate.now().minusMonths(3).withDayOfMonth(1),
                                ainntektspostListe =
                                    listOf(
                                        tilAinntektspostDto(
                                            beløp = BigDecimal(52500),
                                            fomDato = LocalDate.now().minusMonths(5).withDayOfMonth(1),
                                            tilDato = LocalDate.now().minusMonths(3).withDayOfMonth(1),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                aktiv = null,
            )

            fun ainntekt12Mnd(): Inntekt {
                val fom = YearMonth.now().minusMonths(12).atDay(1)
                val tom = YearMonth.now().atDay(1)
                val inntekt =
                    Inntekt(
                        behandling = behandling,
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        belop = BigDecimal(14000),
                        datoFom = fom,
                        datoTom = tom,
                        opprinneligFom = fom,
                        opprinneligTom = tom,
                        ident = testdataBM.ident,
                        gjelderBarn = testdataBarn1.ident,
                        kilde = Kilde.OFFENTLIG,
                        taMed = true,
                    )
                inntekt.inntektsposter =
                    mutableSetOf(
                        Inntektspost(
                            beløp = BigDecimal(14000),
                            kode = "fastloenn",
                            inntektstype = null,
                            inntekt = inntekt,
                        ),
                    )

                return inntekt
            }

            behandling.inntekter.add(ainntekt12Mnd())
            val ainntektMedSammePeriodeMenAnnetBeløp = ainntekt12Mnd()
            ainntektMedSammePeriodeMenAnnetBeløp.belop = BigDecimal(123456)
            behandling.inntekter.add(ainntektMedSammePeriodeMenAnnetBeløp)
            try {
                entityManager.persist(behandling)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val grunnlagMedAinntekt = behandling.grunnlag.first()

            val skattepliktigeInntekter =
                Jsonoperasjoner.jsonTilObjekt<SkattepliktigeInntekter>(grunnlagMedAinntekt.data)

            val transformereInntekter =
                TransformerInntekterRequest(
                    ainntektHentetDato = LocalDate.now(),
                    ainntektsposter =
                        skattepliktigeInntekter.ainntekter
                            .flatMap { it.ainntektspostListe }
                            .tilAinntektsposter(testdataBM.tilRolle(behandling)),
                    kontantstøtteliste = emptyList(),
                    skattegrunnlagsliste = emptyList(),
                    småbarnstilleggliste = emptyList(),
                    utvidetBarnetrygdliste = emptyList(),
                )

            val transformerteInntekterrespons =
                inntektApi.transformerInntekter(transformereInntekter)

            // hvis
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                transformerteInntekterrespons.summertÅrsinntektListe,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.inntekter.size shouldBe 2
                behandling.inntekter.filter { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }.belop shouldBe
                    BigDecimal(
                        52500,
                    )
                behandling.inntekter.filter { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }.belop shouldBe
                    BigDecimal(
                        210000,
                    )
            }
        }

        @Test
        @Transactional
        open fun `skal slette duplikate inntekter med samme type men forskjellig periode,ved oppdatering av grunnlag for Ainntekt 3 og 12 mnd`() {
            // gitt
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            testdataManager.oppretteOgLagreGrunnlag<AinntektGrunnlagDto>(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentet = LocalDate.of(YearMonth.now().minusYears(1).year, 1, 1).atStartOfDay(),
                aktiv = null,
            )

            behandling.grunnlag.size shouldBe 1
            behandling.inntekter.size shouldBe 0

            val grunnlagMedAinntekt = behandling.grunnlag.first()

            val skattepliktigeInntekter =
                Jsonoperasjoner.jsonTilObjekt<SkattepliktigeInntekter>(grunnlagMedAinntekt.data)

            val transformereOriginalAinntekt =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = grunnlagMedAinntekt.innhentet.toLocalDate(),
                    ainntektsposter =
                        skattepliktigeInntekter.ainntekter
                            .flatMap { it.ainntektspostListe }
                            .tilAinntektsposter(testdataBM.tilRolle(behandling)),
                ).bygge()

            behandling.inntekter.size shouldBe 0

            entityManager.merge(behandling)

            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                inntektApi.transformerInntekter(transformereOriginalAinntekt).summertÅrsinntektListe,
            )

            entityManager.refresh(behandling)

            behandling.inntekter.size shouldBe 2
            behandling.inntekter
                .filter { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }
                .filter { it.belop == BigDecimal(70000) }
                .size shouldBe 1

            val oppdatertAinntekt =
                skattepliktigeInntekter.ainntekter
                    .flatMap { it.ainntektspostListe }
                    .map { it.copy(beløp = it.beløp + BigDecimal(1000)) }
                    .tilAinntektsposter(testdataBM.tilRolle(behandling))

            val transformereOppdatertAinntekt =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = grunnlagMedAinntekt.innhentet.toLocalDate(),
                    ainntektsposter = oppdatertAinntekt,
                ).bygge()

            val transformerteÅrsinnekterOppdatert = inntektApi.transformerInntekter(transformereOppdatertAinntekt)

            val nyeManupilerteInntekter =
                transformerteÅrsinnekterOppdatert.summertÅrsinntektListe.map {
                    it.copy(
                        periode =
                            it.periode.lagPeriode(
                                YearMonth.now().minusYears(3),
                                YearMonth.now().minusYears(2),
                            ),
                    )
                }

            // hvis
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                nyeManupilerteInntekter,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.inntekter.size shouldBe 2
                behandling.inntekter.filter { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }.belop shouldBe
                    BigDecimal(
                        71000,
                    )
                behandling.inntekter.filter { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }.belop shouldBe BigDecimal.ZERO
            }
        }

        @Test
        @Transactional
        open fun `skal slette duplikat barnetillegg med samme type og periode,ved oppdatering av grunnlag`() {
            // gitt
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            val originaltBarnetilleggsgrunnlag =
                BarnetilleggGrunnlagDto(
                    barnPersonId = behandling.søknadsbarn.first().ident!!,
                    beløpBrutto = BigDecimal(3500),
                    barnetilleggType = "Pensjon",
                    barnType = "særkullsbarn",
                    partPersonId = behandling.bidragsmottaker!!.ident!!,
                    periodeFra = YearMonth.now().minusMonths(5).atDay(1),
                    periodeTil = YearMonth.now().minusMonths(4).atDay(1),
                )

            val summereOriginalKontantstøtte =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    barnetillegg = listOf(originaltBarnetilleggsgrunnlag).tilBarnetillegg(behandling.bidragsmottaker!!),
                ).bygge()

            behandling.inntekter.size shouldBe 0

            val originalTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOriginalKontantstøtte)
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                originalTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            entityManager.refresh(behandling)
            behandling.inntekter.size shouldBe 1

            val nyttBarnetilleggsgrunnlag = originaltBarnetilleggsgrunnlag.copy(beløpBrutto = BigDecimal(3750))
            val summereOppdatertKontantstøtte =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    barnetillegg = listOf(nyttBarnetilleggsgrunnlag).tilBarnetillegg(behandling.bidragsmottaker!!),
                ).bygge()

            val nyTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOppdatertKontantstøtte)

            // hvis
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                nyTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.inntekter.size shouldBe 1
                behandling.inntekter.filter { Inntektsrapportering.BARNETILLEGG == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.BARNETILLEGG == it.type }.belop shouldBe
                    BigDecimal(
                        45000,
                    )
            }
        }

        @Test
        @Transactional
        open fun `skal slette duplikat kontantstøtte med samme type og periode,ved oppdatering av grunnlag`() {
            // gitt
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            val originaltKontantstøttegrunnlag =
                KontantstøtteGrunnlagDto(
                    barnPersonId = behandling.søknadsbarn.first().ident!!,
                    beløp = 3500,
                    partPersonId = behandling.bidragsmottaker!!.ident!!,
                    periodeFra = YearMonth.now().minusMonths(5).atDay(1),
                    periodeTil = YearMonth.now().minusMonths(4).atDay(1),
                )

            val summereOriginalKontantstøtte =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    kontantstøtte =
                        listOf(originaltKontantstøttegrunnlag)
                            .tilKontantstøtte(behandling.bidragsmottaker!!),
                ).bygge()

            behandling.inntekter.size shouldBe 0

            val originalTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOriginalKontantstøtte)
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                originalTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            entityManager.refresh(behandling)
            behandling.inntekter.size shouldBe 1

            val nyttKontantstøttegrunnlag = originaltKontantstøttegrunnlag.copy(beløp = 3750)
            val summereOppdatertKontantstøtte =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    kontantstøtte = listOf(nyttKontantstøttegrunnlag).tilKontantstøtte(behandling.bidragsmottaker!!),
                ).bygge()

            val nyTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOppdatertKontantstøtte)

            // hvis
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                nyTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.inntekter.size shouldBe 1
                behandling.inntekter.filter { Inntektsrapportering.KONTANTSTØTTE == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.KONTANTSTØTTE == it.type }.belop shouldBe
                    BigDecimal(
                        45000,
                    )
            }
        }

        @Test
        @Transactional
        open fun `skal slette duplikat småbarnstillegg med samme type og periode,ved oppdatering av grunnlag`() {
            // gitt
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            val originaltSmåbarnstilleggsgrunnlag =
                SmåbarnstilleggGrunnlagDto(
                    beløp = BigDecimal(3500),
                    personId = behandling.bidragsmottaker!!.ident!!,
                    periodeFra = YearMonth.now().minusMonths(5).atDay(1),
                    periodeTil = YearMonth.now().minusMonths(4).atDay(1),
                    manueltBeregnet = false,
                )

            val summereOriginalSmåbarnstillegg =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    småbarnstillegg = listOf(originaltSmåbarnstilleggsgrunnlag).tilSmåbarnstillegg(behandling.bidragsmottaker!!),
                ).bygge()

            behandling.inntekter.size shouldBe 0

            val originalTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOriginalSmåbarnstillegg)
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                originalTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            entityManager.refresh(behandling)
            behandling.inntekter.size shouldBe 1

            val nyttSmåbarnstilleggsgrunnlag = originaltSmåbarnstilleggsgrunnlag.copy(beløp = BigDecimal(3750))
            val summereOppdatertSmåbarnstillegg =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    småbarnstillegg = listOf(nyttSmåbarnstilleggsgrunnlag).tilSmåbarnstillegg(behandling.bidragsmottaker!!),
                ).bygge()

            val nyTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOppdatertSmåbarnstillegg)

            // hvis
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                nyTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.inntekter.size shouldBe 1
                behandling.inntekter.filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }.belop shouldBe
                    BigDecimal(
                        45000,
                    )
            }
        }

        @Test
        @Transactional
        open fun `skal slette duplikat utvidet barnetrygd med samme type og periode,ved oppdatering av grunnlag`() {
            // gitt
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            val originalUtvidetBarnetrygdsgrunnlag =
                UtvidetBarnetrygdGrunnlagDto(
                    beløp = BigDecimal(3500),
                    personId = behandling.bidragsmottaker!!.ident!!,
                    periodeFra = YearMonth.now().minusMonths(5).atDay(1),
                    periodeTil = YearMonth.now().minusMonths(4).atDay(1),
                    manueltBeregnet = false,
                )

            val summereOriginalUtvidetBarnetrygd =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    utvidetBarnetrygd =
                        listOf(
                            originalUtvidetBarnetrygdsgrunnlag,
                        ).tilUtvidetBarnetrygd(behandling.bidragsmottaker!!),
                ).bygge()

            behandling.inntekter.size shouldBe 0

            val originalTransformerteInntekterrespons =
                inntektApi.transformerInntekter(summereOriginalUtvidetBarnetrygd)
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                originalTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            entityManager.refresh(behandling)
            behandling.inntekter.size shouldBe 1

            val nyttUtvidetBarnetrygdsgrunnlag = originalUtvidetBarnetrygdsgrunnlag.copy(beløp = BigDecimal(3750))
            val summereOppdatertUtvidetBarnetrygd =
                TransformerInntekterRequestBuilder(
                    ainntektHentetDato = LocalDate.now(),
                    utvidetBarnetrygd = listOf(nyttUtvidetBarnetrygdsgrunnlag).tilUtvidetBarnetrygd(behandling.bidragsmottaker!!),
                ).bygge()

            val nyTransformerteInntekterrespons = inntektApi.transformerInntekter(summereOppdatertUtvidetBarnetrygd)

            // hvis
            inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
                behandling,
                behandling.bidragsmottaker!!,
                nyTransformerteInntekterrespons.summertÅrsinntektListe,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.inntekter.size shouldBe 1
                behandling.inntekter.filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }.belop shouldBe
                    BigDecimal(
                        45000,
                    )
            }
        }
    }

    @Nested
    @DisplayName("Teste manuell oppdatering av inntekter")
    open inner class OppdatereInntekterManuelt {
        @Test
        open fun `skal oppdatere eksisterende inntekt`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            val kontantstøtte =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.KONTANTSTØTTE,
                    belop = BigDecimal(14000),
                    datoFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    datoTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    ident = testdataBM.ident,
                    gjelderBarn = testdataBarn1.ident,
                    kilde = Kilde.MANUELL,
                    taMed = true,
                )

            val lagretKontantstøtte = inntektRepository.save(kontantstøtte)

            val behandlingEtterOppdatering =
                behandlingRepository.findBehandlingById(behandling.id!!)

            behandlingEtterOppdatering.get().inntekter.size shouldBe 1

            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(
                    oppdatereManuellInntekt =
                        oppretteRequestForOppdateringAvManuellInntekt(idInntekt = lagretKontantstøtte.id!!),
                )

            // hvis
            inntektService.oppdatereInntektManuelt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                shouldBePresent(oppdatertBehandling)
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .type shouldBe
                    forespørselOmOppdateringAvInntekter.oppdatereManuellInntekt!!.type
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .belop shouldBe
                    forespørselOmOppdateringAvInntekter.oppdatereManuellInntekt.beløp
            }
        }

        @Test
        fun `skal slette manuelle inntekter`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            val kontantstøtte =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.KONTANTSTØTTE,
                    belop = BigDecimal(14000),
                    datoFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    datoTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    ident = testdataBM.ident,
                    gjelderBarn = testdataBarn1.ident,
                    kilde = Kilde.MANUELL,
                    taMed = true,
                )

            val lagretKontantstøtte = inntektRepository.save(kontantstøtte)

            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(sletteInntekt = lagretKontantstøtte.id!!)

            // hvis
            inntektService.oppdatereInntektManuelt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                shouldBePresent(oppdatertBehandling)
                oppdatertBehandling.get().inntekter.size shouldBe 0
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere periode på offentlige inntekter`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            val ainntekt =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT,
                    belop = BigDecimal(50000),
                    datoFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    datoTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    opprinneligFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    opprinneligTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    ident = testdataBM.ident,
                    kilde = Kilde.OFFENTLIG,
                    taMed = true,
                )

            val postAinntekt =
                Inntektspost(
                    beløp = ainntekt.belop,
                    inntektstype = Inntektstype.LØNNSINNTEKT,
                    kode = "fastloenn",
                    inntekt = ainntekt,
                )
            ainntekt.inntektsposter = mutableSetOf(postAinntekt)

            val lagretInntekt = inntektRepository.save(ainntekt)

            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(
                    oppdatereInntektsperiode =
                        OppdaterePeriodeInntekt(
                            id = lagretInntekt.id!!,
                            taMedIBeregning = true,
                            angittPeriode =
                                Datoperiode(
                                    lagretInntekt.datoFom!!.minusYears(2),
                                    lagretInntekt.datoTom?.plusMonths(1),
                                ),
                        ),
                )

            behandling.inntekter.add(lagretInntekt)
            // hvis
            inntektService.oppdatereInntektManuelt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)
            entityManager.flush()
            entityManager.refresh(oppdatertBehandling.get())

            assertSoftly {
                shouldBePresent(oppdatertBehandling)
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .belop shouldBe ainntekt.belop
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .datoFom shouldBe
                    forespørselOmOppdateringAvInntekter.oppdatereInntektsperiode!!
                        .angittPeriode!!
                        .fom
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .datoTom shouldBe
                    forespørselOmOppdateringAvInntekter.oppdatereInntektsperiode
                        .angittPeriode!!
                        .til
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .opprinneligFom shouldBe ainntekt.opprinneligFom
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .opprinneligTom shouldBe ainntekt.opprinneligTom
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .inntektsposter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .inntektsposter
                    .first()
                    .inntektstype shouldBe postAinntekt.inntektstype
            }
        }
    }

    @Nested
    @DisplayName("Teste maunuell oppdatering av inntekt")
    open inner class OppdatereInntektManuelt {
        @Test
        open fun `skal oppdatere eksisterende manuell inntekt`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            val manuellInntekt =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                    belop = BigDecimal(563000),
                    datoFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    datoTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    ident = testdataBM.ident,
                    kilde = Kilde.MANUELL,
                    taMed = true,
                )

            val lagretManuellInntekt = inntektRepository.save(manuellInntekt)
            val lagretBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            lagretBehandling.isPresent shouldBe true
            lagretBehandling.get().inntekter.size shouldBe 1
            lagretBehandling
                .get()
                .inntekter
                .first()
                .belop shouldBe manuellInntekt.belop
            lagretManuellInntekt.id shouldNotBe null
            lagretManuellInntekt.belop shouldBe manuellInntekt.belop

            val forespørselOmOppdateringAvInntekter =
                OppdatereInntektRequest(
                    oppdatereManuellInntekt =
                        OppdatereManuellInntekt(
                            id = lagretManuellInntekt.id!!,
                            taMed = true,
                            type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                            beløp = BigDecimal(643000),
                            datoFom =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            datoTom =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(12)
                                    .atDay(31),
                            ident = Personident(testdataBM.ident),
                        ),
                )

            // hvis
            inntektService.oppdatereInntektManuelt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                shouldBePresent(oppdatertBehandling)
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .belop shouldBe
                    forespørselOmOppdateringAvInntekter.oppdatereManuellInntekt!!.beløp
            }
        }

        @Test
        open fun `skal slette manuell inntekt`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            val kontantstøtte =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.KONTANTSTØTTE,
                    belop = BigDecimal(14000),
                    datoFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    datoTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    ident = testdataBM.ident,
                    gjelderBarn = testdataBarn1.ident,
                    kilde = Kilde.MANUELL,
                    taMed = true,
                )

            val lagretKontantstøtte = inntektRepository.save(kontantstøtte)
            val lagretBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            lagretBehandling.isPresent shouldBe true
            lagretBehandling.get().inntekter.size shouldBe 1
            lagretBehandling
                .get()
                .inntekter
                .first()
                .belop shouldBe kontantstøtte.belop

            val forespørselOmOppdateringAvInntekter = OppdatereInntektRequest(sletteInntekt = lagretKontantstøtte.id!!)

            // hvis
            inntektService.oppdatereInntektManuelt(
                behandling.id!!,
                forespørselOmOppdateringAvInntekter,
            )

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                shouldBePresent(oppdatertBehandling)
                oppdatertBehandling.get().inntekter.size shouldBe 0
            }
        }

        @Test
        @Transactional
        open fun `skal ta med offentlig inntekt som er ekplisitt ytelse og sette datoFom og datoTom automatisk`() {
            // gitt
            val behandling = oppretteTestbehandling(false, false, false)
            behandling.virkningstidspunkt = LocalDate.parse("2023-05-01")
            val utvidetBarnetrygd =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    belop = BigDecimal(14000),
                    opprinneligFom = YearMonth.parse("2023-01").atDay(1),
                    opprinneligTom = YearMonth.parse("2024-05").atEndOfMonth(),
                    datoTom = null,
                    datoFom = null,
                    ident = testdataBM.ident,
                    gjelderBarn = testdataBarn1.ident,
                    kilde = Kilde.OFFENTLIG,
                    taMed = false,
                )

            behandling.inntekter.add(utvidetBarnetrygd)
            testdataManager.lagreBehandlingNewTransaction(behandling)
            val lagraInntekter = inntektRepository.findAll()

            val oppdatereInntektRequest =
                OppdatereInntektRequest(
                    oppdatereInntektsperiode =
                        OppdaterePeriodeInntekt(
                            taMedIBeregning = true,
                            id = lagraInntekter.find { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }!!.id!!,
                        ),
                )

            // hvis
            inntektService.oppdatereInntektManuelt(behandling.id!!, oppdatereInntektRequest)

            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly(oppdatertBehandling.get()) {
                it.shouldNotBeNull()
                inntekter.size shouldBe 1
                inntekter.first().datoFom shouldBe behandling.virkningstidspunkt
                inntekter.first().datoTom shouldBe YearMonth.parse("2024-05").atEndOfMonth()
            }
        }

        @Test
        @Transactional
        open fun `skal legge til ny manuell inntekt`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            val oppdatereInntektRequest =
                OppdatereInntektRequest(
                    oppdatereManuellInntekt =
                        OppdatereManuellInntekt(
                            taMed = true,
                            type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                            beløp = BigDecimal(643000),
                            datoFom =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            datoTom =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(12)
                                    .atDay(31),
                            ident = Personident(testdataBM.ident),
                        ),
                )
            // hvis
            inntektService.oppdatereInntektManuelt(behandling.id!!, oppdatereInntektRequest)

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                shouldBePresent(behandling)
                behandling.inntekter.size shouldBe 1
                behandling.inntekter.first().type shouldBe oppdatereInntektRequest.oppdatereManuellInntekt?.type
                behandling.inntekter.first().belop shouldBe oppdatereInntektRequest.oppdatereManuellInntekt?.beløp
            }
        }

        @Test
        fun `skal oppdatere periode på offentlige inntekter`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()
            stubUtils.stubKodeverkPensjonsbeskrivelser()

            val ainntekt =
                Inntekt(
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT,
                    belop = BigDecimal(50000),
                    datoFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    datoTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    opprinneligFom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .atDay(1),
                    opprinneligTom =
                        YearMonth
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .atDay(31),
                    ident = testdataBM.ident,
                    kilde = Kilde.OFFENTLIG,
                    taMed = true,
                )

            val postAinntekt =
                Inntektspost(
                    beløp = ainntekt.belop,
                    inntektstype = Inntektstype.LØNNSINNTEKT,
                    kode = "fastloenn",
                    inntekt = ainntekt,
                )
            ainntekt.inntektsposter = mutableSetOf(postAinntekt)

            val lagretInntekt = inntektRepository.save(ainntekt)

            val oppdatereInntektRequest =
                OppdatereInntektRequest(
                    oppdatereInntektsperiode =
                        OppdaterePeriodeInntekt(
                            id = lagretInntekt.id!!,
                            taMedIBeregning = false,
                            angittPeriode =
                                Datoperiode(
                                    lagretInntekt.datoFom!!.minusYears(2),
                                    lagretInntekt.datoTom?.plusMonths(1),
                                ),
                        ),
                )

            // hvis
            inntektService.oppdatereInntektManuelt(behandling.id!!, oppdatereInntektRequest)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                shouldBePresent(oppdatertBehandling)
                oppdatertBehandling.get().inntekter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .belop shouldBe ainntekt.belop
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .datoFom shouldBe null
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .datoTom shouldBe null
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .opprinneligFom shouldBe ainntekt.opprinneligFom
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .opprinneligTom shouldBe ainntekt.opprinneligTom
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .inntektsposter.size shouldBe 1
                oppdatertBehandling
                    .get()
                    .inntekter
                    .first()
                    .inntektsposter
                    .first()
                    .inntektstype shouldBe postAinntekt.inntektstype
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere periode på inntekter etter endring i virkningstidspunkt`() {
            val behandling = testdataManager.oppretteBehandling(false, false, false)
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
                        datoFom = YearMonth.parse("2023-06"),
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
            testdataManager.lagreBehandling(behandling)
            inntektService.rekalkulerPerioderInntekter(behandling.id!!)

            val inntekter = behandling!!.inntekter.toList()
            inntekter.filter { it.taMed } shouldHaveSize 3
            inntekter.filter { it.datoFom != null && it.datoFom!! > virkningstidspunkt } shouldHaveSize 2
        }
    }
}
