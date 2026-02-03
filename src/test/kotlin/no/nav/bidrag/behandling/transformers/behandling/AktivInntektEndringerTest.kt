package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntekt
import no.nav.bidrag.behandling.transformers.validering.barnIdent
import no.nav.bidrag.behandling.transformers.validering.bmIdent
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth

class AktivInntektEndringerTest : AktivGrunnlagTestFelles() {
    @Nested
    inner class InntektPosterEndringerTest {
        @Test
        fun `skal finne endringer i inntekstposter`() {
            val nyeInntektsposter =
                setOf(
                    InntektPost(
                        kode = "kode",
                        beløp = BigDecimal(1000),
                    ),
                    InntektPost(
                        kode = "kode2",
                        beløp = BigDecimal(1000),
                    ),
                    InntektPost(
                        kode = "kode6",
                        beløp = BigDecimal(1000),
                    ),
                )

            val eksisterendePoster =
                setOf(
                    Inntektspost(
                        kode = "kode",
                        beløp = BigDecimal(2000),
                        id = 1,
                        inntektstype = null,
                        inntekt = null,
                    ),
                    Inntektspost(
                        kode = "kode3",
                        beløp = BigDecimal(3000),
                        id = 2,
                        inntektstype = null,
                        inntekt = null,
                    ),
                    Inntektspost(
                        kode = "kode4",
                        beløp = BigDecimal(2000),
                        id = 3,
                        inntektstype = null,
                        inntekt = null,
                    ),
                    Inntektspost(
                        kode = "kode6",
                        beløp = BigDecimal(1000),
                        id = 4,
                        inntektstype = null,
                        inntekt = null,
                    ),
                )

            val endringer = mapTilInntektspostEndringer(nyeInntektsposter, eksisterendePoster).toList()

            endringer shouldHaveSize 4
            assertSoftly(endringer.find { it.kode == "kode" }!!) {
                endringstype shouldBe GrunnlagInntektEndringstype.ENDRING
                beløp shouldBe BigDecimal(1000)
            }
            assertSoftly(endringer.find { it.kode == "kode2" }!!) {
                endringstype shouldBe GrunnlagInntektEndringstype.NY
                beløp shouldBe BigDecimal(1000)
            }
            assertSoftly(endringer.find { it.kode == "kode3" }!!) {
                endringstype shouldBe GrunnlagInntektEndringstype.SLETTET
                beløp shouldBe BigDecimal(3000)
            }
            assertSoftly(endringer.find { it.kode == "kode4" }!!) {
                endringstype shouldBe GrunnlagInntektEndringstype.SLETTET
                beløp shouldBe BigDecimal(2000)
            }
        }

        @Test
        fun `skal ikke finne endringer i inntekstposter hvis ingen endring`() {
            val nyeInntektsposter =
                setOf(
                    InntektPost(
                        kode = "kode",
                        beløp = BigDecimal(1000),
                    ),
                    InntektPost(
                        kode = "kode2",
                        beløp = BigDecimal(1000),
                    ),
                )

            val eksisterendePoster =
                setOf(
                    Inntektspost(
                        kode = "kode",
                        beløp = BigDecimal(1000),
                        id = 1,
                        inntektstype = null,
                        inntekt = null,
                    ),
                    Inntektspost(
                        kode = "kode2",
                        beløp = BigDecimal(1000),
                        id = 2,
                        inntektstype = null,
                        inntekt = null,
                    ),
                )

            val endringer = mapTilInntektspostEndringer(nyeInntektsposter, eksisterendePoster).toList()

            endringer shouldHaveSize 0
        }
    }

