package no.nav.bidrag.behandling.transformers

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import org.junit.Test
import java.time.YearMonth

class DateUtilsTest {
    @Test
    fun `skal matche periode som overlapper selv om matchende periode starter før søkeperiode`() {
        val perioder =
            listOf(
                ÅrMånedsperiode(YearMonth.parse("2025-07"), YearMonth.parse("2025-10")),
                ÅrMånedsperiode(YearMonth.parse("2026-02"), null),
            )
        val søkeperiode = ÅrMånedsperiode(YearMonth.parse("2025-08"), YearMonth.parse("2026-01"))

        val resultat = perioder.filtrerMatchendePeriode(søkeperiode)

        resultat.shouldContainExactly(perioder.first())
    }

    @Test
    fun `skal matche åpne perioder når søkeperiode overlapper`() {
        val perioder =
            listOf(
                ÅrMånedsperiode(YearMonth.parse("2026-02"), null),
            )
        val søkeperiode = ÅrMånedsperiode(YearMonth.parse("2026-03"), YearMonth.parse("2026-05"))

        val resultat = perioder.filtrerMatchendePeriode(søkeperiode)

        resultat.shouldContainExactly(perioder.first())
    }

    @Test
    fun `skal ikke matche perioder som ikke overlapper`() {
        val perioder =
            listOf(
                ÅrMånedsperiode(YearMonth.parse("2025-07"), YearMonth.parse("2025-10")),
                ÅrMånedsperiode(YearMonth.parse("2026-02"), null),
            )
        val søkeperiode = ÅrMånedsperiode(YearMonth.parse("2025-11"), YearMonth.parse("2026-01"))

        val resultat = perioder.filtrerMatchendePeriode(søkeperiode)

        resultat.shouldBeEmpty()
    }
}

