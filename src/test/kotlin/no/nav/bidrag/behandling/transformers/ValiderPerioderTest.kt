package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

class ValiderPerioderTest {
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

    @Test
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

        val overlappendePerioder = inntekter.finnOverlappendePerioder().toList()

        overlappendePerioder shouldHaveSize 4
        assertSoftly(overlappendePerioder[0]) {
            periode.fom shouldBe LocalDate.parse("2022-03-01")
            periode.til shouldBe null
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                )
        }
        assertSoftly(overlappendePerioder[1]) {
            periode.fom shouldBe LocalDate.parse("2022-04-01")
            periode.til shouldBe null
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                )
        }
        assertSoftly(overlappendePerioder[2]) {
            periode.fom shouldBe LocalDate.parse("2022-04-01")
            periode.til shouldBe null
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                    Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                )
        }
        assertSoftly(overlappendePerioder[3]) {
            periode.fom shouldBe LocalDate.parse("2022-09-01")
            periode.til shouldBe LocalDate.parse("2022-10-31")
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.KAPITALINNTEKT,
                    Inntektsrapportering.KAPITALINNTEKT_EGNE_OPPLYSNINGER,
                )
        }
    }

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

        val overlappendePerioder = inntekter.finnOverlappendePerioder().toList()

        overlappendePerioder shouldHaveSize 3
        assertSoftly(overlappendePerioder[0]) {
            periode.fom shouldBe LocalDate.parse("2023-03-01")
            periode.til shouldBe LocalDate.parse("2023-12-31")
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.AINNTEKT,
                    Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                )
        }
        assertSoftly(overlappendePerioder[1]) {
            periode.fom shouldBe LocalDate.parse("2023-12-01")
            periode.til shouldBe LocalDate.parse("2023-12-31")
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.AINNTEKT,
                    Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                )
        }
        assertSoftly(overlappendePerioder[2]) {
            periode.fom shouldBe LocalDate.parse("2023-12-01")
            periode.til shouldBe LocalDate.parse("2024-02-29")
            rapporteringTyper shouldContainAll
                listOf(
                    Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
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

        val overlappendePerioder = inntekter.finnOverlappendePerioder().toList()

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

        val overlappendePerioder = inntekter.finnOverlappendePerioder().toList()

        overlappendePerioder shouldHaveSize 1
        assertSoftly(overlappendePerioder[0]) {
            periode.fom shouldBe LocalDate.parse("2022-01-01")
            periode.til shouldBe null
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

        val overlappendePerioder = inntekter.finnOverlappendePerioder().toList()

        overlappendePerioder shouldHaveSize 0
    }

    private fun opprettInntekt(
        datoFom: YearMonth,
        datoTom: YearMonth?,
        type: Inntektsrapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
        inntektstyper: List<Inntektstype> = emptyList(),
    ) = Inntekt(
        datoFom = datoFom.atDay(1),
        datoTom = datoTom?.atEndOfMonth(),
        belop = BigDecimal.ONE,
        ident = "",
        id = Random.nextLong(1000),
        kilde = Kilde.OFFENTLIG,
        taMed = true,
        type = type,
        inntektsposter =
            inntektstyper.map {
                Inntektspost(
                    beløp = BigDecimal.ONE,
                    inntektstype = it,
                    kode = "",
                )
            }.toMutableSet(),
    )
}
