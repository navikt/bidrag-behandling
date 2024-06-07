package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerStatus
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequest
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.utils.hentInntektForBarn
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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

        @Disabled("Wiremock issues - OK alene, feiler i fellesskap med andre")
        @Test
        @Transactional
        open fun `ytelser skal ikke listes som årsinntekter i DTO`() {
            // gitt
            val behandling = oppretteBehandling()
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
                )
            testdataManager.lagreBehandling(behandling)

            entityManager.refresh(behandling)

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
                behandlingDto.inntekter.årsinntekter.filter { ytelser.contains(it.rapporteringstype) }.size shouldBe 0
                behandlingDto.inntekter.barnetillegg.size shouldBe 0
                behandlingDto.inntekter.kontantstøtte.size shouldBe 0
                behandlingDto.inntekter.småbarnstillegg.size shouldBe 1
                behandlingDto.inntekter.utvidetBarnetrygd.size shouldBe 1
                behandlingDto.inntekter.årsinntekter.filter { Inntektsrapportering.AINNTEKT_BEREGNET_3MND == it.rapporteringstype }.size shouldBe 3
            }
        }

        @Test
        @Disabled("Wiremock issues - OK alene, feiler i fellesskap med andre")
        @Transactional
        open fun `skal oppdatere lista over ikke-aktiverte endringer i grunnlagsdata dersom grunnlag har blitt oppdatert`() {
            // gitt
            val behandling = oppretteBehandling()

            // Setter innhentetdato til før innhentetdato i stub-input-fil hente-grunnlagrespons.json
            oppretteOgLagreGrunnlag(behandling)
            kjøreStubber(behandling)

            // hvis
            val behandlingDto = behandlingService.henteBehandling(behandling.id!!)
            // så
            assertSoftly {
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata shouldNotBe null
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata.inntekter.årsinntekter shouldHaveSize 0
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata.husstandsbarn shouldHaveSize 0
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
        open fun `skal opprette en særlige utgifter behandling`() {
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
                        engangsbeløpstype = Engangsbeløptype.SÆRTILSKUDD_KONFIRMASJON,
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
            opprettetBehandlingAfter.engangsbeloptype shouldBe Engangsbeløptype.SÆRTILSKUDD_KONFIRMASJON
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
                actualBehandlingFetched.inntekter.iterator().next().belop,
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
            behandlingEtter.husstandsbarn shouldHaveSize 1
            behandlingEtter.husstandsbarn.first().ident shouldBe "newident"
            behandlingEtter.husstandsbarn.first().kilde shouldBe Kilde.OFFENTLIG
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
            behandling.husstandsbarn =
                mutableSetOf(
                    Husstandsbarn(
                        behandling,
                        kilde = Kilde.MANUELL,
                        ident = identOriginaltIkkeMedISaken,
                        fødselsdato = LocalDate.parse("2021-01-01"),
                    ),
                    Husstandsbarn(
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
            behandlingEtter.husstandsbarn shouldHaveSize 3
            behandlingEtter.husstandsbarn.find { it.ident == identOriginaltMedISaken }!!.kilde shouldBe Kilde.OFFENTLIG
            behandlingEtter.husstandsbarn.find { it.ident == identOriginaltIkkeMedISaken }!!.kilde shouldBe Kilde.MANUELL
            behandlingEtter.husstandsbarn.find { it.ident == "1111234" }!!.kilde shouldBe Kilde.OFFENTLIG
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
    open inner class OppdatereBehandling {
        @Test
        fun `skal oppdatere virkningstidspunkt data`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"

            val createdBehandling = behandlingRepository.save(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.årsak)

            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    virkningstidspunkt =
                        OppdatereVirkningstidspunkt(
                            årsak = VirkningstidspunktÅrsakstype.FRA_BARNETS_FØDSEL,
                            virkningstidspunkt = null,
                            notat =
                                OppdaterNotat(
                                    notat,
                                ),
                        ),
                ),
            )

            val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(VirkningstidspunktÅrsakstype.FRA_BARNETS_FØDSEL, updatedBehandling.årsak)
            assertEquals(notat, updatedBehandling.virkningstidspunktbegrunnelseKunINotat)
        }

        @Test
        fun `skal caste 404 exception hvis behandlingen ikke er der - oppdater`() {
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.oppdaterBehandling(
                    1234,
                    OppdaterBehandlingRequestV2(
                        virkningstidspunkt =
                            OppdatereVirkningstidspunkt(
                                notat =
                                    OppdaterNotat(
                                        "New Notat",
                                    ),
                            ),
                    ),
                )
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere boforhold data`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"

            val createdBehandling = behandlingRepository.save(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.årsak)
            assertEquals(0, createdBehandling.husstandsbarn.size)
            assertEquals(0, createdBehandling.sivilstand.size)

            val husstandsBarn =
                setOf(
                    HusstandsbarnDtoV2(
                        null,
                        Kilde.OFFENTLIG,
                        true,
                        emptySet(),
                        ident = "Manuelt",
                        navn = "ident!",
                        fødselsdato = LocalDate.now().minusMonths(156),
                    ),
                )
            val sivilstand =
                setOf(
                    SivilstandDto(
                        null,
                        LocalDate.now(),
                        LocalDate.now(),
                        Sivilstandskode.BOR_ALENE_MED_BARN,
                        Kilde.OFFENTLIG,
                    ),
                )

            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    boforhold =
                        OppdaterBoforholdRequest(
                            husstandsBarn,
                            sivilstand,
                            notat =
                                OppdaterNotat(
                                    kunINotat = notat,
                                ),
                        ),
                ),
            )

            val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(1, updatedBehandling.husstandsbarn.size)
            assertEquals(1, updatedBehandling.sivilstand.size)
            assertEquals(notat, updatedBehandling.boforholdsbegrunnelseKunINotat)
        }

        @Test
        fun `skal oppdatere behandling`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"

            val createdBehandling = behandlingRepository.save(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.årsak)

            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    virkningstidspunkt =
                        OppdatereVirkningstidspunkt(
                            notat =
                                OppdaterNotat(
                                    notat,
                                ),
                        ),
                    inntekter =
                        OppdatereInntekterRequestV2(
                            notat =
                                OppdaterNotat(
                                    notat,
                                ),
                        ),
                    boforhold =
                        OppdaterBoforholdRequest(
                            notat =
                                OppdaterNotat(
                                    notat,
                                ),
                        ),
                ),
            )

            val oppdatertBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(3, oppdatertBehandling.roller.size)
            assertEquals(notat, oppdatertBehandling.virkningstidspunktbegrunnelseKunINotat)
        }

        @Test
        @Transactional
        open fun `skal aktivere valgte nyinnhenta grunnlag`() {
            // gitt
            val behandling = behandlingRepository.save(prepareBehandling())
            kjøreStubber(behandling)

            testdataManager.oppretteOgLagreGrunnlag<AinntektGrunnlagDto>(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
            )

            val opppdatereBehandlingRequest =
                OppdaterBehandlingRequestV2(
                    aktivereGrunnlagForPerson =
                        AktivereGrunnlagRequest(
                            Personident(behandling.bidragsmottaker?.ident!!),
                            setOf(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER),
                        ),
                )

            // hvis
            behandlingService.oppdaterBehandling(behandling.id!!, opppdatereBehandlingRequest)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            oppdatertBehandling.get().grunnlag.first().aktiv shouldNotBe null
        }
    }

    @Nested
    open inner class OppdatereVirkningstidspunktTest {
        @Test
        @Transactional
        open fun `skal oppdatere ikke aktivert sivilstand ved endring av virkningsdato fremover i tid`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false)
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
                    nyttSivilstandsgrunnlag.tilSivilstandRequest(),
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
            val behandling = testdataManager.oppretteBehandling(false)
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
                    nyttSivilstandsgrunnlag.tilSivilstandRequest(),
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
                ).first().periodeFom shouldBeEqual nyVirkningsdato
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere virkningstidspunkt og oppdatere gjeldende aktiverte boforhold og sivilstand`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false)
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
                    .first().periodeFom shouldBeEqual behandling.virkningstidspunkt!!
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
                    .first().periodeFom shouldBeEqual nyVirkningsdato
            }
            val sivilstandgrunnlag = behandling.grunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }
            assertSoftly(sivilstandgrunnlag) { g ->
                g shouldHaveSize 2
                g.filter { !it.erBearbeidet } shouldHaveSize 1
                g.filter { it.aktiv != null } shouldHaveSize 2
                jsonListeTilObjekt<Sivilstand>(g.filter { it.erBearbeidet }.maxBy { it.aktiv!! }.data)
                    .first().periodeFom shouldBeEqual nyVirkningsdato
            }
        }
    }

    @Nested
    open inner class OppdatereInntekter {
        @Test
        fun `skal legge til inntekter manuelt`() {
            val actualBehandling = oppretteBehandling()

            assertNotNull(actualBehandling.id)

            assertEquals(0, actualBehandling.inntekter.size)
            assertNull(actualBehandling.inntektsbegrunnelseKunINotat)

            behandlingService.oppdaterBehandling(
                actualBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    inntekter =
                        OppdatereInntekterRequestV2(
                            oppdatereManuelleInntekter =
                                mutableSetOf(
                                    OppdatereManuellInntekt(
                                        type = Inntektsrapportering.KAPITALINNTEKT,
                                        beløp = BigDecimal.valueOf(4000),
                                        datoFom = LocalDate.now().minusMonths(4),
                                        datoTom = LocalDate.now().plusMonths(4),
                                        ident = Personident("123"),
                                    ),
                                    OppdatereManuellInntekt(
                                        type = Inntektsrapportering.BARNETILLEGG,
                                        beløp = BigDecimal.valueOf(4000),
                                        datoFom = LocalDate.now().minusMonths(4),
                                        datoTom = LocalDate.now().plusMonths(4),
                                        ident = Personident("123"),
                                        gjelderBarn = Personident("1233"),
                                        inntektstype = Inntektstype.BARNETILLEGG_AAP,
                                    ),
                                ),
                            notat =
                                OppdaterNotat(
                                    "Kun i Notat",
                                ),
                        ),
                ),
            )

            val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(2, expectedBehandling.inntekter.size)
            assertEquals("Kun i Notat", expectedBehandling.inntektsbegrunnelseKunINotat)
        }

        @Test
        fun `skal feile ved validering hvis barnetillegg legges til uten gjelder barn`() {
            val actualBehandling = oppretteBehandling()

            val error =
                assertThrows<HttpClientErrorException> {
                    behandlingService.oppdaterBehandling(
                        actualBehandling.id!!,
                        OppdaterBehandlingRequestV2(
                            inntekter =
                                OppdatereInntekterRequestV2(
                                    oppdatereManuelleInntekter =
                                        mutableSetOf(
                                            OppdatereManuellInntekt(
                                                type = Inntektsrapportering.BARNETILLEGG,
                                                beløp = BigDecimal.valueOf(4000),
                                                datoFom = LocalDate.now().minusMonths(4),
                                                datoTom = LocalDate.now().plusMonths(4),
                                                ident = Personident("123"),
                                            ),
                                        ),
                                    notat =
                                        OppdaterNotat(
                                            "Kun i Notat",
                                        ),
                                ),
                        ),
                    )
                }
            error.message shouldContain "Ugyldig data ved oppdatering av inntekter: BARNETILLEGG må ha gyldig ident for gjelder barn, Barnetillegg må ha gyldig inntektstype"
        }

        @Test
        @Transactional
        open fun `skal slette manuelt oppretta inntekter`() {
            stubUtils.stubOpprettForsendelse()

            val actualBehandling = oppretteBehandling()

            assertNotNull(actualBehandling.id)

            assertEquals(0, actualBehandling.inntekter.size)

            behandlingService.oppdaterBehandling(
                actualBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    inntekter =
                        OppdatereInntekterRequestV2(
                            oppdatereManuelleInntekter =
                                mutableSetOf(
                                    OppdatereManuellInntekt(
                                        type = Inntektsrapportering.KAPITALINNTEKT,
                                        beløp = BigDecimal.valueOf(4000),
                                        datoFom = LocalDate.now().minusMonths(4),
                                        datoTom = LocalDate.now().plusMonths(4),
                                        ident = Personident("123"),
                                    ),
                                ),
                            notat =
                                OppdaterNotat(
                                    "not null",
                                ),
                        ),
                ),
            )

            val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)
            entityManager.refresh(expectedBehandling)

            assertEquals(1, expectedBehandling.inntekter.size)
            assertNotNull(expectedBehandling.inntektsbegrunnelseKunINotat)

            behandlingService.oppdaterBehandling(
                actualBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    inntekter =
                        OppdatereInntekterRequestV2(
                            sletteInntekter = expectedBehandling.inntekter.map { it.id!! }.toSet(),
                        ),
                ),
            )

            val expectedBehandlingWithoutInntekter =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(0, expectedBehandlingWithoutInntekter.inntekter.size)
            assertNotNull(expectedBehandlingWithoutInntekter.inntektsbegrunnelseKunINotat)
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
            entityManager.createNativeQuery("select count(*) from rolle r where r.behandling_id = " + behandling.id)
                .singleResult

        val deletedCount =
            entityManager.createNativeQuery(
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

        fun prepareRoles(behandling: Behandling): Set<Rolle> {
            return setOf(
                testdataBM.tilRolle(behandling),
                testdataBP.tilRolle(behandling),
                testdataBarn1.tilRolle(behandling),
            )
        }
    }

    fun oppretteBehandling(): Behandling {
        val behandling = prepareBehandling()

        return behandlingRepository.save(behandling)
    }

    fun oppretteOgLagreGrunnlag(behandling: Behandling) {
        testdataManager.oppretteOgLagreGrunnlag(
            behandling = behandling,
            grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER, true),
            innhentet = LocalDateTime.now(),
            grunnlagsdata =
                SummerteInntekter(
                    versjon = "123",
                    inntekter =
                        listOf(
                            SummertMånedsinntekt(
                                gjelderÅrMåned = YearMonth.now().minusYears(1).withMonth(12),
                                sumInntekt = BigDecimal(45000),
                                inntektPostListe =
                                    listOf(
                                        InntektPost(
                                            beløp = BigDecimal(45000),
                                            inntekstype = Inntektstype.LØNNSINNTEKT,
                                            kode = "lønnFraSmåbrukarlaget",
                                        ),
                                    ),
                            ),
                        ),
                ),
        )

        testdataManager.oppretteOgLagreGrunnlag(
            behandling = behandling,
            grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
            innhentet = LocalDateTime.now(),
            aktiv = LocalDateTime.now(),
            grunnlagsdata =
                SummerteInntekter(
                    versjon = "123",
                    inntekter =
                        listOf(
                            SummertÅrsinntekt(
                                sumInntekt = BigDecimal(388000),
                                inntektRapportering = Inntektsrapportering.LIGNINGSINNTEKT,
                                periode =
                                    ÅrMånedsperiode(
                                        YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                                        YearMonth.now().withMonth(1).atDay(1),
                                    ),
                                inntektPostListe = emptyList(),
                            ),
                        ),
                ),
        )

        testdataManager.oppretteOgLagreGrunnlag(
            behandling,
            Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
            innhentet = LocalDateTime.now(),
            grunnlagsdata =
                SkattepliktigeInntekter(
                    ainntekter =
                        listOf(
                            AinntektGrunnlagDto(
                                ainntektspostListe = emptyList(),
                                personId = behandling.bidragsmottaker?.ident!!,
                                periodeFra = behandling.søktFomDato.withDayOfMonth(1),
                                periodeTil = behandling.søktFomDato.plusMonths(1).withDayOfMonth((1)),
                            ),
                        ),
                ),
        )
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
