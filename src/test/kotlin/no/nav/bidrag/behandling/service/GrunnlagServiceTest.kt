package no.nav.bidrag.behandling.service

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.transformers.LocalDateTimeTypeAdapter
import no.nav.bidrag.behandling.transformers.LocalDateTypeAdapter
import no.nav.bidrag.behandling.transformers.YearMonthTypeAdapter
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.lang.reflect.Type
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

    val totaltAntallGrunnlag = 10

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
    open inner class OpdatereGrunnlagForBehandling {
        @Test
        fun `skal lagre skattegrunnlag`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk()

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.INNTEKT,
                )

            assertThat(grunnlag?.data?.isNotEmpty())

            val grunnlagInntekt = jsonTilGrunnlagInntekt(grunnlag?.data!!)

            assertSoftly {
                grunnlagInntekt.skattegrunnlag shouldNotBe emptySet<SkattegrunnlagGrunnlagDto>()
                grunnlagInntekt.skattegrunnlag.size shouldBe 1
                grunnlagInntekt.skattegrunnlag[0].personId shouldBe "99057812345"
            }
        }

        @Test
        fun `skal sette til aktiv dersom ikke tidligere lagret`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk(tomRespons = true)

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
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk()

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
                )
            val husstandsmedlemmer = jsonTilRelatertPersonGrunnlagDto(grunnlag?.data!!)

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
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk()

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.SIVILSTAND,
                )
            val sivilstand = jsonTilSivilstandGrunnlagDto(grunnlag?.data!!)

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
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk()

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlagBarnetillegg =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.BARNETILLEGG,
                )
            grunnlagBarnetillegg shouldNotBe null
            val barnetillegg = jsonTilBarnetilleggGrunnlagDto(grunnlagBarnetillegg?.data!!)
            assertSoftly {
                barnetillegg.size shouldBe 1
            }

            val grunnlagBarnetilsyn =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.BARNETILSYN,
                )
            grunnlagBarnetilsyn shouldNotBe null
            val barnetilsyn = jsonTilBarnetilsynGrunnlagDto(grunnlagBarnetilsyn?.data!!)
            assertSoftly {
                barnetilsyn.size shouldBe 1
            }

            val grunnlagKontantstøtte =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.KONTANTSTØTTE,
                )
            grunnlagKontantstøtte shouldNotBe null
            val kontantstøtte = jsonTilKontantstøtteGrunnlagDto(grunnlagKontantstøtte?.data!!)
            assertSoftly {
                kontantstøtte.size shouldBe 1
            }

            val grunnlagUtvidetBarnetrygd =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                )
            grunnlagUtvidetBarnetrygd shouldNotBe null

            val utvidetBarnetrygd =
                jsonTilUtvidetBarnetrygdGrunnlagDto(grunnlagUtvidetBarnetrygd?.data!!)
            assertSoftly {
                utvidetBarnetrygd.size shouldBe 1
            }

            val grunnlagSmåbarnstillegg =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                )
            grunnlagSmåbarnstillegg shouldNotBe null
            val småbarnstillegg =
                jsonTilSmåbarnstilleggGrunnlagDto(grunnlagSmåbarnstillegg?.data!!)
            assertSoftly {
                småbarnstillegg.size shouldBe 1
            }
        }

        @Test
        fun `skal lagre arbeidsforhold`() {
            // gitt
            val behandling = testdataManager.opprettBehandling(false)
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk()

            // hvis
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            assertSoftly {
                oppdatertBehandling.isPresent shouldBe true
                oppdatertBehandling.get().grunnlag.size shouldBe totaltAntallGrunnlag
            }

            val grunnlag =
                grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                    behandlingId = behandling.id!!,
                    Grunnlagsdatatype.ARBEIDSFORHOLD,
                )
            val arbeidsforhold = jsonTilArbeidsforholdGrunnlagDto(grunnlag?.data!!)

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
                    ),
                )

            // hvis
            val opplysninger = grunnlagService.hentSistInnhentet(b.id!!, Grunnlagsdatatype.BOFORHOLD)

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
            val bm = Personident(behandling.getBidragsmottaker()!!.ident!!)
            val barn =
                behandling.getSøknadsbarn().filter { r -> r.ident != null }.map { Personident(it.ident!!) }
                    .sortedBy { it.verdi }.toSet()

            stubUtils.stubHenteGrunnlagOk()

            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            // hvis
            val nyesteGrunnlagPerType = grunnlagService.hentAlleSistInnhentet(behandling.id!!)

            // så
            assertSoftly {
                nyesteGrunnlagPerType.size shouldBe totaltAntallGrunnlag
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.size shouldBe 1
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.HUSSTANDSMEDLEMMER }.size shouldBe 1
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.INNTEKT }.size shouldBe 1
                nyesteGrunnlagPerType.filter { g -> g.type == Grunnlagsdatatype.SIVILSTAND }.size shouldBe 1
            }
        }
    }

    companion object {
        fun jsonTilGrunnlagInntekt(json: String): GrunnlagInntekt =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson(
                    json,
                    GrunnlagInntekt::class.java,
                )

        fun jsonTilRelatertPersonGrunnlagDto(json: String): Set<RelatertPersonGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<RelatertPersonGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<RelatertPersonGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilSivilstandGrunnlagDto(json: String): Set<SivilstandGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<SivilstandGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<SivilstandGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilArbeidsforholdGrunnlagDto(json: String): Set<ArbeidsforholdGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<ArbeidsforholdGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<ArbeidsforholdGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilBarnetilleggGrunnlagDto(json: String): Set<BarnetilleggGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<BarnetilleggGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<BarnetilleggGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilBarnetilsynGrunnlagDto(json: String): Set<BarnetilsynGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<BarnetilsynGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<BarnetilsynGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilKontantstøtteGrunnlagDto(json: String): Set<KontantstøtteGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<KontantstøtteGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<KontantstøtteGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilUtvidetBarnetrygdGrunnlagDto(json: String): Set<UtvidetBarnetrygdGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<UtvidetBarnetrygdGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<UtvidetBarnetrygdGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilSmåbarnstilleggGrunnlagDto(json: String): Set<SmåbarnstilleggGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<SmåbarnstilleggGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<SmåbarnstilleggGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }
    }
}