    @Nested
    inner class InntekterEndringerTest {
        @Test
        fun `skal ikke finne differanser i inntekter hvis ikke endret for BM`() {
            val behandling = byggBehandling()
            val inntekter =
                setOf(
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 2),
                        datoTom = YearMonth.of(2024, 1),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        beløp = 32160000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 16000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf(
                                "annenArbeidsinntekt" to BigDecimal(6000),
                                "arbeidsavklaringspenger" to BigDecimal(10000),
                            ),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 1),
                        datoTom = YearMonth.of(2022, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("annenArbeidsinntekt" to BigDecimal(5000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 2),
                        datoTom = YearMonth.of(2024, 1),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        beløp = 0.toBigDecimal(),
                        ident = barnIdent,
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 16000.toBigDecimal(),
                        ident = barnIdent,
                        inntektstyperKode =
                            listOf(
                                "annenArbeidsinntekt" to BigDecimal(6000),
                                "arbeidsavklaringspenger" to BigDecimal(10000),
                            ),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 1),
                        datoTom = YearMonth.of(2022, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        ident = barnIdent,
                        inntektstyperKode =
                            listOf("annenArbeidsinntekt" to BigDecimal(5000)),
                    ),
                )

            val resultat =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    inntekter,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )
            resultat.shouldHaveSize(4)
            resultat.filter { it.endringstype == GrunnlagInntektEndringstype.ENDRING }.shouldHaveSize(0)

