package no.nav.bidrag.behandling.transformers.validering

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.behandling.mapValideringsfeilForYtelseSomGjelderBarn
import no.nav.bidrag.behandling.transformers.behandling.mapValideringsfeilForÅrsinntekter
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.finnOverlappendePerioderInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.random.Random

class ValiderInntektPerioderTest {
    @Nested
    inner class ValiderInntekter {
        @Test
        fun `Skal validere inntekter hvis gyldig`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-03"),
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-03"),
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.KAPITALINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-04"),
                        YearMonth.parse("2022-06"),
                        ident = bmIdent,
                        taMed = false,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-04"),
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        ident = bmIdent,
                        gjelderBarn = barn2Ident,
                        taMed = true,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                )

            val resultat = inntekter.mapValideringsfeilForÅrsinntekter(LocalDate.parse("2022-01-01"), roller).toList()
            resultat shouldHaveSize 0
        }

        @Test
        fun `Skal feile validering av inntekter hvis datoFom er etter dagens dato`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        YearMonth.now().plusMonths(1),
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                )

            val resultat = inntekter.mapValideringsfeilForÅrsinntekter(LocalDate.parse("2022-01-01"), roller).toList()
            resultat shouldHaveSize 1
            assertSoftly(resultat[0]) {
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 1
                manglerPerioder shouldBe false
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe true
                harFeil shouldBe true
                ident shouldBe bmIdent
                rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
            }
        }

        @Test
        fun `Skal feile validering av inntekter hvis datoFom er etter virkningstidspunkt når virkningstidspunkt er fram i tid`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        YearMonth.now().plusMonths(3),
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                )

            val resultat = inntekter.mapValideringsfeilForÅrsinntekter(LocalDate.now().plusMonths(1), roller).toList()
            resultat shouldHaveSize 1
            assertSoftly(resultat[0]) {
                hullIPerioder shouldHaveSize 1
                overlappendePerioder shouldHaveSize 0
                manglerPerioder shouldBe false
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe true
                harFeil shouldBe true
                ident shouldBe bmIdent
                rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
            }
        }

        @Test
        fun `Skal validere av inntekter hvis datoFom er etter dagens dato og virkingstidspunkt er fram i tid`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        YearMonth.now().plusMonths(1),
                        YearMonth.now().plusYears(1).minusMonths(1),
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.now().plusYears(1),
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                )

            val resultat = inntekter.mapValideringsfeilForÅrsinntekter(LocalDate.now().plusYears(1), roller).toList()
            resultat shouldHaveSize 0
        }

        @Test
        fun `Skal validere inntekter hvis ingen periode`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        YearMonth.parse("2022-02"),
                        YearMonth.parse("2022-03"),
                        ident = bmIdent,
                        taMed = false,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-04"),
                        YearMonth.parse("2022-06"),
                        ident = bmIdent,
                        taMed = false,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-08"),
                        YearMonth.parse("2022-09"),
                        ident = bmIdent,
                        taMed = false,
                    ),
                )

            val resultat = inntekter.mapValideringsfeilForÅrsinntekter(LocalDate.parse("2022-01-01"), roller).toList()
            resultat shouldHaveSize 1

            assertSoftly(resultat[0]) {
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 0
                manglerPerioder shouldBe true
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                harFeil shouldBe true
                ident shouldBe bmIdent
                rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
            }
        }

        @Test
        fun `Skal validere inntekter hvis ingen periode for BP i Særbidrag`() {
            val bmIdent = "31233123"
            val bpIdent = "31233333123"
            val barnIdent = "21333123"
            val virkningstidspunkt = YearMonth.now()
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(bpIdent, Rolletype.BIDRAGSPLIKTIG),
                    opprettRolle(barnIdent, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        Inntektsrapportering.INNTEKTSOPPLYSNINGER_FRA_ARBEIDSGIVER,
                        ident = bmIdent,
                        taMed = true,
                    ),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        Inntektsrapportering.AINNTEKT,
                        ident = bmIdent,
                        taMed = false,
                    ),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        Inntektsrapportering.AINNTEKT,
                        ident = bpIdent,
                        taMed = false,
                    ),
                )

            val resultat =
                inntekter
                    .mapValideringsfeilForÅrsinntekter(
                        virkningstidspunkt.atDay(1),
                        roller,
                        TypeBehandling.SÆRBIDRAG,
                    ).toList()
            resultat shouldHaveSize 1

            assertSoftly(resultat[0]) {
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 0
                manglerPerioder shouldBe true
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                harFeil shouldBe true
                ident shouldBe bpIdent
                rolle!!.rolletype shouldBe Rolletype.BIDRAGSPLIKTIG
            }
        }

        @Test
        fun `Skal validere inntekter hvis barn har inntekter med hull i periode og behandling er av type SÆRBIDRAG`() {
            val bmIdent = "31233123"
            val bpIdent = "3333"
            val barnIdent = "21333123"
            val virkningstidspunkt = YearMonth.now()
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(bpIdent, Rolletype.BIDRAGSPLIKTIG),
                    opprettRolle(barnIdent, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(virkningstidspunkt, null, ident = bmIdent, taMed = true),
                    opprettInntekt(virkningstidspunkt, null, ident = bpIdent, taMed = true),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        Inntektsrapportering.AINNTEKT,
                        ident = barnIdent,
                        taMed = true,
                    ),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        Inntektsrapportering.AINNTEKT,
                        ident = barnIdent,
                        taMed = true,
                    ),
                )

            val resultat =
                inntekter
                    .mapValideringsfeilForÅrsinntekter(
                        virkningstidspunkt.atDay(1),
                        roller,
                        TypeBehandling.SÆRBIDRAG,
                    ).toList()
            resultat shouldHaveSize 1

            assertSoftly(resultat[0]) {
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 1
                assertSoftly(overlappendePerioder.first().periode) {
                    fom shouldBe virkningstidspunkt.atDay(1)
                    til shouldBe null
                }
                manglerPerioder shouldBe false
                ingenLøpendePeriode shouldBe false
                fremtidigPeriode shouldBe false
                harFeil shouldBe true
                ident shouldBe barnIdent
                rolle!!.rolletype shouldBe Rolletype.BARN
            }
        }

        @Test
        fun `Skal ikke validere inntekter hvis barn har inntekter med hull i periode og behandling er av type FORSKUDD`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(YearMonth.parse("2022-01"), null, ident = bmIdent, taMed = true),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-06"),
                        ident = barnIdent,
                        taMed = true,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-08"),
                        YearMonth.parse("2022-09"),
                        ident = bmIdent,
                        taMed = false,
                    ),
                )

            val resultat =
                inntekter
                    .mapValideringsfeilForÅrsinntekter(
                        LocalDate.parse("2022-01-01"),
                        roller,
                        TypeBehandling.FORSKUDD,
                    ).toList()
            resultat shouldHaveSize 0
        }

        @Test
        fun `Skal validere inntekter for ytelser`() {
            val bmIdent = "31233123"
            val barnIdent = "21333123"
            val barn2Ident = "44444"
            val roller =
                setOf(
                    opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                    opprettRolle(barnIdent, Rolletype.BARN),
                    opprettRolle(barn2Ident, Rolletype.BARN),
                )
            val inntekter =
                setOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        ident = bmIdent,
                        gjelderBarn = barnIdent,
                        taMed = true,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        ident = bmIdent,
                        gjelderBarn = barn2Ident,
                        taMed = true,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-05"),
                        null,
                        ident = bmIdent,
                        gjelderBarn = barn2Ident,
                        taMed = true,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                )

            val resultat =
                inntekter
                    .mapValideringsfeilForYtelseSomGjelderBarn(
                        Inntektsrapportering.BARNETILLEGG,
                        LocalDate.parse("2022-01-01"),
                        roller,
                    ).toList()
            resultat shouldHaveSize 1

            assertSoftly(resultat[0]) {
                overlappendePerioder shouldHaveSize 1
                assertSoftly(overlappendePerioder.toList()[0]) {
                    periode.fom shouldBe LocalDate.parse("2022-05-01")
                    periode.til shouldBe null
                }
                harFeil shouldBe true
                fremtidigPeriode shouldBe false
                ident shouldBe bmIdent
                gjelderBarn shouldBe barn2Ident
                rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
            }
        }
    }

    @Nested
    inner class HullIPerioderTester {
        @Test
        fun `skal finne hull i perioder`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-02"), YearMonth.parse("2022-03")),
                    opprettInntekt(YearMonth.parse("2022-04"), YearMonth.parse("2022-06")),
                    opprettInntekt(YearMonth.parse("2022-08"), YearMonth.parse("2022-09")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2022-01-01"))

            hullPerioder shouldHaveSize 3
            hullPerioder[0].fom shouldBe LocalDate.parse("2022-01-01")
            hullPerioder[0].til shouldBe LocalDate.parse("2022-02-01")

            hullPerioder[1].fom shouldBe LocalDate.parse("2022-07-01")
            hullPerioder[1].til shouldBe LocalDate.parse("2022-08-01")

            hullPerioder[2].fom shouldBe LocalDate.parse("2022-09-30")
            hullPerioder[2].til shouldBe null
        }

        @Test
        fun `skal finne hull i perioder scenarie 2`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), YearMonth.parse("2022-12")),
                    opprettInntekt(YearMonth.parse("2023-12"), YearMonth.parse("2024-03")),
                    opprettInntekt(YearMonth.parse("2023-03"), YearMonth.parse("2024-02")),
                    opprettInntekt(YearMonth.parse("2023-12"), YearMonth.parse("2024-01")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-12-01")).toList()

            hullPerioder shouldHaveSize 1
            hullPerioder[0].fom shouldBe LocalDate.parse("2024-01-31")
            hullPerioder[0].til shouldBe null
        }

        @Test
        fun `skal finne hull i perioder scenarie 3`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), null),
                    opprettInntekt(YearMonth.parse("2023-01"), YearMonth.parse("2023-02")),
                    opprettInntekt(YearMonth.parse("2023-04"), YearMonth.parse("2023-08")),
                    opprettInntekt(YearMonth.parse("2023-12"), YearMonth.parse("2024-01")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-12-01")).toList()

            hullPerioder shouldHaveSize 0
        }

        @Test
        fun `skal finne hull i perioder scenarie 4`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2023-01"), YearMonth.parse("2023-02")),
                    opprettInntekt(YearMonth.parse("2023-04"), YearMonth.parse("2023-08")),
                    opprettInntekt(YearMonth.parse("2023-12"), YearMonth.parse("2024-01")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-01-01")).toList()

            hullPerioder shouldHaveSize 3
            hullPerioder[0].fom shouldBe LocalDate.parse("2023-03-01")
            hullPerioder[0].til shouldBe LocalDate.parse("2023-04-01")

            hullPerioder[1].fom shouldBe LocalDate.parse("2023-09-01")
            hullPerioder[1].til shouldBe LocalDate.parse("2023-12-01")

            hullPerioder[2].fom shouldBe LocalDate.parse("2024-01-31")
            hullPerioder[2].til shouldBe null
        }

        @Test
        fun `skal finne hull i perioder scenarie 5`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), YearMonth.parse("2022-02")),
                    opprettInntekt(YearMonth.parse("2022-05"), YearMonth.parse("2022-08")),
                    opprettInntekt(YearMonth.parse("2023-01"), YearMonth.parse("2023-02")),
                    opprettInntekt(YearMonth.parse("2023-03"), YearMonth.parse("2023-08")),
                    opprettInntekt(YearMonth.parse("2023-08"), YearMonth.parse("2024-01")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-01-01")).toList()

            hullPerioder shouldHaveSize 1
            hullPerioder[0].fom shouldBe LocalDate.parse("2024-01-31")
            hullPerioder[0].til shouldBe null
        }

        @Test
        fun `skal finne hull i perioder scenarie 6`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), YearMonth.parse("2023-05")),
                    opprettInntekt(YearMonth.parse("2023-01"), YearMonth.parse("2023-02")),
                    opprettInntekt(YearMonth.parse("2023-04"), YearMonth.parse("2023-08")),
                    opprettInntekt(YearMonth.parse("2023-08"), YearMonth.parse("2023-09")),
                    opprettInntekt(YearMonth.parse("2023-12"), YearMonth.parse("2024-01")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-01-01")).toList()

            hullPerioder shouldHaveSize 2
            hullPerioder[0].fom shouldBe LocalDate.parse("2023-10-01")
            hullPerioder[0].til shouldBe LocalDate.parse("2023-12-01")

            hullPerioder[1].fom shouldBe LocalDate.parse("2024-01-31")
            hullPerioder[1].til shouldBe null
        }

        @Test
        fun `skal finne hull i perioder scenarie 7`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), YearMonth.parse("2022-09")),
                    opprettInntekt(YearMonth.parse("2022-07"), YearMonth.parse("2023-05")),
                    opprettInntekt(YearMonth.parse("2023-03"), YearMonth.parse("2023-07")),
                    opprettInntekt(YearMonth.parse("2023-09"), YearMonth.parse("2024-02")),
                    opprettInntekt(YearMonth.parse("2023-10"), YearMonth.parse("2024-01")),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2022-01-01")).toList()

            hullPerioder shouldHaveSize 2
            hullPerioder[0].fom shouldBe LocalDate.parse("2023-08-01")
            hullPerioder[0].til shouldBe LocalDate.parse("2023-09-01")

            hullPerioder[1].fom shouldBe LocalDate.parse("2024-01-31")
            hullPerioder[1].til shouldBe null
        }

        @Test
        fun `skal ikke finne hull i perioder hvis inntekter er tomt`() {
            val inntekter = emptyList<Inntekt>()

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-01-01")).toList()

            hullPerioder shouldHaveSize 0
        }

        @Test
        fun `skal ikke finne hull i perioder hvis det bare finnes ett inntekt`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), null),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2023-01-01")).toList()

            hullPerioder shouldHaveSize 0
        }

        @Test
        fun `skal ikke finne hull i perioder når det er en periode med null datoTom`() {
            val inntekter =
                listOf(
                    opprettInntekt(YearMonth.parse("2022-01"), YearMonth.parse("2022-03")),
                    opprettInntekt(YearMonth.parse("2022-08"), YearMonth.parse("2022-12")),
                    opprettInntekt(YearMonth.parse("2022-04"), null),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2022-01-01"))

            hullPerioder shouldHaveSize 0
        }

        @Test
        fun `skal ikke finne hull i perioder for ytelser`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-02"),
                        YearMonth.parse("2022-03"),
                        type = Inntektsrapportering.BARNETILLEGG,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-06"),
                        YearMonth.parse("2022-08"),
                        type = Inntektsrapportering.BARNETILLEGG,
                    ),
                )

            val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2022-01-01"))

            hullPerioder shouldHaveSize 0
        }
    }

    @Nested
    @Disabled("")
    inner class OverlappendePerioderTest {
        @Test
        fun `skal finne overlappende perioder scenarie 2`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-12"),
                        type = Inntektsrapportering.AINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2023-01"),
                        YearMonth.parse("2023-12"),
                        type = Inntektsrapportering.AINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2023-03"),
                        YearMonth.parse("2024-02"),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2023-12"),
                        YearMonth.parse("2024-02"),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 1
            assertSoftly(overlappendePerioder[0]) {
                periode.fom shouldBe LocalDate.parse("2023-03-01")
                periode.til shouldBe LocalDate.parse("2024-02-29")
                rapporteringTyper shouldHaveSize 3
                idListe shouldHaveSize 3
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.AINNTEKT,
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    )
            }
        }

        @Test
        fun `skal finne overlappende perioder scenarie 3`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.AINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.KAPITALINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.KAPITALINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 2
            assertSoftly(overlappendePerioder[0]) {
                periode.fom shouldBe LocalDate.parse("2022-01-01")
                periode.til shouldBe null
                rapporteringTyper shouldHaveSize 1
                idListe shouldHaveSize 2
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.KAPITALINNTEKT,
                    )
            }
            assertSoftly(overlappendePerioder[1]) {
                periode.fom shouldBe LocalDate.parse("2022-01-01")
                periode.til shouldBe null
                rapporteringTyper shouldHaveSize 4
                idListe shouldHaveSize 4
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.AINNTEKT,
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        Inntektsrapportering.LIGNINGSINNTEKT,
                    )
            }
        }

        @Test
        fun `skal ikke finne overlappende perioder hvis inntekspostene ikke overlapper`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-05"),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        inntektstyper = listOf(Inntektstype.UTVIDET_BARNETRYGD),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 0
        }

        @Test
        fun `skal finne overlappende perioder for barnetillegg`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-05"),
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_AAP),
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 1
            assertSoftly(overlappendePerioder[0]) {
                periode.fom shouldBe LocalDate.parse("2022-01-01")
                periode.til shouldBe LocalDate.parse("2022-05-31")
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.BARNETILLEGG,
                    )
                inntektstyper shouldContainAll listOf(Inntektstype.BARNETILLEGG_PENSJON)
            }
        }

        @Test
        fun `skal ikke finne overlappende perioder for barnetillegg hvis inntekstypene ikke overlapper`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-05"),
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_AAP),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_DNB),
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.BARNETILLEGG,
                        inntektstyper = listOf(Inntektstype.BARNETILLEGG_DAGPENGER),
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 0
        }

        @Test
        @Disabled("Fungerer ikke i github")
        fun `skal finne overlappende perioder`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        YearMonth.parse("2022-05"),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-03"),
                        null,
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-04"),
                        null,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-08"),
                        YearMonth.parse("2022-11"),
                        type = Inntektsrapportering.KAPITALINNTEKT,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-09"),
                        YearMonth.parse("2022-10"),
                        type = Inntektsrapportering.KAPITALINNTEKT_EGNE_OPPLYSNINGER,
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 2
            assertSoftly(overlappendePerioder[0]) {
                periode.fom shouldBe LocalDate.parse("2022-03-01")
                periode.til shouldBe null
                idListe shouldHaveSize 3
                rapporteringTyper shouldHaveSize 3
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    )
            }
            assertSoftly(overlappendePerioder[1]) {
                periode.fom shouldBe LocalDate.parse("2022-09-01")
                periode.til shouldBe LocalDate.parse("2022-10-31")
                rapporteringTyper shouldHaveSize 2
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.KAPITALINNTEKT,
                        Inntektsrapportering.KAPITALINNTEKT_EGNE_OPPLYSNINGER,
                    )
            }
        }

        @Test
        fun `skal finne overlappende perioder og merge sammen`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 1
            assertSoftly(overlappendePerioder[0]) {
                periode.fom shouldBe LocalDate.parse("2022-01-01")
                periode.til shouldBe null
                rapporteringTyper shouldHaveSize 1
                idListe shouldHaveSize 3
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.UTVIDET_BARNETRYGD,
                    )
            }
        }

        @Test
        fun `skal finne overlappende perioder og merge sammen 2`() {
            val inntekter =
                listOf(
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-01"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                    opprettInntekt(
                        YearMonth.parse("2022-02"),
                        null,
                        type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    ),
                )

            val overlappendePerioder = inntekter.finnOverlappendePerioderInntekt().toList()

            overlappendePerioder shouldHaveSize 1
            assertSoftly(overlappendePerioder[0]) {
                periode.fom shouldBe LocalDate.parse("2022-01-01")
                periode.til shouldBe null
                rapporteringTyper shouldHaveSize 1
                idListe shouldHaveSize 4
                rapporteringTyper shouldContainAll
                    listOf(
                        Inntektsrapportering.UTVIDET_BARNETRYGD,
                    )
            }
        }
    }

    @Test
    fun `Skal feile validering av inntekter hvis siste periode ikke slutter med opphørsdato hvis opphørsdato er tilbake i tid`() {
        val bmIdent = "31233123"
        val barnIdent = "21333123"
        val barn2Ident = "44444"
        val behandling = oppretteBehandling()
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().minusMonths(3), behandling),
            )
        behandling.roller = roller
        val inntekter =
            setOf(
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    null,
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
            )

        val resultat = inntekter.mapValideringsfeilForÅrsinntekter(YearMonth.now().minusMonths(5).atDay(1), roller).toList()
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            hullIPerioder shouldHaveSize 0
            overlappendePerioder shouldHaveSize 0
            manglerPerioder shouldBe false
            ingenLøpendePeriode shouldBe false
            ugyldigSluttPeriode shouldBe true
            harFeil shouldBe true
            ident shouldBe bmIdent
            rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
        }
    }

    @Test
    fun `Skal feile validering av inntekter hvis siste periode ikke er løpende hvis opphørsdato er fram i tid`() {
        val bmIdent = "31233123"
        val barnIdent = "21333123"
        val barn2Ident = "44444"
        val behandling = oppretteBehandling()
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().plusMonths(1).withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        val inntekter =
            setOf(
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    YearMonth.now(),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
            )

        val resultat = inntekter.mapValideringsfeilForÅrsinntekter(YearMonth.now().minusMonths(5).atDay(1), roller).toList()
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            hullIPerioder shouldHaveSize 1
            overlappendePerioder shouldHaveSize 0
            manglerPerioder shouldBe false
            ingenLøpendePeriode shouldBe true
            ugyldigSluttPeriode shouldBe false
            harFeil shouldBe true
            ident shouldBe bmIdent
            rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
        }
    }

    @Test
    fun `Skal validere gyldig resultat av inntekter hvis siste periode slutter måneden før opphørsdato`() {
        val bmIdent = "31233123"
        val barnIdent = "21333123"
        val barn2Ident = "44444"
        val behandling = oppretteBehandling()
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().minusMonths(2).withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        val inntekter =
            setOf(
                opprettInntekt(
                    YearMonth.now().minusMonths(6),
                    YearMonth.now().minusMonths(5),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(4),
                    YearMonth.now().minusMonths(3),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
            )

        val resultat = inntekter.mapValideringsfeilForÅrsinntekter(YearMonth.now().minusMonths(6).atDay(1), roller).toList()
        resultat shouldHaveSize 0
    }

    @Test
    fun `Skal validere gyldig resultat av inntekter når opphørsdato er inneværende måned`() {
        val bmIdent = "31233123"
        val barnIdent = "21333123"
        val barn2Ident = "44444"
        val behandling = oppretteBehandling()
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().plusMonths(1).withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        val inntekter =
            setOf(
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    null,
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
            )

        val resultat = inntekter.mapValideringsfeilForÅrsinntekter(YearMonth.now().minusMonths(5).atDay(1), roller).toList()
        resultat shouldHaveSize 0
    }

    @Test
    fun `Skal validere gyldig resultat av inntekter når opphørsdato tilbake i tid for ene barnet men ingen opphør for andre`() {
        val bmIdent = "31233123"
        val barnIdent = "21333123"
        val barn2Ident = "44444"
        val behandling = oppretteBehandling()
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barnIdent, Rolletype.BARN, null, behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().minusMonths(4), behandling),
            )
        behandling.roller = roller
        val inntekter =
            setOf(
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    null,
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
            )

        val resultat = inntekter.mapValideringsfeilForÅrsinntekter(YearMonth.now().minusMonths(5).atDay(1), roller).toList()
        resultat shouldHaveSize 0
    }

    @Test
    fun `Skal ikke validere resultat av inntekter for ytelse som gjelder barn når opphørsdato for barnet er satt`() {
        val bmIdent = "31233123"
        val barnIdent = "21333123"
        val barn2Ident = "44444"
        val barn3Ident = "123213213"
        val behandling = oppretteBehandling()
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barnIdent, Rolletype.BARN, LocalDate.now().plusMonths(1).withDayOfMonth(1), behandling),
                opprettRolle(barn3Ident, Rolletype.BARN, LocalDate.now().minusMonths(1).withDayOfMonth(1), behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().minusMonths(3).withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        val inntekter =
            setOf(
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    gjelderBarn = barn3Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    YearMonth.now().minusMonths(2),
                    ident = bmIdent,
                    gjelderBarn = barn3Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    gjelderBarn = barnIdent,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    null,
                    ident = bmIdent,
                    gjelderBarn = barnIdent,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().minusMonths(4),
                    ident = bmIdent,
                    gjelderBarn = barn2Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    YearMonth.now().minusMonths(3),
                    null,
                    ident = bmIdent,
                    gjelderBarn = barn2Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
            )

        val resultat = inntekter.mapValideringsfeilForYtelseSomGjelderBarn(Inntektsrapportering.BARNETILLEGG, YearMonth.now().minusMonths(5).atDay(1), roller).toList()
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            hullIPerioder shouldHaveSize 0
            ugyldigSluttPeriode shouldBe true
            gjelderBarn shouldBe barn2Ident
        }
    }
}

