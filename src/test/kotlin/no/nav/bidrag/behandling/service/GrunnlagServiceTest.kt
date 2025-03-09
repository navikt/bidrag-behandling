package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.HentetGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.SivilstandRepository
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.service.GrunnlagServiceTest.Companion.tilAinntektspostDto
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdVoksneRequest
import no.nav.bidrag.behandling.transformers.tilTypeBoforhold
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.lagGrunnlagsdata
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGrunnlag
import no.nav.bidrag.behandling.utils.testdata.oppretteArbeidsforhold
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.testdata.tilTransformerInntekterRequest
import no.nav.bidrag.behandling.utils.testdata.voksenPersonIBpsHusstand
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.grunnlag.HentGrunnlagFeiltype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    lateinit var sivilstandRepository: SivilstandRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @MockBean
    lateinit var bidragPersonConsumer: BidragPersonConsumer

    @Autowired
    lateinit var entityManager: EntityManager

    val totaltAntallGrunnlag = 26

    @BeforeEach
    fun setup() {
        try {
            grunnlagRepository.deleteAll()
            behandlingRepository.deleteAll()
            sivilstandRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore
        }

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
        @Nested
        open inner class Bidrag {
            // TODO: Fikse eksisterende og legge til flere tester, f.eks fra FORSKUDD
            @Test
            @Transactional
            open fun `skal lagre ytelser`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        // TODO: Lage egen mock-respons for bidrag for BM og BP
                        // Bruker for enkelhetsskyld  forskudd mock-respons for BM og BP - dette gjør at returnert grunnlag ikke blir riktig i forhold til grunnlagsrequesten
                        Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly {
                    behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                    behandling.grunnlag.size shouldBe 32
                    behandling.inntekter.size shouldBe 28
                }

                val alleGrunnlagBm =
                    behandling.grunnlag.filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }

                validereGrunnlagBm(alleGrunnlagBm, true)

                val alleGrunnlagBarn =
                    behandling.grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }

                assertSoftly {
                    alleGrunnlagBarn.filter { Grunnlagsdatatype.BARNETILLEGG == it.type }.size shouldBe 0
                    alleGrunnlagBarn.filter { Grunnlagsdatatype.KONTANTSTØTTE == it.type }.size shouldBe 0
                    alleGrunnlagBarn.filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }.size shouldBe 0
                    alleGrunnlagBarn.filter { Grunnlagsdatatype.UTVIDET_BARNETRYGD == it.type }.size shouldBe 0
                }

                val alleInntekterBm =
                    behandling.inntekter.filter { behandling.bidragsmottaker!!.ident == it.ident }

                assertSoftly {
                    alleInntekterBm.size shouldBe 8
                    alleInntekterBm.filter { Inntektsrapportering.BARNETILLEGG == it.type }.size shouldBe 0
                    behandling
                        .inntekter
                        .filter { Inntektsrapportering.BARNETILLEGG == it.type }
                        .size shouldBe 0
                    alleInntekterBm.filter { Inntektsrapportering.KONTANTSTØTTE == it.type }.size shouldBe 0
                    behandling
                        .inntekter
                        .filter { Inntektsrapportering.KONTANTSTØTTE == it.type }
                        .size shouldBe 0
                    alleInntekterBm.filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }.size shouldBe 1
                    behandling
                        .inntekter
                        .filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }
                        .size shouldBe 2
                    alleInntekterBm.filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }.size shouldBe 1
                    behandling
                        .inntekter
                        .filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }
                        .size shouldBe 2
                }
            }

            @Test
            @Transactional
            open fun `skal hente og lagre andre barn til bidragsmottaker`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                responsobjekt = lagGrunnlagsdata("hente-grunnlagrespons-bidrag-andre-barn-bm"),
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                behandling.underholdskostnader shouldHaveSize 2

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                behandling.underholdskostnader.shouldHaveSize(4)
                val søknadsbarnUnderholdskostnader = behandling.underholdskostnader.filter { it.barnetsRolleIBehandlingen != null }
                val andreBarnUnderholdskostnader = behandling.underholdskostnader.filter { it.barnetsRolleIBehandlingen == null }
                andreBarnUnderholdskostnader.shouldHaveSize(2)
                søknadsbarnUnderholdskostnader.shouldHaveSize(2)
                søknadsbarnUnderholdskostnader.first().kilde shouldBe null
                andreBarnUnderholdskostnader.first().kilde shouldBe Kilde.OFFENTLIG
                andreBarnUnderholdskostnader.first().person.ident shouldBe testdataBarnBm.ident
            }

            @Test
            @Transactional
            open fun `skal hente og oppdatere andre barn til bidragsmottaker grunnlag hvis endret`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                behandling.underholdskostnader.add(
                    Underholdskostnad(
                        behandling = behandling,
                        kilde = Kilde.OFFENTLIG,
                        person = Person(ident = testdataHusstandsmedlem1.ident, fødselsdato = testdataHusstandsmedlem1.fødselsdato),
                    ),
                )
                behandling.grunnlag.add(
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.ANDRE_BARN,
                        listOf(
                            RelatertPersonGrunnlagDto(
                                navn = testdataBarn1.navn,
                                fødselsdato = testdataBarn1.fødselsdato,
                                partPersonId = testdataBarnBm.ident,
                                relasjon = Familierelasjon.BARN,
                                borISammeHusstandDtoListe = emptyList(),
                                relatertPersonPersonId = testdataBarn1.ident,
                            ),
                            RelatertPersonGrunnlagDto(
                                relatertPersonPersonId = testdataHusstandsmedlem1.ident,
                                navn = testdataHusstandsmedlem1.navn,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                partPersonId = testdataBarnBm.ident,
                                relasjon = Familierelasjon.BARN,
                                borISammeHusstandDtoListe = emptyList(),
                            ),
                        ),
                        behandling.bidragsmottaker!!.ident!!,
                        false,
                    ),
                )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                responsobjekt = lagGrunnlagsdata("hente-grunnlagrespons-bidrag-andre-barn-bm"),
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                behandling.underholdskostnader shouldHaveSize 3

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                assertSoftly {
                    behandling.underholdskostnader.shouldHaveSize(5)
                    val søknadsbarnUnderholdskostnader = behandling.underholdskostnader.filter { it.barnetsRolleIBehandlingen != null }
                    val andreBarnUnderholdskostnader = behandling.underholdskostnader.filter { it.barnetsRolleIBehandlingen == null }
                    andreBarnUnderholdskostnader.shouldHaveSize(3)
                    søknadsbarnUnderholdskostnader.shouldHaveSize(2)
                    søknadsbarnUnderholdskostnader.first().kilde shouldBe null
                    val andreBarnHusstandsmedlem = andreBarnUnderholdskostnader.find { it.person.ident == testdataHusstandsmedlem1.ident }!!
                    val andreBarnBarnBM = andreBarnUnderholdskostnader.find { it.person.ident == testdataBarnBm.ident }!!
                    andreBarnHusstandsmedlem.kilde shouldBe Kilde.MANUELL
                    andreBarnBarnBM.kilde shouldBe Kilde.OFFENTLIG

                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.ANDRE_BARN }.shouldHaveSize(2)
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.ANDRE_BARN && it.aktiv != null }.shouldHaveSize(2)
                }
            }

            @Test
            @Transactional
            open fun `skal hente og lagre barnetilsyn`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-bidrag-barnetilsyn-bm.json",
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                behandling.underholdskostnader shouldHaveSize 2
                behandling.underholdskostnader.flatMap { it.barnetilsyn } shouldHaveSize 0

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.BARNETILSYN == it.type }) {
                    filter { it.aktiv == null } shouldHaveSize 0
                    filter { it.aktiv != null } shouldHaveSize 2
                    filter { it.aktiv != null && it.erBearbeidet } shouldHaveSize 1
                    filter { it.aktiv != null && !it.erBearbeidet } shouldHaveSize 1
                }

                behandling.grunnlag.filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype } shouldHaveSize 5

                val barnetilsyn =
                    behandling.grunnlag.find { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype && Grunnlagsdatatype.BARNETILSYN == it.type }
                assertSoftly(barnetilsyn) {
                    it.shouldNotBeNull()
                    it.aktiv.shouldNotBeNull()
                    it.innhentet.shouldNotBeNull()
                    it.data.shouldNotBeNull()
                }

                assertSoftly(jsonListeTilObjekt<BarnetilsynGrunnlagDto>(barnetilsyn?.data!!).sortedBy { it.periodeFra }) {
                    shouldHaveSize(2)
                    it.elementAt(0).beløp shouldBe 4000
                    it.elementAt(0).periodeFra shouldBe LocalDate.of(2023, 1, 1)
                    it.elementAt(0).periodeTil shouldBe LocalDate.of(2024, 1, 1)
                    it.elementAt(0).skolealder shouldBe Skolealder.OVER
                    it.elementAt(0).tilsynstype shouldBe Tilsynstype.HELTID
                    it.elementAt(0).partPersonId shouldBe "313213213"
                    it.elementAt(0).barnPersonId shouldBe "1344124"
                }

                behandling.underholdskostnader.flatMap { it.barnetilsyn } shouldHaveSize 2

                val uTestbarn1 =
                    behandling.underholdskostnader
                        .find {
                            it.person.rolle
                                .first()
                                .personident
                                ?.verdi == testdataBarn1.ident
                        }

                assertSoftly(uTestbarn1) {
                    shouldNotBeNull()
                    harTilsynsordning shouldBe true
                }

                val stønadTilBarnetilsyn = uTestbarn1!!.barnetilsyn.sortedBy { it.fom }
                stønadTilBarnetilsyn shouldHaveSize 2

                assertSoftly(stønadTilBarnetilsyn.elementAt(0)) {
                    it.omfang shouldBe Tilsynstype.HELTID
                    it.under_skolealder shouldBe true
                    it.fom shouldBe LocalDate.of(2023, 1, 1)
                    it.tom shouldBe LocalDate.of(2023, 12, 31)
                    it.kilde shouldBe Kilde.OFFENTLIG
                }

                assertSoftly(stønadTilBarnetilsyn.elementAt(1)) {
                    it.omfang shouldBe Tilsynstype.HELTID
                    it.under_skolealder shouldBe true
                    it.fom shouldBe LocalDate.of(2024, 1, 1)
                    it.tom shouldBe null
                    it.kilde shouldBe Kilde.OFFENTLIG
                }
            }

            @Test
            @Transactional
            open fun `skal lagre arbeidsforhold`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val grunnlag =
                    behandling.grunnlag.find { it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER && Grunnlagsdatatype.ARBEIDSFORHOLD == it.type && !it.erBearbeidet }

                val arbeidsforhold = jsonListeTilObjekt<ArbeidsforholdGrunnlagDto>(grunnlag?.data!!)

                assertSoftly {
                    arbeidsforhold.size shouldBe 3
                    arbeidsforhold
                        .filter { a ->
                            a.partPersonId == behandling.bidragsmottaker!!.ident!! &&
                                a.sluttdato == null &&
                                a.startdato ==
                                LocalDate.of(
                                    2002,
                                    11,
                                    3,
                                ) &&
                                a.arbeidsgiverNavn == "SAUEFABRIKK" &&
                                a.arbeidsgiverOrgnummer == "896929119"
                        }.toSet()
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal lagre skattegrunnlag`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        // TODO: Lage egen mock-respons for bidrag for BM og BP
                        // Bruker for enkelhetsskyld  forskudd mock-respons for BM og BP - dette gjør at returnert grunnlag ikke blir riktig i forhold til grunnlagsrequesten
                        Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly {
                    behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                    behandling.grunnlag.size shouldBe 37
                }

                val grunnlagBp =
                    behandling.grunnlag.filter { Rolletype.BIDRAGSPLIKTIG == it.rolle.rolletype }
                assertSoftly {
                    grunnlagBp.size shouldBe 14
                    grunnlagBp.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type }.size shouldBe 1
                    grunnlagBp.filter { Grunnlagsdatatype.BOFORHOLD == it.type }.size shouldBe 6
                    grunnlagBp.filter { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type }.size shouldBe 2
                    grunnlagBp.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 2
                    grunnlagBp.filter { Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == it.type }.size shouldBe 1
                    grunnlagBp.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 2

                    val grunnlagBm =
                        behandling.grunnlag.filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    assertSoftly {
                        grunnlagBm.size shouldBe 15
                        grunnlagBm.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type }.size shouldBe 1
                        grunnlagBm.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 2
                        grunnlagBm.filter { Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == it.type }.size shouldBe 1
                        grunnlagBm.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 2
                    }

                    val grunnlagBarn =
                        behandling.grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }
                    assertSoftly {
                        grunnlagBarn.size shouldBe 8
                        grunnlagBarn.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type }.size shouldBe 2
                        grunnlagBarn.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 4
                        grunnlagBarn.filter { Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == it.type }.size shouldBe 2
                        grunnlagBarn.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type }.size shouldBe 4
                    }
                }
            }

            @Test
            @Transactional
            open fun `skal hente boforhold andre voksne i husstanden for BP som ikke har andre voksne i husstanden`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(false, false, false, true, TypeBehandling.BIDRAG)
                testdataManager.lagreBehandlingNewTransaction(behandling)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bm.json",
                            )

                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp-uten-andre-voksne.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                val grunnlagBarn = behandling.grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }

                grunnlagBarn.groupBy { it.rolle }.forEach {
                    assertSoftly(it.value) { gbarn ->
                        gbarn shouldHaveSize 4
                        gbarn.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                        gbarn.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                        gbarn.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    }
                }

                grunnlagBarn shouldHaveSize 8

                val grunnlagBm = behandling.grunnlag.filter { it.rolle == behandling.bidragsmottaker }

                assertSoftly(grunnlagBm) { gbm ->
                    gbm shouldHaveSize 13
                    gbm.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                    gbm.filter { it.type == Grunnlagsdatatype.BARNETILLEGG } shouldHaveSize 2
                    gbm.filter { it.type == Grunnlagsdatatype.BOFORHOLD } shouldHaveSize 0
                    gbm.filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN } shouldHaveSize 3
                    gbm.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    gbm.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet } shouldHaveSize 1
                    gbm.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                    gbm.filter { it.erBearbeidet } shouldHaveSize 7
                }

                val grunnlagBp = behandling.grunnlag.filter { it.rolle == behandling.bidragspliktig }

                assertSoftly(grunnlagBp) { gbp ->
                    gbp shouldHaveSize 9
                    gbp.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD } shouldHaveSize 3
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN } shouldHaveSize 0
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && it.erBearbeidet } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                    gbp.filter { it.erBearbeidet } shouldHaveSize 5
                }
            }

            @Test
            @Transactional
            open fun `skal hente og lagre informasjon om tillegsstønad`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-bidrag-tilleggsstønad-bm.json",
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                behandling.grunnlag
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.TILLEGGSSTØNAD == it.type && !it.erBearbeidet } shouldHaveSize 0

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                behandling.grunnlag
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                    .filter { Grunnlagsdatatype.TILLEGGSSTØNAD == it.type && !it.erBearbeidet } shouldHaveSize 1
            }
        }

        @Nested
        open inner class Forskudd {
            @Test
            @Transactional
            open fun `skal lagre ytelser`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
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
                    oppdatertBehandling.get().grunnlag.size shouldBe 21
                    oppdatertBehandling.get().inntekter.size shouldBe 20
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
                    oppdatertBehandling
                        .get()
                        .inntekter
                        .filter { Inntektsrapportering.BARNETILLEGG == it.type }
                        .size shouldBe 0
                    alleInntekterBm.filter { Inntektsrapportering.KONTANTSTØTTE == it.type }.size shouldBe 0
                    oppdatertBehandling
                        .get()
                        .inntekter
                        .filter { Inntektsrapportering.KONTANTSTØTTE == it.type }
                        .size shouldBe 0
                    alleInntekterBm.filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }.size shouldBe 1
                    oppdatertBehandling
                        .get()
                        .inntekter
                        .filter { Inntektsrapportering.SMÅBARNSTILLEGG == it.type }
                        .size shouldBe 1
                    alleInntekterBm.filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }.size shouldBe 1
                    oppdatertBehandling
                        .get()
                        .inntekter
                        .filter { Inntektsrapportering.UTVIDET_BARNETRYGD == it.type }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal lagre skattegrunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
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
                    skattegrunnlag.skattegrunnlag.filter { it.personId == behandling.bidragsmottaker!!.ident!! }.size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal slette gammel feilmelding ved ny feilfri innhenting`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.grunnlagsinnhentingFeilet =
                    "{\"BARNETILLEGG\":{\"grunnlagstype\":\"BARNETILLEGG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"}}"
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
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

                assertSoftly(oppdatertBehandling) {
                    it.isPresent shouldBe true
                    it.get().grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                    it.get().grunnlag.size shouldBe totaltAntallGrunnlag
                    it.get().grunnlagsinnhentingFeilet shouldBe null
                }
            }

            @Test
            fun `skal ikke oppdatere grunnlag dersom venteperioden etter forrige innhenting ikke er over`() {
                // gitt
                val grunnlagSistInnhentet = LocalDateTime.now().minusMinutes(30)
                val behandling = testdataManager.oppretteBehandling(false)
                behandling.grunnlagSistInnhentet = grunnlagSistInnhentet
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

                assertSoftly {
                    oppdatertBehandling.isPresent shouldBe true
                    oppdatertBehandling.get().grunnlagSistInnhentet = grunnlagSistInnhentet
                    oppdatertBehandling.get().grunnlag.size shouldBe 5
                }
            }

            @Test
            @Transactional
            open fun `skal ikke lagre ny innhenting av inntekt hvis ingen endringer`() {
                // gitt
                val innhentingstidspunkt: LocalDateTime = LocalDate.of(2024, 1, 1).atStartOfDay()
                val behandling = testdataManager.oppretteBehandling(false)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val skattegrunnlag =
                    SkattegrunnlagGrunnlagDto(
                        periodeFra =
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
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
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                responsobjekt =
                                    tilHentGrunnlagDto(
                                        skattegrunnlag =
                                            listOf(
                                                skattegrunnlag,
                                            ),
                                    ),
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

                assertSoftly {
                    oppdatertBehandling.isPresent shouldBe true
                    oppdatertBehandling.get().grunnlag.size shouldBe 11
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
                    oppdaterteSkattegrunnlag.skattegrunnlag
                        .filter { it.personId == behandling.bidragsmottaker!!.ident }
                        .map { it.skattegrunnlagspostListe }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal automatisk aktivere grunnlag hvis ingen endringer som må bekreftes`() {
                // gitt
                val innhentingstidspunkt: LocalDateTime = LocalDate.of(2024, 1, 1).atStartOfDay()
                val behandling = testdataManager.oppretteBehandling(false)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val skattegrunnlag =
                    SkattegrunnlagGrunnlagDto(
                        periodeFra =
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
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
                val skattegrunnlagNy =
                    SkattegrunnlagGrunnlagDto(
                        periodeFra =
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
                        periodeTil = YearMonth.now().withMonth(1).atDay(1),
                        personId = behandling.bidragsmottaker!!.ident!!,
                        skattegrunnlagspostListe =
                            listOf(
                                SkattegrunnlagspostDto(
                                    beløp = BigDecimal(450000),
                                    belop = BigDecimal(450000),
                                    inntektType = "renteinntektAvObligasjon",
                                    skattegrunnlagType = "Something else",
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
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling,
                    Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                    innhentingstidspunkt,
                    innhentingstidspunkt,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                skattegrunnlagListe = listOf(skattegrunnlag),
                            ).tilSummerteInntekter(behandling.bidragsmottaker!!),
                )
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                responsobjekt =
                                    tilHentGrunnlagDto(
                                        skattegrunnlag =
                                            listOf(
                                                skattegrunnlagNy,
                                            ),
                                    ),
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

                assertSoftly {
                    oppdatertBehandling.isPresent shouldBe true
                    val grunnlagListe = oppdatertBehandling.get().grunnlag
                    grunnlagListe.size shouldBe 12
                    grunnlagListe.filter { it.aktiv == null } shouldHaveSize 3
                    grunnlagListe
                        .filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet } shouldHaveSize 2
                }
            }

            @Test
            @Transactional
            open fun `skal lagre ikke aktiv grunnlag hvis beregnet inntekt ikke er lik lagret grunnlag`() {
                // gitt
                val innhentingstidspunkt: LocalDateTime = LocalDate.of(2024, 1, 1).atStartOfDay()
                val behandling = testdataManager.oppretteBehandling(false)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val skattegrunnlag =
                    SkattegrunnlagGrunnlagDto(
                        periodeFra =
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
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
                val skattegrunnlagNy =
                    SkattegrunnlagGrunnlagDto(
                        periodeFra =
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
                        periodeTil = YearMonth.now().withMonth(1).atDay(1),
                        personId = behandling.bidragsmottaker!!.ident!!,
                        skattegrunnlagspostListe =
                            listOf(
                                SkattegrunnlagspostDto(
                                    beløp = BigDecimal(450000),
                                    belop = BigDecimal(450000),
                                    inntektType = "renteinntektAvObligasjon",
                                    skattegrunnlagType = "Something else",
                                ),
                                SkattegrunnlagspostDto(
                                    beløp = BigDecimal(100000),
                                    belop = BigDecimal(100000),
                                    inntektType = "gevinstVedRealisasjonAvAksje",
                                    skattegrunnlagType = "Something else",
                                ),
                            ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling,
                    Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                    innhentingstidspunkt,
                    innhentingstidspunkt,
                    grunnlagsdata = SkattepliktigeInntekter(skattegrunnlag = listOf(skattegrunnlagNy)),
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling,
                    Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                    innhentingstidspunkt,
                    innhentingstidspunkt,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                skattegrunnlagListe = listOf(skattegrunnlag),
                            ).tilSummerteInntekter(behandling.bidragsmottaker!!),
                )
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                responsobjekt =
                                    tilHentGrunnlagDto(
                                        skattegrunnlag =
                                            listOf(
                                                skattegrunnlagNy,
                                            ),
                                    ),
                            )

                        else -> stubUtils.stubHenteGrunnlag(rolleIdent = it.ident, tomRespons = true)
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly(behandling.grunnlag) { g ->
                    g.size shouldBe 12
                    g.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 3
                    g.filter { it.aktiv == null && it.erBearbeidet } shouldHaveSize 3
                    g.filter { !it.erBearbeidet } shouldHaveSize 5
                }
            }

            @Test
            @Transactional
            open fun `skal ikke lagre ny innhenting av småbarnstillegg hvis ingen endringer`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)
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
                            stubUtils.stubHenteGrunnlag(
                                it.ident,
                                responsobjekt =
                                    tilHentGrunnlagDto(
                                        småbarnstillegg =
                                            listOf(
                                                småbarnstillegg,
                                            ),
                                    ),
                            )

                        Rolletype.BARN -> stubUtils.stubHenteGrunnlag(it.ident, tomRespons = true)

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

                assertSoftly {
                    oppdatertBehandling.isPresent shouldBe true
                    oppdatertBehandling.get().grunnlag.size shouldBe 11
                    oppdatertBehandling
                        .get()
                        .grunnlag
                        .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
                        .size shouldBe 2
                    oppdatertBehandling
                        .get()
                        .grunnlag
                        .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
                        .filter { it.erBearbeidet }
                        .size shouldBe 1
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
                val behandling = oppretteTestbehandling(false, false, false)
                testdataManager.lagreBehandlingNewTransaction(behandling)
                stubUtils.stubHenteGrunnlag(tomRespons = true)

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
                behandling.grunnlag.size shouldBe 4

                val alleAktiveGrunnlag = behandling.grunnlag.filter { it.aktiv != null }
                alleAktiveGrunnlag.size shouldBe 3
                val sistInnhentaSmåbarnstillegg =
                    behandling.grunnlag
                        .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
                        .maxBy { it.innhentet }
                sistInnhentaSmåbarnstillegg.aktiv shouldBe null

                val gjeldendeSmåbarnstillegg =
                    behandling.grunnlag
                        .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type && it.aktiv != null }
                        .maxBy { it.innhentet }
                gjeldendeSmåbarnstillegg.shouldNotBeNull()

                assertSoftly(jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(gjeldendeSmåbarnstillegg.data)) {
                    it shouldHaveSize 1
                    it.filter { sbt -> sbt.personId == behandling.bidragsmottaker!!.ident!! } shouldHaveSize 1
                    it.filter { sbt -> sbt.beløp == småbarnstillegg.beløp } shouldHaveSize 1
                }
            }

            @Test
            @Transactional
            open fun `skal overskrive forrige ikke aktive grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }
                stubbeHentingAvPersoninfoForTestpersoner()

                val aktivertSmåbarnstillegg =
                    SmåbarnstilleggGrunnlagDto(
                        beløp = BigDecimal(3400),
                        manueltBeregnet = false,
                        periodeFra = YearMonth.now().minusMonths(7).atDay(1),
                        periodeTil = YearMonth.now().atDay(1),
                        personId = behandling.bidragsmottaker!!.ident!!,
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling,
                        Grunnlagsdatatype.SMÅBARNSTILLEGG,
                        data = tilJson(setOf(aktivertSmåbarnstillegg)),
                        innhentet = LocalDateTime.now().minusDays(1),
                        aktiv = LocalDateTime.now().minusDays(1),
                        rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

                val ikkeAktivertSmåbarnstillegg1 =
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
                        data = tilJson(setOf(ikkeAktivertSmåbarnstillegg1)),
                        innhentet = LocalDateTime.now().minusDays(1),
                        aktiv = null,
                        rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

                val ikkeAktivertSmåbarnstillegg2 =
                    SmåbarnstilleggGrunnlagDto(
                        beløp = BigDecimal(2700),
                        manueltBeregnet = false,
                        periodeFra = YearMonth.now().minusMonths(7).atDay(1),
                        periodeTil = YearMonth.now().atDay(1),
                        personId = behandling.bidragsmottaker!!.ident!!,
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling,
                        Grunnlagsdatatype.SMÅBARNSTILLEGG,
                        data = tilJson(setOf(ikkeAktivertSmåbarnstillegg2)),
                        innhentet = LocalDateTime.now().minusDays(2),
                        aktiv = null,
                        rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

                behandlingRepository.save(behandling)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlag.size shouldBe 27

                val småbarnstillegg = behandling.grunnlag.filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }

                assertSoftly(småbarnstillegg) { sb ->
                    sb.size shouldBe 3
                    sb.filter { it.aktiv != null } shouldHaveSize 3
                    sb.filter { it.erBearbeidet } shouldHaveSize 1
                }
            }

            @Test
            @Transactional
            open fun `skal ikke lagre tomt grunnlag dersom sist lagrede grunnlag var tomt`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)
                stubUtils.stubHenteGrunnlag(tomRespons = true)

                val lagretGrunnlag =
                    behandlingRepository.findBehandlingById(behandling.id!!).get().grunnlag

                lagretGrunnlag.size shouldBe 0

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

                grunnlag shouldBe null
            }

            @Test
            @Transactional
            open fun `skal aktivere førstegangsinnhenting av sivilstand`() {
                // gitt
                val behandling = oppretteTestbehandling(false, false, false)
                testdataManager.lagreBehandlingNewTransaction(behandling)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type }) {
                    it.shouldNotBeEmpty()
                }

                assertSoftly(behandling.grunnlag) { g ->
                    g.size shouldBe 21
                    g.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.size shouldBe 2
                    g.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.filter { it.erBearbeidet } shouldHaveSize 1
                    g
                        .filter { Grunnlagsdatatype.SIVILSTAND == it.type }
                        .filter { it.aktiv != null } shouldHaveSize 2
                }

                assertSoftly(behandling.sivilstand) { s ->
                    s.size shouldBe 1
                    s.filter { behandling.virkningstidspunktEllerSøktFomDato == it.datoFom }
                }
            }

            @Test
            @Transactional
            open fun `skal ikke aktivere bearbeida sivilstandsgrunnlag som krever godkjenning`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, true, false)
                behandling.virkningstidspunkt = LocalDate.now().minusYears(2)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }) {
                    it shouldHaveSize 2
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }) {
                    it shouldHaveSize 4
                    it.filter { it.aktiv == null } shouldHaveSize 2
                    it.find { it.aktiv == null && it.erBearbeidet } shouldNotBe null
                }
            }

            @Test
            @Transactional
            open fun `skal ikke aktivere bearbeida boforholdsgrunnlag som krever godkjenning`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, true)
                behandling.virkningstidspunkt = LocalDate.now().minusYears(2)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type }) {
                    it shouldHaveSize 3
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type }) {
                    it shouldHaveSize 6
                    it.filter { it.aktiv == null } shouldHaveSize 3
                    it.filter { it.aktiv == null && it.erBearbeidet } shouldHaveSize 2
                }
            }

            @Test
            @Transactional
            open fun `skal automatisk aktivere førstegangsinnhenting av grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

                assertSoftly {
                    oppdatertBehandling.isPresent shouldBe true
                    oppdatertBehandling.get().grunnlag.size shouldBe 21
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
            open fun `skal legge inn søknadsbarn som husstandsmedlemmer dersom grunnlagsdata mangler`() {
                // gitt
                val behandling = oppretteTestbehandling(false)
                val personidentBarnSomIkkeErInkludertIGrunnlag = "01010112345"
                behandling.roller.add(
                    Rolle(
                        behandling,
                        Rolletype.BARN,
                        personidentBarnSomIkkeErInkludertIGrunnlag,
                        LocalDate.now().minusYears(6),
                        navn = "Pelle Parafin",
                    ),
                )
                behandling.husstandsmedlem.clear()
                behandling.grunnlag.clear()
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly(behandling) {
                    grunnlag.size shouldBe 22
                    grunnlag
                        .filter { g -> Grunnlagsdatatype.BOFORHOLD == g.type }
                        .size shouldBe 4
                    grunnlag
                        .filter { g -> Grunnlagsdatatype.BOFORHOLD == g.type }
                        .filter { g -> g.erBearbeidet }
                        .size shouldBe 3
                    husstandsmedlem.size shouldBe 3
                }
            }

            @Test
            @Transactional
            open fun `skal lagre husstandsmedlemmer`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)

                behandling.husstandsmedlem.clear()
                behandling.grunnlag.clear()
                entityManager.persist(behandling)
                entityManager.flush()

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

                assertSoftly(oppdatertBehandling) {
                    it.isPresent shouldBe true
                    it.get().grunnlag.size shouldBe 21
                    it
                        .get()
                        .grunnlag
                        .filter { g -> Grunnlagsdatatype.BOFORHOLD == g.type }
                        .size shouldBe 3
                    it
                        .get()
                        .grunnlag
                        .filter { g -> Grunnlagsdatatype.BOFORHOLD == g.type }
                        .filter { g -> g.erBearbeidet }
                        .size shouldBe 2
                    it.get().husstandsmedlem.size shouldBe 2
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
                    husstandsmedlemmer
                        .filter { h ->
                            h.navn == "Småstein Nilsen" &&
                                h.fødselsdato ==
                                LocalDate.of(
                                    2020,
                                    1,
                                    24,
                                ) &&
                                h.erBarn
                        }.toSet()
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal lagre sivilstand`() {
                // gitt
                val behandling = oppretteTestbehandling(false, false, false)
                testdataManager.lagreBehandlingNewTransaction(behandling)
                assertSoftly(behandling.sivilstand) { s ->
                    s.size shouldBe 0
                }
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                assertSoftly(behandling.grunnlag) { g ->
                    g.size shouldBe 21
                    g.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.size shouldBe 2
                    g.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.filter { it.erBearbeidet }.size shouldBe 1
                }

                assertSoftly(behandling.sivilstand) { s ->
                    s.size shouldBe 1
                    s.filter { behandling.virkningstidspunktEllerSøktFomDato == it.datoFom }
                }
            }

            @Test
            @Transactional
            open fun `skal lagre småbarnstillegg`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)
                stubbeHentingAvPersoninfoForTestpersoner()
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
                    sivilstand
                        .filter { s ->
                            s.personId == testdataBM.ident &&
                                s.bekreftelsesdato ==
                                LocalDate.of(
                                    2021,
                                    1,
                                    1,
                                ) &&
                                s.gyldigFom ==
                                LocalDate.of(
                                    2021,
                                    1,
                                    1,
                                ) &&
                                s.master == "FREG" &&
                                s.historisk == true &&
                                s.registrert ==
                                LocalDateTime.parse(
                                    "2022-01-01T10:03:57.285",
                                    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                                ) &&
                                s.type == SivilstandskodePDL.GIFT
                        }.toSet()
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal lagre barnetillegg og kontantstøtte som gjelder barn med rolle i behandling`() {
                // gitt
                val behandling = oppretteTestbehandling(false)

                val barnFraHenteGrunnlagresponsJson = "01057812300"

                behandling.roller.removeAll(behandling.roller.filter { Rolletype.BARN == it.rolletype }.toSet())

                behandling.roller.add(
                    Rolle(
                        behandling = behandling,
                        rolletype = Rolletype.BARN,
                        ident = barnFraHenteGrunnlagresponsJson,
                        fødselsdato = LocalDate.now().minusYears(14),
                    ),
                )

                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlag.size shouldBe totaltAntallGrunnlag + 2

                val grunnlagBarnetillegg =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.BARNETILLEGG == it.type }
                grunnlagBarnetillegg.isNotEmpty() shouldBe true

                val grunnlagKontantstøtte =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.KONTANTSTØTTE == it.type }
                grunnlagKontantstøtte.isNotEmpty() shouldBe true

                val grunnlagUtvidetBarnetrygd =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.UTVIDET_BARNETRYGD == it.type }
                grunnlagUtvidetBarnetrygd.isNotEmpty() shouldBe true

                val grunnlagSmåbarnstillegg =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
                grunnlagSmåbarnstillegg.isNotEmpty() shouldBe true
            }

            @Test
            @Transactional
            open fun `skal ikke lagre barnetillegg eller kontantstøtte som gjelder barn som ikke er del av behandling`() {
                // gitt
                val behandling = oppretteTestbehandling(false)
                testdataManager.lagreBehandlingNewTransaction(behandling)
                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlag.size shouldBe totaltAntallGrunnlag

                val grunnlagBarnetillegg =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.BARNETILLEGG == it.type }
                grunnlagBarnetillegg.isEmpty() shouldBe true

                val grunnlagKontantstøtte =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.KONTANTSTØTTE == it.type }
                grunnlagKontantstøtte.isEmpty() shouldBe true

                val grunnlagUtvidetBarnetrygd =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.UTVIDET_BARNETRYGD == it.type }
                grunnlagUtvidetBarnetrygd.isNotEmpty() shouldBe true

                val grunnlagSmåbarnstillegg =
                    behandling.grunnlag
                        .filter { it.behandling.id == behandling.id }
                        .filter { Rolletype.BIDRAGSMOTTAKER == it.rolle.rolletype }
                        .filter { Grunnlagsdatatype.SMÅBARNSTILLEGG == it.type }
                grunnlagSmåbarnstillegg.isNotEmpty() shouldBe true
            }

            @Test
            @Transactional
            open fun `skal lagre arbeidsforhold`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)

                stubbeHentingAvPersoninfoForTestpersoner()
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
                    arbeidsforhold
                        .filter { a ->
                            a.partPersonId == behandling.bidragsmottaker!!.ident!! &&
                                a.sluttdato == null &&
                                a.startdato ==
                                LocalDate.of(
                                    2002,
                                    11,
                                    3,
                                ) &&
                                a.arbeidsgiverNavn == "SAUEFABRIKK" &&
                                a.arbeidsgiverOrgnummer == "896929119"
                        }.toSet()
                        .size shouldBe 1
                }
            }
        }

        @Nested
        open inner class Særbidrag {
            @Test
            fun `skal lagre innhentede inntekter selv om innhenting feiler for enkelte år i en periode`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(true, false, false, true, TypeBehandling.SÆRBIDRAG)
                stubbeHentingAvPersoninfoForTestpersoner()
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bm.json",
                            )

                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp-feil-for-skattegrunnlag.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()
                assertSoftly(
                    behandling.grunnlag
                        .filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
                        .filter {
                            it.rolle ==
                                behandling.bidragspliktig!!
                        }.toSet(),
                ) { g ->
                    g shouldHaveSize 1
                    assertSoftly(g.first().konvertereData<SkattepliktigeInntekter>()?.skattegrunnlag) {
                        it?.shouldHaveSize(2)
                        it?.filter { LocalDate.of(2022, 1, 1) == it.periodeFra }?.shouldHaveSize(1)
                        it?.filter { LocalDate.of(2023, 1, 1) == it.periodeTil }?.shouldHaveSize(1)
                        it?.filter { LocalDate.of(2024, 1, 1) == it.periodeFra }?.shouldHaveSize(1)
                        it?.filter { LocalDate.of(2025, 1, 1) == it.periodeTil }?.shouldHaveSize(1)
                    }
                }
            }

            @Test
            @Transactional
            open fun `skal hente grunnlag for behandling av særbidrag`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, false, true, TypeBehandling.SÆRBIDRAG)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bm.json",
                            )

                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                val grunnlagBarn = behandling.grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }

                grunnlagBarn.groupBy { it.rolle }.forEach {
                    assertSoftly(it.value) { gbarn ->
                        gbarn shouldHaveSize 4
                        gbarn.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                        gbarn.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                        gbarn.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    }
                }

                grunnlagBarn shouldHaveSize 8

                val grunnlagBm = behandling.grunnlag.filter { it.rolle == behandling.bidragsmottaker }

                assertSoftly(grunnlagBm) { gbm ->
                    gbm shouldHaveSize 13
                    gbm.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                    gbm.filter { it.type == Grunnlagsdatatype.BARNETILLEGG } shouldHaveSize 2
                    gbm.filter { it.type == Grunnlagsdatatype.BOFORHOLD } shouldHaveSize 0
                    gbm.filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN } shouldHaveSize 3
                    gbm.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    gbm.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet } shouldHaveSize 1
                    gbm.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                    gbm.filter { it.erBearbeidet } shouldHaveSize 7
                }

                val grunnlagBp = behandling.grunnlag.filter { it.rolle == behandling.bidragspliktig }

                assertSoftly(grunnlagBp) { gbp ->
                    gbp shouldHaveSize 9
                    gbp.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD } shouldHaveSize 3
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN } shouldHaveSize 0
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && it.erBearbeidet } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                    gbp.filter { it.erBearbeidet } shouldHaveSize 5
                }
            }

            @Test
            @Transactional
            open fun `skal hente boforhold andre voksne i husstanden for BP som ikke har andre voksne i husstanden`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, false, true, TypeBehandling.SÆRBIDRAG)

                stubbeHentingAvPersoninfoForTestpersoner()
                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bm.json",
                            )

                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp-uten-andre-voksne.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                behandling.grunnlagSistInnhentet?.toLocalDate() shouldBe LocalDate.now()

                val grunnlagBarn = behandling.grunnlag.filter { Rolletype.BARN == it.rolle.rolletype }

                grunnlagBarn.groupBy { it.rolle }.forEach {
                    assertSoftly(it.value) { gbarn ->
                        gbarn shouldHaveSize 4
                        gbarn.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                        gbarn.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                        gbarn.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    }
                }

                grunnlagBarn shouldHaveSize 8

                val grunnlagBm = behandling.grunnlag.filter { it.rolle == behandling.bidragsmottaker }

                assertSoftly(grunnlagBm) { gbm ->
                    gbm shouldHaveSize 13
                    gbm.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                    gbm.filter { it.type == Grunnlagsdatatype.BARNETILLEGG } shouldHaveSize 2
                    gbm.filter { it.type == Grunnlagsdatatype.BOFORHOLD } shouldHaveSize 0
                    gbm.filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN } shouldHaveSize 3
                    gbm.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    gbm.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet } shouldHaveSize 1
                    gbm.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                    gbm.filter { it.erBearbeidet } shouldHaveSize 7
                }

                val grunnlagBp = behandling.grunnlag.filter { it.rolle == behandling.bidragspliktig }

                assertSoftly(grunnlagBp) { gbp ->
                    gbp shouldHaveSize 9
                    gbp.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD } shouldHaveSize 3
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN } shouldHaveSize 0
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && it.erBearbeidet } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER } shouldHaveSize 2
                    gbp.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet } shouldHaveSize 1
                    gbp.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER } shouldHaveSize 1
                    gbp.filter { it.erBearbeidet } shouldHaveSize 5
                }
            }
        }
    }

    @Nested
    @DisplayName("Teste aktivering av grunnlag")
    open inner class AktivereGrunnlag {
        @Nested
        @DisplayName("Teste aktivering av grunnlag for bidrag")
        open inner class Bidrag {
            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type inntekt for bp i behandling av bidrag`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)
                stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()

                val skattepliktigeInntekter = oppretteSkattepliktigeInntekter(behandling)

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = skattepliktigeInntekter,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = skattepliktigeInntekter.tilBearbeidaInntekter(behandling.bidragspliktig!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 5
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
                    behandling.inntekter.size shouldBe 4
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragspliktig!!.ident }
                        .size shouldBe 4
                    behandling.inntekter
                        .filter { Inntektsrapportering.KAPITALINNTEKT == it.type }
                        .filter { BigDecimal.ZERO == it.belop }
                        .size shouldBe 1
                    behandling.inntekter
                        .filter { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                        .filter { BigDecimal(1368000) == it.belop }
                        .size shouldBe 1
                    behandling.inntekter
                        .first { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }
                        .belop shouldBe BigDecimal(1472000)
                    behandling.inntekter
                        .first { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }
                        .belop shouldBe BigDecimal(368000)
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type arbeidsforhold for bp i behandling av bidrag`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(false, inkludereBp = true, behandlingstype = TypeBehandling.BIDRAG)

                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)
                stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()

                val arbeidsforhold = oppretteArbeidsforhold(behandling.bidragspliktig!!.ident!!)

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.ARBEIDSFORHOLD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = arbeidsforhold,
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.ARBEIDSFORHOLD,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assertSoftly(behandling.grunnlag) { g ->
                    g.isNotEmpty()
                    g.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 4
                    g.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.size shouldBe 1
                    g.find { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }?.aktiv?.toLocalDate() shouldBe LocalDate.now()
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type boforhold for barn av BP i behandling av bidrag, og oppdatere husstandsmedlemtabell`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )
                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                Mockito
                    .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
                    .thenReturn(testdataBarn1.tilPersonDto())

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 0
                }

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        setOf(
                            RelatertPersonGrunnlagDto(
                                relatertPersonPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                erBarnAvBmBp = true,
                                navn = null,
                                partPersonId = behandling.bidragspliktig!!.ident!!,
                                borISammeHusstandDtoListe =
                                    listOf(
                                        BorISammeHusstandDto(
                                            testdataHusstandsmedlem1.fødselsdato,
                                            periodeTil = null,
                                        ),
                                    ),
                            ),
                        ),
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            BoforholdResponseV2(
                                bostatus = Bostatuskode.MED_FORELDER,
                                gjelderPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                            ),
                        ),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        personident = Personident(testdataBM.ident),
                        gjelderIdent = Personident(testdataHusstandsmedlem1.ident),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                assertSoftly(behandling.grunnlag) { g ->
                    g.isNotEmpty()
                    g.size shouldBe 2
                    g.filter { Grunnlagsdatatype.BOFORHOLD == it.type }.size shouldBe 2
                    g.filter { it.erBearbeidet }.size shouldBe 1
                    g.find { it.aktiv == null } shouldBe null
                    g.filter { LocalDate.now() == it.aktiv!!.toLocalDate() }.size shouldBe 2
                }

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 1
                    it.first().perioder.size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere tomt grunnlag med boforhold_andre_voksne_i_husstanden`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        false,
                        false,
                        false,
                        true,
                        behandlingstype = TypeBehandling.SÆRBIDRAG,
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling = behandling,
                        data = commonObjectmapper.writeValueAsString(emptySet<RelatertPersonGrunnlagDto>()),
                        innhentet = LocalDateTime.now().minusDays(1),
                        rolle = behandling.bidragspliktig!!,
                        type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                    ),
                )

                testdataManager.lagreBehandlingNewTransaction(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()
                Mockito
                    .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
                    .thenReturn(testdataBarn1.tilPersonDto())

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 0
                }

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        personident = Personident(behandling.bidragspliktig!!.ident!!),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assert(true)
            }
        }

        @Nested
        @DisplayName("Teste aktivering av grunnlag for særbidrag")
        open inner class Særbidrag {
            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type inntekt for bp i behandling av særbidrag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, inkludereBp = true)
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)
                stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()

                val skattepliktigeInntekter = oppretteSkattepliktigeInntekter(behandling)

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = skattepliktigeInntekter,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = skattepliktigeInntekter.tilBearbeidaInntekter(behandling.bidragspliktig!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 5
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
                    behandling.inntekter.size shouldBe 4
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragspliktig!!.ident }
                        .size shouldBe 4
                    behandling.inntekter
                        .filter { Inntektsrapportering.KAPITALINNTEKT == it.type }
                        .filter { BigDecimal.ZERO == it.belop }
                        .size shouldBe 1
                    behandling.inntekter
                        .filter { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                        .filter { BigDecimal(1368000) == it.belop }
                        .size shouldBe 1
                    behandling.inntekter
                        .first { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }
                        .belop shouldBe BigDecimal(1472000)
                    behandling.inntekter
                        .first { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }
                        .belop shouldBe BigDecimal(368000)
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type arbeidsforhold for bp i behandling av særbidrag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, inkludereBp = true)
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)
                stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()

                val arbeidsforhold = oppretteArbeidsforhold(behandling.bidragspliktig!!.ident!!)

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.ARBEIDSFORHOLD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = arbeidsforhold,
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.ARBEIDSFORHOLD,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assertSoftly(behandling.grunnlag) { g ->
                    g.isNotEmpty()
                    g.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 4
                    g.filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.size shouldBe 1
                    g.find { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }?.aktiv?.toLocalDate() shouldBe LocalDate.now()
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type barnetillegg for BP i behandling av særbidrag, og oppdatere inntektstabell`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, false, true, TypeBehandling.SÆRBIDRAG)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)

                val barnetilleggGrunnlag =
                    setOf(
                        BarnetilleggGrunnlagDto(
                            partPersonId = behandling.bidragspliktig!!.ident!!,
                            barnPersonId = behandling.søknadsbarn.first().ident!!,
                            periodeFra =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløpBrutto = BigDecimal(40000),
                            barnetilleggType = "Cash",
                            barnType = "universell",
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = barnetilleggGrunnlag,
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                barnetilleggListe = barnetilleggGrunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragspliktig!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.BARNETILLEGG,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                    behandling.inntekter.size shouldBe 1
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragspliktig!!.ident }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type boforhold for barn av BP i behandling av særbidrag, og oppdatere husstandsmedlemtabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false, true)
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
                behandling.kategori = Særbidragskategori.KONFIRMASJON.name
                behandling.stonadstype = null

                stubbeHentingAvPersoninfoForTestpersoner()
                Mockito
                    .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
                    .thenReturn(testdataBarn1.tilPersonDto())

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 0
                }

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        setOf(
                            RelatertPersonGrunnlagDto(
                                relatertPersonPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                erBarnAvBmBp = true,
                                navn = null,
                                partPersonId = behandling.bidragspliktig!!.ident!!,
                                borISammeHusstandDtoListe =
                                    listOf(
                                        BorISammeHusstandDto(
                                            testdataHusstandsmedlem1.fødselsdato,
                                            periodeTil = null,
                                        ),
                                    ),
                            ),
                        ),
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    rolle = behandling.bidragspliktig!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            BoforholdResponseV2(
                                bostatus = Bostatuskode.MED_FORELDER,
                                gjelderPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                            ),
                        ),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        personident = Personident(testdataBM.ident),
                        gjelderIdent = Personident(testdataHusstandsmedlem1.ident),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                assertSoftly(behandling.grunnlag) { g ->
                    g.isNotEmpty()
                    g.size shouldBe 2
                    g.filter { Grunnlagsdatatype.BOFORHOLD == it.type }.size shouldBe 2
                    g.filter { it.erBearbeidet }.size shouldBe 1
                    g.find { it.aktiv == null } shouldBe null
                    g.filter { LocalDate.now() == it.aktiv!!.toLocalDate() }.size shouldBe 2
                }

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 1
                    it.first().perioder.size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal ikke aktivere boforholdrådata i behandling av særbidrag dersom bearbeida boforhold er ikke er aktivert for samtlige husstandsmedlemmer`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, true, true, TypeBehandling.SÆRBIDRAG)

                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }
                behandling.virkningstidspunkt = LocalDate.now().minusYears(1)

                val rådataBoforhold =
                    behandling.grunnlag
                        .filter { !it.erBearbeidet }
                        .filter { Grunnlagsdatatype.BOFORHOLD == it.type }
                        .first()

                val nyeBorhosperioder =
                    listOf(
                        BorISammeHusstandDto(
                            periodeFra = behandling.virkningstidspunktEllerSøktFomDato,
                            periodeTil = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(7),
                        ),
                        BorISammeHusstandDto(
                            periodeFra = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(10),
                            periodeTil = null,
                        ),
                    )

                val dataBoforhold = jsonListeTilObjekt<RelatertPersonGrunnlagDto>(rådataBoforhold.data)

                val endretBoforholdTestbarn1 =
                    dataBoforhold
                        .find { testdataBarn1.ident == it.gjelderPersonId }
                        ?.copy(borISammeHusstandDtoListe = nyeBorhosperioder)

                val endretBoforhold =
                    listOf(
                        endretBoforholdTestbarn1!!,
                        dataBoforhold.find { testdataBarn2.ident == it.gjelderPersonId }!!,
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling,
                        Grunnlagsdatatype.BOFORHOLD,
                        erBearbeidet = false,
                        data = tilJson(endretBoforhold.toSet()),
                        innhentet = LocalDateTime.now().minusDays(1),
                        aktiv = null,
                        rolle = behandling.bidragspliktig!!,
                    ),
                )

                val bearbeidaBoforhold =
                    BoforholdApi.beregnBoforholdBarnV3(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        behandling.globalOpphørsdato,
                        behandling.tilTypeBoforhold(),
                        endretBoforhold.toMutableList().tilBoforholdBarnRequest(behandling),
                    )

                bearbeidaBoforhold.groupBy { it.gjelderPersonId }.forEach {
                    behandling.grunnlag.add(
                        Grunnlag(
                            behandling,
                            Grunnlagsdatatype.BOFORHOLD,
                            erBearbeidet = true,
                            data = tilJson(it.value),
                            innhentet = LocalDateTime.now().minusDays(1),
                            aktiv = null,
                            rolle = behandling.bidragspliktig!!,
                            gjelder = it.key,
                        ),
                    )
                }

                stubbeHentingAvPersoninfoForTestpersoner()
                behandlingRepository.save(behandling)

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 3
                    g.filter { behandling.bidragspliktig == it.rolle } shouldHaveSize 3
                    g.filter { it.aktiv != null } shouldHaveSize 1
                    g.filter { it.erBearbeidet } shouldHaveSize 1
                    g.filter { !it.erBearbeidet && it.gjelder == null } shouldHaveSize 2
                    g.filter { testdataBarn1.ident == it.gjelder && it.erBearbeidet } shouldHaveSize 1
                }

                // hvis
                grunnlagService.aktivereGrunnlag(
                    behandling,
                    AktivereGrunnlagRequestV2(
                        personident = Personident(testdataBM.ident),
                        gjelderIdent = Personident(testdataBarn1.ident),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD,
                    ),
                )

                // så
                val boforhold = behandling.grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD }
                assertSoftly(boforhold) { sb ->
                    sb.size shouldBe 3
                    sb.filter { it.aktiv != null } shouldHaveSize 2
                    sb.filter { it.erBearbeidet } shouldHaveSize 1
                    sb.filter { !it.erBearbeidet && it.aktiv == null } shouldHaveSize 1
                    sb.filter { it.erBearbeidet && it.aktiv == null } shouldHaveSize 0
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere boforholdrådata dersom bearbeida boforhold er aktivert for samtlige husstandsmedlemmer`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, true, true, TypeBehandling.SÆRBIDRAG)

                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bm.json",
                            )

                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                behandling.virkningstidspunkt = LocalDate.now().minusYears(1)
                val rådataBoforhold =
                    behandling.grunnlag
                        .filter { !it.erBearbeidet }
                        .filter { Grunnlagsdatatype.BOFORHOLD == it.type }
                        .first()

                val nyeBorhosperioder =
                    listOf(
                        BorISammeHusstandDto(
                            periodeFra = behandling.virkningstidspunktEllerSøktFomDato,
                            periodeTil = YearMonth.from(behandling.virkningstidspunktEllerSøktFomDato.plusMonths(7)).atEndOfMonth(),
                        ),
                        BorISammeHusstandDto(
                            periodeFra = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(10),
                            periodeTil = null,
                        ),
                    )

                val dataBoforhold = jsonListeTilObjekt<RelatertPersonGrunnlagDto>(rådataBoforhold.data)

                val endretBoforholdTestbarn1 =
                    dataBoforhold
                        .find { testdataBarn1.ident == it.gjelderPersonId }
                        ?.copy(borISammeHusstandDtoListe = nyeBorhosperioder)

                val endretBoforhold =
                    listOf(
                        endretBoforholdTestbarn1!!,
                        dataBoforhold.find { testdataBarn1.ident == it.gjelderPersonId }!!,
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling,
                        Grunnlagsdatatype.BOFORHOLD,
                        erBearbeidet = false,
                        data = tilJson(endretBoforhold.toSet()),
                        innhentet = LocalDateTime.now().minusDays(1),
                        aktiv = null,
                        rolle = behandling.bidragspliktig!!,
                    ),
                )

                val bearbeidaBoforhold =
                    BoforholdApi.beregnBoforholdBarnV3(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        behandling.globalOpphørsdato,
                        behandling.tilTypeBoforhold(),
                        endretBoforhold.toMutableList().tilBoforholdBarnRequest(behandling, true),
                    )

                bearbeidaBoforhold.groupBy { it.gjelderPersonId }.forEach {
                    val aktivert = if (it.key == testdataBarn1.ident) null else LocalDateTime.now().minusDays(1)
                    behandling.grunnlag.add(
                        Grunnlag(
                            behandling,
                            Grunnlagsdatatype.BOFORHOLD,
                            erBearbeidet = true,
                            data = tilJson(it.value),
                            innhentet = LocalDateTime.now().minusDays(1),
                            aktiv = aktivert,
                            rolle = behandling.bidragspliktig!!,
                            gjelder = it.key,
                        ),
                    )
                }

                stubbeHentingAvPersoninfoForTestpersoner()
                behandlingRepository.save(behandling)

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 4
                    g.filter { behandling.bidragspliktig == it.rolle } shouldHaveSize 4
                    g.filter { it.aktiv != null } shouldHaveSize 2
                    g.filter { it.erBearbeidet } shouldHaveSize 2
                    g.filter { !it.erBearbeidet && it.gjelder == null && it.aktiv === null } shouldHaveSize 1
                    g.filter { testdataBarn1.ident == it.gjelder && it.erBearbeidet && it.aktiv != null } shouldHaveSize 0
                }

                // hvis
                grunnlagService.aktivereGrunnlag(
                    behandling,
                    AktivereGrunnlagRequestV2(
                        personident = Personident(testdataBM.ident),
                        gjelderIdent = Personident(testdataBarn1.ident),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD,
                        overskriveManuelleOpplysninger = false,
                    ),
                )

                // så
                val boforhold = behandling.grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD }
                assertSoftly(boforhold) { b ->
                    b.size shouldBe 4
                    b.filter { it.aktiv != null } shouldHaveSize 4
                    b.filter { it.erBearbeidet } shouldHaveSize 2
                    b.filter { testdataBarn1.ident == it.gjelder && it.erBearbeidet && it.aktiv == null } shouldHaveSize 0
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, true, true, TypeBehandling.SÆRBIDRAG, true)

                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bm.json",
                            )

                        Rolletype.BIDRAGSPLIKTIG ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-sb-bp.json",
                            )

                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                val bfg =
                    behandling.grunnlag.find { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type && !it.erBearbeidet }

                val relatertPerson =
                    bfg
                        .konvertereData<List<RelatertPersonGrunnlagDto>>()
                        ?.find { voksenPersonIBpsHusstand.personident == it.gjelderPersonId }

                val borHosperioder = relatertPerson?.borISammeHusstandDtoListe?.toMutableList()
                borHosperioder?.add(
                    BorISammeHusstandDto(
                        LocalDate.now().plusMonths(4),
                        LocalDate.now().minusMonths(2),
                    ),
                )
                val oppdatertVoksenIBpsHusstand =
                    setOf(relatertPerson!!.copy(borISammeHusstandDtoListe = borHosperioder?.toList()!!))

                val voksneIBpsHusstand =
                    BoforholdApi.beregnBoforholdAndreVoksne(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        oppdatertVoksenIBpsHusstand.tilBoforholdVoksneRequest(),
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        aktiv = null,
                        behandling = behandling,
                        innhentet = LocalDateTime.now().minusDays(3),
                        data = commonObjectmapper.writeValueAsString(voksneIBpsHusstand),
                        rolle = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN.innhentesForRolle(behandling)!!,
                        type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        erBearbeidet = true,
                    ),
                )

                behandling.grunnlag.add(
                    Grunnlag(
                        aktiv = null,
                        behandling = behandling,
                        innhentet = LocalDateTime.now().minusDays(3),
                        data = bfg!!.data,
                        rolle = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN.innhentesForRolle(behandling)!!,
                        type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        erBearbeidet = false,
                    ),
                )

                assertSoftly(
                    behandling.grunnlag
                        .filter { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type },
                ) { voksne ->
                    voksne shouldHaveSize 4
                    voksne.filter { it.aktiv == null } shouldHaveSize 2
                }

                // hvis
                grunnlagService.aktivereGrunnlag(
                    behandling,
                    AktivereGrunnlagRequestV2(
                        Personident(testdataBP.ident),
                        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                        false,
                    ),
                )

                // så
                assertSoftly(
                    behandling.grunnlag
                        .filter { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type },
                ) { voksne ->
                    voksne shouldHaveSize 4
                    voksne.filter { it.aktiv == null } shouldHaveSize 0
                }
            }

            @Test
            @Transactional
            open fun `skal gi 404-respons ved forsøk på å aktivere grunnlag av type utvidet barnetrygd for særbidragbehandling`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(
                        false,
                        false,
                        false,
                        true,
                        behandlingstype = TypeBehandling.SÆRBIDRAG,
                    )

                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val grunnlag =
                    setOf(
                        UtvidetBarnetrygdGrunnlagDto(
                            personId = behandling.bidragspliktig!!.ident!!,
                            periodeFra =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløp = BigDecimal(37500),
                            manueltBeregnet = false,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = grunnlag,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                utvidetBarnetrygdListe = grunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragspliktig!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig!!.ident!!),
                        Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                    }

                // så
                respons.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            @Test
            @Transactional
            open fun `skal gi 404-respons ved forsøk på å aktivere grunnlag av type småbarnstillegg for særbidragbehandling`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(
                        false,
                        false,
                        false,
                        true,
                        behandlingstype = TypeBehandling.SÆRBIDRAG,
                    )

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)

                val grunnlag =
                    setOf(
                        SmåbarnstilleggGrunnlagDto(
                            personId = behandling.bidragspliktig!!.ident!!,
                            periodeFra =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløp = BigDecimal(35000),
                            manueltBeregnet = false,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = grunnlag,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                småbarnstilleggListe = grunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragspliktig!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig!!.ident!!),
                        Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                    }

                // så
                respons.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            @Test
            @Transactional
            open fun `skal gi 404-respons ved forsøk på å aktivere grunnlag av type kontantstøtte for særbidragbehandling`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(false, false, false, true, TypeBehandling.SÆRBIDRAG)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragspliktig!!.ident!!)

                val grunnlag =
                    setOf(
                        KontantstøtteGrunnlagDto(
                            behandling.bidragspliktig!!.ident!!,
                            behandling.søknadsbarn.first().ident!!,
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
                            YearMonth.now().withMonth(1).atDay(1),
                            50000,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = grunnlag,
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                kontantstøtteListe = grunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragspliktig!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.KONTANTSTØTTE,
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                    }

                // så
                respons.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            @Test
            @Transactional
            open fun `skal gi 404-respons ved forsøk på å aktivere grunnlag av type sivilstand for særbidragbehandling`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandlingINyTransaksjon(
                        false,
                        false,
                        false,
                        true,
                        TypeBehandling.SÆRBIDRAG,
                    )

                assertSoftly(behandling.sivilstand) {
                    it.size shouldBe 0
                }

                testdataManager.oppretteOgLagreGrunnlagINyTransaksjon(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        setOf(
                            SivilstandGrunnlagDto(
                                personId = behandling.bidragspliktig!!.ident!!,
                                type = SivilstandskodePDL.SKILT,
                                gyldigFom = LocalDate.now().minusMonths(29),
                                master = "FREG",
                                historisk = false,
                                registrert = LocalDateTime.now().minusMonths(29),
                                bekreftelsesdato = null,
                            ),
                        ),
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            Sivilstand(
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                        ),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragspliktig?.ident!!),
                        Grunnlagsdatatype.SIVILSTAND,
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                    }

                // så
                respons.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            @Test
            @Transactional
            open fun `skal gi 404-svar hvis ingen grunnlag å aktivere`() {
                // gitt
                val behandling =
                    testdataManager.oppretteBehandling(
                        false,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.SÆRBIDRAG,
                    )
                stubbeHentingAvPersoninfoForTestpersoner()
                Mockito
                    .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
                    .thenReturn(testdataBarn1.tilPersonDto())

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = LocalDateTime.now(),
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            BoforholdResponseV2(
                                bostatus = Bostatuskode.MED_FORELDER,
                                gjelderPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                            ),
                        ),
                )

                entityManager.flush()
                entityManager.refresh(behandling)

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(testdataHusstandsmedlem1.ident),
                        Grunnlagsdatatype.BOFORHOLD,
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        grunnlagService.aktivereGrunnlag(
                            behandling,
                            aktivereGrunnlagRequest,
                        )
                    }

                // så
                respons.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        @Nested
        @DisplayName("Teste aktivering av grunnlag for forskudd")
        open inner class Forskudd {
            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type inntekt, og oppdatere inntektstabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
                stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()

                val skattegrunlagFraDato =
                    behandling.søktFomDato
                        .minusYears(1)
                        .withMonth(1)
                        .withDayOfMonth(1)

                val skattepliktigeInntekter =
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
                                                    YearMonth
                                                        .now()
                                                        .minusMonths(2)
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
                    )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = skattepliktigeInntekter,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = skattepliktigeInntekter.tilBearbeidaInntekter(behandling.bidragsmottaker!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragsmottaker?.ident!!),
                        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                entityManager.flush()
                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 5
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
                    behandling.inntekter.size shouldBe 4
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragsmottaker!!.ident }
                        .size shouldBe 4
                    behandling.inntekter
                        .filter { Inntektsrapportering.KAPITALINNTEKT == it.type }
                        .filter { BigDecimal.ZERO == it.belop }
                        .size shouldBe 1
                    behandling.inntekter
                        .filter { Inntektsrapportering.LIGNINGSINNTEKT == it.type }
                        .filter { BigDecimal(1368000) == it.belop }
                        .size shouldBe 1
                    behandling.inntekter
                        .first { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.type }
                        .belop shouldBe BigDecimal(1472000)
                    behandling.inntekter
                        .first { Inntektsrapportering.AINNTEKT_BEREGNET_12MND == it.type }
                        .belop shouldBe BigDecimal(368000)
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type barnetillegg, og oppdatere inntektstabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val barnetilleggGrunnlag =
                    setOf(
                        BarnetilleggGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            barnPersonId = behandling.søknadsbarn.first().ident!!,
                            periodeFra =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløpBrutto = BigDecimal(40000),
                            barnetilleggType = "Cash",
                            barnType = "universell",
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = barnetilleggGrunnlag,
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                barnetilleggListe = barnetilleggGrunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragsmottaker!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragsmottaker?.ident!!),
                        Grunnlagsdatatype.BARNETILLEGG,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                entityManager.flush()
                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                    behandling.inntekter.size shouldBe 1
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragsmottaker!!.ident }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type kontantstøtte, og oppdatere inntektstabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val grunnlag =
                    setOf(
                        KontantstøtteGrunnlagDto(
                            behandling.bidragsmottaker!!.ident!!,
                            behandling.søknadsbarn.first().ident!!,
                            YearMonth
                                .now()
                                .minusYears(1)
                                .withMonth(1)
                                .atDay(1),
                            YearMonth.now().withMonth(1).atDay(1),
                            50000,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = grunnlag,
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                kontantstøtteListe = grunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragsmottaker!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragsmottaker?.ident!!),
                        Grunnlagsdatatype.KONTANTSTØTTE,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                entityManager.flush()

                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                    behandling.inntekter.size shouldBe 1
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragsmottaker!!.ident }
                        .size shouldBe 1
                    behandling.inntekter
                        .filter { Inntektsrapportering.KONTANTSTØTTE == it.type }
                        .filter { BigDecimal(600000) == it.belop }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type småbarnstillegg, og oppdatere inntektstabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val grunnlag =
                    setOf(
                        SmåbarnstilleggGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            periodeFra =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløp = BigDecimal(35000),
                            manueltBeregnet = false,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = grunnlag,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                småbarnstilleggListe = grunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragsmottaker!!),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragsmottaker?.ident!!),
                        Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                entityManager.flush()

                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 2
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                    behandling.inntekter.size shouldBe 1
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragsmottaker!!.ident }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type utvidet barnetrygd, og oppdatere inntektstabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)

                stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

                val grunnlag =
                    setOf(
                        UtvidetBarnetrygdGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            periodeFra =
                                YearMonth
                                    .now()
                                    .minusYears(1)
                                    .withMonth(1)
                                    .atDay(1),
                            periodeTil = YearMonth.now().withMonth(1).atDay(1),
                            beløp = BigDecimal(37500),
                            manueltBeregnet = false,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata = grunnlag,
                )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        opprettHentGrunnlagDto()
                            .copy(
                                utvidetBarnetrygdListe = grunnlag.toList(),
                            ).tilSummerteInntekter(behandling.bidragsmottaker!!),
                )
                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragsmottaker?.ident!!),
                        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)
                entityManager.flush()

                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.grunnlag.isNotEmpty()
                    behandling.grunnlag.filter { LocalDate.now() == it.aktiv?.toLocalDate() }.size shouldBe 5
                    behandling.grunnlag.filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 0
                    behandling.inntekter.size shouldBe 1
                    behandling.inntekter
                        .filter { Kilde.OFFENTLIG == it.kilde }
                        .filter { it.ident == behandling.bidragsmottaker!!.ident }
                        .size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type boforhold, og oppdatere husstandsmedlemtabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)
                stubbeHentingAvPersoninfoForTestpersoner()
                Mockito
                    .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
                    .thenReturn(testdataBarn1.tilPersonDto())

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 0
                }

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        setOf(
                            RelatertPersonGrunnlagDto(
                                relatertPersonPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                erBarnAvBmBp = true,
                                navn = null,
                                partPersonId = behandling.bidragsmottaker!!.ident!!,
                                borISammeHusstandDtoListe =
                                    listOf(
                                        BorISammeHusstandDto(
                                            testdataHusstandsmedlem1.fødselsdato,
                                            periodeTil = null,
                                        ),
                                    ),
                            ),
                        ),
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            BoforholdResponseV2(
                                bostatus = Bostatuskode.MED_FORELDER,
                                gjelderPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                            ),
                        ),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        personident = Personident(testdataBM.ident),
                        gjelderIdent = Personident(testdataHusstandsmedlem1.ident),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                assertSoftly(behandling.grunnlag) { g ->
                    g.isNotEmpty()
                    g.size shouldBe 2
                    g.filter { Grunnlagsdatatype.BOFORHOLD == it.type }.size shouldBe 2
                    g.filter { it.erBearbeidet }.size shouldBe 1
                    g.find { it.aktiv == null } shouldBe null
                    g.filter { LocalDate.now() == it.aktiv!!.toLocalDate() }.size shouldBe 2
                }

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 1
                    it.first().perioder.size shouldBe 1
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere grunnlag av type sivilstand, og oppdatere sivilstandstabell`() {
                // gitt
                val behandling = testdataManager.oppretteBehandlingINyTransaksjon(false, false, false)

                assertSoftly(behandling.sivilstand) {
                    it.size shouldBe 0
                }

                testdataManager.oppretteOgLagreGrunnlagINyTransaksjon(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    grunnlagsdata =
                        setOf(
                            SivilstandGrunnlagDto(
                                personId = behandling.bidragsmottaker!!.ident!!,
                                type = SivilstandskodePDL.SKILT,
                                gyldigFom = LocalDate.now().minusMonths(29),
                                master = "FREG",
                                historisk = false,
                                registrert = LocalDateTime.now().minusMonths(29),
                                bekreftelsesdato = null,
                            ),
                        ),
                )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = null,
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            Sivilstand(
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                        ),
                )

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(behandling.bidragsmottaker?.ident!!),
                        Grunnlagsdatatype.SIVILSTAND,
                    )

                // hvis
                grunnlagService.aktivereGrunnlag(behandling, aktivereGrunnlagRequest)

                // så
                assertSoftly(behandling.grunnlag) { g ->
                    g.isNotEmpty()
                    g.size shouldBe 2
                    g.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.size shouldBe 2
                    g.filter { it.erBearbeidet }.size shouldBe 1
                    g.find { it.aktiv == null } shouldBe null
                    g.filter { LocalDate.now() == it.aktiv!!.toLocalDate() }.size shouldBe 2
                }

                assertSoftly(behandling.sivilstand) {
                    it.size shouldBe 1
                    it.first().sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                }
            }

            @Test
            @Transactional
            open fun `skal ikke aktivere boforholdrådata dersom bearbeida boforhold er ikke er aktivert for samtlige husstandsmedlemmer`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                val grunnlag = opprettAlleAktiveGrunnlagFraFil(behandling, "hente-grunnlagrespons.json")

                entityManager.refresh(behandling)

                val rådataBoforhold = grunnlag.find { !it.erBearbeidet && it.type == Grunnlagsdatatype.BOFORHOLD }

                val nyeBorhosperioder =
                    listOf(
                        BorISammeHusstandDto(
                            periodeFra = behandling.virkningstidspunktEllerSøktFomDato,
                            periodeTil = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(7),
                        ),
                        BorISammeHusstandDto(
                            periodeFra = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(10),
                            periodeTil = null,
                        ),
                    )

                val dataBoforhold = jsonListeTilObjekt<RelatertPersonGrunnlagDto>(rådataBoforhold!!.data)

                val endretBoforholdTestbarn1 =
                    dataBoforhold
                        .find { testdataBarn1.ident == it.relatertPersonPersonId }
                        ?.copy(borISammeHusstandDtoListe = nyeBorhosperioder)

                val endretBoforhold =
                    listOf(
                        endretBoforholdTestbarn1!!,
                        dataBoforhold.find { testdataBarn2.ident == it.relatertPersonPersonId }!!,
                    )

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling,
                        Grunnlagsdatatype.BOFORHOLD,
                        erBearbeidet = false,
                        data = tilJson(endretBoforhold.toSet()),
                        innhentet = LocalDateTime.now().minusDays(1),
                        aktiv = LocalDateTime.now().minusDays(1),
                        rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

                val bearbeidaBoforhold =
                    BoforholdApi.beregnBoforholdBarnV3(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        behandling.globalOpphørsdato,
                        behandling.tilTypeBoforhold(),
                        endretBoforhold.toMutableList().tilBoforholdBarnRequest(behandling),
                    )

                bearbeidaBoforhold.groupBy { it.gjelderPersonId }.forEach {
                    behandling.grunnlag.add(
                        Grunnlag(
                            behandling,
                            Grunnlagsdatatype.BOFORHOLD,
                            erBearbeidet = true,
                            data = tilJson(it.value),
                            innhentet = LocalDateTime.now().minusDays(1),
                            aktiv = LocalDateTime.now().minusDays(1),
                            rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                            gjelder = it.key,
                        ),
                    )
                }

                stubbeHentingAvPersoninfoForTestpersoner()
                behandlingRepository.save(behandling)

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 3
                    g.filter { behandling.bidragsmottaker == it.rolle } shouldHaveSize 3
                    g.filter { it.aktiv != null } shouldHaveSize 3
                    g.filter { it.erBearbeidet } shouldHaveSize 2
                    g.filter { !it.erBearbeidet && it.gjelder == null } shouldHaveSize 1
                    g.filter { testdataBarn1.ident == it.gjelder && it.erBearbeidet } shouldHaveSize 1
                    g.filter { testdataBarn2.ident == it.gjelder && it.erBearbeidet } shouldHaveSize 1
                }

                // hvis
                grunnlagService.oppdatereGrunnlagForBehandling(behandling)

                // så
                val boforhold = behandling.grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD }
                assertSoftly(boforhold) { sb ->
                    sb.size shouldBe 5
                    sb.filter { it.aktiv != null } shouldHaveSize 3
                    sb.filter { it.erBearbeidet } shouldHaveSize 3
                }
            }

            @Test
            @Transactional
            open fun `skal aktivere boforholdrådata dersom bearbeida boforhold er aktivert for samtlige husstandsmedlemmer`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, false)
                behandling.roller.forEach {
                    when (it.rolletype) {
                        Rolletype.BIDRAGSMOTTAKER -> stubUtils.stubHenteGrunnlag(it.ident)
                        Rolletype.BARN ->
                            stubUtils.stubHenteGrunnlag(
                                rolleIdent = it.ident,
                                navnResponsfil = "hente-grunnlagrespons-barn1.json",
                            )

                        else -> throw Exception()
                    }
                }

                val grunnlag = opprettAlleAktiveGrunnlagFraFil(behandling, "hente-grunnlagrespons.json")

                entityManager.refresh(behandling)

                val rådataBoforhold = grunnlag.find { !it.erBearbeidet && it.type == Grunnlagsdatatype.BOFORHOLD }

                behandling.grunnlag.add(
                    Grunnlag(
                        behandling,
                        Grunnlagsdatatype.BOFORHOLD,
                        erBearbeidet = false,
                        data = rådataBoforhold!!.data,
                        innhentet = LocalDateTime.now().minusDays(1),
                        aktiv = null,
                        rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                    ),
                )

                val bearbeidaBoforhold =
                    BoforholdApi.beregnBoforholdBarnV3(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        behandling.globalOpphørsdato,
                        behandling.tilTypeBoforhold(),
                        jsonListeTilObjekt<RelatertPersonGrunnlagDto>(
                            rådataBoforhold.data,
                        ).tilBoforholdBarnRequest(behandling),
                    )

                bearbeidaBoforhold.groupBy { it.gjelderPersonId }.forEach {
                    behandling.grunnlag.add(
                        Grunnlag(
                            behandling,
                            Grunnlagsdatatype.BOFORHOLD,
                            erBearbeidet = true,
                            data = tilJson(it.value),
                            innhentet = LocalDateTime.now().minusDays(1),
                            aktiv = if (testdataBarn1.ident == it.key) null else LocalDateTime.now().minusDays(1),
                            rolle = behandling.roller.first { Rolletype.BIDRAGSMOTTAKER == it.rolletype },
                            gjelder = it.key,
                        ),
                    )
                }

                stubbeHentingAvPersoninfoForTestpersoner()
                behandlingRepository.save(behandling)

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 3
                    g.filter { behandling.bidragsmottaker == it.rolle } shouldHaveSize 3
                    g.filter { it.aktiv != null } shouldHaveSize 1
                    g.filter { it.erBearbeidet } shouldHaveSize 2
                    g.filter { !it.erBearbeidet && it.gjelder == null && it.aktiv === null } shouldHaveSize 1
                    g.filter { testdataBarn1.ident == it.gjelder && it.erBearbeidet && it.aktiv == null } shouldHaveSize 1
                    g.filter { testdataBarn2.ident == it.gjelder && it.erBearbeidet } shouldHaveSize 1
                }

                assertSoftly(behandling.husstandsmedlem) { hb ->
                    hb shouldHaveSize 0
                    hb.filter { Kilde.OFFENTLIG == it.kilde }
                }

                // hvis
                grunnlagService.aktivereGrunnlag(
                    behandling,
                    AktivereGrunnlagRequestV2(
                        personident = Personident(testdataBM.ident),
                        gjelderIdent = Personident(testdataBarn1.ident),
                        grunnlagstype = Grunnlagsdatatype.BOFORHOLD,
                        overskriveManuelleOpplysninger = false,
                    ),
                )

                // så
                val boforhold = behandling.grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD }
                assertSoftly(boforhold) { b ->
                    b.size shouldBe 3
                    b.filter { it.aktiv != null } shouldHaveSize 3
                    b.filter { it.erBearbeidet } shouldHaveSize 2
                }

                assertSoftly(behandling.husstandsmedlem) { hb ->
                    hb shouldHaveSize 1
                    hb.filter { Kilde.OFFENTLIG == it.kilde }
                }
            }

            @Test
            @Transactional
            open fun `skal gi 404-svar hvis ingen grunnlag å aktivere`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false)
                stubbeHentingAvPersoninfoForTestpersoner()
                Mockito
                    .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
                    .thenReturn(testdataBarn1.tilPersonDto())

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 2
                }

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                    aktiv = LocalDateTime.now(),
                    gjelderIdent = testdataHusstandsmedlem1.ident,
                    grunnlagsdata =
                        setOf(
                            BoforholdResponseV2(
                                bostatus = Bostatuskode.MED_FORELDER,
                                gjelderPersonId = testdataHusstandsmedlem1.ident,
                                fødselsdato = testdataHusstandsmedlem1.fødselsdato,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = testdataHusstandsmedlem1.fødselsdato,
                                periodeTom = null,
                            ),
                        ),
                )

                entityManager.flush()
                entityManager.refresh(behandling)

                val aktivereGrunnlagRequest =
                    AktivereGrunnlagRequestV2(
                        Personident(testdataHusstandsmedlem1.ident),
                        Grunnlagsdatatype.BOFORHOLD,
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        grunnlagService.aktivereGrunnlag(
                            behandling,
                            aktivereGrunnlagRequest,
                        )
                    }

                // så
                respons.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    @Nested
    @DisplayName("Teste hentSistInnhentet")
    open inner class HentSistInnhentet {
        @Test
        fun `skal være bare en rad med aktive opplysninger`() {
            // gitt
            val b = testdataManager.oppretteBehandling(false)

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
    @DisplayName("Teste feilhåndtering")
    open inner class Feilhåndtering {
        @MockkBean
        lateinit var bidragGrunnlagConsumerMock: BidragGrunnlagConsumer

        @Autowired
        lateinit var grunnlagServiceMock: GrunnlagService

        @Test
        @Transactional
        open fun `skal håndtere at bidrag-grunnlag er nede`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            behandling.grunnlag shouldBe emptySet()

            every { bidragGrunnlagConsumerMock.henteGrunnlag(any(), any()) } returns (
                HentetGrunnlag(null, "Teknisk feil ved henting av grunnlag")
            )

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            assertSoftly(behandling) { b ->
                b.grunnlag shouldHaveSize 0
                b.grunnlagsinnhentingFeilet shouldNotBe null
                b.grunnlagSistInnhentet shouldNotBe null
                b.grunnlagSistInnhentet!!.toLocalDate() shouldBe LocalDate.now()
            }
        }

        @Test
        @Transactional
        open fun `skal ikke lagre tomt grunnlag dersom innhenting feiler`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            behandling.grunnlag shouldBe emptySet()

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    feilrapporteringListe = oppretteFeilrapporteringerForPerson(behandling.bidragsmottaker!!.ident!!),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every { (bidragGrunnlagConsumerMock.henteGrunnlag(any(), any())) } returns (HentetGrunnlag(innhentingMedFeil))

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            assertSoftly(behandling) { b ->
                b.grunnlag shouldHaveSize 0
                b.grunnlagsinnhentingFeilet shouldNotBe null
            }
        }

        @Test
        @Transactional
        open fun `skal slette grunnlagsinnhentingFeilet hvis ny innhenting ikke inneholder feil`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)

            behandling.grunnlag shouldBe emptySet()

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    feilrapporteringListe = oppretteFeilrapporteringerForPerson(behandling.bidragsmottaker!!.ident!!),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            assertSoftly(behandling) { b ->
                b.grunnlag shouldHaveSize 0
                b.grunnlagsinnhentingFeilet shouldNotBe null
            }

            // gitt
            val innhentingUtenFeil =
                opprettHentGrunnlagDto().copy(
                    ainntektListe =
                        oppretteSkattepliktigeInntekter(
                            behandling,
                            behandling.bidragsmottaker!!,
                        ).ainntekter,
                    skattegrunnlagListe =
                        oppretteSkattepliktigeInntekter(
                            behandling,
                            behandling.bidragsmottaker!!,
                        ).skattegrunnlag,
                )

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingUtenFeil))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            assertSoftly(behandling) { b ->
                b.grunnlag shouldHaveSize 5
                b.grunnlagsinnhentingFeilet shouldBe null
            }
        }

        @Test
        @Transactional
        open fun `skal ikke lagre ny innhenting av arbeidsforhold med teknisk feil dersom forrige innhenting var OK`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    true,
                    inkludereArbeidsforhold = true,
                    setteDatabaseider = true,
                    inkludereInntektsgrunnlag = true,
                )

            val arbeidsforhold =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }
                    .konvertereData<Set<ArbeidsforholdGrunnlagDto>>()
                    ?.first()

            val navnNyArbeidsgiver = "Syn & bedrag AS"
            val nyttArbeidsforhold = arbeidsforhold?.copy(arbeidsgiverNavn = navnNyArbeidsgiver)

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    arbeidsforholdListe = listOf(nyttArbeidsforhold!!),
                    feilrapporteringListe = oppretteFeilrapporteringerForPerson(behandling.bidragsmottaker!!.ident!!),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            behandling.grunnlagsinnhentingFeilet shouldBe null

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 1
                si[0]
                    .konvertereData<Set<ArbeidsforholdGrunnlagDto>>()
                    ?.filter { it.arbeidsgiverNavn == navnNyArbeidsgiver }
                    ?.shouldHaveSize(0)
            }
        }

        @Test
        @Transactional
        open fun `skal lagre ny innhenting av arbeidsforhold med funksjonell feil dersom forrige innhenting var OK`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    true,
                    inkludereArbeidsforhold = true,
                    setteDatabaseider = true,
                    inkludereInntektsgrunnlag = true,
                )

            val arbeidsforhold =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }
                    .konvertereData<Set<ArbeidsforholdGrunnlagDto>>()
                    ?.first()

            val navnNyArbeidsgiver = "Syn & bedrag AS"
            val nyttArbeidsforhold = arbeidsforhold?.copy(arbeidsgiverNavn = navnNyArbeidsgiver)

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    arbeidsforholdListe = listOf(nyttArbeidsforhold!!),
                    feilrapporteringListe =
                        oppretteFeilrapporteringerForPerson(
                            behandling.bidragsmottaker!!.ident!!,
                            HentGrunnlagFeiltype.FUNKSJONELL_FEIL,
                        ),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every { (bidragGrunnlagConsumerMock.henteGrunnlag(any(), any())) } returns (HentetGrunnlag(innhentingMedFeil))

            behandling.grunnlagsinnhentingFeilet shouldBe null

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 4
                assertSoftly(si.filter { it.rolle == behandling.bidragsmottaker!! }.maxBy { it.innhentet }) {
                    it.aktiv shouldBe null
                    it
                        .konvertereData<Set<ArbeidsforholdGrunnlagDto>>()
                        ?.filter {
                            it.arbeidsgiverNavn == navnNyArbeidsgiver
                        }?.shouldHaveSize(1)
                }
            }
        }

        @Test
        @Transactional
        open fun `skal ikke lagre ny innhenting av skattegrunnlag med teknisk feil dersom forrige innhenting var OK`() {
            // gitt
            val behandling =
                oppretteTestbehandling(true, setteDatabaseider = true, inkludereInntektsgrunnlag = true)

            val skattegrunnlag =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
                    .konvertereData<SkattepliktigeInntekter>()
                    ?.skattegrunnlag!!
                    .filter { it.skattegrunnlagspostListe.isNotEmpty() }
                    .first()

            val skattegrunnlagspost = skattegrunnlag.skattegrunnlagspostListe.first()

            val nyPost = skattegrunnlagspost.copy(beløp = BigDecimal(550000), kode = "Seiling")
            val nyttSkattegrunnlagselement = skattegrunnlag.copy(skattegrunnlagspostListe = listOf(nyPost))

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    skattegrunnlagListe = listOf(nyttSkattegrunnlagselement),
                    feilrapporteringListe = oppretteFeilrapporteringerForPerson(behandling.bidragsmottaker!!.ident!!),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            behandling.grunnlagsinnhentingFeilet shouldBe null

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 1
                si[0]
                    .konvertereData<SkattepliktigeInntekter>()
                    ?.skattegrunnlag
                    ?.filter { s ->
                        s.skattegrunnlagspostListe.filter { it.kode == "Seiling" }.isNotEmpty()
                    }?.shouldHaveSize(0)
            }
        }

        @Test
        @Transactional
        open fun `skal lagre ny innhenting av skattegrunnlag med funksjonell feil dersom forrige innhenting var OK`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    true,
                    inkludereBp = true,
                    setteDatabaseider = true,
                    inkludereInntektsgrunnlag = true,
                )

            val skattegrunnlag =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
                    .konvertereData<SkattepliktigeInntekter>()
                    ?.skattegrunnlag!!
                    .filter { it.skattegrunnlagspostListe.isNotEmpty() }
                    .first()

            val skattegrunnlagspost = skattegrunnlag.skattegrunnlagspostListe.first()

            val nyPost = skattegrunnlagspost.copy(beløp = BigDecimal(550000), kode = "Seiling")
            val nyttSkattegrunnlagselement = skattegrunnlag.copy(skattegrunnlagspostListe = listOf(nyPost))

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    skattegrunnlagListe = listOf(nyttSkattegrunnlagselement),
                    feilrapporteringListe =
                        oppretteFeilrapporteringer(
                            setOf(
                                behandling.bidragsmottaker!!.ident!!,
                                behandling.bidragspliktig!!.ident!!,
                                behandling.søknadsbarn.first().ident!!,
                            ),
                            HentGrunnlagFeiltype.FUNKSJONELL_FEIL,
                        ),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 30
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            behandling.grunnlagsinnhentingFeilet shouldBe null

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 2
                assertSoftly(si.maxBy { it.innhentet }) { siste ->
                    siste.aktiv shouldBe null
                    siste
                        .konvertereData<SkattepliktigeInntekter>()
                        ?.skattegrunnlag
                        ?.filter { s ->
                            s.skattegrunnlagspostListe.filter { it.kode == "Seiling" }.isNotEmpty()
                        }?.shouldHaveSize(1)
                }
            }
        }

        @Test
        @Transactional
        open fun `skal lagre endret skattegrunnlag med teknisk feil dersom forrige innhenting hadde teknisk feil`() {
            // gitt
            val behandling =
                oppretteTestbehandling(true, setteDatabaseider = true, inkludereInntektsgrunnlag = true)

            val skattegrunnlag =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
                    .konvertereData<SkattepliktigeInntekter>()
                    ?.skattegrunnlag!!
                    .filter { it.skattegrunnlagspostListe.isNotEmpty() }
                    .first()

            val skattegrunnlagspost = skattegrunnlag.skattegrunnlagspostListe.first()

            val nyPost = skattegrunnlagspost.copy(beløp = BigDecimal(550000), kode = "Seiling")
            val nyttSkattegrunnlagselement = skattegrunnlag.copy(skattegrunnlagspostListe = listOf(nyPost))

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    skattegrunnlagListe = listOf(nyttSkattegrunnlagselement),
                    feilrapporteringListe = oppretteFeilrapporteringerForPerson(behandling.bidragsmottaker!!.ident!!),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            behandling.grunnlagsinnhentingFeilet =
                "{\"SKATTEPLIKTIGE_INNTEKTER\":{\"grunnlagstype\":\"SKATTEGRUNNLAG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"},\n" +
                "\"SUMMERTE_MÅNEDSINNTEKTER\":{\"grunnlagstype\":\"SKATTEGRUNNLAG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"}}"

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 2
                assertSoftly(si.maxBy { it.innhentet }) { siste ->
                    siste.aktiv shouldBe null
                    siste
                        .konvertereData<SkattepliktigeInntekter>()
                        ?.skattegrunnlag
                        ?.filter { s ->
                            s.skattegrunnlagspostListe.filter { it.kode == "Seiling" }.isNotEmpty()
                        }?.shouldHaveSize(1)
                }
            }
        }

        @Test
        @Transactional
        open fun `skal lagre endret skattegrunnlag med funksjonell feil dersom forrige innhenting hadde teknisk feil`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    true,
                    inkludereBp = true,
                    setteDatabaseider = true,
                    inkludereInntektsgrunnlag = true,
                )

            val skattegrunnlag =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
                    .konvertereData<SkattepliktigeInntekter>()
                    ?.skattegrunnlag!!
                    .filter { it.skattegrunnlagspostListe.isNotEmpty() }
                    .first()

            val skattegrunnlagspost = skattegrunnlag.skattegrunnlagspostListe.first()

            val nyPost = skattegrunnlagspost.copy(beløp = BigDecimal(550000), kode = "Seiling")
            val nyttSkattegrunnlagselement = skattegrunnlag.copy(skattegrunnlagspostListe = listOf(nyPost))

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    skattegrunnlagListe = listOf(nyttSkattegrunnlagselement),
                    feilrapporteringListe =
                        oppretteFeilrapporteringerForPerson(
                            behandling.bidragsmottaker!!.ident!!,
                            HentGrunnlagFeiltype.FUNKSJONELL_FEIL,
                        ),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 10
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            behandling.grunnlagsinnhentingFeilet =
                "{\"SKATTEPLIKTIGE_INNTEKTER\":{\"grunnlagstype\":\"SKATTEGRUNNLAG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"},\n" +
                "\"SUMMERTE_MÅNEDSINNTEKTER\":{\"grunnlagstype\":\"SKATTEGRUNNLAG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"}}"

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 2
                assertSoftly(si.maxBy { it.innhentet }) { siste ->
                    siste.aktiv shouldBe null
                    siste
                        .konvertereData<SkattepliktigeInntekter>()
                        ?.skattegrunnlag
                        ?.filter { s ->
                            s.skattegrunnlagspostListe.filter { it.kode == "Seiling" }.isNotEmpty()
                        }?.shouldHaveSize(1)
                }
            }
        }

        @Test
        @Transactional
        open fun `skal lagre endret skattegrunnlag med funksjonell feil dersom forrige innhenting ikke hadde feil`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    true,
                    inkludereBp = true,
                    inkludereInntektsgrunnlag = true,
                    setteDatabaseider = true,
                )
            val skattegrunnlag =
                behandling.grunnlag
                    .first { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && !it.erBearbeidet }
                    .konvertereData<SkattepliktigeInntekter>()
                    ?.skattegrunnlag!!
                    .filter { it.skattegrunnlagspostListe.isNotEmpty() }
                    .first()

            val skattegrunnlagspost = skattegrunnlag.skattegrunnlagspostListe.first()

            val nyPost = skattegrunnlagspost.copy(beløp = BigDecimal(550000), kode = "Seiling")
            val nyttSkattegrunnlagselement = skattegrunnlag.copy(skattegrunnlagspostListe = listOf(nyPost))

            val innhentingMedFeil =
                opprettHentGrunnlagDto().copy(
                    skattegrunnlagListe = listOf(nyttSkattegrunnlagselement),
                    feilrapporteringListe =
                        oppretteFeilrapporteringer(
                            setOf(
                                behandling.bidragsmottaker!!.ident!!,
                                behandling.bidragspliktig!!.ident!!,
                                behandling.søknadsbarn.first().ident!!,
                            ),
                            HentGrunnlagFeiltype.FUNKSJONELL_FEIL,
                        ),
                )

            innhentingMedFeil.feilrapporteringListe shouldHaveSize 30
            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId == behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(innhentingMedFeil, "Teknisk feil ved henting av grunnlag"))

            every {
                bidragGrunnlagConsumerMock.henteGrunnlag(
                    match {
                        it.find { request ->
                            request.personId != behandling.bidragsmottaker?.ident!!
                        } != null
                    },
                    any(),
                )
            } returns (HentetGrunnlag(opprettHentGrunnlagDto()))

            behandling.grunnlagsinnhentingFeilet =
                "{\"SKATTEPLIKTIGE_INNTEKTER\":{\"grunnlagstype\":\"SKATTEGRUNNLAG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"},\n" +
                "\"SUMMERTE_MÅNEDSINNTEKTER\":{\"grunnlagstype\":\"SKATTEGRUNNLAG\",\"personId\":\"313213213\",\"periodeFra\":[2023,1,1],\"periodeTil\":[2023,12,31],\"feiltype\":\"TEKNISK_FEIL\",\"feilmelding\":\"Ouups!\"}}"

            // hvis
            grunnlagServiceMock.oppdatereGrunnlagForBehandling(behandling)

            // så
            behandling.grunnlagsinnhentingFeilet shouldNotBe null

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.type && !it.erBearbeidet }) { si ->
                si shouldHaveSize 2
                assertSoftly(si.maxBy { it.innhentet }) { siste ->
                    siste.aktiv shouldBe null
                    siste
                        .konvertereData<SkattepliktigeInntekter>()
                        ?.skattegrunnlag
                        ?.filter { s ->
                            s.skattegrunnlagspostListe.filter { it.kode == "Seiling" }.isNotEmpty()
                        }?.shouldHaveSize(1)
                }
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
            tilleggsstønadBarnetilsynListe = emptyList(),
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

    private fun validereGrunnlagBm(
        grunnlag: List<Grunnlag>,
        gjelderBidrag: Boolean = false,
    ) {
        assertSoftly {
            grunnlag.size shouldBe if (gjelderBidrag) 13 else 13
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.ARBEIDSFORHOLD }.size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.BOFORHOLD }.size shouldBe if (gjelderBidrag) 0 else 3
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SIVILSTAND }.size shouldBe if (gjelderBidrag) 1 else 2
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.BARNETILLEGG }.size shouldBe 0
            grunnlag
                .filter { g -> g.type == Grunnlagsdatatype.BARNETILLEGG }
                .filter { it.erBearbeidet }
                .size shouldBe 0
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.KONTANTSTØTTE }.size shouldBe 0
            grunnlag
                .filter { g -> g.type == Grunnlagsdatatype.KONTANTSTØTTE }
                .filter { it.erBearbeidet }
                .size shouldBe 0
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SMÅBARNSTILLEGG }.size shouldBe 2
            grunnlag
                .filter { g -> g.type == Grunnlagsdatatype.SMÅBARNSTILLEGG }
                .filter { it.erBearbeidet }
                .size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.UTVIDET_BARNETRYGD }.size shouldBe 2
            grunnlag
                .filter { g -> g.type == Grunnlagsdatatype.UTVIDET_BARNETRYGD }
                .filter { it.erBearbeidet }
                .size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }.size shouldBe 2
            grunnlag
                .filter { g -> g.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER }
                .filter { it.erBearbeidet }
                .size shouldBe 1
            grunnlag.filter { g -> g.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER }.size shouldBe 1
            grunnlag
                .filter { g -> g.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER }
                .filter { it.erBearbeidet }
                .size shouldBe 1
        }
    }

    private fun stubbeHentingAvPersoninfoForTestpersoner() {
        Mockito.`when`(bidragPersonConsumer.hentPerson(testdataBM.ident)).thenReturn(testdataBM.tilPersonDto())
        Mockito.`when`(bidragPersonConsumer.hentPerson(testdataBP.ident)).thenReturn(testdataBP.tilPersonDto())
        Mockito
            .`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
            .thenReturn(testdataBarn1.tilPersonDto())
        Mockito
            .`when`(bidragPersonConsumer.hentPerson(testdataBarn2.ident))
            .thenReturn(testdataBarn2.tilPersonDto())
        Mockito
            .`when`(bidragPersonConsumer.hentPerson(testdataHusstandsmedlem1.ident))
            .thenReturn(testdataHusstandsmedlem1.tilPersonDto())
        Mockito
            .`when`(bidragPersonConsumer.hentPerson(testdataBarnBm.ident))
            .thenReturn(testdataBarnBm.tilPersonDto())
        Mockito
            .`when`(bidragPersonConsumer.hentPerson(testdataBarnBm2.ident))
            .thenReturn(testdataBarnBm2.tilPersonDto())
    }
}

