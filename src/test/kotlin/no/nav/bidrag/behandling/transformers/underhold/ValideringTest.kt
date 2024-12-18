package no.nav.bidrag.behandling.transformers.underhold

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdsperiode
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ValideringTest {
    @Nested
    open inner class OverlappendeDatoperioder {
        @Test
        fun `skal identifisere og gruppere perioder som overlapper`() {
            // gitt
            val førstePeriode =
                Underholdsperiode(
                    Underholdselement.STØNAD_TIL_BARNETILSYN,
                    DatoperiodeDto(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 9, 1)),
                )
            val periodeSomOverlapperFørstePeriode =
                Underholdsperiode(
                    Underholdselement.STØNAD_TIL_BARNETILSYN,
                    DatoperiodeDto(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 8, 1)),
                )
            val periodeSomOverlapperFørsteOgAndrePeriode =
                Underholdsperiode(
                    Underholdselement.STØNAD_TIL_BARNETILSYN,
                    DatoperiodeDto(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 7, 1)),
                )
            val duplikatAvPeriodeSomOverlapperFørsteOgAndrePeriode =
                Underholdsperiode(
                    Underholdselement.STØNAD_TIL_BARNETILSYN,
                    DatoperiodeDto(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 7, 1)),
                )
            val periodeSomIkkeOverlapperAndrePerioder =
                Underholdsperiode(
                    Underholdselement.STØNAD_TIL_BARNETILSYN,
                    DatoperiodeDto(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 11, 1)),
                )

            // hvis
            val resultat =
                finneOverlappendePerioder(
                    setOf(
                        periodeSomOverlapperFørstePeriode,
                        duplikatAvPeriodeSomOverlapperFørsteOgAndrePeriode,
                        periodeSomIkkeOverlapperAndrePerioder,
                        førstePeriode,
                        periodeSomOverlapperFørsteOgAndrePeriode,
                    ),
                )

            // så
            assertSoftly(resultat) {
                shouldNotBeNull()
                shouldHaveSize(2)
            }

            assertSoftly(resultat[førstePeriode]) {
                shouldNotBeNull()
                shouldHaveSize(2)
                contains(periodeSomOverlapperFørstePeriode) && contains(periodeSomOverlapperFørsteOgAndrePeriode)
            }

            assertSoftly(resultat[periodeSomOverlapperFørstePeriode]) {
                shouldNotBeNull()
                shouldHaveSize(1)
                contains(periodeSomOverlapperFørsteOgAndrePeriode)
            }
        }
    }

    @Nested
    @DisplayName("Teste validering av stønad til barnetilsyn")
    open inner class BarnetilsynTest {
        @Test
        open fun `skal gi valideringsfeil dersom periode med fom etter første i inneværende måned eksisterer`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }

            u.shouldNotBeNull()

            val fom = LocalDate.now().withDayOfMonth(2)

            u.barnetilsyn.add(Barnetilsyn(2L, u, fom, null, false, Tilsynstype.HELTID, Kilde.MANUELL))

            // hvis
            val valideringsfeil = u.barnetilsyn.validerePerioder()

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.fom shouldBe fom
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.tom shouldBe null
        }

        @Test
        open fun `skal gi valideringsfeil dersom periode med tomdato i inneværende måned eksisterer`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }

            u.shouldNotBeNull()

            val fom = LocalDate.now().minusMonths(6).withDayOfMonth(1)
            val ugyldigTom = LocalDate.now().withDayOfMonth(1)

            u.barnetilsyn.add(
                Barnetilsyn(2L, u, fom, ugyldigTom, false, Tilsynstype.HELTID, Kilde.MANUELL),
            )

            // hvis
            val valideringsfeil = u.barnetilsyn.validerePerioder()

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.fom shouldBe fom
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.tom shouldBe ugyldigTom
        }

        @Test
        open fun `skal gi valideringsfeil dersom perioder overlapper`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }

            u.shouldNotBeNull()

            val fomLavesteVerdi = LocalDate.now().minusMonths(6).withDayOfMonth(1)

            u.barnetilsyn.add(
                Barnetilsyn(1L, u, fomLavesteVerdi, null, false, Tilsynstype.HELTID, Kilde.MANUELL),
            )

            u.barnetilsyn.add(
                Barnetilsyn(
                    2L,
                    u,
                    fomLavesteVerdi.plusDays(2),
                    LocalDate.now().withDayOfMonth(1).minusDays(1),
                    false,
                    Tilsynstype.HELTID,
                    Kilde.MANUELL,
                ),
            )

            // hvis
            val valideringsfeil = u.barnetilsyn.validerePerioder()

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.overlappendePerioder.size shouldBe 1
        }

        @Test
        open fun `skal ikke gi valideringsfeil med gyldig tom og fom `() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }

            u.shouldNotBeNull()

            val fomHøyesteGyldigeVerdi = LocalDate.now().withDayOfMonth(1)
            val tomHøyesteGyldigeVerdi = LocalDate.now().withDayOfMonth(1).minusDays(1)

            u.barnetilsyn.add(
                Barnetilsyn(1L, u, fomHøyesteGyldigeVerdi, null, false, Tilsynstype.HELTID, Kilde.MANUELL),
            )

            u.barnetilsyn.add(
                Barnetilsyn(
                    2L,
                    u,
                    fomHøyesteGyldigeVerdi.minusDays(2),
                    tomHøyesteGyldigeVerdi,
                    false,
                    Tilsynstype.DELTID,
                    Kilde.MANUELL,
                ),
            )

            // hvis
            val valideringsfeil = u.barnetilsyn.validerePerioder()

            // så
            valideringsfeil.shouldBeNull()
        }
    }

    @Nested
    @DisplayName("Teste validering av faktisk utgiftsperioder")
    open inner class FaktiskeUtgifterTest {
        @Test
        open fun `skal gi valideringsfeil dersom fremtidig periode eksisterer`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val underholdskostnad =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident == it.barnetsRolleIBehandlingen?.personident
                }

            underholdskostnad.shouldNotBeNull()

            val faktiskeTilsynsutgifter = mutableSetOf<FaktiskTilsynsutgift>()

            faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    id = 1,
                    underholdskostnad = underholdskostnad,
                    fom = LocalDate.now().withDayOfMonth(2),
                    tilsynsutgift = BigDecimal(8000),
                    kostpenger = BigDecimal(1250),
                    kommentar = "Treretters",
                ),
            )

            faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    id = 1,
                    underholdskostnad = underholdskostnad,
                    fom = LocalDate.now().minusMonths(2).withDayOfMonth(1),
                    tom =
                        LocalDate
                            .now()
                            .minusMonths(1)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    tilsynsutgift = BigDecimal(4000),
                    kostpenger = BigDecimal(1250),
                    kommentar = "Treretters",
                ),
            )

            // hvis
            val valideringsfeil = faktiskeTilsynsutgifter.validerePerioderFaktiskTilsynsutgift()

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.fom shouldBe
                LocalDate
                    .now()
                    .withDayOfMonth(2)
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.tom shouldBe null
        }

        @Test
        open fun `skal ikke gi valideringsfeil dersom perioder overlapper`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val underholdskostnad =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }

            underholdskostnad.shouldNotBeNull()

            val faktiskeTilsynsutgifter = mutableSetOf<FaktiskTilsynsutgift>()

            faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    id = 1,
                    underholdskostnad = underholdskostnad,
                    fom = LocalDate.now().minusMonths(3).withDayOfMonth(1),
                    tom =
                        LocalDate
                            .now()
                            .minusMonths(1)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    tilsynsutgift = BigDecimal(8000),
                    kostpenger = BigDecimal(1250),
                    kommentar = "Treretters",
                ),
            )

            faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    id = 2,
                    underholdskostnad = underholdskostnad,
                    fom = LocalDate.now().minusMonths(4).withDayOfMonth(1),
                    tom = null,
                    tilsynsutgift = BigDecimal(6000),
                    kostpenger = BigDecimal(1000),
                    kommentar = "Kostpenger gjelder ikke fredager",
                ),
            )

            // hvis
            val valideringsfeil = faktiskeTilsynsutgifter.validerePerioderFaktiskTilsynsutgift()

            // så
            valideringsfeil.shouldBeNull()
        }
    }

    @Nested
    @DisplayName("Teste validering av tilleggsstønad")
    open inner class TilleggsstønadTest {
        @Test
        fun `skal gi valideringsfeil dersom tilleggsstønad mangler tilsynsutgift`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val underholdskostnad =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident == it.barnetsRolleIBehandlingen?.personident
                }
            underholdskostnad.shouldNotBeNull()

            val tilleggsstønad =
                setOf(
                    Tilleggsstønad(
                        id = 1L,
                        underholdskostnad = underholdskostnad,
                        fom = LocalDate.now().minusMonths(6).withDayOfMonth(1),
                        tom = null,
                        dagsats = BigDecimal(365),
                    ),
                )

            // hvis
            val valideringsfeil = tilleggsstønad.validerePerioderTilleggsstønad(underholdskostnad)

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift shouldHaveSize 1
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .first()
                .periode.fom shouldBe tilleggsstønad.first().fom
        }

        @Test
        fun `skal gi valideringsfeil dersom det ikke finnes tilsynsutgift for alle tilleggsstønadsperiodene`() {
            // gitt
            val fomdatoFørstePeriodeUtenTilsynsutgift = LocalDate.now().minusMonths(6).withDayOfMonth(1)
            val fomdatoAndrePeriodeUtenTilsynsutgift = LocalDate.now().minusMonths(2).withDayOfMonth(1)

            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }
            u.shouldNotBeNull()

            u.faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    id = 20L,
                    u,
                    fom = LocalDate.now().minusMonths(8).withDayOfMonth(1),
                    tom =
                        LocalDate
                            .now()
                            .minusMonths(6)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    tilsynsutgift = BigDecimal(4000),
                ),
            )

            u.faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    id = 22L,
                    u,
                    fom = LocalDate.now().minusMonths(4).withDayOfMonth(1),
                    tom = fomdatoAndrePeriodeUtenTilsynsutgift.minusDays(1),
                    tilsynsutgift = BigDecimal(4200),
                ),
            )

            val tilleggsstønad = mutableSetOf<Tilleggsstønad>()

            tilleggsstønad.add(
                Tilleggsstønad(
                    id = 10L,
                    u,
                    fom = LocalDate.now().minusMonths(8).withDayOfMonth(1),
                    tom =
                        LocalDate
                            .now()
                            .minusMonths(6)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    dagsats = BigDecimal(64),
                ),
            )

            tilleggsstønad.add(
                Tilleggsstønad(
                    id = 11L,
                    u,
                    fom = fomdatoFørstePeriodeUtenTilsynsutgift,
                    tom =
                        LocalDate
                            .now()
                            .minusMonths(4)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    dagsats = BigDecimal(64),
                ),
            )

            tilleggsstønad.add(
                Tilleggsstønad(
                    id = 12L,
                    u,
                    fom = LocalDate.now().minusMonths(4).withDayOfMonth(1),
                    tom = fomdatoAndrePeriodeUtenTilsynsutgift.minusDays(1),
                    dagsats = BigDecimal(64),
                ),
            )

            tilleggsstønad.add(
                Tilleggsstønad(
                    id = 13L,
                    u,
                    fom = fomdatoAndrePeriodeUtenTilsynsutgift,
                    tom = null,
                    dagsats = BigDecimal(365),
                ),
            )

            // hvis
            val valideringsfeil = tilleggsstønad.validerePerioderTilleggsstønad(u)

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift shouldHaveSize 2
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .minBy { it.periode.fom }
                .periode.fom shouldBe fomdatoFørstePeriodeUtenTilsynsutgift
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .maxBy { it.periode.fom }
                .periode.fom shouldBe fomdatoAndrePeriodeUtenTilsynsutgift
        }

        @Test
        fun `skal gi valideringsfeil dersom tilleggsstønad har fremtidig periode`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }
            u.shouldNotBeNull()

            val tilleggsstønad =
                setOf(
                    Tilleggsstønad(
                        id = 10L,
                        u,
                        fom = LocalDate.now().withDayOfMonth(2),
                        tom = null,
                        dagsats = BigDecimal(365),
                    ),
                )

            // hvis
            val valideringsfeil = tilleggsstønad.validerePerioderTilleggsstønad(u)

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.fom shouldBe tilleggsstønad.first().fom
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift shouldHaveSize 1
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .first()
                .periode.fom shouldBe tilleggsstønad.first().fom
        }

        @Test
        fun `skal ikke gi valideringsfeil dersom tilleggsstønad har tilsynsutgift`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }
            u.shouldNotBeNull()

            val tilleggsstønad =
                Tilleggsstønad(
                    1L,
                    u,
                    fom = LocalDate.now().minusMonths(6).withDayOfMonth(1),
                    tom = null,
                    BigDecimal(365),
                )

            u.tilleggsstønad.add(tilleggsstønad)

            val faktiskTilsynsutgift =
                FaktiskTilsynsutgift(
                    1L,
                    u,
                    fom = tilleggsstønad.fom,
                    tom = tilleggsstønad.tom,
                    tilsynsutgift = BigDecimal(5000),
                )
            u.faktiskeTilsynsutgifter.add(faktiskTilsynsutgift)

            // hvis
            val valideringsfeil = u.tilleggsstønad.validerePerioderTilleggsstønad(u)

            // så
            valideringsfeil.shouldBeNull()
        }

        @Test
        fun `skal gi valideringsfeil dersom tom-dato er i inneværende måned`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }
            u.shouldNotBeNull()

            u.tilleggsstønad.add(
                Tilleggsstønad(
                    id = 1L,
                    underholdskostnad = u,
                    fom = LocalDate.now().minusMonths(6).withDayOfMonth(1),
                    tom = LocalDate.now().withDayOfMonth(1),
                    dagsats = BigDecimal(365),
                ),
            )

            // hvis
            val valideringsfeil = u.tilleggsstønad.validerePerioderTilleggsstønad(u)

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.fremtidigePerioder.shouldHaveSize(1)
            valideringsfeil.fremtidigePerioder
                .first()
                .periode.tom shouldBe LocalDate.now().withDayOfMonth(1)
        }

        @Test
        fun `skal gi valideringsfeil dersom perioder overlapper hverandre`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnIBehandling = behandling.søknadsbarn.first()
            barnIBehandling.personident.shouldNotBeNull()

            val u =
                behandling.underholdskostnader.find {
                    barnIBehandling.personident!! == it.barnetsRolleIBehandlingen?.personident
                }
            u.shouldNotBeNull()

            val fom = LocalDate.now().minusMonths(6).withDayOfMonth(1)
            val tom = LocalDate.now().withDayOfMonth(1)
            val fomLavesteTilOverlappendePeriode = LocalDate.now().minusMonths(5).withDayOfMonth(1)
            val fomHøyesteTilOverlappendePeriode = LocalDate.now().minusMonths(4).withDayOfMonth(1)

            u.tilleggsstønad.add(
                Tilleggsstønad(
                    id = 12L,
                    u,
                    fom = fomLavesteTilOverlappendePeriode,
                    tom =
                        LocalDate
                            .now()
                            .minusMonths(4)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    dagsats = BigDecimal(64),
                ),
            )

            u.tilleggsstønad.add(
                Tilleggsstønad(
                    id = 12L,
                    u,
                    fom = fomHøyesteTilOverlappendePeriode,
                    tom = null,
                    dagsats = BigDecimal(64),
                ),
            )

            u.tilleggsstønad.add(Tilleggsstønad(13L, u, fom, tom, BigDecimal(365)))

            // hvis
            val valideringsfeil = u.tilleggsstønad.validerePerioderTilleggsstønad(u)

            // så
            valideringsfeil.shouldNotBeNull()

            assertSoftly(valideringsfeil.overlappendePerioder) {
                size shouldBe 1
                keys shouldHaveSize 1
                keys.first() shouldBe Underholdsperiode(Underholdselement.TILLEGGSSTØNAD, DatoperiodeDto(fom, tom))
                values shouldHaveSize 1
            }

            assertSoftly(
                valideringsfeil.overlappendePerioder.entries
                    .first()
                    .value,
            ) {
                shouldHaveSize(2)
                minBy { it.periode.fom }.periode.fom shouldBe fomLavesteTilOverlappendePeriode
                maxBy { it.periode.fom }.periode.fom shouldBe fomHøyesteTilOverlappendePeriode
            }
        }
    }
}
