package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerStatus
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettKategoriRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.utils.hentInntektForBarn
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class BehandlingServiceTest : TestContainerRunner() {
    @MockBean
    lateinit var forsendelseService: ForsendelseService

    @Autowired
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var testdataManager: TestdataManager

    @PersistenceContext
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun initMock() {
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
    }

    @AfterEach
    fun resette() {
        behandlingRepository.deleteAll()
    }

    @Nested
    open inner class HenteBehandling {
        @Test
        fun `skal kaste 404 exception hvis behandlingen ikke er der`() {
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.henteBehandling(1234)
            }
        }

        @Test
        @Transactional
        open fun `skal hente behandling med beregnet inntekter`() {
            val behandling = prepareBehandling()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.roller = oppretteBehandlingRoller(behandling)
            behandling.grunnlagSistInnhentet = LocalDateTime.now()
            behandling.inntekter =
                mutableSetOf(
                    Inntekt(
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        BigDecimal.valueOf(1234),
                        LocalDate.parse("2023-01-01"),
                        null,
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = LocalDate.parse("2023-09-01"),
                        behandling = behandling,
                    ),
                    Inntekt(
                        Inntektsrapportering.LIGNINGSINNTEKT,
                        BigDecimal.valueOf(500000),
                        LocalDate.parse("2023-01-01"),
                        LocalDate.parse("2023-09-01"),
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        false,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = LocalDate.parse("2023-09-01"),
                        behandling = behandling,
                    ),
                    Inntekt(
                        Inntektsrapportering.BARNETILLEGG,
                        BigDecimal(555),
                        LocalDate.parse("2024-01-01"),
                        LocalDate.parse("2024-05-01"),
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2024-01-01"),
                        opprinneligTom = LocalDate.parse("2024-05-01"),
                        gjelderBarn = testdataBarn1.ident,
                        behandling = behandling,
                    ),
                    Inntekt(
                        Inntektsrapportering.KONTANTSTØTTE,
                        BigDecimal(999),
                        LocalDate.parse("2024-01-01"),
                        LocalDate.parse("2024-05-01"),
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2024-01-01"),
                        opprinneligTom = LocalDate.parse("2024-05-01"),
                        gjelderBarn = testdataBarn2.ident,
                        behandling = behandling,
                    ),
                )
            behandlingRepository.save(behandling)
            kjøreStubber(behandling)

            val behandlingDto = behandlingService.henteBehandling(behandling.id!!)

            assertSoftly(behandlingDto) {
                it.inntekter.beregnetInntekter shouldHaveSize 3
                val inntekterAlle =
                    it.inntekter.beregnetInntekter.find { it.inntektGjelderBarnIdent == null }
                val inntekterBarn1 =
                    it.inntekter.beregnetInntekter.hentInntektForBarn(testdataBarn1.ident)
                val inntekterBarn2 =
                    it.inntekter.beregnetInntekter.hentInntektForBarn(testdataBarn2.ident)
                inntekterAlle.shouldNotBeNull()
                inntekterBarn1.shouldNotBeNull()
                inntekterBarn2.shouldNotBeNull()

                assertSoftly(inntekterAlle) {
                    summertInntektListe shouldHaveSize 1
                    summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(1234)
                    summertInntektListe[0].barnetillegg shouldBe null
                    summertInntektListe[0].kontantstøtte shouldBe null
                }
                assertSoftly(inntekterBarn2) {
                    summertInntektListe shouldHaveSize 3
                    summertInntektListe[1].skattepliktigInntekt shouldBe BigDecimal(1234)
                    summertInntektListe[1].barnetillegg shouldBe null
                    summertInntektListe[1].kontantstøtte shouldBe BigDecimal(999)
                }
                assertSoftly(inntekterBarn1) {
                    summertInntektListe shouldHaveSize 3
                    summertInntektListe[1].skattepliktigInntekt shouldBe BigDecimal(1234)
                    summertInntektListe[1].barnetillegg shouldBe BigDecimal(555)
                    summertInntektListe[1].kontantstøtte shouldBe null
                }
            }
        }

        @Test
        @Transactional
        open fun `ytelser skal ikke listes som årsinntekter i DTO`() {
            // gitt
            val behandling = oppretteBehandling()
            behandling.grunnlagSistInnhentet = LocalDateTime.now()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.inntekter =
                mutableSetOf(
                    Inntekt(
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        BigDecimal.valueOf(1234),
                        LocalDate.parse("2023-01-01"),
                        null,
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = LocalDate.parse("2023-09-01"),
                        behandling = behandling,
                    ),
                    Inntekt(
                        Inntektsrapportering.BARNETILLEGG,
                        BigDecimal.valueOf(2450),
                        LocalDate.parse("2023-01-01"),
                        null,
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = null,
                        behandling = behandling,
                        gjelderBarn = testdataBarn1.ident,
                    ),
                    Inntekt(
                        Inntektsrapportering.KONTANTSTØTTE,
                        BigDecimal.valueOf(3500),
                        LocalDate.parse("2023-01-01"),
                        null,
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = null,
                        behandling = behandling,
                        gjelderBarn = testdataBarn1.ident,
                    ),
                    Inntekt(
                        Inntektsrapportering.UTVIDET_BARNETRYGD,
                        BigDecimal.valueOf(3000),
                        LocalDate.parse("2023-01-01"),
                        null,
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = null,
                        behandling = behandling,
                        gjelderBarn = testdataBarn1.ident,
                    ),
                    Inntekt(
                        Inntektsrapportering.SMÅBARNSTILLEGG,
                        BigDecimal.valueOf(1850),
                        LocalDate.parse("2023-01-01"),
                        null,
                        testdataBM.ident,
                        Kilde.OFFENTLIG,
                        true,
                        opprinneligFom = LocalDate.parse("2023-01-01"),
                        opprinneligTom = null,
                        behandling = behandling,
                        gjelderBarn = null,
                    ),
                )
            testdataManager.lagreBehandling(behandling)

            // Setter innhentetdato til før innhentetdato i stub-input-fil hente-grunnlagrespons.json
            kjøreStubber(behandling)

            // hvis
            val behandlingDto = behandlingService.henteBehandling(behandling.id!!)

            // så
            val ytelser =
                setOf(
                    Inntektsrapportering.BARNETILLEGG,
                    Inntektsrapportering.KONTANTSTØTTE,
                    Inntektsrapportering.SMÅBARNSTILLEGG,
                    Inntektsrapportering.UTVIDET_BARNETRYGD,
                )

            assertSoftly {
                behandlingDto.inntekter.årsinntekter
                    .filter { ytelser.contains(it.rapporteringstype) }
                    .size shouldBe 0
                behandlingDto.inntekter.barnetillegg.size shouldBe 1
                behandlingDto.inntekter.kontantstøtte.size shouldBe 1
                behandlingDto.inntekter.småbarnstillegg.size shouldBe 1
                behandlingDto.inntekter.utvidetBarnetrygd.size shouldBe 1
                behandlingDto.inntekter.årsinntekter
                    .filter { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.rapporteringstype }
                    .size shouldBe
                    1
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere lista over ikke-aktiverte endringer i grunnlagsdata dersom grunnlag har blitt oppdatert`() {
            // gitt
            val behandling = oppretteBehandling()

            // skrur av grunnlagsinnhenting
            behandling.grunnlagSistInnhentet = LocalDateTime.now()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")

            kjøreStubber(behandling)

            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = LocalDateTime.now().minusDays(5),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                Sivilstand(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = LocalDate.now().minusYears(13),
                                    periodeTom = null,
                                    sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                                ),
                            ),
                        ),
                ),
            )

            behandling.grunnlag.add(
                Grunnlag(
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                Sivilstand(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = LocalDate.now().minusYears(15),
                                    periodeTom = null,
                                    sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                                ),
                            ),
                        ),
                ),
            )

            // hvis
            val behandlingDto = behandlingService.henteBehandling(behandling.id!!)

            // så
            assertSoftly {
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata shouldNotBe null
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata.inntekter.årsinntekter shouldHaveSize 0
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata.husstandsmedlem shouldHaveSize 0
            }
        }
    }

    @Nested
    open inner class OppretteBehandling {
        @Test
        @Transactional
        open fun `skal opprette en forskuddsbehandling hvis det finnes behandling med samme søknadsid men som er slettet`() {
            val søknadsid = 123213L
            val actualBehandling = behandlingRepository.save(prepareBehandling(søknadsid))
            behandlingRepository.delete(actualBehandling)
            stubUtils.stubHenteGrunnlag()
            stubUtils.stubHentePersoninfo(personident = testdataBarn1.ident)
            stubKodeverkProvider()
            kjøreStubber(actualBehandling)

            val opprettetBehandling =
                behandlingService.opprettBehandling(
                    OpprettBehandlingRequest(
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        søktFomDato = LocalDate.parse("2023-01-01"),
                        mottattdato = LocalDate.parse("2023-01-01"),
                        søknadFra = SøktAvType.BIDRAGSMOTTAKER,
                        saksnummer = "12312",
                        søknadsid = søknadsid,
                        behandlerenhet = "1233",
                        stønadstype = Stønadstype.FORSKUDD,
                        engangsbeløpstype = null,
                        roller =
                            setOf(
                                OpprettRolleDto(
                                    rolletype = Rolletype.BARN,
                                    ident = Personident(testdataBarn1.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                                OpprettRolleDto(
                                    rolletype = Rolletype.BIDRAGSMOTTAKER,
                                    ident = Personident(testdataBM.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                            ),
                    ),
                )

            opprettetBehandling.id shouldNotBe actualBehandling.id

            val opprettetBehandlingAfter =
                behandlingService.hentBehandlingById(opprettetBehandling.id)

            opprettetBehandlingAfter.stonadstype shouldBe Stønadstype.FORSKUDD
            opprettetBehandlingAfter.virkningstidspunkt shouldBe LocalDate.parse("2023-01-01")
            opprettetBehandlingAfter.årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            opprettetBehandlingAfter.roller shouldHaveSize 2
            opprettetBehandlingAfter.soknadsid shouldBe søknadsid
            opprettetBehandlingAfter.saksnummer shouldBe "12312"
            opprettetBehandlingAfter.behandlerEnhet shouldBe "1233"
        }

        @Test
        @Transactional
        open fun `skal opprette en særbidrag behandling`() {
            val søknadsid = 123213L
            stubUtils.stubHenteGrunnlag()
            stubUtils.stubHentePersoninfo(personident = testdataBarn1.ident)
            stubKodeverkProvider()

            val opprettetBehandling =
                behandlingService.opprettBehandling(
                    OpprettBehandlingRequest(
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        søktFomDato = LocalDate.parse("2023-01-01"),
                        mottattdato = LocalDate.parse("2023-01-01"),
                        søknadFra = SøktAvType.BIDRAGSPLIKTIG,
                        saksnummer = "12312",
                        søknadsid = søknadsid,
                        behandlerenhet = "1233",
                        stønadstype = null,
                        engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
                        kategori =
                            OpprettKategoriRequestDto(
                                Særbidragskategori.KONFIRMASJON.name,
                            ),
                        roller =
                            setOf(
                                OpprettRolleDto(
                                    rolletype = Rolletype.BARN,
                                    ident = Personident(testdataBarn1.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                                OpprettRolleDto(
                                    rolletype = Rolletype.BIDRAGSMOTTAKER,
                                    ident = Personident(testdataBM.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                                OpprettRolleDto(
                                    rolletype = Rolletype.BIDRAGSPLIKTIG,
                                    ident = Personident(testdataBP.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                            ),
                    ),
                )

            val opprettetBehandlingAfter =
                behandlingService.hentBehandlingById(opprettetBehandling.id)

            opprettetBehandlingAfter.stonadstype shouldBe null
            opprettetBehandlingAfter.engangsbeloptype shouldBe Engangsbeløptype.SÆRBIDRAG
            opprettetBehandlingAfter.særbidragKategori shouldBe Særbidragskategori.KONFIRMASJON
            opprettetBehandlingAfter.kategoriBeskrivelse.shouldBeNull()
            opprettetBehandlingAfter.virkningstidspunkt shouldBe LocalDate.now().withDayOfMonth(1)
            opprettetBehandlingAfter.årsak shouldBe null
            opprettetBehandlingAfter.roller shouldHaveSize 3
            opprettetBehandlingAfter.soknadsid shouldBe søknadsid
            opprettetBehandlingAfter.saksnummer shouldBe "12312"
            opprettetBehandlingAfter.behandlerEnhet shouldBe "1233"
            opprettetBehandlingAfter.utgift shouldBe null
        }

        @Test
        @Transactional
        open fun `skal opprette en særbidrag behandling med kategori ANNET`() {
            val søknadsid = 123213L
            stubUtils.stubHenteGrunnlag()
            stubUtils.stubHentePersoninfo(personident = testdataBarn1.ident)
            stubKodeverkProvider()

            val opprettetBehandling =
                behandlingService.opprettBehandling(
                    OpprettBehandlingRequest(
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        søktFomDato = LocalDate.parse("2023-01-01"),
                        mottattdato = LocalDate.parse("2023-01-01"),
                        søknadFra = SøktAvType.BIDRAGSPLIKTIG,
                        saksnummer = "12312",
                        søknadsid = søknadsid,
                        behandlerenhet = "1233",
                        stønadstype = null,
                        engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
                        kategori =
                            OpprettKategoriRequestDto(
                                Særbidragskategori.ANNET.name,
                                "Dette er test",
                            ),
                        roller =
                            setOf(
                                OpprettRolleDto(
                                    rolletype = Rolletype.BARN,
                                    ident = Personident(testdataBarn1.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                                OpprettRolleDto(
                                    rolletype = Rolletype.BIDRAGSMOTTAKER,
                                    ident = Personident(testdataBM.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                                OpprettRolleDto(
                                    rolletype = Rolletype.BIDRAGSPLIKTIG,
                                    ident = Personident(testdataBP.ident),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                            ),
                    ),
                )

            val opprettetBehandlingAfter =
                behandlingService.hentBehandlingById(opprettetBehandling.id)

            opprettetBehandlingAfter.stonadstype shouldBe null
            opprettetBehandlingAfter.engangsbeloptype shouldBe Engangsbeløptype.SÆRBIDRAG
            opprettetBehandlingAfter.særbidragKategori shouldBe Særbidragskategori.ANNET
            opprettetBehandlingAfter.kategoriBeskrivelse shouldBe "Dette er test"
            opprettetBehandlingAfter.virkningstidspunkt shouldBe LocalDate.now().withDayOfMonth(1)
            opprettetBehandlingAfter.årsak shouldBe null
            opprettetBehandlingAfter.roller shouldHaveSize 3
            opprettetBehandlingAfter.soknadsid shouldBe søknadsid
            opprettetBehandlingAfter.saksnummer shouldBe "12312"
            opprettetBehandlingAfter.behandlerEnhet shouldBe "1233"
            opprettetBehandlingAfter.utgift shouldBe null
        }

        @Test
        @Transactional
        open fun `skal ikke opprette en behandling hvis det allerede finnes med samme søknadsid`() {
            val søknadsid = 123213L
            val actualBehandling = behandlingRepository.save(prepareBehandling(søknadsid))

            kjøreStubber(actualBehandling)
            val opprettetBehandling =
                behandlingService.opprettBehandling(
                    OpprettBehandlingRequest(
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        søktFomDato = LocalDate.parse("2023-01-01"),
                        mottattdato = LocalDate.parse("2023-01-01"),
                        søknadFra = SøktAvType.BIDRAGSMOTTAKER,
                        saksnummer = "12312",
                        søknadsid = søknadsid,
                        behandlerenhet = "1233",
                        stønadstype = Stønadstype.FORSKUDD,
                        engangsbeløpstype = null,
                        roller =
                            setOf(
                                OpprettRolleDto(
                                    rolletype = Rolletype.BARN,
                                    ident = Personident("213"),
                                    fødselsdato = LocalDate.parse("2005-01-01"),
                                ),
                            ),
                    ),
                )

            opprettetBehandling.id shouldBe actualBehandling.id
        }

        @Test
        fun `skal opprette en behandling med inntekter`() {
            val behandling = prepareBehandling()

            behandling.inntekter =
                mutableSetOf(
                    Inntekt(
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        BigDecimal.valueOf(555.55),
                        LocalDate.now().minusMonths(4),
                        null,
                        "ident",
                        Kilde.OFFENTLIG,
                        true,
                        behandling = behandling,
                    ),
                )

            val actualBehandling = behandlingRepository.save(behandling)

            assertNotNull(actualBehandling.id)

            val actualBehandlingFetched =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(Stønadstype.FORSKUDD, actualBehandlingFetched.stonadstype)
            assertEquals(1, actualBehandlingFetched.inntekter.size)
            assertEquals(
                BigDecimal.valueOf(555.55),
                actualBehandlingFetched.inntekter
                    .iterator()
                    .next()
                    .belop,
            )
        }
    }

    @Nested
    open inner class SletteBehandling {
        @Test
        fun `delete behandling`() {
            val behandling = oppretteBehandling()
            behandlingRepository.delete(behandling)

            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.hentBehandlingById(behandling.id!!)
            }
        }
    }

    @Nested
    open inner class AktivereGrunnlag {
        @Test
        @Transactional
        open fun `skal aktivere sivilstand`() {
            // gitt
            val b = testdataManager.oppretteBehandling(false)
            kjøreStubber(b)

            // ny sivilstand
            b.grunnlag.add(
                Grunnlag(
                    behandling = b,
                    innhentet = LocalDateTime.now(),
                    erBearbeidet = false,
                    rolle = b.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                SivilstandGrunnlagDto(
                                    bekreftelsesdato = b.virkningstidspunktEllerSøktFomDato.minusYears(15),
                                    gyldigFom = b.virkningstidspunktEllerSøktFomDato.minusYears(15),
                                    historisk = false,
                                    master = "Freg",
                                    personId = b.bidragsmottaker!!.ident!!,
                                    registrert = b.virkningstidspunktEllerSøktFomDato.minusYears(8).atStartOfDay(),
                                    type = SivilstandskodePDL.GIFT,
                                ),
                            ),
                        ),
                ),
            )

            b.grunnlag.add(
                Grunnlag(
                    behandling = b,
                    innhentet = LocalDateTime.now(),
                    erBearbeidet = true,
                    rolle = b.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                Sivilstand(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = LocalDate.now().minusYears(15),
                                    periodeTom = null,
                                    sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                                ),
                            ),
                        ),
                ),
            )

            val ikkeAktiverteEndringerIGrunnlagsdata = grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(b)

            assertSoftly(ikkeAktiverteEndringerIGrunnlagsdata.sivilstand) {
                it shouldNotBe null
                it!!.grunnlag shouldHaveSize 1
                it.sivilstand shouldHaveSize 1
            }

            // hvis
            val svar =
                behandlingService.aktivereGrunnlag(
                    b.id!!,
                    AktivereGrunnlagRequestV2(Personident(b.bidragsmottaker!!.ident!!), Grunnlagsdatatype.SIVILSTAND),
                )

            // så
            assertSoftly(svar) {
                it.ikkeAktiverteEndringerIGrunnlagsdata.sivilstand shouldBe null
                it.aktiveGrunnlagsdata.sivilstand shouldNotBe null
                it.aktiveGrunnlagsdata.sivilstand!!.grunnlag shouldHaveSize 1
            }
        }

        @Test
        @Transactional
        open fun `skal aktivere boforhold for barn`() {
            // gitt
            val b = testdataManager.oppretteBehandling(false)
            val personidentBarnBoforholdSkalAktiveresFor = Personident(testdataBarn2.ident)
            kjøreStubber(b)

            val grunnlagHusstandsmedlemmer =
                setOf(
                    RelatertPersonGrunnlagDto(
                        relatertPersonPersonId = testdataBarn1.ident,
                        fødselsdato = testdataBarn1.fødselsdato,
                        erBarnAvBmBp = true,
                        navn = "Lyrisk Sopp",
                        partPersonId = b.bidragsmottaker!!.ident!!,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = LocalDate.parse("2023-01-01"),
                                    periodeTil = LocalDate.parse("2023-05-31"),
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        relatertPersonPersonId = personidentBarnBoforholdSkalAktiveresFor.verdi,
                        fødselsdato = testdataBarn2.fødselsdato,
                        erBarnAvBmBp = true,
                        navn = "Lyrisk Sopp",
                        partPersonId = b.bidragsmottaker!!.ident!!,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = LocalDate.parse("2023-01-01"),
                                    periodeTil = LocalDate.parse("2023-05-31"),
                                ),
                                BorISammeHusstandDto(
                                    periodeFra = LocalDate.parse("2023-08-01"),
                                    periodeTil = null,
                                ),
                            ),
                    ),
                )
            b.grunnlag.add(
                Grunnlag(
                    behandling = b,
                    innhentet = LocalDateTime.now(),
                    erBearbeidet = false,
                    rolle = b.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(grunnlagHusstandsmedlemmer),
                ),
            )

            val boforholdPeriodisert =
                BoforholdApi.beregnBoforholdBarnV2(
                    b.virkningstidspunktEllerSøktFomDato,
                    grunnlagHusstandsmedlemmer.tilBoforholdBarnRequest(b),
                )

            boforholdPeriodisert
                .filter { it.relatertPersonPersonId != null && it.relatertPersonPersonId == personidentBarnBoforholdSkalAktiveresFor.verdi }
                .groupBy { it.relatertPersonPersonId }
                .forEach {
                    b.grunnlag.add(
                        Grunnlag(
                            behandling = b,
                            innhentet = LocalDateTime.now(),
                            erBearbeidet = true,
                            rolle = b.bidragsmottaker!!,
                            type = Grunnlagsdatatype.BOFORHOLD,
                            gjelder = it.key,
                            data = commonObjectmapper.writeValueAsString(it.value),
                        ),
                    )
                }

            val ikkeAktiverteEndringerIGrunnlagsdata = grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(b)

            assertSoftly(ikkeAktiverteEndringerIGrunnlagsdata.husstandsmedlem) {
                it shouldHaveSize 1
                it.first().perioder shouldHaveSize 3
            }

            // hvis
            val svar =
                behandlingService.aktivereGrunnlag(
                    b.id!!,
                    AktivereGrunnlagRequestV2(personidentBarnBoforholdSkalAktiveresFor, Grunnlagsdatatype.BOFORHOLD),
                )

            // så
            assertSoftly(svar) {
                it.ikkeAktiverteEndringerIGrunnlagsdata.husstandsmedlem shouldHaveSize 0
                it.aktiveGrunnlagsdata.husstandsmedlem shouldHaveSize 2
            }
        }
    }

    @Nested
    open inner class OppdaterRoller {
        @Test
        fun `legge til flere roller`() {
            val b = oppretteBehandling()
            kjøreStubber(b)

            val response =
                behandlingService.oppdaterRoller(
                    b.id!!,
                    listOf(
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident("newident"),
                            null,
                            fødselsdato = LocalDate.now().minusMonths(144),
                        ),
                    ),
                )
            response.status shouldBe OppdaterRollerStatus.ROLLER_OPPDATERT
            val behandlingEtter = behandlingService.hentBehandlingById(b.id!!)
            behandlingEtter.roller shouldHaveSize 4
            behandlingEtter.husstandsmedlem shouldHaveSize 1
            behandlingEtter.husstandsmedlem.first().ident shouldBe "newident"
            behandlingEtter.husstandsmedlem.first().kilde shouldBe Kilde.OFFENTLIG
        }

        @Test
        fun `skal oppdatere roller og slette behandling hvis alle barn er slettet`() {
            val b = oppretteBehandling()
            kjøreStubber(b)

            val response =
                behandlingService.oppdaterRoller(
                    b.id!!,
                    listOf(
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident(b.søknadsbarn.first().ident!!),
                            null,
                            fødselsdato = LocalDate.now().minusMonths(144),
                            true,
                        ),
                    ),
                )

            response.status shouldBe OppdaterRollerStatus.BEHANDLING_SLETTET
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.hentBehandlingById(b.id!!)
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere roller`() {
            // gitt
            val identOriginaltMedISaken = "1111"
            val identOriginaltIkkeMedISaken = "111123"
            val behandling = oppretteBehandling()
            kjøreStubber(behandling)

            behandling.roller =
                mutableSetOf(
                    Rolle(
                        behandling,
                        ident = identOriginaltMedISaken,
                        rolletype = Rolletype.BARN,
                        foedselsdato = LocalDate.parse("2021-01-01"),
                    ),
                    Rolle(
                        behandling,
                        ident = "123123123",
                        rolletype = Rolletype.BARN,
                        foedselsdato = LocalDate.parse("2021-01-01"),
                    ),
                )
            behandling.husstandsmedlem =
                mutableSetOf(
                    Husstandsmedlem(
                        behandling,
                        kilde = Kilde.MANUELL,
                        ident = identOriginaltIkkeMedISaken,
                        fødselsdato = LocalDate.parse("2021-01-01"),
                    ),
                    Husstandsmedlem(
                        behandling,
                        kilde = Kilde.OFFENTLIG,
                        ident = identOriginaltMedISaken,
                        fødselsdato = LocalDate.parse("2021-01-01"),
                    ),
                )

            behandlingRepository.save(behandling)
            // hvis
            val response =
                behandlingService.oppdaterRoller(
                    behandling.id!!,
                    listOf(
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident(identOriginaltMedISaken),
                            null,
                            fødselsdato = LocalDate.now().minusMonths(144),
                            true,
                        ),
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident(identOriginaltIkkeMedISaken),
                            null,
                            fødselsdato = LocalDate.now().minusMonths(144),
                        ),
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident("1111234"),
                            null,
                            fødselsdato = LocalDate.now().minusMonths(144),
                        ),
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident("5555566666"),
                            "Person som ikke finnes",
                            fødselsdato = LocalDate.now().minusMonths(144),
                            true,
                        ),
                    ),
                )
            val behandlingEtter = behandlingService.hentBehandlingById(behandling.id!!)
            response.status shouldBe OppdaterRollerStatus.ROLLER_OPPDATERT
            behandlingEtter.søknadsbarn shouldHaveSize 3
            behandlingEtter.husstandsmedlem shouldHaveSize 3
            behandlingEtter.husstandsmedlem.find { it.ident == identOriginaltMedISaken }!!.kilde shouldBe Kilde.OFFENTLIG
            behandlingEtter.husstandsmedlem.find { it.ident == identOriginaltIkkeMedISaken }!!.kilde shouldBe Kilde.MANUELL
            behandlingEtter.husstandsmedlem.find { it.ident == "1111234" }!!.kilde shouldBe Kilde.OFFENTLIG
        }

        @Test
        @Transactional
        open fun `skal ikke oppdatere roller hvis vedtak er fattet for behandling`() {
            // gitt
            val identOriginaltMedISaken = "1111"
            val behandling = oppretteBehandling()
            kjøreStubber(behandling)

            behandling.vedtaksid = 12

            behandlingRepository.save(behandling)

            val exception =
                assertThrows<HttpClientErrorException> {
                    behandlingService.oppdaterRoller(
                        behandling.id!!,
                        listOf(
                            OpprettRolleDto(
                                Rolletype.BARN,
                                Personident(identOriginaltMedISaken),
                                null,
                                fødselsdato = LocalDate.now().minusMonths(144),
                                true,
                            ),
                        ),
                    )
                }
            exception.message shouldContain "Kan ikke oppdatere behandling hvor vedtak er fattet"
        }
    }

    @Nested
    open inner class OppdatereVirkningstidspunktTest {
        @Test
        @Transactional
        open fun `skal oppdatere ikke aktivert sivilstand ved endring av virkningsdato fremover i tid`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubPersonConsumer()
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            val nyttSivilstandsgrunnlag =
                setOf(
                    SivilstandGrunnlagDto(
                        bekreftelsesdato = null,
                        gyldigFom = LocalDate.now().minusMonths(2),
                        historisk = false,
                        master = "Freg",
                        personId = behandling.bidragsmottaker!!.ident,
                        registrert = LocalDateTime.now().minusMonths(2),
                        type = SivilstandskodePDL.GIFT,
                    ),
                )
            behandling.grunnlag.add(
                Grunnlag(
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    erBearbeidet = false,
                    data = tilJson(nyttSivilstandsgrunnlag),
                ),
            )

            val periodisertSivilstandshistorikk =
                SivilstandApi.beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    nyttSivilstandsgrunnlag.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.foedselsdato),
                )
            behandling.grunnlag.add(
                Grunnlag(
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    erBearbeidet = true,
                    data = tilJson(periodisertSivilstandshistorikk),
                ),
            )

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }) { g ->
                g shouldHaveSize 4
                g.filter { it.aktiv == null } shouldHaveSize 2
            }

            val nyVirkningsdato = behandling.virkningstidspunkt!!.plusMonths(5)

            // hvis
            behandlingService.oppdatereVirkningstidspunkt(
                behandling.id!!,
                OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningsdato),
            )

            // så
            entityManager.flush()
            entityManager.refresh(behandling)
            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }) { s ->
                s shouldHaveSize 4
                jsonListeTilObjekt<Sivilstand>(
                    s.first { it.erBearbeidet && it.aktiv == null }.data,
                ).first().periodeFom shouldBeEqual nyVirkningsdato
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere ikke aktivert sivilstand ved endring av virkningsdato bakover i tid`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubPersonConsumer()
            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            val nyttSivilstandsgrunnlag =
                setOf(
                    SivilstandGrunnlagDto(
                        bekreftelsesdato = null,
                        gyldigFom = LocalDate.now().minusMonths(2),
                        historisk = false,
                        master = "Freg",
                        personId = behandling.bidragsmottaker!!.ident,
                        registrert = LocalDateTime.now().minusMonths(2),
                        type = SivilstandskodePDL.GIFT,
                    ),
                )
            behandling.grunnlag.add(
                Grunnlag(
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    erBearbeidet = false,
                    data = tilJson(nyttSivilstandsgrunnlag),
                ),
            )

            val periodisertSivilstandshistorikk =
                SivilstandApi.beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    nyttSivilstandsgrunnlag.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.foedselsdato),
                )
            behandling.grunnlag.add(
                Grunnlag(
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    erBearbeidet = true,
                    data = tilJson(periodisertSivilstandshistorikk),
                ),
            )

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }) { g ->
                g shouldHaveSize 4
                g.filter { it.aktiv == null } shouldHaveSize 2
            }

            val nyVirkningsdato = behandling.virkningstidspunkt!!.minusMonths(5)

            // hvis
            behandlingService.oppdatereVirkningstidspunkt(
                behandling.id!!,
                OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningsdato),
            )

            // så
            entityManager.flush()
            entityManager.refresh(behandling)
            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }) { s ->
                s shouldHaveSize 4
                jsonListeTilObjekt<Sivilstand>(
                    s.first { it.erBearbeidet && it.aktiv == null }.data,
                ).minByOrNull { it.periodeFom }!!.periodeFom shouldBeEqual nyVirkningsdato
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere virkningstidspunkt og oppdatere gjeldende aktiverte boforhold og sivilstand`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false, false, false)
            stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
            stubPersonConsumer()

            grunnlagService.oppdatereGrunnlagForBehandling(behandling)

            val nyVirkningsdato = behandling.virkningstidspunkt!!.minusMonths(1)
            val request = OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningsdato)

            assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type }) { g ->
                g shouldHaveSize 3
                g.filter { it.aktiv != null } shouldHaveSize 3
                g.filter { !it.erBearbeidet } shouldHaveSize 1
                jsonListeTilObjekt<BoforholdResponse>(g.first { it.erBearbeidet }.data)
                    .first()
                    .periodeFom shouldBeEqual behandling.virkningstidspunkt!!
            }

            // hvis
            behandlingService.oppdatereVirkningstidspunkt(behandling.id!!, request)

            // så
            entityManager.flush()
            entityManager.refresh(behandling)
            val boforholdsgrunnlag = behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type }
            assertSoftly(boforholdsgrunnlag) { g ->
                g shouldHaveSize 3
                g.filter { !it.erBearbeidet } shouldHaveSize 1
                g.filter { it.aktiv != null } shouldHaveSize 3
                jsonListeTilObjekt<BoforholdResponse>(g.filter { it.erBearbeidet }.maxBy { it.aktiv!! }.data)
                    .first()
                    .periodeFom shouldBeEqual nyVirkningsdato
            }
            val sivilstandgrunnlag = behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }
            assertSoftly(sivilstandgrunnlag) { g ->
                g shouldHaveSize 2
                g.filter { !it.erBearbeidet } shouldHaveSize 1
                g.filter { it.aktiv != null } shouldHaveSize 2
                jsonListeTilObjekt<Sivilstand>(g.filter { it.erBearbeidet }.maxBy { it.aktiv!! }.data)
                    .first()
                    .periodeFom shouldBeEqual nyVirkningsdato
            }
        }
    }

    @Test
    fun `delete behandling rolle`() {
        val behandling = oppretteBehandling()

        assertEquals(3, behandling.roller.size)
        behandling.roller.removeIf { it.rolletype == Rolletype.BARN }

        behandlingRepository.save(behandling)

        val updatedBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        assertEquals(2, updatedBehandling.roller.size)

        val realCount =
            entityManager
                .createNativeQuery("select count(*) from rolle r where r.behandling_id = " + behandling.id)
                .singleResult

        val deletedCount =
            entityManager
                .createNativeQuery(
                    "select count(*) from rolle r where r.behandling_id = ${behandling.id} and r.deleted = true",
                ).singleResult

        assertEquals(3L, realCount)
        assertEquals(1L, deletedCount)
    }

    companion object {
        fun prepareBehandling(søknadsid: Long = 123123): Behandling {
            val behandling =
                Behandling(
                    Vedtakstype.FASTSETTELSE,
                    YearMonth.now().atDay(1),
                    YearMonth.now().atEndOfMonth(),
                    LocalDate.now(),
                    "1900000",
                    søknadsid,
                    null,
                    "1234",
                    "Z9999",
                    "Navn Navnesen",
                    "bisys",
                    SøktAvType.BIDRAGSMOTTAKER,
                    Stønadstype.FORSKUDD,
                    null,
                )
            val createRoller = prepareRoles(behandling)
            val roller =
                HashSet(
                    createRoller.map {
                        Rolle(
                            behandling,
                            it.rolletype,
                            it.ident,
                            it.foedselsdato,
                            it.opprettet,
                        )
                    },
                )

            behandling.roller.addAll(roller)
            return behandling
        }

        fun prepareRoles(behandling: Behandling): Set<Rolle> =
            setOf(
                testdataBM.tilRolle(behandling),
                testdataBP.tilRolle(behandling),
                testdataBarn1.tilRolle(behandling),
            )
    }

    fun oppretteBehandling(): Behandling {
        val behandling = prepareBehandling()

        return behandlingRepository.save(behandling)
    }

    private fun kjøreStubber(behandling: Behandling) {
        stubSjablonProvider()
        stubKodeverkProvider()
        stubUtils.stubbeGrunnlagsinnhentingForBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        stubUtils.stubKodeverkSkattegrunnlag()
        stubUtils.stubKodeverkLønnsbeskrivelse()
        stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
        stubUtils.stubKodeverkYtelsesbeskrivelser()
        stubUtils.stubKodeverkPensjonsbeskrivelser()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
    }
}
