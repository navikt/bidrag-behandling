package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.transformers.grunnlag.grunnlagsdataTyperYtelser
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.dto.Kilde
import no.nav.bidrag.boforhold.response.BoforholdBeregnet
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.response.Sivilstand
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.sivilstand.response.Status
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.random.Random

class AktivGrunnlagMappingKtTest {
    @BeforeEach
    fun initMocks() {
        stubPersonConsumer()
        stubKodeverkProvider()
    }

    fun byggBehandling(): Behandling {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak()
        val grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse_endringsdiff.json",
            )

        behandling.grunnlag = grunnlag
        return behandling
    }

    @Nested
    inner class InntekterEndringerTest {
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
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 16000.toBigDecimal(),
                        inntektstyperKode =
                            listOf("annenArbeidsinntekt" to BigDecimal(6000), "arbeidsavklaringspenger" to BigDecimal(10000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 1),
                        datoTom = YearMonth.of(2022, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
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
            resultat.shouldHaveSize(6)
            resultat.none { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldBe true
            resultat.none { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT && it.periode.fom.year == 2023 } shouldBe true
            resultat.none { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT && it.periode.fom.year == 2022 } shouldBe true
            val resultatNy = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.NY }
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 1
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_3MND } shouldHaveSize 1
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT } shouldHaveSize 1
            resultatNy.filter { it.rapporteringstype == Inntektsrapportering.KAPITALINNTEKT } shouldHaveSize 3
        }

        @Test
        fun `skal finne differanser i inntekter hvis endret periode og beløp`() {
            val behandling = byggBehandling()

            val inntekter =
                setOf(
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 11),
                        datoTom = YearMonth.of(2023, 11),
                        type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                        beløp = 32160000.toBigDecimal(),
                        inntektstyperKode =
                            listOf("fastloenn" to BigDecimal(32160000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2023, 1),
                        datoTom = YearMonth.of(2023, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 17000.toBigDecimal(),
                        inntektstyperKode =
                            listOf("annenArbeidsinntekt" to BigDecimal(6000), "arbeidsavklaringspenger" to BigDecimal(11000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 1),
                        datoTom = YearMonth.of(2022, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        inntektstyperKode =
                            listOf("arbeidsavklaringspenger" to BigDecimal(5000)),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2024, 1),
                        datoTom = YearMonth.of(2024, 12),
                        type = Inntektsrapportering.LIGNINGSINNTEKT,
                        beløp = 5000.toBigDecimal(),
                        inntektstyperKode =
                            listOf("arbeidsavklaringspenger" to BigDecimal(5000)),
                    ),
                )

            val resultat =
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    inntekter,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                )
            resultat.shouldHaveSize(10)

            val resultatSlettet = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.SLETTET }
            resultatSlettet shouldHaveSize 1
            resultatSlettet.filter { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 1

            val resultatEndring = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.ENDRING }
            resultatEndring shouldHaveSize 3
            resultatEndring.filter { it.rapporteringstype == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 2
            resultatEndring.filter { it.rapporteringstype == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldHaveSize 1

            val resultatNy = resultat.filter { it.endringstype == GrunnlagInntektEndringstype.NY }
            resultatNy shouldHaveSize 6
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
                        beløp = 32160000.toBigDecimal(),
                    ),
                    opprettInntekt(
                        datoFom = YearMonth.of(2022, 11),
                        datoTom = YearMonth.of(2023, 11),
                        type = Inntektsrapportering.BARNETILLEGG,
                        gjelderBarn = testdataBarn2.ident,
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
            barnetilleggNy!!.beløp shouldBe 12000.0.toBigDecimal()

            val barnetilleggNyBarn2 =
                resultatNy.find {
                    it.rapporteringstype == Inntektsrapportering.BARNETILLEGG && it.gjelderBarn!!.verdi == testdataBarn2.ident
                }
            barnetilleggNyBarn2!!.beløp shouldBe 24000.0.toBigDecimal()
        }

        @Test
        fun `skal ikke finne differanser i boforhold hvis ingen endring`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn2.ident,
                    ),
                )
            val aktivGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val nyGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val resultat = listOf(nyGrunnlagBoforhold).hentEndringerBoforhold(listOf(aktivGrunnlagBoforhold))

            resultat shouldHaveSize 0
        }
    }

    @Nested
    inner class BoforholdGrunnlagendringTest {
        @Test
        fun `skal finne differanser i boforhold ved endring`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn2.ident,
                    ),
                )
            val aktivGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val boforholdGrunnlagListe =
                listOf(
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn2.ident,
                    ),
                )
            val nyGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(boforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val resultat = listOf(nyGrunnlagBoforhold).hentEndringerBoforhold(listOf(aktivGrunnlagBoforhold))

            resultat shouldHaveSize 2
            val resultatBarn1 = resultat.find { it.ident == testdataBarn1.ident }
            resultatBarn1!!.perioder shouldHaveSize 1
            resultatBarn1.perioder.toList()[0].datoFom shouldBe LocalDate.parse("2005-01-01")
            resultatBarn1.perioder.toList()[0].datoTom shouldBe null
            resultatBarn1.perioder.toList()[0].bostatus shouldBe Bostatuskode.MED_FORELDER

            val resultatBarn2 = resultat.find { it.ident == testdataBarn2.ident }
            resultatBarn2!!.perioder shouldHaveSize 1
            resultatBarn2.perioder.toList()[0].datoFom shouldBe LocalDate.parse("2023-12-01")
            resultatBarn2.perioder.toList()[0].datoTom shouldBe null
            resultatBarn2.perioder.toList()[0].bostatus shouldBe Bostatuskode.MED_FORELDER
        }

        @Test
        fun `skal finne differanser i boforhold ved endring hvis ny periode kommer`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                )
            val aktivGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val boforholdGrunnlagListe =
                listOf(
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2004, 1).atDay(1),
                        periodeTom = YearMonth.of(2004, 12).atEndOfMonth(),
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                    BoforholdBeregnet(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        relatertPersonPersonId = testdataBarn1.ident,
                    ),
                )
            val nyGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(boforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val resultat = listOf(nyGrunnlagBoforhold).hentEndringerBoforhold(listOf(aktivGrunnlagBoforhold))

            resultat shouldHaveSize 1
            val resultatBarn1 = resultat.find { it.ident == testdataBarn1.ident }
            resultatBarn1!!.perioder shouldHaveSize 3
            resultatBarn1.perioder.toList()[0].datoFom shouldBe LocalDate.parse("2004-01-01")
            resultatBarn1.perioder.toList()[0].datoTom shouldBe LocalDate.parse("2004-12-31")
            resultatBarn1.perioder.toList()[0].bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
        }
    }

    @Nested
    inner class SivilstandGrunnlagendringTest {
        @Test
        fun `skal ikke finne differanser i sivilstand ved endring`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.OK,
                    sivilstandListe =
                        listOf(
                            Sivilstand(
                                periodeFom = YearMonth.of(2022, 1).atDay(1),
                                periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2023, 1).atDay(1),
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                        ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag))

            resultat shouldBe null
        }

        @Test
        fun `skal finne differanser i sivilstand ved endring`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.OK,
                    sivilstandListe =
                        listOf(
                            Sivilstand(
                                periodeFom = YearMonth.of(2022, 1).atDay(1),
                                periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2023, 1).atDay(1),
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                        ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val sivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.OK,
                    sivilstandListe =
                        listOf(
                            Sivilstand(
                                periodeFom = YearMonth.of(2022, 1).atDay(1),
                                periodeTom = YearMonth.of(2022, 8).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2022, 9).atDay(1),
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                        ),
                )
            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(sivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag))

            resultat shouldNotBe null
            resultat!!.grunnlag shouldHaveSize 2
            resultat.sivilstand shouldHaveSize 2
            resultat.status shouldBe Status.OK
            assertSoftly(resultat.sivilstand.toList()[0]) {
                it.datoFom shouldBe LocalDate.parse("2022-01-01")
                it.datoTom shouldBe LocalDate.parse("2022-08-31")
                it.sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                it.kilde shouldBe Kilde.OFFENTLIG
            }
            assertSoftly(resultat.sivilstand.toList()[1]) {
                it.datoFom shouldBe LocalDate.parse("2022-09-01")
                it.datoTom shouldBe null
                it.sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
                it.kilde shouldBe Kilde.OFFENTLIG
            }
        }

        @Test
        fun `skal finne differanser i sivilstand ved endring hvis status er feilet`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.OK,
                    sivilstandListe =
                        listOf(
                            Sivilstand(
                                periodeFom = YearMonth.of(2022, 1).atDay(1),
                                periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2023, 1).atDay(1),
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                        ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val nySivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.LOGISK_FEIL_I_TIDSLINJE,
                    sivilstandListe = emptyList(),
                )

            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(nySivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag))

            resultat shouldNotBe null
            resultat!!.status shouldBe Status.LOGISK_FEIL_I_TIDSLINJE
            resultat.sivilstand shouldHaveSize 0
            resultat.grunnlag shouldHaveSize 2
        }

        @Test
        fun `skal finne differanser i sivilstand endringer i lengde`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.OK,
                    sivilstandListe =
                        listOf(
                            Sivilstand(
                                periodeFom = YearMonth.of(2021, 1).atDay(1),
                                periodeTom = YearMonth.of(2021, 12).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2022, 1).atDay(1),
                                periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2023, 1).atDay(1),
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                        ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val nySivilstandGrunnlagListe =
                SivilstandBeregnet(
                    status = Status.OK,
                    sivilstandListe =
                        listOf(
                            Sivilstand(
                                periodeFom = YearMonth.of(2021, 1).atDay(1),
                                periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                                sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                            ),
                            Sivilstand(
                                periodeFom = YearMonth.of(2023, 1).atDay(1),
                                periodeTom = null,
                                sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                            ),
                        ),
                )

            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(nySivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag))

            resultat shouldNotBe null
            resultat!!.status shouldBe Status.OK
            resultat.sivilstand shouldHaveSize 2
            resultat.grunnlag shouldHaveSize 2
        }

        fun opprettSivilstandGrunnlag(behandling: Behandling) =
            Grunnlag(
                erBearbeidet = false,
                rolle = behandling.bidragsmottaker!!,
                type = Grunnlagsdatatype.SIVILSTAND,
                data =
                    commonObjectmapper.writeValueAsString(
                        setOf(
                            SivilstandGrunnlagDto(
                                personId = "213",
                                type = SivilstandskodePDL.SKILT,
                                gyldigFom = LocalDate.of(2005, 1, 1),
                                historisk = true,
                                bekreftelsesdato = LocalDate.now(),
                                master = "PDL",
                                registrert = LocalDateTime.now(),
                            ),
                            SivilstandGrunnlagDto(
                                personId = "213",
                                type = SivilstandskodePDL.GIFT,
                                gyldigFom = LocalDate.of(2022, 1, 1),
                                historisk = false,
                                bekreftelsesdato = LocalDate.now(),
                                master = "PDL",
                                registrert = LocalDateTime.now(),
                            ),
                        ),
                    ),
                behandling = behandling,
                innhentet = LocalDateTime.now(),
            )
    }

    fun beregnYtelser(
        behandling: Behandling,
        inntekter: Set<Inntekt>,
    ): Set<IkkeAktivInntektDto> =
        grunnlagsdataTyperYtelser.flatMap {
            behandling.grunnlag.toList().hentEndringerInntekter(
                behandling.bidragsmottaker!!,
                inntekter,
                it,
            )
        }.toSet()

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
