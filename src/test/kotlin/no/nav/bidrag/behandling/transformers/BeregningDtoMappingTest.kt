package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBarnebidragsberegningPeriodeDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegning
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.utils.testdata.TestDataPerson
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteUtgift
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erAvvisning
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class BeregningDtoMappingTest {
    @Test
    fun `skal mappe beregning resultat til DTO`() {
        val beregningBarn =
            listOf(
                opprettGyldigBeregning(testdataBarn1),
                opprettGyldigBeregning(
                    testdataBarn2,
                ),
            )
        val resultatDto = beregningBarn.tilDto()

        assertSoftly {
            resultatDto.size shouldBe 2
            assertSoftly(resultatDto.find { it.barn.ident!!.verdi == testdataBarn1.ident }!!) {
                barn.navn shouldBe testdataBarn1.navn
                barn.fødselsdato shouldBe testdataBarn1.fødselsdato
                barn.ident!!.verdi shouldBe testdataBarn1.ident
                perioder shouldHaveSize 2
                assertSoftly(perioder[0]) {
                    beløp shouldBe BigDecimal(1000)
                    inntekt shouldBe BigDecimal(2000)
                    antallBarnIHusstanden shouldBe 3
                    regel shouldBe "Regel beregning"
                    sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                }
                assertSoftly(perioder[1]) {
                    beløp shouldBe BigDecimal(2000)
                    inntekt shouldBe BigDecimal(3400)
                    antallBarnIHusstanden shouldBe 3
                    regel shouldBe "Regel beregning"
                    sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                }
            }
        }
    }

    @Test
    fun `skal mappe beregning resultat til DTO for avslag`() {
        val beregningBarn =
            listOf(
                ResultatForskuddsberegningBarn(
                    ResultatRolle(
                        Personident(testdataBarn1.ident),
                        testdataBarn1.navn,
                        testdataBarn1.fødselsdato,
                        stønadstype = Stønadstype.FORSKUDD,
                        referanse = testdataBarn1.tilRolle().tilGrunnlagsreferanse(),
                        erRevurderingsbarn = false,
                    ),
                    løperForskudd = false,
                    BeregnetForskuddResultat(
                        beregnetForskuddPeriodeListe =
                            listOf(
                                ResultatPeriode(
                                    periode = ÅrMånedsperiode(LocalDate.parse("2022-01-01"), null),
                                    grunnlagsreferanseListe = emptyList(),
                                    resultat =
                                        ResultatBeregning(
                                            belop = BigDecimal.ZERO,
                                            kode = Resultatkode.BARNETS_INNTEKT,
                                            regel = "",
                                        ),
                                ),
                            ),
                    ),
                ),
            )
        val resultatDto = beregningBarn.tilDto()

        assertSoftly {
            resultatDto.size shouldBe 1
            assertSoftly(resultatDto.find { it.barn.ident!!.verdi == testdataBarn1.ident }!!) {
                barn.navn shouldBe testdataBarn1.navn
                barn.fødselsdato shouldBe testdataBarn1.fødselsdato
                barn.ident!!.verdi shouldBe testdataBarn1.ident
                perioder shouldHaveSize 1
                assertSoftly(perioder[0]) {
                    beløp shouldBe BigDecimal(0)
                    inntekt shouldBe BigDecimal(0)
                    antallBarnIHusstanden shouldBe 0
                    regel shouldBe ""
                    resultatKode shouldBe Resultatkode.BARNETS_INNTEKT
                }
            }
        }
    }

    @Test
    fun `skal markere siste periode etter sortering`() {
        val perioder =
            listOf(
                ResultatBarnebidragsberegningPeriodeDto(
                    periode = ÅrMånedsperiode(YearMonth.of(2024, 5), null),
                    vedtakstype = Vedtakstype.ENDRING,
                    resultatKode = Resultatkode.REDUSERT_FORSKUDD_50_PROSENT,
                ),
                ResultatBarnebidragsberegningPeriodeDto(
                    periode = ÅrMånedsperiode(YearMonth.of(2024, 1), null),
                    vedtakstype = Vedtakstype.ENDRING,
                    resultatKode = Resultatkode.REDUSERT_FORSKUDD_50_PROSENT,
                ),
            )

        val markertePerioder = perioder.sortedBy { it.periode.fom }.markerSistePeriode()

        assertSoftly(markertePerioder) {
            shouldHaveSize(2)
            this[0].periode.fom shouldBe YearMonth.of(2024, 1)
            this[0].erSistePeriode shouldBe false
            this[1].periode.fom shouldBe YearMonth.of(2024, 5)
            this[1].erSistePeriode shouldBe true
        }
    }

    @Test
    fun `skal mappe beregning resultat til Dto hvor delberegning inntekt og husstand mangler`() {
        val resultat1 =
            listOf(
                ResultatForskuddsberegningBarn(
                    barn =
                        ResultatRolle(
                            Personident(testdataBarn1.ident),
                            testdataBarn1.navn,
                            testdataBarn1.fødselsdato,
                            referanse = testdataBarn1.tilRolle().tilGrunnlagsreferanse(),
                            erRevurderingsbarn = false,
                        ),
                    resultat =
                        BeregnetForskuddResultat(
                            beregnetForskuddPeriodeListe =
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                        grunnlagsreferanseListe = listOf("sluttberegning1"),
                                        resultat =
                                            ResultatBeregning(
                                                belop = BigDecimal(1000),
                                                kode = Resultatkode.REDUSERT_FORSKUDD_50_PROSENT,
                                                regel = "regel",
                                            ),
                                    ),
                                ),
                            grunnlagListe =
                                listOf(
                                    GrunnlagDto(
                                        type = Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
                                        referanse = "sluttberegning1",
                                        innhold = POJONode(""),
                                        grunnlagsreferanseListe = listOf("sivilstandPeriode1"),
                                    ),
                                    GrunnlagDto(
                                        type = Grunnlagstype.SIVILSTAND_PERIODE,
                                        referanse = "sivilstandPeriode1",
                                        innhold =
                                            POJONode(
                                                SivilstandPeriode(
                                                    periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                                    sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
                                                    manueltRegistrert = false,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                ),
            )
        val resultatDto = resultat1.tilDto()

        assertSoftly(resultatDto[0]) {
            barn.navn shouldBe testdataBarn1.navn
            barn.fødselsdato shouldBe testdataBarn1.fødselsdato
            barn.ident!!.verdi shouldBe testdataBarn1.ident
            assertSoftly(perioder[0]) {
                beløp shouldBe BigDecimal(1000)
                inntekt shouldBe BigDecimal(0)
                antallBarnIHusstanden shouldBe 0
                sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
            }
        }
    }

    @Test
    fun `skal mappe ResultatBidragsberegning til DTO uten barn`() {
        val resultat =
            ResultatBidragsberegning(
                vedtakstype = Vedtakstype.ENDRING,
                bpHarFullEvneIAllePerioder = true,
                inneholderBeregningForRevurderingsbarn = false,
                resultatBarn = emptyList(),
            )

        val dto = resultat.tilDto(kanFatteVedtakBegrunnelse = null)

        assertSoftly(dto) {
            kanFatteVedtak shouldBe true
            kanFatteVedtakBegrunnelse.shouldBeNull()
            resultatBarn shouldHaveSize 0
            skalFatteVedtakForRevurderingsbarn shouldBe false
            kanFatteVedtakForRevurderingsbarn shouldBe false
        }
    }

    @Test
    fun `skal sette kanFatteVedtak til false nar begrunnelse er satt`() {
        val resultat =
            ResultatBidragsberegning(
                vedtakstype = Vedtakstype.ENDRING,
                resultatBarn = emptyList(),
            )

        val dto = resultat.tilDto(kanFatteVedtakBegrunnelse = "Mangler grunnlag")

        assertSoftly(dto) {
            kanFatteVedtak shouldBe false
            kanFatteVedtakBegrunnelse shouldBe "Mangler grunnlag"
        }
    }

    @Test
    fun `skal mappe ResultatBidragsberegning med barn og sortere resultatBarn etter fodselsdato`() {
        val søknadsbarn =
            opprettResultatBidragsberegningBarn(
                testDataPerson = testdataBarn2,
                referanse = "barn2",
                erRevurderingsbarn = false,
            )
        val yngreBarn =
            opprettResultatBidragsberegningBarn(
                testDataPerson = testdataBarn1,
                referanse = "barn1",
                erRevurderingsbarn = false,
            )

        val resultat =
            ResultatBidragsberegning(
                vedtakstype = Vedtakstype.INNKREVING,
                resultatBarn = listOf(søknadsbarn, yngreBarn),
            )

        val dto = resultat.tilDto(kanFatteVedtakBegrunnelse = null)

        assertSoftly(dto) {
            resultatBarn shouldHaveSize 2
            // Yngste barnet (senere fødselsdato) skal komme sist
            resultatBarn.last().barn.fødselsdatoSortering shouldBe
                resultatBarn.maxOf { it.barn.fødselsdatoSortering }
            resultatBarn.forEach {
                it.perioder shouldHaveSize 0
                it.resultatUtenBeregning shouldBe true
                it.medInnkreving shouldBe true
                it.erAvvisning shouldBe false
            }
        }
    }

    @Test
    fun `skal markere erAvvisning nar avslagskode er en avvisningskode`() {
        val avvisningskode = Resultatkode.entries.first { it.erAvvisning() }
        val barn =
            opprettResultatBidragsberegningBarn(
                testDataPerson = testdataBarn1,
                referanse = "barn1",
                erRevurderingsbarn = false,
                avslagskode = avvisningskode,
            )

        val resultat =
            ResultatBidragsberegning(
                vedtakstype = Vedtakstype.ENDRING,
                resultatBarn = listOf(barn),
            )

        val dto = resultat.tilDto(kanFatteVedtakBegrunnelse = null)

        assertSoftly(dto.resultatBarn.single()) {
            erAvvisning shouldBe true
        }
    }

    @Test
    fun `skal mappe BeregnetSaerbidragResultat til DTO`() {
        val behandling = oppretteTestbehandling(behandlingstype = TypeBehandling.SÆRBIDRAG, inkludereBoforhold = false, inkludereSivilstand = false)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name, medId = true)

        val beregnetResultat =
            BeregnetSærbidragResultat(
                beregnetSærbidragPeriodeListe =
                    listOf(
                        no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatPeriode(
                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                            resultat =
                                no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatBeregning(
                                    beløp = BigDecimal(2500),
                                    resultatkode = Resultatkode.SÆRBIDRAG_INNVILGET,
                                ),
                            grunnlagsreferanseListe = emptyList(),
                        ),
                    ),
                grunnlagListe = emptyList(),
            )

        val dto = beregnetResultat.tilDto(behandling)

        assertSoftly(dto) {
            resultat shouldBe BigDecimal(2500)
            resultatKode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET
            periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt!!)
            utgiftsposter shouldHaveSize 1
            beregning shouldBe behandling.utgift?.tilBeregningDto()
        }
    }

    private fun opprettResultatBidragsberegningBarn(
        testDataPerson: TestDataPerson,
        referanse: String,
        erRevurderingsbarn: Boolean,
        avslagskode: Resultatkode? = null,
    ) = ResultatBidragsberegningBarn(
        barn =
            ResultatRolle(
                ident = Personident(testDataPerson.ident),
                navn = testDataPerson.navn,
                fødselsdato = testDataPerson.fødselsdato,
                referanse = referanse,
                stønadstype = Stønadstype.BIDRAG,
                erRevurderingsbarn = erRevurderingsbarn,
            ),
        resultat =
            BeregnetBarnebidragResultat(
                beregnetBarnebidragPeriodeListe = emptyList(),
                grunnlagListe = emptyList(),
            ),
        avslagskode = avslagskode,
        opphørsdato = null,
        `løperBidrag` = true,
    )

    private fun opprettGyldigBeregning(testDataPerson: TestDataPerson) =
        ResultatForskuddsberegningBarn(
            barn =
                ResultatRolle(
                    Personident(testDataPerson.ident),
                    testDataPerson.navn,
                    testDataPerson.fødselsdato,
                    referanse = testdataBarn1.tilRolle().tilGrunnlagsreferanse(),
                    erRevurderingsbarn = false,
                ),
            resultat =
                BeregnetForskuddResultat(
                    beregnetForskuddPeriodeListe =
                        listOf(
                            ResultatPeriode(
                                periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                grunnlagsreferanseListe = listOf("sluttberegning1"),
                                resultat =
                                    ResultatBeregning(
                                        belop = BigDecimal(1000),
                                        kode = Resultatkode.REDUSERT_FORSKUDD_50_PROSENT,
                                        regel = "Regel beregning",
                                    ),
                            ),
                            ResultatPeriode(
                                periode = ÅrMånedsperiode("2022-01", "2022-12"),
                                grunnlagsreferanseListe = listOf("sluttberegning2"),
                                resultat =
                                    ResultatBeregning(
                                        belop = BigDecimal(2000),
                                        kode = Resultatkode.FORHØYET_FORSKUDD_100_PROSENT,
                                        regel = "Regel beregning",
                                    ),
                            ),
                        ),
                    grunnlagListe =
                        listOf(
                            GrunnlagDto(
                                type = Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
                                referanse = "sluttberegning1",
                                innhold = POJONode(""),
                                grunnlagsreferanseListe =
                                    listOf(
                                        "delberegningInntekt1",
                                        "delberegningBarnIHusstand1",
                                        "sivilstandPeriode1",
                                    ),
                            ),
                            GrunnlagDto(
                                type = Grunnlagstype.SLUTTBEREGNING_FORSKUDD,
                                referanse = "sluttberegning2",
                                innhold = POJONode(""),
                                grunnlagsreferanseListe =
                                    listOf(
                                        "delberegningInntekt2",
                                        "delberegningBarnIHusstand1",
                                        "sivilstandPeriode1",
                                    ),
                            ),
                            GrunnlagDto(
                                type = Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                                referanse = "delberegningInntekt1",
                                innhold =
                                    POJONode(
                                        DelberegningSumInntekt(
                                            periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                            totalinntekt = BigDecimal(2000),
                                        ),
                                    ),
                            ),
                            GrunnlagDto(
                                type = Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                                referanse = "delberegningInntekt2",
                                innhold =
                                    POJONode(
                                        DelberegningSumInntekt(
                                            periode = ÅrMånedsperiode("2022-01", "2022-12"),
                                            totalinntekt = BigDecimal(3400),
                                        ),
                                    ),
                            ),
                            GrunnlagDto(
                                type = Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND,
                                referanse = "delberegningBarnIHusstand1",
                                innhold =
                                    POJONode(
                                        DelberegningBarnIHusstand(
                                            periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                            antallBarn = 3.0,
                                        ),
                                    ),
                            ),
                            GrunnlagDto(
                                type = Grunnlagstype.SIVILSTAND_PERIODE,
                                referanse = "sivilstandPeriode1",
                                innhold =
                                    POJONode(
                                        SivilstandPeriode(
                                            periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                            sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
                                            manueltRegistrert = false,
                                        ),
                                    ),
                            ),
                        ),
                ),
        )
}
