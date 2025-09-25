package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.henteNyesteAktiveGrunnlag
import no.nav.bidrag.behandling.database.datamodell.henteNyesteIkkeAktiveGrunnlag
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.underhold.aktivereBarnetilsynHvisIngenEndringerMåAksepteres
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.leggeTilGjeldendeBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggeTilNyttBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.oppretteBarnetilsynGrunnlagDto
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import stubPersonConsumer
import stubUnderholdskostnadRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class UnderholdServiceTest {
    @MockK
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @MockK
    lateinit var personRepository: PersonRepository

    @MockkBean
    lateinit var personConsumer: BidragPersonConsumer

    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var validering: ValiderBeregning

    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockK
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    lateinit var vedtakGrunnlagsmapper: VedtakGrunnlagMapper

    val notatService = NotatService()

    lateinit var behandlingTilGrunnlagMappingV2: BehandlingTilGrunnlagMappingV2

    lateinit var dtomapper: Dtomapper

    lateinit var underholdService: UnderholdService

    @BeforeEach
    fun setup() {
        stubSjablonProvider()
        personConsumer = stubPersonConsumer()
        val personService = PersonService(personConsumer)

        every { barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(any(), any()) } returns emptySet()
        behandlingTilGrunnlagMappingV2 =
            BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val beregnBarnebidragApi = BeregnBarnebidragApi()
        vedtakGrunnlagsmapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                ValiderBeregning(),
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
        dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validering,
                validerBehandlingService,
                vedtakGrunnlagsmapper,
                beregnBarnebidragApi,
            )
        underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                personService,
            )

        stubUnderholdskostnadRepository(underholdskostnadRepository)
    }

    @Nested
    @DisplayName("Tester sletting fra underholdskostnad")
    open inner class Slette {
        @Test
        open fun `skal slette tilleggsstønad fra underholdskostnad`() {
            // gitt
            val universalid = 1L
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )
            val underholdskostnad = behandling.underholdskostnader.first()

            val tilleggsstønad =
                Tilleggsstønad(
                    id = universalid,
                    underholdskostnad = underholdskostnad,
                    dagsats =
                        BigDecimal(
                            350,
                        ),
                    fom = LocalDate.now(),
                )
            underholdskostnad.tilleggsstønad.add(tilleggsstønad)
            behandling.underholdskostnader.add(underholdskostnad)
            val request = SletteUnderholdselement(idUnderhold = 1, idElement = 1, Underholdselement.TILLEGGSSTØNAD)

            val uFørSleting = behandling.underholdskostnader.find { it.id == universalid }
            uFørSleting.shouldNotBeNull()
            uFørSleting.tilleggsstønad.shouldNotBeEmpty()

            // hvis
            underholdService.sletteFraUnderhold(behandling, request)

            // så
            val u = behandling.underholdskostnader.find { it.id == universalid }
            u.shouldNotBeNull()
            u.tilleggsstønad.shouldBeEmpty()
        }

        @Test
        open fun `skal slette andre barn`() {
            // gitt
            val universalid = 1L
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barn = Person(id = 10, navn = "Generalen", fødselsdato = LocalDate.now())
            val underholdskostnadTilBarnSomSlettes = Underholdskostnad(id = 100, behandling = behandling, person = barn)
            behandling.underholdskostnader.add(underholdskostnadTilBarnSomSlettes)

            notatService.oppdatereNotat(
                behandling,
                NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
                "Begrunnelse for andre barn",
                behandling.bidragsmottaker!!,
            )

            val request = SletteUnderholdselement(idUnderhold = 100, idElement = 10, Underholdselement.BARN)

            every { underholdskostnadRepository.deleteById(underholdskostnadTilBarnSomSlettes.id!!) } returns Unit
            every { personRepository.deleteById(barn.id!!) } returns Unit

            assertSoftly(behandling.underholdskostnader.find { it.id == underholdskostnadTilBarnSomSlettes.id!! }) {
                shouldNotBeNull()
                person?.id shouldBe barn.id
            }

            assertSoftly(behandling.notater.find { it.rolle == behandling.bidragsmottaker }) {
                shouldNotBeNull()
                innhold shouldBe "Begrunnelse for andre barn"
            }

            // hvis
            underholdService.sletteFraUnderhold(behandling, request)

            // så
            val u = behandling.underholdskostnader.find { it.id == universalid }
            u.shouldNotBeNull()
            u.tilleggsstønad.shouldBeEmpty()

            behandling.underholdskostnader.find { it.id == underholdskostnadTilBarnSomSlettes.id!! }.shouldBeNull()

            behandling.notater.find { it.rolle == behandling.bidragsmottaker }.shouldBeNull()
        }

        @Test
        open fun `skal ikke slette notat ved sletting av underhold så lenge det finnes andre barn`() {
            // gitt
            val universalid = 1L
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val generalen = Person(id = 10, navn = "Generalen", fødselsdato = LocalDate.now())
            val kostnadForUnderholdAvGeneralen =
                Underholdskostnad(id = 100, behandling = behandling, person = generalen)
            behandling.underholdskostnader.add(kostnadForUnderholdAvGeneralen)

            val flaggkommandøren = Person(id = 11, navn = "Flaggkommandøren", fødselsdato = LocalDate.now())
            val kostnadForUnderholdAvFlaggkommandøren =
                Underholdskostnad(id = 101, behandling = behandling, person = flaggkommandøren)
            behandling.underholdskostnader.add(kostnadForUnderholdAvFlaggkommandøren)

            notatService.oppdatereNotat(
                behandling,
                NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
                "Begrunnelse for andre barn",
                behandling.bidragsmottaker!!,
            )

            val request = SletteUnderholdselement(idUnderhold = 100, idElement = 10, Underholdselement.BARN)

            every { underholdskostnadRepository.deleteById(kostnadForUnderholdAvGeneralen.id!!) } returns Unit
            every { personRepository.deleteById(generalen.id!!) } returns Unit

            assertSoftly(behandling.underholdskostnader.find { it.id == kostnadForUnderholdAvGeneralen.id!! }) {
                shouldNotBeNull()
                person?.id shouldBe generalen.id
            }

            assertSoftly(behandling.notater.find { it.rolle == behandling.bidragsmottaker }) {
                shouldNotBeNull()
                innhold shouldBe "Begrunnelse for andre barn"
            }

            // hvis
            underholdService.sletteFraUnderhold(behandling, request)

            // så
            val u = behandling.underholdskostnader.find { it.id == universalid }
            u.shouldNotBeNull()
            u.tilleggsstønad.shouldBeEmpty()

            behandling.underholdskostnader.find { it.id == kostnadForUnderholdAvGeneralen.id!! }.shouldBeNull()

            assertSoftly(behandling.notater.find { it.rolle == behandling.bidragsmottaker }) {
                shouldNotBeNull()
                rolle shouldBe behandling.bidragsmottaker!!
                innhold shouldBe "Begrunnelse for andre barn"
                type shouldBe NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD
            }
        }
    }

    @Nested
    @DisplayName("Tester opprettelse av underholdkostnad for nytt barn")
    open inner class Opprette {
        @Test
        open fun `skal opprette underholdskostnad ved opprettelse av nytt barn`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )
            val universalid = 1L
            val navnAnnetBarnBp = "Stig E. Spill"
            val fødselsdatoAnnetBarnBp = LocalDate.now().minusMonths(96)

            val request = BarnDto(navn = navnAnnetBarnBp, fødselsdato = fødselsdatoAnnetBarnBp)
            val barn = Person(id = 100, navn = navnAnnetBarnBp, fødselsdato = fødselsdatoAnnetBarnBp)

            every { personRepository.save(any()) } returns barn
            every { underholdskostnadRepository.save(any()) } returns
                Underholdskostnad(
                    id = universalid,
                    behandling = behandling,
                    person = barn,
                )

            // hvis
            underholdService.oppretteUnderholdskostnad(behandling, request)

            // så
            val u = behandling.underholdskostnader.find { it.id == universalid }
            u.shouldNotBeNull()
            u.tilleggsstønad.shouldBeEmpty()
        }

        @Test
        open fun `skal ikke være mulig å opprette mer enn ett underhold per barn oppgitt med personident per behandling`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val annetBarnMedPersonident = Person(ident = "11223312345", fødselsdato = LocalDate.now())

            behandling.underholdskostnader.add(
                Underholdskostnad(id = 101, behandling = behandling, person = annetBarnMedPersonident),
            )

            val request = BarnDto(personident = Personident(annetBarnMedPersonident.ident!!))

            every { personRepository.save(any()) } returns annetBarnMedPersonident
            every { personRepository.findFirstByIdent(annetBarnMedPersonident.ident!!) } returns annetBarnMedPersonident
            every { underholdskostnadRepository.save(any()) } returns
                Underholdskostnad(
                    id = 102,
                    behandling = behandling,
                    person = annetBarnMedPersonident,
                )

            // hvis
            val respons =
                assertFailsWith<HttpClientErrorException> {
                    underholdService.oppretteUnderholdskostnad(behandling, request)
                }

            // så
            respons.statusCode shouldBe HttpStatus.CONFLICT
        }

        @Test
        open fun `skal ikke være mulig å opprette mer enn ett underhold per barn oppgitt med navn og fødselsdato per behandling`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val generalen = Person(navn = "Generalen", fødselsdato = LocalDate.now())

            behandling.underholdskostnader.add(
                Underholdskostnad(id = 101, behandling = behandling, person = generalen),
            )

            val request = BarnDto(navn = generalen.navn, fødselsdato = generalen.fødselsdato)

            every { personRepository.save(any()) } returns generalen
            every { underholdskostnadRepository.save(any()) } returns
                Underholdskostnad(
                    id = 102,
                    behandling = behandling,
                    person = generalen,
                )

            // hvis
            val respons =
                assertFailsWith<HttpClientErrorException> {
                    underholdService.oppretteUnderholdskostnad(behandling, request)
                }

            // så
            respons.statusCode shouldBe HttpStatus.CONFLICT
        }

        @Test
        open fun `feil dersom barns personident ikke finnes`() {
            // gitt
            val personidentBarn = "12345678910"
            every { personConsumer.hentPerson(personidentBarn) }.throws(HttpClientErrorException(HttpStatus.NOT_FOUND))

            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val request = BarnDto(personident = Personident(personidentBarn))

            // hvis
            val respons =
                assertFailsWith<HttpClientErrorException> {
                    underholdService.oppretteUnderholdskostnad(behandling, request)
                }

            // så
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    @Nested
    @DisplayName("Tester oppdatering av underholdkostnad ")
    open inner class Oppdatere {
        @Nested
        @DisplayName("Tester oppdatering av tillegsstønad ")
        open inner class Tilleggsstønadstest {
            @Test
            open fun `skal legge til ny tilleggsstønadsperiode`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    OppdatereTilleggsstønadRequest(
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        dagsats = BigDecimal(365),
                    )

                underholdskostnad.tilleggsstønad.add(
                    Tilleggsstønad(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        request.dagsats,
                    ),
                )

                // hvis
                underholdService.oppdatereTilleggsstønad(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.tilleggsstønad.shouldNotBeEmpty()

                assertSoftly(u.tilleggsstønad.first()) {
                    dagsats shouldBe request.dagsats
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                }
            }

            @Test
            open fun `skal endre eksisterende tilleggsstønadsperiode`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                underholdskostnad.tilleggsstønad.add(
                    Tilleggsstønad(
                        id = 1,
                        underholdskostnad = underholdskostnad,
                        dagsats = BigDecimal(390),
                        fom = LocalDate.now().minusMonths(4).withDayOfMonth(1),
                    ),
                )

                val request =
                    OppdatereTilleggsstønadRequest(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        dagsats = BigDecimal(365),
                    )

                // hvis
                underholdService.oppdatereTilleggsstønad(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.tilleggsstønad.shouldNotBeEmpty()
                u.tilleggsstønad shouldHaveSize 1

                assertSoftly(u.tilleggsstønad.first()) {
                    id shouldBe 1
                    dagsats shouldBe request.dagsats
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                }
            }
        }

        @Nested
        @DisplayName("Tester oppdatering av stønad til barnetilsyn ")
        open inner class Barnetilsynstest {
            @Test
            open fun `skal legge til ny stønad til barnetilsynsperiode`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    StønadTilBarnetilsynDto(
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        skolealder = Skolealder.OVER,
                        tilsynstype = Tilsynstype.HELTID,
                    )

                underholdskostnad.barnetilsyn.add(
                    Barnetilsyn(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        under_skolealder =
                            when (request.skolealder) {
                                Skolealder.OVER -> false
                                Skolealder.UNDER -> true
                                else -> null
                            },
                        request.tilsynstype!!,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )
                // hvis
                underholdService.oppdatereStønadTilBarnetilsynManuelt(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.barnetilsyn.shouldNotBeEmpty()

                assertSoftly(u.barnetilsyn.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    under_skolealder shouldBe false
                    omfang shouldBe Tilsynstype.HELTID
                }
            }

            @Test
            open fun `skal endre eksisterende stønad til barnetilsynsperiode`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                underholdskostnad.barnetilsyn.add(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnad,
                        fom = LocalDate.now(),
                        under_skolealder = true,
                        kilde = Kilde.OFFENTLIG,
                        omfang = Tilsynstype.HELTID,
                    ),
                )

                underholdskostnad.barnetilsyn shouldHaveSize 1

                val request =
                    StønadTilBarnetilsynDto(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        skolealder = Skolealder.OVER,
                        tilsynstype = Tilsynstype.DELTID,
                    )

                // hvis
                underholdService.oppdatereStønadTilBarnetilsynManuelt(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.barnetilsyn.shouldNotBeEmpty()
                u.barnetilsyn shouldHaveSize 1

                assertSoftly(u.barnetilsyn.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    under_skolealder shouldBe false
                    omfang shouldBe request.tilsynstype
                    kilde shouldBe Kilde.MANUELL
                }
            }

            @Test
            open fun `skal sette kilde til manuell dersom periode på offentlig barnetilsynsinnslag endres`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                underholdskostnad.barnetilsyn.add(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnad,
                        fom = LocalDate.now(),
                        under_skolealder = true,
                        kilde = Kilde.OFFENTLIG,
                        omfang = Tilsynstype.HELTID,
                    ),
                )

                underholdskostnad.barnetilsyn shouldHaveSize 1

                val request =
                    StønadTilBarnetilsynDto(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        skolealder = Skolealder.OVER,
                        tilsynstype = Tilsynstype.DELTID,
                    )

                // hvis
                underholdService.oppdatereStønadTilBarnetilsynManuelt(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.barnetilsyn.shouldNotBeEmpty()
                u.barnetilsyn shouldHaveSize 1

                assertSoftly(u.barnetilsyn.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    under_skolealder shouldBe false
                    omfang shouldBe request.tilsynstype
                    kilde shouldBe Kilde.MANUELL
                }
            }

            @Test
            open fun `skal endre kilde på manuell innslag til offentlig hvis periode matcher`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                behandling.leggeTilGjeldendeBarnetilsyn(
                    oppretteBarnetilsynGrunnlagDto(
                        behandling = behandling,
                        periodeFra = LocalDate.parse("2024-01-01"),
                        periodeTil = LocalDate.parse("2024-08-01"),
                    ),
                    false,
                )
                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                underholdskostnad.barnetilsyn.add(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnad,
                        fom = LocalDate.parse("2023-01-01"),
                        under_skolealder = true,
                        kilde = Kilde.MANUELL,
                        omfang = Tilsynstype.HELTID,
                    ),
                )

                underholdskostnad.barnetilsyn shouldHaveSize 1

                val request =
                    StønadTilBarnetilsynDto(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.parse("2024-03-01"),
                                LocalDate.parse("2024-06-30"),
                            ),
                        skolealder = Skolealder.OVER,
                        tilsynstype = Tilsynstype.DELTID,
                    )

                // hvis
                underholdService.oppdatereStønadTilBarnetilsynManuelt(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.barnetilsyn.shouldNotBeEmpty()
                u.barnetilsyn shouldHaveSize 1

                assertSoftly(u.barnetilsyn.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    under_skolealder shouldBe false
                    omfang shouldBe request.tilsynstype
                    kilde shouldBe Kilde.OFFENTLIG
                }
            }

            @Test
            open fun `skal endre kilde på offentlig innslag til manuell hvis periode ikke matcher`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                behandling.leggeTilGjeldendeBarnetilsyn(
                    oppretteBarnetilsynGrunnlagDto(
                        behandling = behandling,
                        periodeFra = LocalDate.parse("2024-01-01"),
                        periodeTil = LocalDate.parse("2024-08-01"),
                    ),
                    false,
                )
                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                underholdskostnad.barnetilsyn.add(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnad,
                        fom = LocalDate.parse("2024-01-01"),
                        under_skolealder = true,
                        kilde = Kilde.OFFENTLIG,
                        omfang = Tilsynstype.HELTID,
                    ),
                )

                underholdskostnad.barnetilsyn shouldHaveSize 1

                val request =
                    StønadTilBarnetilsynDto(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.parse("2023-12-01"),
                                null,
                            ),
                        skolealder = Skolealder.OVER,
                        tilsynstype = Tilsynstype.DELTID,
                    )

                // hvis
                underholdService.oppdatereStønadTilBarnetilsynManuelt(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.barnetilsyn.shouldNotBeEmpty()
                u.barnetilsyn shouldHaveSize 1

                assertSoftly(u.barnetilsyn.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    under_skolealder shouldBe false
                    omfang shouldBe request.tilsynstype
                    kilde shouldBe Kilde.MANUELL
                }
            }
        }

        @Nested
        @DisplayName("Tester oppdatering av faktiske utgifter ")
        open inner class FaktiskeUtgiftertest {
            @Test
            open fun `skal legge til ny faktiske tilsynsutgifter`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    OppdatereFaktiskTilsynsutgiftRequest(
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        utgift = BigDecimal(6000),
                        kostpenger = BigDecimal(1000),
                        kommentar = "Kostpenger gjelder ikke fredager",
                    )

                underholdskostnad.faktiskeTilsynsutgifter.add(
                    FaktiskTilsynsutgift(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        tilsynsutgift = request.utgift,
                        kostpenger = request.kostpenger,
                        kommentar = request.kommentar,
                    ),
                )
                // hvis
                underholdService.oppdatereFaktiskeTilsynsutgifter(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.faktiskeTilsynsutgifter.shouldNotBeEmpty()

                assertSoftly(u.faktiskeTilsynsutgifter.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    tilsynsutgift shouldBe request.utgift
                    kostpenger shouldBe request.kostpenger
                }
            }

            @Test
            open fun `skal endre eksisterende periode med faktiske tilsynsutgifter`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }

                underholdskostnad.shouldNotBeNull()

                underholdskostnad.faktiskeTilsynsutgifter.add(
                    FaktiskTilsynsutgift(
                        id = 1,
                        underholdskostnad = underholdskostnad,
                        fom = LocalDate.now(),
                        tilsynsutgift = BigDecimal(8000),
                        kostpenger = BigDecimal(1250),
                        kommentar = "Treretters",
                    ),
                )

                val request =
                    OppdatereFaktiskTilsynsutgiftRequest(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        utgift = BigDecimal(6000),
                        kostpenger = BigDecimal(1000),
                        kommentar = "Kostpenger gjelder ikke fredager",
                    )

                // hvis
                underholdService.oppdatereFaktiskeTilsynsutgifter(underholdskostnad, request)

                // så
                val u = behandling.underholdskostnader.first()
                u.shouldNotBeNull()
                u.faktiskeTilsynsutgifter.shouldNotBeEmpty()
                u.faktiskeTilsynsutgifter shouldHaveSize 1

                assertSoftly(u.faktiskeTilsynsutgifter.first()) {
                    id shouldBe request.id
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    tilsynsutgift shouldBe request.utgift
                    kostpenger shouldBe request.kostpenger
                    kommentar shouldBe request.kommentar
                }
            }
        }

        @Nested
        @DisplayName("Tester oppdatering av begrunnelse")
        open inner class Begrunnelse {
            @Test
            open fun `skal ikke kunne legge inn begrunnelse for andre barn hvis andre barn mangler`() {
                // gitt
                val behandling =
                    oppretteTestbehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnader.find {
                        barnIBehandling.ident!! == it.rolle?.ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    OppdatereBegrunnelseRequest(
                        begrunnelse = "Begrunnelse for annet barn",
                    )

                // hvis
                val respons =
                    assertFailsWith<HttpClientErrorException> {
                        underholdService.oppdatereBegrunnelse(behandling, request)
                    }

                // så
                respons.shouldNotBeNull()
            }
        }
    }

    @Nested
    @DisplayName("Tester justering av perioder i fbm endring av virkningsdato")
    open inner class OppdatereVirkningsdato {
        @Test
        open fun `skal tilpasse perioder for aktive bearbeida barnetilsynsgrunnlag etter virkningstidspunkt`() {
            // gitt
            val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN

            val b =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            b.leggeTilGjeldendeBarnetilsyn()

            val nyVirkningsdato =
                b
                    .henteNyesteAktiveGrunnlag(
                        Grunnlagstype(grunnlagsdatatype, false),
                        grunnlagsdatatype.innhentesForRolle(b)!!,
                    ).konvertereData<Set<BarnetilsynGrunnlagDto>>()
                    ?.minBy { it.periodeFra }
                    ?.periodeFra
                    ?.plusMonths(4)

            nyVirkningsdato shouldNotBe null

            b.grunnlag
                .filter { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet && it.aktiv != null }
                .maxBy { it.innhentet }
                .konvertereData<Set<BarnetilsynGrunnlagDto>>()
                ?.minBy { it.periodeFra }
                ?.periodeFra
                ?.shouldBeBefore(nyVirkningsdato!!)
            b.virkningstidspunkt = nyVirkningsdato

            // hvis
            underholdService.tilpasseUnderholdEtterVirkningsdato(b)

            // så
            val aktiveBearbeidaBarnetilsyn =
                b.grunnlag.hentAlleAktiv().filter { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet }
            aktiveBearbeidaBarnetilsyn shouldHaveSize 1
            assertSoftly(aktiveBearbeidaBarnetilsyn.first()) {
                gjelder.shouldNotBeNull()
                aktiv.shouldNotBeNull()
                erBearbeidet shouldBe true
            }

            aktiveBearbeidaBarnetilsyn.first().konvertereData<Set<BarnetilsynGrunnlagDto>>()?.shouldHaveSize(1)

            assertSoftly(aktiveBearbeidaBarnetilsyn.first().konvertereData<Set<BarnetilsynGrunnlagDto>>()?.first()) {
                it?.periodeFra.shouldNotBeNull()
                it!!.periodeFra shouldBe nyVirkningsdato
                it.periodeTil shouldBe null
                it.beløp shouldBe 4000
                it.barnPersonId shouldBe testdataBarn1.ident
            }
        }

        @Test
        open fun `skal tilpasse perioder for ikke-aktive bearbeida barnetilsynsgrunnlag etter virkningstidspunkt`() {
            // gitt
            val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN

            val b =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            b.leggeTilGjeldendeBarnetilsyn(
                oppretteBarnetilsynGrunnlagDto(
                    b,
                    skolealder = Skolealder.UNDER,
                    tilsynstype = Tilsynstype.DELTID,
                ),
            )
            b.leggeTilNyttBarnetilsyn()

            val nyVirkningsdato =
                b
                    .henteNyesteIkkeAktiveGrunnlag(
                        Grunnlagstype(grunnlagsdatatype, false),
                        grunnlagsdatatype.innhentesForRolle(b)!!,
                    ).konvertereData<Set<BarnetilsynGrunnlagDto>>()
                    ?.filter { it.barnPersonId == testdataBarn1.ident }
                    ?.minBy { it.periodeFra }
                    ?.periodeFra
                    ?.plusMonths(1)

            nyVirkningsdato shouldNotBe null

            b.grunnlag
                .filter { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet && it.aktiv == null }
                .maxBy { it.innhentet }
                .konvertereData<Set<BarnetilsynGrunnlagDto>>()
                ?.minBy { it.periodeFra }
                ?.periodeFra
                ?.shouldBeBefore(nyVirkningsdato!!)
            b.virkningstidspunkt = nyVirkningsdato

            // hvis
            underholdService.tilpasseUnderholdEtterVirkningsdato(b)

            // så
            val ikkeaktiveBearbeidaBarnetilsyn =
                b.grunnlag.hentAlleIkkeAktiv().filter { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet }
            ikkeaktiveBearbeidaBarnetilsyn shouldHaveSize 1
            assertSoftly(ikkeaktiveBearbeidaBarnetilsyn.find { it.gjelder == testdataBarn1.ident }!!) {
                gjelder.shouldNotBeNull()
                aktiv.shouldBeNull()
                erBearbeidet shouldBe true
            }

            ikkeaktiveBearbeidaBarnetilsyn
                .find { it.gjelder == testdataBarn1.ident }!!
                .konvertereData<Set<BarnetilsynGrunnlagDto>>()
                ?.shouldHaveSize(4)

            assertSoftly(
                ikkeaktiveBearbeidaBarnetilsyn
                    .find { it.gjelder == testdataBarn1.ident }!!
                    .konvertereData<Set<BarnetilsynGrunnlagDto>>()
                    ?.minBy { it.periodeFra },
            ) {
                it?.periodeFra.shouldNotBeNull()
                it!!.periodeFra shouldBe nyVirkningsdato
                it.periodeTil shouldNotBe null
                it.beløp shouldBe 4500
                it.barnPersonId shouldBe testdataBarn1.ident
            }
        }

        @Test
        open fun `skal tilpasse perioder etter virkningsdato for alle tabeller i underholdskostnad`() {
            // gitt
            val b =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            b.leggeTilGjeldendeBarnetilsyn()
            val u =
                b.underholdskostnader.find { it.rolle?.personident?.verdi == testdataBarn1.ident }!!

            u.barnetilsyn.first().fom = b.virkningstidspunktEllerSøktFomDato.minusMonths(2)

            u.faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    underholdskostnad = u,
                    fom = b.virkningstidspunktEllerSøktFomDato.minusMonths(2),
                    tilsynsutgift = BigDecimal(5000),
                    kostpenger = BigDecimal(900),
                ),
            )

            u.tilleggsstønad.add(
                Tilleggsstønad(
                    underholdskostnad = u,
                    dagsats = BigDecimal(120),
                    fom = b.virkningstidspunktEllerSøktFomDato.minusMonths(2),
                ),
            )

            b.virkningstidspunkt = b.virkningstidspunkt?.plusMonths(1)

            // hvis
            underholdService.tilpasseUnderholdEtterVirkningsdato(b)

            // så
            u.barnetilsyn.first().fom shouldBe
                b.grunnlag
                    .hentSisteAktiv()
                    .find { Grunnlagsdatatype.BARNETILSYN == it.type }
                    .konvertereData<Set<BarnetilsynGrunnlagDto>>()!!
                    .minBy { it.periodeFra }
                    .periodeFra
            u.faktiskeTilsynsutgifter.first().fom shouldBe b.virkningstidspunkt
            u.tilleggsstønad.first().fom shouldBe b.virkningstidspunkt
        }

        @Test
        open fun `skal aktivere grunnlag og justere perioder for barnetilsyn etter endring av virkningsdato`() {
            // gitt
            val b =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            b.leggeTilGjeldendeBarnetilsyn(
                oppretteBarnetilsynGrunnlagDto(
                    b,
                    periodeFraAntallMndTilbake = 13,
                    periodeTilAntallMndTilbake = 1,
                ),
            )

            val originaltGrunnlag =
                b.grunnlag
                    .hentAlleAktiv()
                    .find { Grunnlagsdatatype.BARNETILSYN == it.type && !it.erBearbeidet }

            originaltGrunnlag.shouldNotBeNull()
            originaltGrunnlag.aktiv.shouldNotBeNull()

            val dataOriginaltGrunnlag = originaltGrunnlag.konvertereData<Set<BarnetilsynGrunnlagDto>>()

            val u =
                b.underholdskostnader.find { it.rolle?.personident?.verdi == testdataBarn1.ident }!!

            u.barnetilsyn.add(
                Barnetilsyn(
                    underholdskostnad = u,
                    fom = b.virkningstidspunktEllerSøktFomDato,
                    kilde = Kilde.MANUELL,
                    omfang = Tilsynstype.HELTID,
                    under_skolealder = false,
                ),
            )

            b.virkningstidspunkt = LocalDate.now()

            // hvis
            underholdService.tilpasseUnderholdEtterVirkningsdato(b)

            // så
            assertSoftly(b.grunnlag.hentAlleAktiv().filter { Grunnlagsdatatype.BARNETILSYN == it.type }) {
                shouldHaveSize(2)

                val ikkeBearbeida = find { !it.erBearbeidet }
                val bearbeida = find { it.erBearbeidet }

                ikkeBearbeida.shouldNotBeNull()
                bearbeida.shouldNotBeNull()

                ikkeBearbeida.aktiv?.toLocalDate() shouldBe originaltGrunnlag.aktiv!!.toLocalDate()
                bearbeida.aktiv?.toLocalDate() shouldBe LocalDate.now()

                val perioderIkkeBearbeida = ikkeBearbeida.konvertereData<Set<BarnetilsynGrunnlagDto>>()
                val perioderBearbeida = bearbeida.konvertereData<Set<BarnetilsynGrunnlagDto>>()

                perioderIkkeBearbeida.shouldNotBeNull()
                perioderIkkeBearbeida.shouldHaveSize(1)
                perioderIkkeBearbeida.first().periodeFra shouldBe dataOriginaltGrunnlag!!.first().periodeFra
                perioderIkkeBearbeida.first().periodeTil shouldBe dataOriginaltGrunnlag.first().periodeTil

                perioderBearbeida.shouldBeEmpty()
            }

            assertSoftly(u.barnetilsyn) {
                shouldHaveSize(1)
                first().kilde shouldBe Kilde.MANUELL
                first().fom shouldBe b.virkningstidspunkt
                first().tom shouldBe null
            }
        }
    }

    @Nested
    @DisplayName("Tester automatisk aktivering av barnetilsyn")
    open inner class Aktivere {
        @Test
        open fun `skal aktivere nytt barnetilsynsgrunnlag dersom det ikke inneholder endringer som må godkjennes`() {
            // gitt
            val b =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            b.leggeTilGjeldendeBarnetilsyn()
            b.leggeTilNyttBarnetilsyn()

            // hvis
            b.aktivereBarnetilsynHvisIngenEndringerMåAksepteres()

            // så
            val ikkeaktiveBearbeidaBarnetilsyn =
                b.grunnlag.hentAlleIkkeAktiv().filter { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet }
            ikkeaktiveBearbeidaBarnetilsyn shouldHaveSize 1
            assertSoftly(ikkeaktiveBearbeidaBarnetilsyn.find { it.gjelder == testdataBarn1.ident }!!) {
                gjelder.shouldNotBeNull()
                aktiv.shouldBeNull()
                erBearbeidet shouldBe true
            }

            ikkeaktiveBearbeidaBarnetilsyn
                .find { it.gjelder == testdataBarn1.ident }!!
                .konvertereData<Set<BarnetilsynGrunnlagDto>>()
                ?.shouldHaveSize(3)

            val aktiveBearbeidaBarnetilsyn =
                b.grunnlag.hentAlleAktiv().filter { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet }
            aktiveBearbeidaBarnetilsyn shouldHaveSize 2

            aktiveBearbeidaBarnetilsyn.filter { it.gjelder == testdataBarn2.ident } shouldHaveSize 1

            assertSoftly(aktiveBearbeidaBarnetilsyn.find { it.gjelder == testdataBarn2.ident }) {
                it?.aktiv.shouldNotBeNull()
                it?.erBearbeidet shouldBe true
            }

            val dataTestbarn2 =
                aktiveBearbeidaBarnetilsyn
                    .find { it.gjelder == testdataBarn2.ident }
                    .konvertereData<Set<BarnetilsynGrunnlagDto>>()!!
            dataTestbarn2 shouldHaveSize 1

            assertSoftly(dataTestbarn2.first()) {
                beløp shouldBe 4000
                barnPersonId shouldBe testdataBarn2.ident
                periodeFra shouldBeGreaterThan b.virkningstidspunktEllerSøktFomDato
                periodeTil shouldBe null
                tilsynstype shouldBe Tilsynstype.HELTID
                skolealder shouldBe Skolealder.OVER
            }
        }
    }
}
