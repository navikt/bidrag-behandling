package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GrunnlagServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    val totaltAntallGrunnlag = 16

    @BeforeEach
    fun setup() {
        grunnlagRepository.deleteAll()
        behandlingRepository.deleteAll()

        stubUtils.stubKodeverkSkattegrunnlag()
        stubUtils.stubKodeverkLønnsbeskrivelse()
        stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
        stubUtils.stubKodeverkYtelsesbeskrivelser()
    }

    @Nested
    @DisplayName("Teste oppdatereGrunnlagForBehandling")
    open inner class OppdatereGrunnlagForBehandling {
        @Test
        fun `skal lagre skattegrunnlag`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            behandling.roller.forEach {
                when (it.rolletype) {
                    Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlagOk(it)
                    Rolletype.BARN ->
                        stubUtils.stubHenteGrunnlagOk(
                            rolle = it,
                            navnResponsfil = "hente-grunnlagrespons-barn.json",
                        )

                    else -> throw Exception()
                }
            }

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlagBarn = oppdatertBehandling.get().grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }
            assertSoftly {
                grunnlagBarn.size shouldBe 6
                grunnlagBarn.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type }.size shouldBe 2
                grunnlagBarn.filter { Grunnlagsdatatype.INNTEKT == it.type }.size shouldBe 2
                grunnlagBarn.filter { Grunnlagsdatatype.INNTEKT_BEARBEIDET == it.type }.size shouldBe 2
            }

            val grunnlagBm =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.INNTEKT,
                )

            assertThat(grunnlagBm?.data?.isNotEmpty())

            val grunnlagInntekt = jsonTilObjekt<GrunnlagInntekt>(grunnlagBm?.data!!)

            assertSoftly {
                grunnlagInntekt.skattegrunnlag shouldNotBe emptySet<SkattegrunnlagGrunnlagDto>()
                grunnlagInntekt.skattegrunnlag.size shouldBe 1
                grunnlagInntekt.skattegrunnlag[0].personId shouldBe "99057812345"
            }
        }

        @Test
        fun `skal ikke oppdatere grunnlag dersom venteperioden etter forrige innhenting ikke er over`() {
            // gitt
            val grunnlagSistInnhentet = LocalDateTime.now().minusMinutes(30)
            val behandling = testdataManager.opprettBehandling(false)
            behandling.grunnlagSistInnhentet = grunnlagSistInnhentet

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlagSistInnhentet = grunnlagSistInnhentet
                oppdatertBehandling.get().grunnlag.size shouldBe 0
            }
        }

        @Test
        @Transactional
        open fun `skal ikke lagre ny innhenting av inntekt hvis ingen endringer`() {
            // gitt
            val innhentingstidspunkt: LocalDateTime = LocalDate.of(2024, 1, 1).atStartOfDay()
            val behandling = testdataManager.opprettBehandling(false)

            val skattegrunnlag =
                SkattegrunnlagGrunnlagDto(
                    periodeFra = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                    periodeTil = YearMonth.now().withMonth(1).atDay(1),
                    personId = behandling.bidragsmottaker!!.ident!!,
                    skattegrunnlagspostListe =
                        listOf(
                            SkattegrunnlagspostDto(
                                beløp = BigDecimal(450000),
                                belop = BigDecimal(450000),
                                inntektType = "renteinntektAvObligasjon",
                                skattegrunnlagType = "ORINÆR",
                            ),
                        ),
                )

            testdataManager.oppretteOgLagreGrunnlag(
                behandling.id!!,
                Grunnlagsdatatype.INNTEKT,
                innhentingstidspunkt,
                innhentingstidspunkt,
                grunnlagsdata =
                    GrunnlagInntekt(
                        ainntekt = emptyList(),
                        skattegrunnlag = listOf(skattegrunnlag),
                    ),
            )
            behandling.roller.forEach {
                when (it.rolletype) {
                    Rolletype.BIDRAGSMOTTAKER ->
                        stubUtils.stubHenteGrunnlagOk(
                            rolle = it,
                            responsobjekt =
                                tilHentGrunnlagDto(
                                    skattegrunnlag =
                                        listOf(
                                            skattegrunnlag,
                                        ),
                                ),
                        )

                    else -> stubUtils.stubHenteGrunnlagOk(rolle = it, tomRespons = true)
                }
            }

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe 2
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.INNTEKT,
                )

            assertSoftly {
                grunnlag shouldNotBe null
                grunnlag?.data shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                grunnlag?.innhentet?.toLocalDate() shouldBe innhentingstidspunkt.toLocalDate()
                grunnlag?.aktiv?.toLocalDate() shouldBe innhentingstidspunkt.toLocalDate()
            }

            val grunnlagInntekt = jsonTilObjekt<GrunnlagInntekt>(grunnlag?.data!!)

            assertSoftly {
                grunnlagInntekt.skattegrunnlag shouldNotBe emptySet<SkattegrunnlagGrunnlagDto>()
                grunnlagInntekt.skattegrunnlag.size shouldBe 1
                grunnlagInntekt.skattegrunnlag[0].personId shouldBe behandling.bidragsmottaker!!.ident
            }
        }

        @Test
        fun `skal ikke lagre ny innhenting av småbarnstillegg hvis ingen endringer`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            val småbarnstillegg =
                SmåbarnstilleggGrunnlagDto(
                    beløp = BigDecimal(3700),
                    manueltBeregnet = false,
                    periodeFra = YearMonth.now().minusMonths(7).atDay(1),
                    periodeTil = YearMonth.now().atDay(1),
                    personId = behandling.bidragsmottaker!!.ident!!,
                )

            behandling.grunnlag.add(
                Grunnlag(
                    behandling,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    data = tilJson(setOf(småbarnstillegg)),
                    innhentet = LocalDateTime.now().minusDays(1),
                    aktiv = LocalDateTime.now().minusDays(1),
                    rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                ),
            )

            behandlingRepository.save(behandling)

            behandling.roller.forEach {
                when (it.rolletype) {
                    Rolletype.BIDRAGSMOTTAKER ->
                        stubUtils.stubHenteGrunnlagOk(
                            it,
                            responsobjekt = tilHentGrunnlagDto(småbarnstillegg = listOf(småbarnstillegg)),
                        )

                    Rolletype.BARN -> stubUtils.stubHenteGrunnlagOk(it, tomRespons = true)

                    else -> throw Exception()
                }
            }

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe 2
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                )

            assertSoftly {
                grunnlag shouldNotBe null
                grunnlag?.data shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                grunnlag?.innhentet?.toLocalDate() shouldBe LocalDate.now().minusDays(1)
                grunnlag?.aktiv?.toLocalDate() shouldBe LocalDate.now().minusDays(1)
            }

            assertThat(grunnlag?.data?.isNotEmpty())

            val lagredeSmåbarnstillegg = jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(grunnlag?.data!!)

            assertSoftly {
                lagredeSmåbarnstillegg shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                lagredeSmåbarnstillegg.size shouldBe 1
                lagredeSmåbarnstillegg.filter { sbt -> sbt.personId == behandling.bidragsmottaker!!.ident!! }.size shouldBe 1
                lagredeSmåbarnstillegg.filter { sbt -> sbt.beløp == småbarnstillegg.beløp }.size shouldBe 1
            }
        }

        @Test
        fun `skal lagre tomt grunnlag uten å sette til aktiv dersom sist lagrede grunnlag ikke var tomt`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubHenteGrunnlagOk(tomRespons = true)

            val småbarnstillegg =
                SmåbarnstilleggGrunnlagDto(
                    beløp = BigDecimal(3700),
                    manueltBeregnet = false,
                    periodeFra = YearMonth.now().minusMonths(7).atDay(1),
                    periodeTil = YearMonth.now().atDay(1),
                    personId = behandling.bidragsmottaker!!.ident!!,
                )

            behandling.grunnlag.add(
                Grunnlag(
                    behandling,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    data = tilJson(setOf(småbarnstillegg)),
                    innhentet = LocalDateTime.now().minusDays(1),
                    aktiv = LocalDateTime.now().minusDays(1),
                    rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                ),
            )

            behandlingRepository.save(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe 2
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                )

            assertSoftly {
                jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(grunnlag!!.data) shouldBe emptySet()
                grunnlag.aktiv shouldBe null
            }

            val gjeldendeAktiveGrunnlag = grunnlagRepository.findAll().filter { g -> g.aktiv != null }

            gjeldendeAktiveGrunnlag.size shouldBe 1

            val småbarnstilleggGrunnlagDto =
                jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(gjeldendeAktiveGrunnlag.first().data)

            assertSoftly {
                småbarnstilleggGrunnlagDto shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                småbarnstilleggGrunnlagDto.size shouldBe 1
                småbarnstilleggGrunnlagDto.filter { sbt -> sbt.personId == behandling.bidragsmottaker!!.ident!! }.size shouldBe 1
                småbarnstilleggGrunnlagDto.filter { sbt -> sbt.beløp == småbarnstillegg.beløp }.size shouldBe 1
            }
        }

        @Test
        fun `skal ikke lagre tomt grunnlag dersom sist lagrede grunnlag var tomt`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubHenteGrunnlagOk(tomRespons = true)

            val lagretGrunnlag = behandlingRepository.findBehandlingById(behandling.id!!).get().grunnlag

            lagretGrunnlag.size shouldBe 0

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe 0
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.INNTEKT,
                )

            grunnlag shouldBe null
        }

        @Test
        fun `skal sette til aktiv dersom ikke tidligere lagret`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            oppdatertBehandling.get().grunnlag.forEach {
                assertSoftly {
                    it.data shouldNotBe null
                    it.aktiv shouldNotBe null
                    it.aktiv?.toLocalDate() shouldBe LocalDate.now()
                }
            }
        }

        @Test
        fun `skal lagre husstandsmedlemmer`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
                )
            val husstandsmedlemmer = jsonListeTilObjekt<RelatertPersonGrunnlagDto>(grunnlag?.data!!)

            assertSoftly {
                husstandsmedlemmer.size shouldBe 2
                husstandsmedlemmer.filter { h ->
                    h.navn == "Småstein Nilsen" && h.fødselsdato ==
                        LocalDate.of(
                            2020,
                            1,
                            24,
                        ) && h.erBarnAvBmBp
                }.toSet().size shouldBe 1
            }
        }

        @Test
        fun `skal lagre sivilstand`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SIVILSTAND,
                )
            val sivilstand = jsonListeTilObjekt<SivilstandGrunnlagDto>(grunnlag?.data!!)

            assertSoftly {
                sivilstand.size shouldBe 2
                sivilstand.filter { s ->
                    s.personId == "99057812345" && s.bekreftelsesdato ==
                        LocalDate.of(
                            2021,
                            1,
                            1,
                        ) && s.gyldigFom ==
                        LocalDate.of(
                            2021,
                            1,
                            1,
                        ) && s.master == "FREG" &&
                        s.historisk == true && s.registrert ==
                        LocalDateTime.parse(
                            "2022-01-01T10:03:57.285",
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        ) && s.type == SivilstandskodePDL.GIFT
                }.toSet().size shouldBe 1
            }
        }

        @Test
        fun `skal lagre småbarnstillegg`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SIVILSTAND,
                )
            val sivilstand = jsonListeTilObjekt<SivilstandGrunnlagDto>(grunnlag?.data!!)

            assertSoftly {
                sivilstand.size shouldBe 2
                sivilstand.filter { s ->
                    s.personId == "99057812345" && s.bekreftelsesdato ==
                        LocalDate.of(
                            2021,
                            1,
                            1,
                        ) && s.gyldigFom ==
                        LocalDate.of(
                            2021,
                            1,
                            1,
                        ) && s.master == "FREG" &&
                        s.historisk == true && s.registrert ==
                        LocalDateTime.parse(
                            "2022-01-01T10:03:57.285",
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        ) && s.type == SivilstandskodePDL.GIFT
                }.toSet().size shouldBe 1
            }
        }

        @Test
        fun `skal lagre yteslser`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlagBarnetillegg =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.BARNETILLEGG,
                )
            grunnlagBarnetillegg shouldNotBe null
            val barnetillegg = jsonListeTilObjekt<BarnetilleggGrunnlagDto>(grunnlagBarnetillegg?.data!!)
            assertSoftly {
                barnetillegg.size shouldBe 1
            }

            val grunnlagBarnetilsyn =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.BARNETILSYN,
                )
            grunnlagBarnetilsyn shouldNotBe null
            val barnetilsyn = jsonListeTilObjekt<BarnetilsynGrunnlagDto>(grunnlagBarnetilsyn?.data!!)
            assertSoftly {
                barnetilsyn.size shouldBe 1
            }

            val grunnlagKontantstøtte =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.KONTANTSTØTTE,
                )
            grunnlagKontantstøtte shouldNotBe null
            val kontantstøtte = jsonListeTilObjekt<KontantstøtteGrunnlagDto>(grunnlagKontantstøtte?.data!!)
            assertSoftly {
                kontantstøtte.size shouldBe 1
            }

            val grunnlagUtvidetBarnetrygd =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                )
            grunnlagUtvidetBarnetrygd shouldNotBe null

            val utvidetBarnetrygd =
                jsonListeTilObjekt<UtvidetBarnetrygdGrunnlagDto>(grunnlagUtvidetBarnetrygd?.data!!)
            assertSoftly {
                utvidetBarnetrygd.size shouldBe 1
            }

            val grunnlagSmåbarnstillegg =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                )
            grunnlagSmåbarnstillegg shouldNotBe null
            val småbarnstillegg =
                jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(grunnlagSmåbarnstillegg?.data!!)
            assertSoftly {
                småbarnstillegg.size shouldBe 1
            }
        }

        @Test
        fun `skal lagre arbeidsforhold`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubbeGrunnlagsinnhenting(behandling)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.ARBEIDSFORHOLD,
                )
            val arbeidsforhold = jsonListeTilObjekt<ArbeidsforholdGrunnlagDto>(grunnlag?.data!!)

            assertSoftly {
                arbeidsforhold.size shouldBe 3
                arbeidsforhold.filter { a ->
                    a.partPersonId == "99057812345" && a.sluttdato == null && a.startdato ==
                        LocalDate.of(
                            2002,
                            11,
                            3,
                        ) && a.arbeidsgiverNavn == "SAUEFABRIKK" &&
                        a.arbeidsgiverOrgnummer == "896929119"
                }.toSet().size shouldBe 1
            }
        }
    }

    @Nested
    @DisplayName("Teste aktivering av grunnlag")
    open inner class AktivereGrunnlag {
        @Test
        open fun `skal aktivere grunnlag`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            testdataManager.oppretteOgLagreGrunnlag<GrunnlagInntekt>(
                behandlingsid = behandling.id!!,
                grunnlagsdatatype = Grunnlagsdatatype.INNTEKT,
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
            )

            val lagraIkkeAktiveGrunnlag =
                grunnlagRepository.findAll().filter { it.aktiv == null }.map { it.id!! }.toSet()

            // hvis
            grunnlagService.aktivereGrunnlag(lagraIkkeAktiveGrunnlag)

            // så
            val aktiverteGrunnlag = grunnlagRepository.findById(lagraIkkeAktiveGrunnlag.first())

            assertSoftly {
                aktiverteGrunnlag.isPresent
                aktiverteGrunnlag.get().aktiv?.toLocalDate() shouldBe LocalDate.now()
            }
        }
    }

    @Nested
    @DisplayName("Teste hentSistInnhentet")
    open inner class HentSistInnhentet {
        @Test
        fun `skal være bare en rad med aktive opplysninger`() {
            // gitt
            val b = testdataManager.opprettBehandling(false)

            val tidspunktInnhentet = LocalDateTime.now()

            val opp4 =
                grunnlagRepository.save(
                    Grunnlag(
                        b,
                        Grunnlagsdatatype.BOFORHOLD_BEARBEIDET,
                        "{\"test\": \"opp\"}",
                        tidspunktInnhentet,
                        tidspunktInnhentet,
                        rolle = b.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

            // hvis
            val opplysninger =
                grunnlagService.hentSistInnhentet(
                    b.id!!,
                    b.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.BOFORHOLD,
                )

            // så
            assertNotNull(opplysninger)
            assertEquals(opp4.id, opplysninger.id)
        }
    }

    @Nested
    @DisplayName("Teste hentAlleSistInnhentet")
    open inner class HentAlleSistInnhentet {
        @Test
        open fun `skal hente nyeste grunnlagsopplysning per type`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(true)

            stubbeGrunnlagsinnhenting(behandling)

            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // hvis
            val nyesteGrunnlagPerType =
                grunnlagService.hentAlleSistInnhentet(
                    behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                )

            // så
            assertSoftly {
                nyesteGrunnlagPerType.size shouldBe 10
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.size shouldBe 1
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.HUSSTANDSMEDLEMMER }.size shouldBe 1
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.INNTEKT }.size shouldBe 1
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.SIVILSTAND }.size shouldBe 1
            }
        }
    }

    private fun stubbeGrunnlagsinnhenting(behandling: Behandling) {
        behandling.roller.forEach {
            when (it.rolletype) {
                Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlagOk(it)
                Rolletype.BARN ->
                    stubUtils.stubHenteGrunnlagOk(
                        rolle = it,
                        navnResponsfil = "hente-grunnlagrespons-barn.json",
                    )

                else -> throw Exception()
            }
        }
    }

    companion object {
        fun tilHentGrunnlagDto(
            hentet: LocalDateTime = LocalDateTime.now(),
            ainntekter: List<AinntektGrunnlagDto> = emptyList(),
            arbeidsforhold: List<ArbeidsforholdGrunnlagDto> = emptyList(),
            barnetillegg: List<BarnetilleggGrunnlagDto> = emptyList(),
            barnetilsyn: List<BarnetilsynGrunnlagDto> = emptyList(),
            feilrapportering: List<FeilrapporteringDto> = emptyList(),
            husstandsmeldemmer: List<RelatertPersonGrunnlagDto> = emptyList(),
            kontantstøtte: List<KontantstøtteGrunnlagDto> = emptyList(),
            sivilstand: List<SivilstandGrunnlagDto> = emptyList(),
            skattegrunnlag: List<SkattegrunnlagGrunnlagDto> = emptyList(),
            småbarnstillegg: List<SmåbarnstilleggGrunnlagDto> = emptyList(),
            utvidetBarnetrygd: List<UtvidetBarnetrygdGrunnlagDto> = emptyList(),
        ) = HentGrunnlagDto(
            ainntektListe = ainntekter,
            arbeidsforholdListe = arbeidsforhold,
            barnetilleggListe = barnetillegg,
            barnetilsynListe = barnetilsyn,
            feilrapporteringListe = feilrapportering,
            hentetTidspunkt = hentet,
            husstandsmedlemmerOgEgneBarnListe = husstandsmeldemmer,
            kontantstøtteListe = kontantstøtte,
            sivilstandListe = sivilstand,
            skattegrunnlagListe = skattegrunnlag,
            småbarnstilleggListe = småbarnstillegg,
            utvidetBarnetrygdListe = utvidetBarnetrygd,
        )
    }
}

