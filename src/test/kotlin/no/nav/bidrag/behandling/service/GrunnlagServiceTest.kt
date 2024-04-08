package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequest
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
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
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(Enclosed::class)
class GrunnlagServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var entityManager: EntityManager

    val totaltAntallGrunnlag = 20

    @BeforeEach
    fun setup() {
        grunnlagRepository.deleteAll()
        behandlingRepository.deleteAll()

        stubUtils.stubKodeverkSkattegrunnlag()
        stubUtils.stubKodeverkLønnsbeskrivelse()
        stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
        stubUtils.stubKodeverkYtelsesbeskrivelser()
        stubUtils.stubKodeverkPensjonsbeskrivelser()
        stubPersonConsumer()
    }

    @Nested
    @DisplayName("Teste oppdatereGrunnlagForBehandling")
    open inner class OppdatereGrunnlagForBehandling {
        @Test
        @Transactional
        open fun `skal lagre ytelser`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
            behandling.roller.forEach {
                when (it.rolletype) {
                    Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlagOk(it)
                    Rolletype.BARN ->
                        stubUtils.stubHenteGrunnlagOk(
                            rolle = it,
                            navnResponsfil = "hente-grunnlagrespons-barn1.json",
                        )

                    else -> throw Exception()
                }
            }

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)
            entityManager.refresh(oppdatertBehandling.get())

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
                oppdatertBehandling.get().inntekter.size shouldBe totaltAntallGrunnlag
            }

            val alleGrunnlagBm =
                oppdatertBehandling.get().grunnlag.filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }

            validereGrunnlagBm(alleGrunnlagBm)

            val alleGrunnlagBarn =
                oppdatertBehandling.get().grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }

            assertSoftly {
                alleGrunnlagBarn.filter { Grunnlagsdatatype.BARNETILLEGG == it.type }.size shouldBe 0
                alleGrunnlagBarn.filter { Grunnlagsdatatype.KONTANTSTØTTE == it.type }.size shouldBe 0
                alleGrunnlagBarn.filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }.size shouldBe 0
                alleGrunnlagBarn.filter { Grunnlagsdatatype.UTVIDET_BARNETRYGD == it.type }.size shouldBe 0
            }

            val alleInntekterBm =
                oppdatertBehandling.get().inntekter.filter { behandling.bidragsmottaker!!.ident == it.ident }

            assertSoftly {
                alleInntekterBm.size shouldBe 8
                alleInntekterBm.filter { Inntektsrapportering.BARNETILLEGG == it.type }.size shouldBe 0
                oppdatertBehandling.get().inntekter.filter { Inntektsrapportering.BARNETILLEGG == it.type }.size shouldBe 0
                alleInntekterBm.filter { Inntektsrapportering.KONTANTSTØTTE == it.type }.size shouldBe 0
                oppdatertBehandling.get().inntekter.filter { Inntektsrapportering.KONTANTSTØTTE == it.type }.size shouldBe 0
                alleInntekterBm.filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }.size shouldBe 1
                oppdatertBehandling.get().inntekter.filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }.size shouldBe 1
                alleInntekterBm.filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }.size shouldBe 1
                oppdatertBehandling.get().inntekter.filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }.size shouldBe 1
            }
        }

        @Test
        @Transactional
        open fun `skal lagre skattegrunnlag`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
            behandling.roller.forEach {
                when (it.rolletype) {
                    Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlagOk(it)
                    Rolletype.BARN ->
                        stubUtils.stubHenteGrunnlagOk(
                            rolle = it,
                            navnResponsfil = "hente-grunnlagrespons-barn1.json",
                        )

                    else -> throw Exception()
                }
            }

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)
            entityManager.refresh(oppdatertBehandling.get())

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlagBarn =
                oppdatertBehandling.get().grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }
            assertSoftly {
                grunnlagBarn.size shouldBe 8
                grunnlagBarn.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type }.size shouldBe 2
                grunnlagBarn.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 4
                grunnlagBarn.filter { Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == it.type }.size shouldBe 2
                grunnlagBarn.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 4
            }

            val grunnlagBm =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    false,
                )

            assertThat(grunnlagBm?.data?.isNotEmpty())

            val skattegrunnlag = jsonTilObjekt<SkattepliktigeInntekter>(grunnlagBm?.data!!)

            assertSoftly {
                skattegrunnlag.skattegrunnlag
                skattegrunnlag.skattegrunnlag shouldNotBe emptySet<SkattegrunnlagspostDto>()
                skattegrunnlag.skattegrunnlag.size shouldBe 1
                skattegrunnlag.skattegrunnlag.filter { it.personId == "99057812345" }.size shouldBe 1
            }
        }

        @Test
        fun `skal ikke oppdatere grunnlag dersom venteperioden etter forrige innhenting ikke er over`() {
            // gitt
            val grunnlagSistInnhentet = LocalDateTime.now().minusMinutes(30)
            val behandling = testdataManager.opprettBehandling(false)
            behandling.grunnlagSistInnhentet = grunnlagSistInnhentet
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)

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
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

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
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentingstidspunkt,
                innhentingstidspunkt,
                grunnlagsdata = SkattepliktigeInntekter(skattegrunnlag = listOf(skattegrunnlag)),
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
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    false,
                )

            assertSoftly {
                grunnlag shouldNotBe null
                grunnlag?.data shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                grunnlag?.innhentet?.toLocalDate() shouldBe innhentingstidspunkt.toLocalDate()
                grunnlag?.aktiv?.toLocalDate() shouldBe innhentingstidspunkt.toLocalDate()
            }

            val oppdaterteSkattegrunnlag = jsonTilObjekt<SkattepliktigeInntekter>(grunnlag?.data!!)

            assertSoftly {
                oppdaterteSkattegrunnlag.skattegrunnlag.filter { it.personId == behandling.bidragsmottaker!!.ident }.size shouldBe 1
                oppdaterteSkattegrunnlag.skattegrunnlag.filter { it.personId == behandling.bidragsmottaker!!.ident }
                    .map { it.skattegrunnlagspostListe }.size shouldBe 1
            }
        }

        @Test
        @Transactional
        open fun `skal ikke lagre ny innhenting av småbarnstillegg hvis ingen endringer`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

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
                            responsobjekt =
                                tilHentGrunnlagDto(
                                    småbarnstillegg =
                                        listOf(
                                            småbarnstillegg,
                                        ),
                                ),
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
                oppdatertBehandling.get().grunnlag.filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }.size shouldBe 2
                oppdatertBehandling.get().grunnlag.filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
                    .filter { it.erBearbeidet }.size shouldBe 1
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    false,
                )

            assertSoftly {
                grunnlag shouldNotBe null
                grunnlag?.data shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                grunnlag?.innhentet?.toLocalDate() shouldBe LocalDate.now().minusDays(1)
                grunnlag?.aktiv?.toLocalDate() shouldBe LocalDate.now().minusDays(1)
            }

            assertThat(grunnlag?.data?.isNotEmpty())

            val lagredeSmåbarnstillegg =
                jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(grunnlag?.data!!)

            assertSoftly {
                lagredeSmåbarnstillegg shouldNotBe emptySet<SmåbarnstilleggGrunnlagDto>()
                lagredeSmåbarnstillegg.size shouldBe 1
                lagredeSmåbarnstillegg.filter { sbt -> sbt.personId == behandling.bidragsmottaker!!.ident!! }.size shouldBe 1
                lagredeSmåbarnstillegg.filter { sbt -> sbt.beløp == småbarnstillegg.beløp }.size shouldBe 1
            }
        }

        @Test
        @Transactional
        open fun `skal lagre tomt grunnlag uten å sette til aktiv dersom sist lagrede grunnlag ikke var tomt`() {
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
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    false,
                )

            assertSoftly {
                jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(grunnlag!!.data) shouldBe emptySet()
                grunnlag.aktiv shouldBe null
            }

            val gjeldendeAktiveGrunnlag =
                grunnlagRepository.findAll().filter { g -> g.aktiv != null }

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

            val lagretGrunnlag =
                behandlingRepository.findBehandlingById(behandling.id!!).get().grunnlag

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
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    false,
                )

            grunnlag shouldBe null
        }

        @Test
        @Transactional
        open fun `skal sette til aktiv dersom ikke tidligere lagret`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

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

            val grunnlagBidragsmottaker =
                oppdatertBehandling.get().grunnlag.filter { grunnlag -> grunnlag.rolle.ident!! == testdataBM.ident }

            validereGrunnlagBm(grunnlagBidragsmottaker)

            val grunnlagBarn1 =
                oppdatertBehandling.get().grunnlag.filter { grunnlag -> grunnlag.rolle.ident!! == testdataBarn1.ident }

            val grunnlagBarn2 =
                oppdatertBehandling.get().grunnlag.filter { grunnlag -> grunnlag.rolle.ident!! == testdataBarn2.ident }

            setOf(grunnlagBarn1, grunnlagBarn2).forEach {
                assertSoftly {
                    it.size shouldBe 4
                    it.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type }.size shouldBe 1
                    it.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 2
                    it.filter { Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == it.type }.size shouldBe 1
                }
            }
        }

        @Test
        @Transactional
        open fun `skal lagre husstandsmedlemmer`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            behandling.husstandsbarn.clear()
            behandling.grunnlag.clear()
            entityManager.persist(behandling)
            entityManager.flush()

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly(oppdatertBehandling) {
                it.isPresent shouldBe true
                it.get().grunnlag.size shouldBe totaltAntallGrunnlag
                it.get().grunnlag.filter { g -> Grunnlagsdatatype.BOFORHOLD == g.type }.size shouldBe 2
                it.get().grunnlag.filter { g -> Grunnlagsdatatype.BOFORHOLD == g.type }
                    .filter { g -> g.erBearbeidet }.size shouldBe 1
                it.get().husstandsbarn.size shouldBe 2
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.BOFORHOLD,
                    false,
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
        @Transactional
        open fun `skal lagre sivilstand`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SIVILSTAND,
                    false,
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
        @Transactional
        open fun `skal lagre småbarnstillegg`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.SIVILSTAND,
                    false,
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
        @Transactional
        open fun `skal lagre barnetillegg og kontantstøtte som gjelder barn med rolle i behandling`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            val barnFraHenteGrunnlagresponsJson = "01057812300"

            behandling.roller.removeAll(behandling.roller.filter { Rolletype.BARN == it.rolletype }.toSet())

            behandling.roller.add(
                Rolle(
                    behandling = behandling,
                    rolletype = Rolletype.BARN,
                    ident = barnFraHenteGrunnlagresponsJson,
                    foedselsdato = LocalDate.now().minusYears(14),
                ),
            )

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            entityManager.refresh(behandling)

            behandling.grunnlag.size shouldBe totaltAntallGrunnlag

            val grunnlagBarnetillegg =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.BARNETILLEGG == it.type }
            grunnlagBarnetillegg.isNotEmpty() shouldBe true

            val grunnlagKontantstøtte =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.KONTANTSTØTTE == it.type }
            grunnlagKontantstøtte.isNotEmpty() shouldBe true

            val grunnlagUtvidetBarnetrygd =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.UTVIDET_BARNETRYGD == it.type }
            grunnlagUtvidetBarnetrygd.isNotEmpty() shouldBe true

            val grunnlagSmåbarnstillegg =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
            grunnlagSmåbarnstillegg.isNotEmpty() shouldBe true
        }

        @Test
        @Transactional
        open fun `skal ikke lagre barnetillegg eller kontantstøtte som gjelder barn som ikke er del av behandling`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            entityManager.refresh(behandling)

            behandling.grunnlag.size shouldBe totaltAntallGrunnlag

            val grunnlagBarnetillegg =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.BARNETILLEGG == it.type }
            grunnlagBarnetillegg.isEmpty() shouldBe true

            val grunnlagKontantstøtte =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.KONTANTSTØTTE == it.type }
            grunnlagKontantstøtte.isEmpty() shouldBe true

            val grunnlagUtvidetBarnetrygd =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.UTVIDET_BARNETRYGD == it.type }
            grunnlagUtvidetBarnetrygd.isNotEmpty() shouldBe true

            val grunnlagSmåbarnstillegg =
                behandling.grunnlag.filter { it.behandling.id == behandling.id }
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
            grunnlagSmåbarnstillegg.isNotEmpty() shouldBe true
        }

        @Test
        @Transactional
        open fun `skal lagre arbeidsforhold`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid = behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagsdatatype.ARBEIDSFORHOLD,
                    false,
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
        @Transactional
        open fun `skal aktivere grunnlag av type inntekt og oppdatere inntektstabell`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
            stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()

            val skattegrunlagFraDato =
                behandling.søktFomDato.minusYears(1).withMonth(1).withDayOfMonth(1)

            testdataManager.oppretteOgLagreGrunnlag(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
                grunnlagsdata =
                    SkattepliktigeInntekter(
                        ainntekter =
                            listOf(
                                AinntektGrunnlagDto(
                                    personId = behandling.bidragsmottaker!!.ident!!,
                                    periodeFra = YearMonth.now().minusMonths(2).atDay(1),
                                    periodeTil = YearMonth.now().minusMonths(1).atDay(1),
                                    ainntektspostListe =
                                        listOf(
                                            tilAinntektspostDto(
                                                beskrivelse = "fastloenn",
                                                beløp = BigDecimal(368000),
                                                inntektstype = "FASTLOENN",
                                                utbetalingsperiode =
                                                    YearMonth.now().minusMonths(2)
                                                        .format(DateTimeFormatter.ofPattern("yyyy-MM")),
                                            ),
                                        ),
                                ),
                            ),
                        skattegrunnlag =
                            listOf(
                                SkattegrunnlagGrunnlagDto(
                                    personId = behandling.bidragsmottaker!!.ident!!,
                                    periodeFra = skattegrunlagFraDato,
                                    periodeTil = skattegrunlagFraDato.plusYears(1),
                                    skattegrunnlagspostListe =
                                        listOf(
                                            SkattegrunnlagspostDto(
                                                skattegrunnlagType = "ORDINÆR",
                                                beløp = BigDecimal(368000),
                                                belop = BigDecimal(368000),
                                                inntektType = "andelIFellesTapVedSalgAvAndelISDF",
                                                kode = "andelIFellesTapVedSalgAvAndelISDF",
                                            ),
                                        ),
                                ),
                                SkattegrunnlagGrunnlagDto(
                                    personId = behandling.bidragsmottaker!!.ident!!,
                                    periodeFra = skattegrunlagFraDato,
                                    periodeTil = skattegrunlagFraDato.plusYears(1),
                                    skattegrunnlagspostListe =
                                        listOf(
                                            SkattegrunnlagspostDto(
                                                skattegrunnlagType = "ORDINÆR",
                                                beløp = BigDecimal(1368000),
                                                belop = BigDecimal(1368000),
                                                inntektType = "samletLoennsinntekt",
                                                kode = "samletLoennsinntekt",
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )

            val aktivereGrunnlagRequest =
                AktivereGrunnlagRequest(
                    Personident(behandling.bidragsmottaker?.ident!!),
                    setOf(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER),
                )

            // hvis
            grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.grunnlag.isNotEmpty()
                behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 3
                behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
                behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
                behandling.inntekter.size shouldBe 4
                behandling.inntekter
                    .filter { Kilde.OFFENTLIG == it.kilde }
                    .filter { it.ident == behandling.bidragsmottaker!!.ident }.size shouldBe 4
                behandling.inntekter.filter { Inntektsrapportering.KAPITALINNTEKT == it.type }
                    .filter { BigDecimal.ZERO == it.belop }.size shouldBe 1
                behandling.inntekter.filter { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                    .filter { BigDecimal(1368000) == it.belop }.size shouldBe 1
                behandling.inntekter.first { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }
                    .belop shouldBe BigDecimal(1472000)
                behandling.inntekter.first { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }
                    .belop shouldBe BigDecimal(368000)
            }
        }

        @Test
        @Transactional
        open fun `skal aktivere grunnlag av type barnetillegg og oppdatere inntektstabell`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            testdataManager.oppretteOgLagreGrunnlag(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
                grunnlagsdata =
                    setOf(
                        BarnetilleggGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            barnPersonId = behandling.søknadsbarn.first().ident!!,
                            periodeFra = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløpBrutto = BigDecimal(40000),
                            barnetilleggType = "Cash",
                            barnType = "universell",
                        ),
                    ),
            )

            val aktivereGrunnlagRequest =
                AktivereGrunnlagRequest(
                    Personident(behandling.bidragsmottaker?.ident!!),
                    setOf(Grunnlagsdatatype.BARNETILLEGG),
                )

            // hvis
            grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.grunnlag.isNotEmpty()
                behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                behandling.inntekter.size shouldBe 1
                behandling.inntekter
                    .filter { Kilde.OFFENTLIG == it.kilde }
                    .filter { it.ident == behandling.bidragsmottaker!!.ident }.size shouldBe 1
            }
        }

        @Test
        @Transactional
        open fun `skal aktivere grunnlag av type kontantstøtte og oppdatere inntektstabell`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            testdataManager.oppretteOgLagreGrunnlag(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
                grunnlagsdata =
                    setOf(
                        KontantstøtteGrunnlagDto(
                            behandling.bidragsmottaker!!.ident!!,
                            behandling.søknadsbarn.first().ident!!,
                            YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                            YearMonth.now().withMonth(1).atDay(1),
                            50000,
                        ),
                    ),
            )

            val aktivereGrunnlagRequest =
                AktivereGrunnlagRequest(
                    Personident(behandling.bidragsmottaker?.ident!!),
                    setOf(Grunnlagsdatatype.KONTANTSTØTTE),
                )

            // hvis
            grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.grunnlag.isNotEmpty()
                behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                behandling.inntekter.size shouldBe 1
                behandling.inntekter
                    .filter { Kilde.OFFENTLIG == it.kilde }
                    .filter { it.ident == behandling.bidragsmottaker!!.ident }.size shouldBe 1
                behandling.inntekter.filter { Inntektsrapportering.KONTANTSTØTTE == it.type }
                    .filter { BigDecimal(600000) == it.belop }.size shouldBe 1
            }
        }

        @Test
        @Transactional
        open fun `skal aktivere grunnlag av type småbarnstillegg og oppdatere inntektstabell`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            testdataManager.oppretteOgLagreGrunnlag(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
                grunnlagsdata =
                    setOf(
                        SmåbarnstilleggGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            periodeFra = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløp = BigDecimal(35000),
                            manueltBeregnet = false,
                        ),
                    ),
            )

            val aktivereGrunnlagRequest =
                AktivereGrunnlagRequest(
                    Personident(behandling.bidragsmottaker?.ident!!),
                    setOf(Grunnlagsdatatype.SMÅBARNSTILLEGG),
                )

            // hvis
            grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.grunnlag.isNotEmpty()
                behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                behandling.inntekter.size shouldBe 1
                behandling.inntekter
                    .filter { Kilde.OFFENTLIG == it.kilde }
                    .filter { it.ident == behandling.bidragsmottaker!!.ident }.size shouldBe 1
            }
        }

        @Test
        @Transactional
        open fun `skal aktivere grunnlag av type utvidet barnetrygd og oppdatere inntektstabell`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            testdataManager.oppretteOgLagreGrunnlag(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
                grunnlagsdata =
                    setOf(
                        UtvidetBarnetrygdGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            periodeFra = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløp = BigDecimal(37500),
                            manueltBeregnet = false,
                        ),
                    ),
            )

            val aktivereGrunnlagRequest =
                AktivereGrunnlagRequest(
                    Personident(behandling.bidragsmottaker?.ident!!),
                    setOf(Grunnlagsdatatype.UTVIDET_BARNETRYGD),
                )

            // hvis
            grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

            // så
            entityManager.refresh(behandling)

            assertSoftly {
                behandling.grunnlag.isNotEmpty()
                behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                behandling.inntekter.size shouldBe 1
                behandling.inntekter
                    .filter { Kilde.OFFENTLIG == it.kilde }
                    .filter { it.ident == behandling.bidragsmottaker!!.ident }.size shouldBe 1
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
            val erBearbeidet = true

            val opp4 =
                grunnlagRepository.save(
                    Grunnlag(
                        behandling = b,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        erBearbeidet = erBearbeidet,
                        "{\"test\": \"opp\"}",
                        innhentet = tidspunktInnhentet,
                        aktiv = tidspunktInnhentet,
                        rolle = b.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

            // hvis
            val opplysninger =
                grunnlagService.hentSistInnhentet(
                    b.id!!,
                    b.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                    Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, erBearbeidet),
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
        @Transactional
        open fun `skal hente nyeste grunnlagsopplysning per type`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(true)

            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // hvis
            val nyesteGrunnlagPerType =
                grunnlagService.hentAlleSistInnhentet(
                    behandling.id!!,
                    behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.id!!,
                )

            // så
            validereGrunnlagBm(nyesteGrunnlagPerType)
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

        fun tilAinntektspostDto(
            inntektstype: String,
            beløp: BigDecimal = BigDecimal.ZERO,
            utbetalingsperiode: String,
            beskrivelse: String? = null,
            etterbetalingsperiodeFra: LocalDate? = null,
            etterbetalingsperiodeTil: LocalDate? = null,
            fordelstype: String? = null,
            opptjeningsperiodeFra: LocalDate? = null,
            opptjeningsperiodeTil: LocalDate? = null,
            virksomhetsid: String? = null,
            opplysningspliktid: String? = null,
        ) = AinntektspostDto(
            utbetalingsperiode = utbetalingsperiode,
            belop = beløp,
            beløp = beløp,
            beskrivelse = beskrivelse,
            etterbetalingsperiodeFra = etterbetalingsperiodeFra,
            etterbetalingsperiodeTil = etterbetalingsperiodeTil,
            fordelType = fordelstype,
            inntektType = inntektstype,
            opplysningspliktigId = opplysningspliktid,
            opptjeningsperiodeFra = opptjeningsperiodeFra,
            opptjeningsperiodeTil = opptjeningsperiodeTil,
            virksomhetId = virksomhetsid,
        )
    }

    private fun validereGrunnlagBm(grunnlag: List<Grunnlag>) {
        assertSoftly {
            grunnlag.size shouldBe 12
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.BOFORHOLD }.size shouldBe 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SIVILSTAND }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.BARNETILLEGG }.size shouldBe 0
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.BARNETILLEGG }
                .filter { it.erBearbeidet }.size shouldBe 0
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.KONTANTSTØTTE }.size shouldBe 0
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.KONTANTSTØTTE }
                .filter { it.erBearbeidet }.size shouldBe 0
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SMÅBARNSTILLEGG }.size shouldBe 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SMÅBARNSTILLEGG }
                .filter { it.erBearbeidet }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.UTVIDET_BARNETRYGD }.size shouldBe 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.UTVIDET_BARNETRYGD }
                .filter { it.erBearbeidet }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }
                .filter { it.erBearbeidet }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER }
                .filter { it.erBearbeidet }.size shouldBe 1
        }
    }
}
