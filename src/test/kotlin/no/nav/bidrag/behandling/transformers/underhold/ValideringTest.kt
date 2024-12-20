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
                DatoperiodeDto(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 9, 1))
            val periodeSomOverlapperFørstePeriode =
                DatoperiodeDto(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 8, 1))
            val periodeSomOverlapperFørsteOgAndrePeriode =
                DatoperiodeDto(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 7, 1))
            val duplikatAvPeriodeSomOverlapperFørsteOgAndrePeriode =
                DatoperiodeDto(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 7, 1))
            val periodeSomIkkeOverlapperAndrePerioder =
                DatoperiodeDto(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 11, 1))

            // hvis
            val resultat =
                finneOverlappendePerioder(
                    listOf(
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

            assertSoftly(resultat.map { it.periode }) {
                shouldNotBeNull()
                shouldHaveSize(2)
                contains(periodeSomOverlapperFørstePeriode) && contains(periodeSomOverlapperFørsteOgAndrePeriode)
            }

            assertSoftly(resultat.map { it.periode }) {
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.stønadTilBarnetilsyn.shouldNotBeNull()
            valideringsfeil.stønadTilBarnetilsyn.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.stønadTilBarnetilsyn.fremtidigePerioder
                .first()
                .fom shouldBe fom
            valideringsfeil.stønadTilBarnetilsyn.fremtidigePerioder
                .first()
                .tom shouldBe null
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.stønadTilBarnetilsyn.shouldNotBeNull()
            valideringsfeil.stønadTilBarnetilsyn.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.stønadTilBarnetilsyn.fremtidigePerioder
                .first()
                .fom shouldBe fom
            valideringsfeil.stønadTilBarnetilsyn.fremtidigePerioder
                .first()
                .tom shouldBe ugyldigTom
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.stønadTilBarnetilsyn.shouldNotBeNull()
            valideringsfeil.stønadTilBarnetilsyn.overlappendePerioder.size shouldBe 1
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.stønadTilBarnetilsyn.shouldBeNull()
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
                .fom shouldBe
                LocalDate
                    .now()
                    .withDayOfMonth(2)
            valideringsfeil.fremtidigePerioder
                .first()
                .tom shouldBe null
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

            underholdskostnad.tilleggsstønad.addAll(tilleggsstønad)
            // hvis
            val valideringsfeil = underholdskostnad.valider()
            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift shouldHaveSize 1
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .first()
                .fom shouldBe tilleggsstønad.first().fom
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

            u.tilleggsstønad.addAll(tilleggsstønad)
            // hvis
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.shouldNotBeNull()
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift shouldHaveSize 2
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .minBy { it.fom }
                .fom shouldBe fomdatoFørstePeriodeUtenTilsynsutgift
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .maxBy { it.fom }
                .fom shouldBe fomdatoAndrePeriodeUtenTilsynsutgift
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

            u.tilleggsstønad.addAll(tilleggsstønad)
            // hvis
            val valideringsfeil = u.valider()
            // så
            valideringsfeil.tilleggsstønad.shouldNotBeNull()
            valideringsfeil.tilleggsstønad.fremtidigePerioder shouldHaveSize 1
            valideringsfeil.tilleggsstønad.fremtidigePerioder
                .first()
                .fom shouldBe tilleggsstønad.first().fom
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift shouldHaveSize 1
            valideringsfeil.tilleggsstønadsperioderUtenFaktiskTilsynsutgift
                .first()
                .fom shouldBe tilleggsstønad.first().fom
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.tilleggsstønad.shouldBeNull()
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.tilleggsstønad.shouldNotBeNull()
            valideringsfeil.tilleggsstønad.fremtidigePerioder.shouldHaveSize(1)
            valideringsfeil.tilleggsstønad.fremtidigePerioder
                .first()
                .tom shouldBe LocalDate.now().withDayOfMonth(1)
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
            val valideringsfeil = u.valider()

            // så
            valideringsfeil.tilleggsstønad.shouldNotBeNull()

            assertSoftly(valideringsfeil.tilleggsstønad.overlappendePerioder) {
                size shouldBe 1
                first() shouldBe DatoperiodeDto(fom, tom)
            }

            assertSoftly(
                valideringsfeil.tilleggsstønad.overlappendePerioder.map { it.periode },
            ) {
                shouldHaveSize(2)
                minBy { it.fom }.fom shouldBe fomLavesteTilOverlappendePeriode
                maxBy { it.fom }.fom shouldBe fomHøyesteTilOverlappendePeriode
            }
        }
    }
}
