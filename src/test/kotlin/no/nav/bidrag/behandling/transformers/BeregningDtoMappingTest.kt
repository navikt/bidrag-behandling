package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BeregningDtoMappingTest {
    @Test
    fun `skal mappe beregning resultat til DTO`() {
        val resultat1 =
            listOf(
                ResultatForskuddsberegningBarn(
                    barn =
                        ResultatRolle(
                            Personident(testdataBarn1.ident),
                            testdataBarn1.navn,
                            testdataBarn1.foedselsdato,
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
                                        grunnlagsreferanseListe =
                                            listOf(
                                                "delberegningInntekt1",
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
                                        type = Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND,
                                        referanse = "delberegningBarnIHusstand1",
                                        innhold =
                                            POJONode(
                                                DelberegningBarnIHusstand(
                                                    periode = ÅrMånedsperiode("2021-01", "2021-12"),
                                                    antallBarn = 3,
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
                ),
            )
        val resultatDto = resultat1.tilDto()

        assertSoftly(resultatDto[0]) {
            barn.navn shouldBe testdataBarn1.navn
            barn.fødselsdato shouldBe testdataBarn1.foedselsdato
            barn.ident!!.verdi shouldBe testdataBarn1.ident
            assertSoftly(perioder[0]) {
                beløp shouldBe BigDecimal(1000)
                inntekt shouldBe BigDecimal(2000)
                antallBarnIHusstanden shouldBe 3
                sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
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
                            testdataBarn1.foedselsdato,
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
            barn.fødselsdato shouldBe testdataBarn1.foedselsdato
            barn.ident!!.verdi shouldBe testdataBarn1.ident
            assertSoftly(perioder[0]) {
                beløp shouldBe BigDecimal(1000)
                inntekt shouldBe BigDecimal(0)
                antallBarnIHusstanden shouldBe 0
                sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
            }
        }
    }
}