fun SkattepliktigeInntekter.tilBearbeidaInntekter(rolle: Rolle): SummerteInntekter<SummertÅrsinntekt> {
    val hentGrunnlagDto =
        opprettHentGrunnlagDto().copy(
            ainntektListe = ainntekter,
            skattegrunnlagListe = skattegrunnlag,
        )
    return hentGrunnlagDto.tilSummerteInntekter(rolle)
}

fun opprettHentGrunnlagDto() =
    HentGrunnlagDto(
        ainntektListe = emptyList(),
        skattegrunnlagListe = emptyList(),
        arbeidsforholdListe = emptyList(),
        barnetilsynListe = emptyList(),
        barnetilleggListe = emptyList(),
        kontantstøtteListe = emptyList(),
        utvidetBarnetrygdListe = emptyList(),
        småbarnstilleggListe = emptyList(),
        sivilstandListe = emptyList(),
        husstandsmedlemmerOgEgneBarnListe = emptyList(),
        feilrapporteringListe = emptyList(),
        hentetTidspunkt = LocalDateTime.now(),
        tilleggsstønadBarnetilsynListe = emptyList(),
    )

fun oppretteFeilrapporteringerForPerson(
    personident: String,
    feiltype: HentGrunnlagFeiltype = HentGrunnlagFeiltype.TEKNISK_FEIL,
): List<FeilrapporteringDto> = oppretteFeilrapporteringer(setOf(personident), feiltype)

