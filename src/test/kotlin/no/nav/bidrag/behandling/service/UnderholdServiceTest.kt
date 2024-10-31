package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
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
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
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

    @InjectMockKs
    lateinit var underholdService: UnderholdService

    @Nested
    @DisplayName("Tester sletting fra underholdskostnad")
    open inner class Slette {
        @Test
        open fun `skal slette tilleggsstønad fra underholdskostnad`() {
            // gitt
            val universalid = 1L
            val behandling =
                oppretteBehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barn = Person(ident = behandling.søknadsbarn.first().ident)
            val underholdskostnad = Underholdskostnad(id = universalid, behandling = behandling, person = barn)
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
            behandling.underholdskostnad.add(underholdskostnad)
            val request = SletteUnderholdselement(idUnderhold = 1, idElement = 1, Underholdselement.TILLEGGSSTØNAD)

            val uFørSleting = behandling.underholdskostnad.find { it.id == universalid }
            uFørSleting.shouldNotBeNull()
            uFørSleting.tilleggsstønad.shouldNotBeEmpty()

            // hvis
            underholdService.sletteFraUnderhold(behandling, request)

            // så
            val u = behandling.underholdskostnad.find { it.id == universalid }
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
            val behandling =
                oppretteBehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val request = BarnDto(navn = navnAnnetBarnBp)
            val barn = Person(navn = navnAnnetBarnBp)

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
            val u = behandling.underholdskostnad.find { it.id == universalid }
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
                    oppretteBehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnad.find {
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
                val u = behandling.underholdskostnad.first()
                u.shouldNotBeNull()
                u.tilleggsstønad.shouldNotBeEmpty()

                assertSoftly(u.tilleggsstønad.first()) {
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
            open fun `skal legge til ny stønad til barnetilsyn`() {
                // gitt
                val behandling =
                    oppretteBehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnad.find {
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
                val u = behandling.underholdskostnad.first()
                u.shouldNotBeNull()
                u.barnetilsyn.shouldNotBeEmpty()

                assertSoftly(u.barnetilsyn.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    under_skolealder shouldBe false
                    omfang shouldBe Tilsynstype.HELTID
                }
            }
        }

        @Nested
        @DisplayName("Tester oppdatering av faktiske utgifter ")
        open inner class FaktiskeUtgiftertest {
            @Test
            open fun `skal legge til ny faktiske utgifter`() {
                // gitt
                val behandling =
                    oppretteBehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnad.find {
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
                val u = behandling.underholdskostnad.first()
                u.shouldNotBeNull()
                u.faktiskeTilsynsutgifter.shouldNotBeEmpty()

                assertSoftly(u.faktiskeTilsynsutgifter.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    tilsynsutgift shouldBe request.utgift
                    kostpenger shouldBe request.kostpenger
                }
            }
        }

        @Nested
        @DisplayName("Tester oppdatering av underhold ")
        open inner class Underhold {
            @Test
            open fun `skal angi tilsynsordning og legge inn begrunnelse`() {
                // gitt
                val behandling =
                    oppretteBehandling(
                        setteDatabaseider = true,
                        inkludereBp = true,
                        behandlingstype = TypeBehandling.BIDRAG,
                    )

                val barnIBehandling = behandling.søknadsbarn.first()
                barnIBehandling.ident.shouldNotBeNull()

                val underholdskostnad =
                    behandling.underholdskostnad.find {
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
                val u = behandling.underholdskostnad.first()
                u.shouldNotBeNull()
                u.faktiskeTilsynsutgifter.shouldNotBeEmpty()

                assertSoftly(u.faktiskeTilsynsutgifter.first()) {
                    fom shouldBe request.periode.fom
                    tom shouldBe request.periode.tom
                    tilsynsutgift shouldBe request.utgift
                    kostpenger shouldBe request.kostpenger
                }
            }
        }
    }
}
