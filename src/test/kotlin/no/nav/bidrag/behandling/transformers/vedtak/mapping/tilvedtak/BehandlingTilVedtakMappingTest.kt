package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import io.kotest.matchers.shouldBe
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth

class BehandlingTilVedtakMappingTest {
    @Test
    fun `skal legge til opphørsperiode når siste periode har sluttdato`() {
        val perioder =
            listOf(
                opprettPeriode(YearMonth.of(2025, 1), YearMonth.of(2025, 3)),
            )

        val resultat = perioder.fyllMellomromMedOpphørsperioder()

        resultat shouldBe
            listOf(
                opprettPeriode(YearMonth.of(2025, 1), YearMonth.of(2025, 3)),
                opprettOpphørsperiode(YearMonth.of(2025, 3), null),
            )
    }

    @Test
    fun `skal legge til opphørsperioder mellom perioder og etter siste periode med sluttdato`() {
        val perioder =
            listOf(
                opprettPeriode(YearMonth.of(2025, 1), YearMonth.of(2025, 3)),
                opprettPeriode(YearMonth.of(2025, 5), YearMonth.of(2025, 7)),
            )

        val resultat = perioder.fyllMellomromMedOpphørsperioder()

        resultat shouldBe
            listOf(
                opprettPeriode(YearMonth.of(2025, 1), YearMonth.of(2025, 3)),
                opprettOpphørsperiode(YearMonth.of(2025, 3), YearMonth.of(2025, 5)),
                opprettPeriode(YearMonth.of(2025, 5), YearMonth.of(2025, 7)),
                opprettOpphørsperiode(YearMonth.of(2025, 7), null),
            )
    }

    @Test
    fun `skal ikke legge til trailing opphørsperiode når siste periode er åpen`() {
        val perioder =
            listOf(
                opprettPeriode(YearMonth.of(2025, 1), YearMonth.of(2025, 3)),
                opprettPeriode(YearMonth.of(2025, 5), null),
            )

        val resultat = perioder.fyllMellomromMedOpphørsperioder()

        resultat shouldBe
            listOf(
                opprettPeriode(YearMonth.of(2025, 1), YearMonth.of(2025, 3)),
                opprettOpphørsperiode(YearMonth.of(2025, 3), YearMonth.of(2025, 5)),
                opprettPeriode(YearMonth.of(2025, 5), null),
            )
    }

    private fun opprettPeriode(
        fom: YearMonth,
        til: YearMonth?,
    ): OpprettPeriodeRequestDto =
        OpprettPeriodeRequestDto(
            periode = ÅrMånedsperiode(fom, til),
            beløp = BigDecimal.ONE,
            valutakode = VALUTAKODE,
            resultatkode = Resultatkode.BEREGNET_BIDRAG.name,
            grunnlagReferanseListe = GRUNNLAGSREFERANSE_LISTE,
        )

    private fun opprettOpphørsperiode(
        fom: YearMonth,
        til: YearMonth?,
    ): OpprettPeriodeRequestDto =
        OpprettPeriodeRequestDto(
            periode = ÅrMånedsperiode(fom, til),
            beløp = null,
            resultatkode = Resultatkode.OPPHØR.name,
            grunnlagReferanseListe = GRUNNLAGSREFERANSE_LISTE,
        )

    private companion object {
        const val VALUTAKODE = "NOK"
        val GRUNNLAGSREFERANSE_LISTE = listOf("grunnlag-1")
    }
}