@OptIn(ExperimentalStdlibApi::class)
fun oppretteFeilrapporteringer(
    personidenter: Set<String>,
    feiltype: HentGrunnlagFeiltype = HentGrunnlagFeiltype.TEKNISK_FEIL,
): List<FeilrapporteringDto> {
    val feilrapporteringer = mutableListOf<FeilrapporteringDto>()

    personidenter.forEach { personident ->
        GrunnlagRequestType.entries.filter { GrunnlagRequestType.BARNETILSYN != it }.forEach {
            feilrapporteringer +=
                FeilrapporteringDto(
                    feilmelding = "Ouups!",
                    grunnlagstype = it,
                    feiltype = feiltype,
                    periodeFra =
                        LocalDate
                            .now()
                            .minusYears(1)
                            .withMonth(1)
                            .withDayOfMonth(1),
                    periodeTil =
                        LocalDate
                            .now()
                            .minusYears(1)
                            .withMonth(12)
                            .withDayOfMonth(31),
                    personId = personident,
                )
        }
    }
    return feilrapporteringer
}

fun HentGrunnlagDto.tilSummerteInntekter(rolle: Rolle): SummerteInntekter<SummertÅrsinntekt> {
    val request = tilTransformerInntekterRequest(rolle, LocalDate.now())
    val response = InntektApi("").transformerInntekter(request)
    return SummerteInntekter(
        inntekter = response.summertÅrsinntektListe,
        versjon = response.versjon,
    )
}