fun opprettRolle(
    ident: String,
    rolletype: Rolletype,
    opphørsdato: LocalDate? = null,
    behandling: Behandling = oppretteBehandling(),
) = Rolle(
    id = Random.nextLong(1000),
    navn = "Test 1",
    ident = ident,
    rolletype = rolletype,
    behandling = behandling,
    fødselsdato = LocalDate.parse("2020-01-01"),
    opprettet = LocalDateTime.now(),
    opphørsdato = opphørsdato,
)

fun opprettInntekt(
    datoFom: YearMonth,
    datoTom: YearMonth?,
    type: Inntektsrapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
    inntektstyper: List<Inntektstype> = emptyList(),
    ident: String = "",
    gjelderBarn: String? = null,
    taMed: Boolean = true,
    behandling: Behandling = oppretteBehandling(),
) = Inntekt(
    datoFom = datoFom.atDay(1),
    datoTom = datoTom?.atEndOfMonth(),
    belop = BigDecimal.ONE,
    ident = ident,
    behandling = behandling,
    gjelderBarn = gjelderBarn,
    id = Random.nextLong(1000),
    kilde = Kilde.OFFENTLIG,
    taMed = taMed,
    type = type,
    inntektsposter =
        inntektstyper
            .map {
                Inntektspost(
                    beløp = BigDecimal.ONE,
                    inntektstype = it,
                    kode = "",
                )
            }.toMutableSet(),
)
