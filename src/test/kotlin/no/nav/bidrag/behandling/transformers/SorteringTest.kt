package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SorteringTest {
    @Test
    fun `skal filtrere ut historiske inntekter`() {
        val inntekter =
            setOf(
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2022-01"),
                    opprinneligTom = YearMonth.parse("2022-12"),
                    type = Inntektsrapportering.AINNTEKT,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.AINNTEKT,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2022-01"),
                    opprinneligTom = YearMonth.parse("2022-12"),
                    type = Inntektsrapportering.LIGNINGSINNTEKT,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.LIGNINGSINNTEKT,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2021-01"),
                    opprinneligTom = YearMonth.parse("2021-12"),
                    type = Inntektsrapportering.LIGNINGSINNTEKT,
                    taMed = true,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2022-01"),
                    opprinneligTom = YearMonth.parse("2022-12"),
                    type = Inntektsrapportering.KAPITALINNTEKT,
                    taMed = false,
                ),
            )

        val filtrertInntekter = inntekter.filtrerUtHistoriskeInntekter()

        filtrertInntekter shouldHaveSize 4
        assertSoftly(filtrertInntekter.filter { it.type == Inntektsrapportering.LIGNINGSINNTEKT }) {
            this shouldHaveSize 2
            this[0].opprinneligFom shouldBe LocalDate.parse("2023-01-01")
            this[0].opprinneligTom shouldBe LocalDate.parse("2023-12-31")

            this[1].opprinneligFom shouldBe LocalDate.parse("2021-01-01")
            this[1].opprinneligTom shouldBe LocalDate.parse("2021-12-31")
        }
        assertSoftly(filtrertInntekter.filter { it.type == Inntektsrapportering.AINNTEKT }) {
            this shouldHaveSize 1
            this[0].opprinneligFom shouldBe LocalDate.parse("2023-01-01")
            this[0].opprinneligTom shouldBe LocalDate.parse("2023-12-31")
        }
        assertSoftly(filtrertInntekter.filter { it.type == Inntektsrapportering.KAPITALINNTEKT }) {
            this shouldHaveSize 1
            this[0].opprinneligFom shouldBe LocalDate.parse("2022-01-01")
            this[0].opprinneligTom shouldBe LocalDate.parse("2022-12-31")
        }
    }

    @Test
    fun `skal filtrere ut historiske inntekter 2`() {
        val inntekter =
            listOf(
                SummertÅrsinntekt(
                    periode = ÅrMånedsperiode(YearMonth.parse("2022-01"), YearMonth.parse("2022-12")),
                    inntektRapportering = Inntektsrapportering.AINNTEKT,
                    sumInntekt = BigDecimal.ZERO,
                ),
                SummertÅrsinntekt(
                    periode = ÅrMånedsperiode(YearMonth.parse("2023-01"), YearMonth.parse("2023-12")),
                    inntektRapportering = Inntektsrapportering.AINNTEKT,
                    sumInntekt = BigDecimal.ZERO,
                ),
                SummertÅrsinntekt(
                    periode = ÅrMånedsperiode(YearMonth.parse("2022-01"), YearMonth.parse("2022-12")),
                    inntektRapportering = Inntektsrapportering.LIGNINGSINNTEKT,
                    sumInntekt = BigDecimal.ZERO,
                ),
                SummertÅrsinntekt(
                    periode = ÅrMånedsperiode(YearMonth.parse("2023-01"), YearMonth.parse("2023-12")),
                    inntektRapportering = Inntektsrapportering.LIGNINGSINNTEKT,
                    sumInntekt = BigDecimal.ZERO,
                ),
                SummertÅrsinntekt(
                    periode = ÅrMånedsperiode(YearMonth.parse("2021-01"), YearMonth.parse("2021-12")),
                    inntektRapportering = Inntektsrapportering.KAPITALINNTEKT,
                    sumInntekt = BigDecimal.ZERO,
                ),
            )

        val summerteInntker =
            SummerteInntekter<SummertÅrsinntekt>(
                inntekter = inntekter,
            )
        val filtrertInntekter = summerteInntker.filtrerUtHistoriskeInntekter().inntekter

        filtrertInntekter shouldHaveSize 3
        assertSoftly(filtrertInntekter.filter { it.inntektRapportering == Inntektsrapportering.LIGNINGSINNTEKT }) {
            this shouldHaveSize 1
            this[0].periode.fom shouldBe YearMonth.parse("2023-01")
            this[0].periode.til shouldBe YearMonth.parse("2023-12")
        }
        assertSoftly(filtrertInntekter.filter { it.inntektRapportering == Inntektsrapportering.AINNTEKT }) {
            this shouldHaveSize 1
            this[0].periode.fom shouldBe YearMonth.parse("2023-01")
            this[0].periode.til shouldBe YearMonth.parse("2023-12")
        }
        assertSoftly(filtrertInntekter.filter { it.inntektRapportering == Inntektsrapportering.KAPITALINNTEKT }) {
            this shouldHaveSize 1
            this[0].periode.fom shouldBe YearMonth.parse("2021-01")
            this[0].periode.til shouldBe YearMonth.parse("2021-12")
        }
    }

    @Test
    fun `skal ikke filtrere ut ytelser og 12mnd og 3mnd`() {
        val inntekter =
            setOf(
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2022-01"),
                    opprinneligTom = YearMonth.parse("2022-12"),
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2022-01"),
                    opprinneligTom = YearMonth.parse("2022-12"),
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2023-01"),
                    opprinneligTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
                    taMed = false,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2021-01"),
                    opprinneligTom = YearMonth.parse("2021-12"),
                    type = Inntektsrapportering.OVERGANGSSTØNAD,
                    taMed = true,
                ),
                opprettInntekt(
                    opprinneligFom = YearMonth.parse("2022-01"),
                    opprinneligTom = YearMonth.parse("2022-12"),
                    type = Inntektsrapportering.FORELDREPENGER,
                    taMed = false,
                ),
            )

        val filtrertInntekter = inntekter.filtrerUtHistoriskeInntekter()

        filtrertInntekter shouldHaveSize 6
    }
}