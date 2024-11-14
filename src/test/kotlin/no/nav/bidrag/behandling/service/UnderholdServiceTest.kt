package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.repository.BarnetilsynRepository
import no.nav.bidrag.behandling.database.repository.FaktiskTilsynsutgiftRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.TilleggsstønadRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.person.PersonDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockKExtension::class)
class UnderholdServiceTest {
    @MockK
    lateinit var barnetilsynRepository: BarnetilsynRepository

    @MockK
    lateinit var faktiskTilsynsutgiftRepository: FaktiskTilsynsutgiftRepository

    @MockK
    lateinit var tilleggsstønadRepository: TilleggsstønadRepository

    @MockK
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @MockK
    lateinit var personRepository: PersonRepository

    @MockK
    lateinit var personService: PersonService

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var validering: ValiderBeregning

    val notatService = NotatService()

    lateinit var behandlingTilGrunnlagMappingV2: BehandlingTilGrunnlagMappingV2

    lateinit var dtomapper: Dtomapper

    lateinit var underholdService: UnderholdService

    @BeforeEach
    fun setup() {
        behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        dtomapper =
            Dtomapper(tilgangskontrollService, validering, validerBehandlingService, behandlingTilGrunnlagMappingV2)
        underholdService =
            UnderholdService(
                barnetilsynRepository,
                faktiskTilsynsutgiftRepository,
                tilleggsstønadRepository,
                underholdskostnadRepository,
                personRepository,
                notatService,
                dtomapper,
            )
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

            every { personService.hentPerson(any()) } returns
                PersonDto(
                    ident = Personident(testdataBarn1.ident),
                    navn = testdataBarn1.navn,
                    fødselsdato = testdataBarn1.fødselsdato,
                )

            // hvis
            underholdService.sletteFraUnderhold(behandling, request)

            // så
            val u = behandling.underholdskostnader.find { it.id == universalid }
            u.shouldNotBeNull()
            u.tilleggsstønad.shouldBeEmpty()
        }
    }

    @Nested
    @DisplayName("Tester opprettelse av underholdkostnad for nytt barn")
    open inner class Opprette {
        @Test
        open fun `skal opprette underholdskostnad ved opprettelse av nytt barn`() {
            // gitt
            val universalid = 1L
            val navnAnnetBarnBp = "Stig E. Spill"
            val fødselsdatoAnnetBarnBp = LocalDate.now().minusMonths(96)
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val request = BarnDto(navn = navnAnnetBarnBp, fødselsdato = fødselsdatoAnnetBarnBp)
            val barn = Person(navn = navnAnnetBarnBp, fødselsdato = fødselsdatoAnnetBarnBp)

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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    TilleggsstønadDto(
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        dagsats = BigDecimal(365),
                    )

                every { tilleggsstønadRepository.save(any()) } returns
                    Tilleggsstønad(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        request.dagsats,
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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
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
                    TilleggsstønadDto(
                        id = 1,
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        dagsats = BigDecimal(365),
                    )

                every { tilleggsstønadRepository.save(any()) } returns
                    Tilleggsstønad(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        request.dagsats,
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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
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

                every { barnetilsynRepository.save(any()) } returns
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
                        request.tilsynstype,
                        kilde = Kilde.OFFENTLIG,
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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
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

                every { barnetilsynRepository.save(any()) } returns
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
                        request.tilsynstype,
                        kilde = Kilde.OFFENTLIG,
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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    FaktiskTilsynsutgiftDto(
                        periode =
                            DatoperiodeDto(
                                LocalDate.now().minusMonths(6).withDayOfMonth(1),
                                null,
                            ),
                        utgift = BigDecimal(6000),
                        kostpenger = BigDecimal(1000),
                        kommentar = "Kostpenger gjelder ikke fredager",
                    )

                every { faktiskTilsynsutgiftRepository.save(any()) } returns
                    FaktiskTilsynsutgift(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        tilsynsutgift = request.utgift,
                        kostpenger = request.kostpenger,
                        kommentar = request.kommentar,
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
            open fun `skal eksisterende periode med faktiske tilsynsutgifter`() {
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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
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
                    FaktiskTilsynsutgiftDto(
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

                every { faktiskTilsynsutgiftRepository.save(any()) } returns
                    FaktiskTilsynsutgift(
                        1L,
                        underholdskostnad,
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        tilsynsutgift = request.utgift,
                        kostpenger = request.kostpenger,
                        kommentar = request.kommentar,
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
        @DisplayName("Tester oppdatering av underhold ")
        open inner class Underholdstest {
            @Test
            open fun `skal angi tilsynsordning og legge inn begrunnelse`() {
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
                        barnIBehandling.ident!! ==
                            it.person.rolle
                                .first()
                                .ident
                    }
                underholdskostnad.shouldNotBeNull()

                val request =
                    OppdatereUnderholdRequest(
                        harTilsynsordning = true,
                        begrunnelse = "Barmet går i SFO",
                    )

                every { personService.hentPerson(any()) } returns
                    PersonDto(
                        ident = Personident(testdataBarn1.ident),
                        navn = testdataBarn1.navn,
                        fødselsdato = testdataBarn1.fødselsdato,
                    )

                // hvis
                val underholdDto = underholdService.oppdatereUnderhold(underholdskostnad, request)

                // så
                assertSoftly(underholdDto) {
                    harTilsynsordning shouldBe request.harTilsynsordning
                    begrunnelse shouldBe request.begrunnelse
                    stønadTilBarnetilsyn.shouldBeEmpty()
                    faktiskTilsynsutgift.shouldBeEmpty()
                    tilleggsstønad.shouldBeEmpty()
                }
            }
        }
    }
}
