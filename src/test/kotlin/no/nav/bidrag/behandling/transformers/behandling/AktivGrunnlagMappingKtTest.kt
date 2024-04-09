package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettRolle
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import org.junit.jupiter.api.Test
import stubPersonConsumer
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.random.Random

class AktivGrunnlagMappingKtTest {
    @Test
    fun `skal finne differanse i inntekter`() {
        stubPersonConsumer()
        stubKodeverkProvider()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak()
        val grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        val inntekter =
            setOf(
                opprettInntekt(
                    datoFom = YearMonth.of(2023, 1),
                    datoTom = YearMonth.of(2023, 12),
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    beløp = 32160000.toBigDecimal(),
                    inntektstyperKode =
                        listOf("fastloenn" to BigDecimal(32160000)),
                ),
            )

        val rolleBm = opprettRolle(behandling, testdataBM)

        val resultat = grunnlag.toList().hentEndringerInntekter(rolleBm, inntekter, Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER)
        resultat.shouldHaveSize(8)
        resultat.none { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldBe true
    }

    fun opprettInntekt(
        datoFom: YearMonth,
        datoTom: YearMonth?,
        type: Inntektsrapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
        inntektstyper: List<Pair<Inntektstype, BigDecimal>> = emptyList(),
        inntektstyperKode: List<Pair<String, BigDecimal>> = emptyList(),
        ident: String = "",
        gjelderBarn: String? = null,
        taMed: Boolean = true,
        beløp: BigDecimal = BigDecimal.ONE,
    ) = Inntekt(
        datoTom = null,
        datoFom = null,
        opprinneligFom = datoFom.atDay(1),
        opprinneligTom = datoTom?.atEndOfMonth(),
        belop = beløp,
        ident = ident,
        gjelderBarn = gjelderBarn,
        id = Random.nextLong(1000),
        kilde = Kilde.OFFENTLIG,
        taMed = taMed,
        type = type,
        inntektsposter =
            (
                inntektstyper.map {
                    Inntektspost(
                        beløp = it.second,
                        inntektstype = it.first,
                        kode = "",
                    )
                } +
                    inntektstyperKode.map {
                        Inntektspost(
                            beløp = it.second,
                            inntektstype = null,
                            kode = it.first,
                        )
                    }
            ).toMutableSet(),
    )
}