fun oppretteSkattepliktigeInntekter(
    behandling: Behandling,
    forRolle: Rolle = behandling.bidragspliktig!!,
    skattegrunnlagFraDato: LocalDate =
        behandling.søktFomDato
            .minusYears(1)
            .withMonth(1)
            .withDayOfMonth(1),
) = SkattepliktigeInntekter(
    ainntekter =
        listOf(
            AinntektGrunnlagDto(
                personId = forRolle.ident!!,
                periodeFra = YearMonth.now().minusMonths(2).atDay(1),
                periodeTil = YearMonth.now().minusMonths(1).atDay(1),
                ainntektspostListe =
                    listOf(
                        tilAinntektspostDto(
                            beskrivelse = "fastloenn",
                            beløp = BigDecimal(368000),
                            inntektstype = "FASTLOENN",
                            utbetalingsperiode =
                                YearMonth
                                    .now()
                                    .minusMonths(2)
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        ),
                    ),
            ),
        ),
    skattegrunnlag =
        listOf(
            SkattegrunnlagGrunnlagDto(
                personId = forRolle.ident!!,
                periodeFra = skattegrunnlagFraDato,
                periodeTil = skattegrunnlagFraDato.plusYears(1),
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
                personId = forRolle.ident!!,
                periodeFra = skattegrunnlagFraDato,
                periodeTil = skattegrunnlagFraDato.plusYears(1),
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
)
