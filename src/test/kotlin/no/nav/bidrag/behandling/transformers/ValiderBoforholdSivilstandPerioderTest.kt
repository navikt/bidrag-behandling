package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.tid.Datoperiode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

class ValiderBoforholdSivilstandPerioderTest {
    @Nested
    inner class ValiderBoforholdPerioder {
        @Test
        fun `skal validere boforhold`() {
            val boforholdListe =
                mutableSetOf(
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2022-01").atDay(1),
                                YearMonth.parse("2022-01").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-02").atDay(1),
                                YearMonth.parse("2022-04").atEndOfMonth(),
                            ) to Bostatuskode.MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-05").atDay(1),
                                null,
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                        ),
                        "barn1",
                        fødselsdato = LocalDate.parse("2020-01-01"),
                    ),
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2022-01").atDay(1),
                                YearMonth.parse("2022-01").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-02").atDay(1),
                                YearMonth.parse("2022-04").atEndOfMonth(),
                            ) to Bostatuskode.MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-05").atDay(1),
                                YearMonth.parse("2023-01").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                        ),
                        "barn2",
                        fødselsdato = LocalDate.parse("2020-01-01"),
                    ),
                    opprettHusstandsbarn(
                        listOf(),
                        "barn3",
                        fødselsdato = LocalDate.parse("2020-01-02"),
                    ),
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2022-02").atDay(1),
                                null,
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                        ),
                        "barn4",
                        fødselsdato = LocalDate.parse("2020-01-03"),
                    ),
                )

            val result = boforholdListe.validerBoforhold(LocalDate.parse("2022-01-01"))

            result shouldHaveSize 4
            assertSoftly(result.toList()[0]) {
                barn!!.ident shouldBe "barn1"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-01")
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe false
            }
            assertSoftly(result.toList()[1]) {
                barn!!.ident shouldBe "barn2"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-01")
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 1
                hullIPerioder[0].fom shouldBe LocalDate.parse("2023-01-31")
                hullIPerioder[0].til shouldBe null
                ingenLøpendePeriode shouldBe true
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true
            }
            assertSoftly(result.toList()[2]) {
                barn!!.ident shouldBe "barn3"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-02")
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe true
                harFeil shouldBe true
            }
            assertSoftly(result.toList()[3]) {
                barn!!.ident shouldBe "barn4"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-03")
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 1
                hullIPerioder[0].fom shouldBe LocalDate.parse("2022-01-01")
                hullIPerioder[0].til shouldBe LocalDate.parse("2022-02-01")
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true
            }
        }

        @Test
        fun `skal validere boforhold hvis barn er født etter virkningstidspunkt`() {
            val boforholdListe =
                mutableSetOf(
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2023-01").atDay(1),
                                YearMonth.parse("2023-02").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(YearMonth.parse("2023-02").atDay(1), null) to Bostatuskode.MED_FORELDER,
                        ),
                        "barn1",
                        fødselsdato = LocalDate.parse("2023-01-01"),
                    ),
                )

            val result = boforholdListe.validerBoforhold(LocalDate.parse("2022-01-01"))

            result shouldHaveSize 1
            assertSoftly(result.toList()[0]) {
                barn!!.ident shouldBe "barn1"
                barn!!.fødselsdato shouldBe LocalDate.parse("2023-01-01")
                overlappendePerioder shouldHaveSize 1
                hullIPerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true
            }
        }

        @Test
        fun `skal validere boforhold hvis periode starter senere enn dagens dato`() {
            val boforholdListe =
                mutableSetOf(
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.now().plusMonths(1).atDay(1),
                                YearMonth.now().plusMonths(2).atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(YearMonth.parse("2022-01").atDay(1), null) to Bostatuskode.MED_FORELDER,
                        ),
                        "barn1",
                        fødselsdato = LocalDate.parse("2021-01-01"),
                    ),
                )

            val result = boforholdListe.validerBoforhold(LocalDate.parse("2022-01-01"))

            result shouldHaveSize 1
            assertSoftly(result.toList()[0]) {
                barn!!.ident shouldBe "barn1"
                barn!!.fødselsdato shouldBe LocalDate.parse("2021-01-01")
                overlappendePerioder shouldHaveSize 1
                hullIPerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe true
                manglerPerioder shouldBe false
                harFeil shouldBe true
            }
        }

        @Test
        fun `skal validere boforhold hvis en eller flere perioder overlapper`() {
            val boforholdListe =
                mutableSetOf(
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2022-01").atDay(1),
                                YearMonth.parse("2022-04").atEndOfMonth(),
                            ) to Bostatuskode.MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-02").atDay(1),
                                YearMonth.parse("2022-09").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-03").atDay(1),
                                null,
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                        ),
                        "barn1",
                        fødselsdato = LocalDate.parse("2020-01-01"),
                    ),
                )

            val result = boforholdListe.validerBoforhold(LocalDate.parse("2022-01-01"))

            result shouldHaveSize 1
            assertSoftly(result.toList()[0]) {
                barn!!.ident shouldBe "barn1"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-01")
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 3
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true

                assertSoftly(overlappendePerioder[0]) {
                    periode.fom shouldBe LocalDate.parse("2022-02-01")
                    periode.til shouldBe LocalDate.parse("2022-04-30")
                    bosstatus shouldHaveSize 2
                    bosstatus.contains(Bostatuskode.MED_FORELDER) shouldBe true
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
                assertSoftly(overlappendePerioder[1]) {
                    periode.fom shouldBe LocalDate.parse("2022-03-01")
                    periode.til shouldBe LocalDate.parse("2022-04-30")
                    bosstatus shouldHaveSize 2
                    bosstatus.contains(Bostatuskode.MED_FORELDER) shouldBe true
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
                assertSoftly(overlappendePerioder[2]) {
                    periode.fom shouldBe LocalDate.parse("2022-03-01")
                    periode.til shouldBe LocalDate.parse("2022-09-30")
                    bosstatus shouldHaveSize 1
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
            }
        }

        @Test
        fun `skal validere boforhold hvis en eller flere perioder overlapper scenarie 2`() {
            val boforholdListe =
                mutableSetOf(
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2022-01").atDay(1),
                                YearMonth.parse("2022-04").atEndOfMonth(),
                            ) to Bostatuskode.MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-02").atDay(1),
                                YearMonth.parse("2022-09").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-03").atDay(1),
                                null,
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                        ),
                        "barn1",
                        fødselsdato = LocalDate.parse("2020-01-01"),
                    ),
                )

            val result = boforholdListe.validerBoforhold(LocalDate.parse("2022-01-01"))

            result shouldHaveSize 1
            assertSoftly(result.toList()[0]) {
                barn!!.ident shouldBe "barn1"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-01")
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 3
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true

                assertSoftly(overlappendePerioder[0]) {
                    periode.fom shouldBe LocalDate.parse("2022-02-01")
                    periode.til shouldBe LocalDate.parse("2022-04-30")
                    bosstatus shouldHaveSize 2
                    bosstatus.contains(Bostatuskode.MED_FORELDER) shouldBe true
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
                assertSoftly(overlappendePerioder[1]) {
                    periode.fom shouldBe LocalDate.parse("2022-03-01")
                    periode.til shouldBe LocalDate.parse("2022-04-30")
                    bosstatus shouldHaveSize 2
                    bosstatus.contains(Bostatuskode.MED_FORELDER) shouldBe true
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
                assertSoftly(overlappendePerioder[2]) {
                    periode.fom shouldBe LocalDate.parse("2022-03-01")
                    periode.til shouldBe LocalDate.parse("2022-09-30")
                    bosstatus shouldHaveSize 1
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
            }
        }

        @Test
        fun `skal validere boforhold hvis en eller flere perioder overlapper scenarie 3`() {
            val boforholdListe =
                mutableSetOf(
                    opprettHusstandsbarn(
                        listOf(
                            Datoperiode(
                                YearMonth.parse("2023-01").atDay(1),
                                YearMonth.parse("2023-04").atEndOfMonth(),
                            ) to Bostatuskode.MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-01").atDay(1),
                                YearMonth.parse("2022-09").atEndOfMonth(),
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                            Datoperiode(
                                YearMonth.parse("2022-09").atDay(1),
                                null,
                            ) to Bostatuskode.IKKE_MED_FORELDER,
                        ),
                        "barn1",
                        fødselsdato = LocalDate.parse("2020-01-01"),
                    ),
                )

            val result = boforholdListe.validerBoforhold(LocalDate.parse("2022-01-01"))

            result shouldHaveSize 1
            assertSoftly(result.toList()[0]) {
                barn!!.ident shouldBe "barn1"
                barn!!.fødselsdato shouldBe LocalDate.parse("2020-01-01")
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 2
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true

                assertSoftly(overlappendePerioder[0]) {
                    periode.fom shouldBe LocalDate.parse("2022-09-01")
                    periode.til shouldBe LocalDate.parse("2022-09-30")
                    bosstatus shouldHaveSize 1
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
                assertSoftly(overlappendePerioder[1]) {
                    periode.fom shouldBe LocalDate.parse("2023-01-01")
                    periode.til shouldBe LocalDate.parse("2023-04-30")
                    bosstatus shouldHaveSize 2
                    bosstatus.contains(Bostatuskode.MED_FORELDER) shouldBe true
                    bosstatus.contains(Bostatuskode.IKKE_MED_FORELDER) shouldBe true
                }
            }
        }
    }

    @Nested
    inner class ValiderSivilstandPerioder {
        @Test
        fun `skal validere sivilstand til å være gyldig`() {
            val sivilstandListe =
                opprettSivilstand(
                    listOf(
                        Datoperiode(
                            YearMonth.parse("2022-01").atDay(1),
                            YearMonth.parse("2022-01").atEndOfMonth(),
                        ) to Sivilstandskode.GIFT_SAMBOER,
                        Datoperiode(
                            YearMonth.parse("2022-02").atDay(1),
                            YearMonth.parse("2022-05").atEndOfMonth(),
                        ) to Sivilstandskode.ENSLIG,
                        Datoperiode(
                            YearMonth.parse("2022-06").atDay(1),
                            YearMonth.parse("2024-01").atEndOfMonth(),
                        ) to Sivilstandskode.GIFT_SAMBOER,
                        Datoperiode(
                            YearMonth.parse("2024-02").atDay(1),
                            null,
                        ) to Sivilstandskode.ENSLIG,
                    ),
                )

            val result = sivilstandListe.validerSivilstand(LocalDate.parse("2022-01-01"))

            assertSoftly(result) {
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe false
            }
        }

        @Test
        fun `skal validere sivilstand`() {
            val sivilstandListe =
                opprettSivilstand(
                    listOf(
                        Datoperiode(
                            YearMonth.parse("2022-01").atDay(1),
                            YearMonth.parse("2022-01").atEndOfMonth(),
                        ) to Sivilstandskode.GIFT_SAMBOER,
                        Datoperiode(
                            YearMonth.parse("2022-02").atDay(1),
                            YearMonth.parse("2022-05").atEndOfMonth(),
                        ) to Sivilstandskode.ENSLIG,
                        Datoperiode(
                            YearMonth.parse("2022-06").atDay(1),
                            YearMonth.parse("2024-01").atEndOfMonth(),
                        ) to Sivilstandskode.GIFT_SAMBOER,
                        Datoperiode(
                            YearMonth.parse("2024-02").atDay(1),
                            YearMonth.parse("2024-03").atEndOfMonth(),
                        ) to Sivilstandskode.ENSLIG,
                    ),
                )

            val result = sivilstandListe.validerSivilstand(LocalDate.parse("2022-01-01"))

            assertSoftly(result) {
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 1
                hullIPerioder[0].fom shouldBe LocalDate.parse("2024-03-31")
                hullIPerioder[0].til shouldBe null
                ingenLøpendePeriode shouldBe true
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                harFeil shouldBe true
            }
        }

        @Test
        fun `skal validere sivilstand hvis fremtidig periode`() {
            val sivilstandListe =
                opprettSivilstand(
                    listOf(Datoperiode(YearMonth.now().plusMonths(1).atDay(1), null) to Sivilstandskode.GIFT_SAMBOER),
                )

            val result = sivilstandListe.validerSivilstand(LocalDate.parse("2022-01-01"))

            assertSoftly(result) {
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 1
                hullIPerioder[0].fom shouldBe LocalDate.parse("2022-01-01")
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe true
                manglerPerioder shouldBe false
                harFeil shouldBe true
            }
        }

        @Test
        fun `skal validere sivilstand hvis mangler periode`() {
            val sivilstandListe = opprettSivilstand(listOf())

            val result = sivilstandListe.validerSivilstand(LocalDate.parse("2022-01-01"))

            assertSoftly(result) {
                overlappendePerioder shouldHaveSize 0
                hullIPerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe true
                harFeil shouldBe true
            }
        }

        @Test
        fun `skal validere sivilstand hvis periodene overlapper`() {
            val sivilstandListe =
                opprettSivilstand(
                    listOf(
                        Datoperiode(
                            YearMonth.parse("2024-01").atDay(1),
                            null,
                        ) to Sivilstandskode.ENSLIG,
                        Datoperiode(
                            YearMonth.parse("2022-01").atDay(1),
                            YearMonth.parse("2022-04").atEndOfMonth(),
                        ) to Sivilstandskode.GIFT_SAMBOER,
                        Datoperiode(
                            YearMonth.parse("2022-02").atDay(1),
                            YearMonth.parse("2022-05").atEndOfMonth(),
                        ) to Sivilstandskode.ENSLIG,
                        Datoperiode(
                            YearMonth.parse("2022-05").atDay(1),
                            YearMonth.parse("2024-01").atEndOfMonth(),
                        ) to Sivilstandskode.GIFT_SAMBOER,
                    ),
                )

            val result = sivilstandListe.validerSivilstand(LocalDate.parse("2022-01-01"))

            assertSoftly(result) {
                hullIPerioder shouldHaveSize 0
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                overlappendePerioder shouldHaveSize 3
                harFeil shouldBe true

                assertSoftly(overlappendePerioder[0]) {
                    periode.fom shouldBe LocalDate.parse("2022-02-01")
                    periode.til shouldBe LocalDate.parse("2022-04-30")
                    sivilstandskode shouldHaveSize 2
                    sivilstandskode.contains(Sivilstandskode.GIFT_SAMBOER) shouldBe true
                    sivilstandskode.contains(Sivilstandskode.ENSLIG) shouldBe true
                }
                assertSoftly(overlappendePerioder[1]) {
                    periode.fom shouldBe LocalDate.parse("2022-05-01")
                    periode.til shouldBe LocalDate.parse("2022-05-31")
                    sivilstandskode shouldHaveSize 2
                    sivilstandskode.contains(Sivilstandskode.GIFT_SAMBOER) shouldBe true
                    sivilstandskode.contains(Sivilstandskode.ENSLIG) shouldBe true
                }
                assertSoftly(overlappendePerioder[2]) {
                    periode.fom shouldBe LocalDate.parse("2024-01-01")
                    periode.til shouldBe LocalDate.parse("2024-01-31")
                    sivilstandskode shouldHaveSize 2
                    sivilstandskode.contains(Sivilstandskode.GIFT_SAMBOER) shouldBe true
                    sivilstandskode.contains(Sivilstandskode.ENSLIG) shouldBe true
                }
            }
        }
    }

    fun opprettSivilstand(perioder: List<Pair<Datoperiode, Sivilstandskode>>): MutableSet<Sivilstand> {
        return perioder.map {
            Sivilstand(
                behandling = oppretteBehandling(),
                kilde = Kilde.MANUELL,
                id = Random.nextLong(1000),
                sivilstand = it.second,
                datoTom = it.first.til,
                datoFom = it.first.fom,
            )
        }.toMutableSet()
    }

    fun opprettHusstandsbarn(
        perioder: List<Pair<Datoperiode, Bostatuskode?>>,
        ident: String,
        fødselsdato: LocalDate = LocalDate.parse("2022-01-01"),
    ): Husstandsbarn =
        Husstandsbarn(
            behandling = oppretteBehandling(),
            kilde = Kilde.MANUELL,
            id = Random.nextLong(1000),
            ident = ident,
            navn = ident,
            fødselsdato = fødselsdato,
            perioder =
                perioder.map {
                    Husstandsbarnperiode(
                        husstandsbarn = opprettHusstandsbarn(oppretteBehandling(), testdataBP),
                        datoFom = it.first.fom,
                        datoTom = it.first.til,
                        bostatus = it.second ?: Bostatuskode.DELT_BOSTED,
                        kilde = Kilde.MANUELL,
                        id = Random.nextLong(1000),
                    )
                }.toMutableSet(),
        )
}