fun HentGrunnlagDto.tilHentGrunnlagDto(
    hentet: LocalDateTime = LocalDateTime.now(),
    ainntekter: List<AinntektGrunnlagDto> = emptyList(),
    arbeidsforhold: List<ArbeidsforholdGrunnlagDto> = emptyList(),
    barnetillegg: List<BarnetilleggGrunnlagDto> = emptyList(),
    barnetilsyn: List<BarnetilsynGrunnlagDto> = emptyList(),
    feilrapportering: List<FeilrapporteringDto> = emptyList(),
    husstandsmeldemmer: List<RelatertPersonGrunnlagDto> = emptyList(),
    kontantstøtte: List<KontantstøtteGrunnlagDto> = emptyList(),
    sivilstand: List<SivilstandGrunnlagDto> = emptyList(),
    skattegrunnlag: List<SkattegrunnlagGrunnlagDto> = emptyList(),
    småbarnstillegg: List<SmåbarnstilleggGrunnlagDto> = emptyList(),
    utvidetBarnetrygd: List<UtvidetBarnetrygdGrunnlagDto> = emptyList(),
) = HentGrunnlagDto(
    ainntektListe = ainntekter,
    arbeidsforholdListe = arbeidsforhold,
    barnetilleggListe = barnetillegg,
    barnetilsynListe = barnetilsyn,
    feilrapporteringListe = feilrapportering,
    hentetTidspunkt = hentet,
    husstandsmedlemmerOgEgneBarnListe = husstandsmeldemmer,
    kontantstøtteListe = kontantstøtte,
    sivilstandListe = sivilstand,
    skattegrunnlagListe = skattegrunnlag,
    småbarnstilleggListe = småbarnstillegg,
    utvidetBarnetrygdListe = utvidetBarnetrygd,
)
