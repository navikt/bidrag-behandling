package no.nav.bidrag.behandling.transformers

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
                opprettInntekt(YearMonth.parse("2022-04"), YearMonth.parse("2022-05")),
                opprettInntekt(YearMonth.parse("2022-06"), YearMonth.parse("2022-08")),
            )

        val hullPerioder = inntekter.finnHullIPerioder(LocalDate.parse("2022-01-01"))

        hullPerioder shouldHaveSize 3
        hullPerioder[0].fom shouldBe LocalDate.parse("2022-01-01")
        hullPerioder[0].til shouldBe LocalDate.parse("2022-02-01")

        hullPerioder[1].fom shouldBe LocalDate.parse("2022-04-01")
        hullPerioder[1].til shouldBe LocalDate.parse("2022-06-01")

        hullPerioder[2].fom shouldBe LocalDate.parse("2022-08-31")
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
                    null,
                    type = Inntektsrapportering.KAPITALINNTEKT,
                ),
                opprettInntekt(
                    YearMonth.parse("2022-09"),
                    null,
                    type = Inntektsrapportering.KAPITALINNTEKT_EGNE_OPPLYSNINGER,
                ),
            )

        val overlappendePerioder = inntekter.finnOverlappendePerioder().toList()

        overlappendePerioder shouldHaveSize 2
        overlappendePerioder[0].rapporteringTyper shouldContainAll
            listOf(
                Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
            )
        overlappendePerioder[1].rapporteringTyper shouldContainAll
            listOf(
                Inntektsrapportering.KAPITALINNTEKT_EGNE_OPPLYSNINGER,
                Inntektsrapportering.KAPITALINNTEKT,
            )
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
