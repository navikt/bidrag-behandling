package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.utils.testdata.TestDataPerson
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

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
                        referanse = testdataBarn1.tilRolle().tilGrunnlagsreferanse(),
                    ),
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

    private fun opprettGyldigBeregning(testDataPerson: TestDataPerson) =
        ResultatForskuddsberegningBarn(
            barn =
                ResultatRolle(
                    Personident(testDataPerson.ident),
                    testDataPerson.navn,
                    testDataPerson.fødselsdato,
                    referanse = testdataBarn1.tilRolle().tilGrunnlagsreferanse(),
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