            val resultatBarn =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    testdataBarn1.tilRolle(behandling)!!,
                    inntekter,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )
            resultatBarn.shouldHaveSize(5)
        }

        @Test
        fun `skal vise endring dersom beløp oppdateres`() {
            // gitt
            val behandling = byggBehandling()

            val bmsInntektsgrunnlag =
                behandling.grunnlag
                    .filter { it.type == Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER && it.erBearbeidet }
                    .filter { it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }

            val ligningsinntekt =
                bmsInntektsgrunnlag
                    .first()
                    .konvertereData<SummerteInntekter<SummertÅrsinntekt>>()
                    ?.inntekter
                    ?.filter {
                        Inntektsrapportering.LIGNINGSINNTEKT ==
                            it.inntektRapportering
                    }?.maxBy { it.periode.fom }

            val endretInntekt =
                ligningsinntekt
                    ?.copy(sumInntekt = BigDecimal(800000))
                    ?.tilInntekt(behandling, behandling.bidragsmottaker!!)

            // hvis
            val resultat =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    setOf(endretInntekt!!),
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )

            // så
            resultat.shouldHaveSize(7)
            resultat.filter { it.endringstype == GrunnlagInntektEndringstype.ENDRING }.shouldHaveSize(1)
        }

        @Test
        fun `skal ikke finne differanser i inntekter hvis ikke endret`() {
            val behandling = byggBehandling()
            val inntekter =
                setOf(
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 2),
                        datoTom = YearMonth.of(2024, 1),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        beløp = 32160000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 16000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf(
                                "annenArbeidsinntekt" to BigDecimal(6000),
                                "arbeidsavklaringspenger" to BigDecimal(10000),
                            ),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 1),
                        datoTom = YearMonth.of(2022, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("annenArbeidsinntekt" to BigDecimal(5000)),
                    ),
                )

            val resultat =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    inntekter,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )
            resultat.shouldHaveSize(4)
            resultat.none { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldBe true
            resultat.none { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT && it.periode.fom.year == 2023 } shouldBe true
            resultat.none { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT && it.periode.fom.year == 2022 } shouldBe true
            val resultatNy = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.NY }
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT } shouldHaveSize 1
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_3MND } shouldHaveSize 1
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT } shouldHaveSize 1
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.KAPITALINNTEKT } shouldHaveSize 2
        }

        @Test
        fun `skal finne differanser i inntekter hvis endret periode og beløp`() {
            val behandling = byggBehandling()

            val inntekter =
                setOf(
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 2),
                        datoTom = YearMonth.of(2024, 1),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        beløp = 32160000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 17000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf(
                                "annenArbeidsinntekt" to BigDecimal(6000),
                                "arbeidsavklaringspenger" to BigDecimal(11000),
                            ),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 1),
                        datoTom = YearMonth.of(2022, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("arbeidsavklaringspenger" to BigDecimal(5000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2024, 1),
                        datoTom = YearMonth.of(2024, 12),
                        type = Inntektsrapportering.AINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        ident = bmIdent,
                    ),
                )

            val resultat =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    inntekter,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )
            resultat.shouldHaveSize(7)

            val resultatSlettet = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.SLETTET }
            resultatSlettet shouldHaveSize 1
            resultatSlettet.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT } shouldHaveSize 1

            val resultatEndring = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.ENDRING }
            resultatEndring shouldHaveSize 2
            resultatEndring.filter { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 2
            resultatEndring.find {
                it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT && it.periode.fom == YearMonth.parse("2022-01")
            } shouldNotBe null
            assertSoftly(
                resultatEndring.find {
                    it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT && it.periode.fom == YearMonth.parse("2023-01")
                }!!,
            ) {
                inntektsposterSomErEndret shouldHaveSize 1
                inntektsposterSomErEndret.find { it.kode == "arbeidsavklaringspenger" }!!.endringstype shouldBe
                    GrunnlagInntektEndringstype.ENDRING
            }
            resultatEndring.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldHaveSize 0

            val resultatNy = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.NY }
            resultatNy shouldHaveSize 4
        }

        @Test
        fun `skal finne differanser i inntekter hvis endret periode og beløp ytelser`() {
            val behandling = byggBehandling()

            val inntekter =
                setOf(
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 11),
                        datoTom = YearMonth.of(2023, 11),
                        type = Inntektsrapportering.BARNETILLEGG,
                        gjelderBarn = testdataBarn1.ident,
                        ident = bmIdent,
                        beløp = 32160000.toBigDecimal(),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 11),
                        datoTom = YearMonth.of(2023, 11),
                        type = Inntektsrapportering.BARNETILLEGG,
                        gjelderBarn = testdataBarn2.ident,
                        ident = bmIdent,
                        beløp = 32160000.toBigDecimal(),
                    ),
                )

            val resultat = beregnYtelser(behandling, inntekter)
            resultat.shouldHaveSize(7)

            val resultatSlettet = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.SLETTET }
            resultatSlettet shouldHaveSize 2
            resultatSlettet.filter {
                it.rapporteringstype == Inntektsrapportering.BARNETILLEGG && it.gjelderBarn!!.verdi == testdataBarn1.ident
            } shouldHaveSize 1
            resultatSlettet.filter {
                it.rapporteringstype == Inntektsrapportering.BARNETILLEGG && it.gjelderBarn!!.verdi == testdataBarn2.ident
            } shouldHaveSize 1
            val barnetilleggSlettet =
                resultatSlettet.find {
                    it.rapporteringstype == Inntektsrapportering.BARNETILLEGG && it.gjelderBarn!!.verdi == testdataBarn1.ident
                }
            barnetilleggSlettet!!.beløp shouldBe 32160000.toBigDecimal()

            val resultatEndring = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.ENDRING }
            resultatEndring shouldHaveSize 0

            val resultatNy = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.NY }
            resultatNy shouldHaveSize 5
            val barnetilleggNy =
                resultatNy.find {
                    it.rapporteringstype == Inntektsrapportering.BARNETILLEGG && it.gjelderBarn!!.verdi == testdataBarn1.ident
                }
            barnetilleggNy!!.beløp shouldBe 12000.toBigDecimal()

            val barnetilleggNyBarn2 =
                resultatNy.find {
                    it.rapporteringstype == Inntektsrapportering.BARNETILLEGG && it.gjelderBarn!!.verdi == testdataBarn2.ident
                }
            barnetilleggNyBarn2!!.beløp shouldBe 24000.toBigDecimal()
        }

        @Test
        fun `skal finne differanser i inntekter hvis 12mnd og 3mnd inntekter bare har endret periode`() {
            val behandling = byggBehandling()

            val inntekter =
                setOf(
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        beløp = 32160000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 10),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        beløp = 32160000.toBigDecimal(),
                        ident = bmIdent,
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                )

            val resultat =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    inntekter,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )
            resultat.shouldHaveSize(7)

            val resultatSlettet = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.SLETTET }
            resultatSlettet shouldHaveSize 0

            val resultatEndring = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.ENDRING }
            resultatEndring shouldHaveSize 2
            resultatEndring.any { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_3MND }.shouldBeTrue()
            resultatEndring.any { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }.shouldBeTrue()
            val ainntekt3Mnd =
                resultatEndring.find {
                    it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_3MND
                }!!
            ainntekt3Mnd.periode.fom shouldBe YearMonth.parse("2023-11")
            ainntekt3Mnd.periode.til shouldBe YearMonth.parse("2024-01")
        }
    }
}
