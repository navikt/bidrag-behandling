package no.nav.bidrag.behandling.transformers.validering

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.validerForBeregning
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test

val bmIdent = "313213213"
val barnIdent = "1344124"
val barn2Ident = "44444"

class ValiderBeregningTest {
    @Test
    fun `skal validere behandling`() {
        val behandling = opprettGyldigBehandling()

        assertDoesNotThrow { behandling.validerForBeregning() }
    }

    @Test
    fun `skal feile validering hvis virkningstidspunkt og årsak ikke er satt`() {
        val behandling = opprettGyldigBehandling()
        behandling.virkningstidspunkt = null
        behandling.årsak = null
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody =
            commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
        assertSoftly(responseBody) {
            virkningstidspunkt shouldNotBe null
            inntekter shouldBe null
            sivilstand shouldBe null
            husstandsbarn shouldBe null
            assertSoftly(virkningstidspunkt!!) {
                harFeil shouldBe true
                manglerVirkningstidspunkt shouldBe true
                manglerÅrsakEllerAvslag shouldBe true
            }
        }
    }

    @Test
    fun `skal feile validering hvis virkningstidspunkt ikke er satt`() {
        val behandling = opprettGyldigBehandling()
        behandling.virkningstidspunkt = null
        behandling.avslag = Resultatkode.IKKE_OMSORG
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody =
            commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
        assertSoftly(responseBody) {
            virkningstidspunkt shouldNotBe null
            inntekter shouldBe null
            sivilstand shouldBe null
            husstandsbarn shouldBe null
            assertSoftly(virkningstidspunkt!!) {
                harFeil shouldBe true
                manglerVirkningstidspunkt shouldBe true
                manglerÅrsakEllerAvslag shouldBe false
            }
        }
    }

    @Test
    fun `skal feile validering hvis sivilstand perioder er ugyldig`() {
        val behandling = opprettGyldigBehandling()
        behandling.sivilstand =
            opprettSivilstand(
                listOf(
                    Datoperiode(
                        YearMonth.parse("2022-02").atDay(1),
                        YearMonth.parse("2022-03").atEndOfMonth(),
                    ) to Sivilstandskode.GIFT_SAMBOER,
                    Datoperiode(
                        YearMonth.parse("2022-03").atDay(1),
                        YearMonth.parse("2022-05").atEndOfMonth(),
                    ) to Sivilstandskode.ENSLIG,
                    Datoperiode(
                        YearMonth.parse("2022-06").atDay(1),
                        YearMonth.parse("2024-01").atEndOfMonth(),
                    ) to Sivilstandskode.GIFT_SAMBOER,
                ),
            )
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody =
            commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
        assertSoftly(responseBody) {
            virkningstidspunkt shouldBe null
            inntekter shouldBe null
            sivilstand shouldNotBe null
            husstandsbarn shouldBe null
            assertSoftly(sivilstand!!) {
                harFeil shouldBe true

                hullIPerioder shouldHaveSize 2
                hullIPerioder[0].fom shouldBe LocalDate.parse("2022-01-01")
                hullIPerioder[0].til shouldBe LocalDate.parse("2022-02-01")
                hullIPerioder[1].fom shouldBe LocalDate.parse("2024-01-31")
                hullIPerioder[1].til shouldBe null

                overlappendePerioder shouldHaveSize 1
                overlappendePerioder[0].periode.fom shouldBe LocalDate.parse("2022-03-01")
                overlappendePerioder[0].periode.til shouldBe LocalDate.parse("2022-03-31")
                overlappendePerioder[0].sivilstandskode shouldContain Sivilstandskode.ENSLIG
                overlappendePerioder[0].sivilstandskode shouldContain Sivilstandskode.GIFT_SAMBOER

                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                ingenLøpendePeriode shouldBe true
            }
        }
    }

    @Test
    fun `skal feile validering hvis husstandsbarn perioder er ugyldig`() {
        val behandling = opprettGyldigBehandling()
        behandling.husstandsbarn =
            mutableSetOf(
                opprettHusstandsbarn(
                    listOf(
                        Datoperiode(
                            YearMonth.parse("2022-02").atDay(1),
                            YearMonth.parse("2023-03").atEndOfMonth(),
                        ) to Bostatuskode.IKKE_MED_FORELDER,
                        Datoperiode(
                            YearMonth.parse("2023-03").atDay(1),
                            YearMonth.parse("2023-06").atEndOfMonth(),
                        ) to Bostatuskode.MED_FORELDER,
                    ),
                    barnIdent,
                    fødselsdato = LocalDate.parse("2023-01-01"),
                ),
            )
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody =
            commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
        assertSoftly(responseBody) {
            virkningstidspunkt shouldBe null
            inntekter shouldBe null
            sivilstand shouldBe null
            husstandsbarn!! shouldHaveSize 1
            assertSoftly(husstandsbarn!![0]) {
                harFeil shouldBe true

                hullIPerioder shouldHaveSize 1
                hullIPerioder[0].fom shouldBe LocalDate.parse("2023-06-30")
                hullIPerioder[0].til shouldBe null

                overlappendePerioder shouldHaveSize 1
                overlappendePerioder[0].periode.fom shouldBe LocalDate.parse("2023-03-01")
                overlappendePerioder[0].periode.til shouldBe LocalDate.parse("2023-03-31")
                overlappendePerioder[0].bosstatus shouldContain Bostatuskode.IKKE_MED_FORELDER
                overlappendePerioder[0].bosstatus shouldContain Bostatuskode.MED_FORELDER

                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe false
                ingenLøpendePeriode shouldBe true
            }
        }
    }

    @Test
    fun `skal feile validering hvis inntekt perioder er ugyldig`() {
        val behandling = opprettGyldigBehandling()
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    YearMonth.parse("2022-01"),
                    YearMonth.parse("2022-06"),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.parse("2022-01"),
                    YearMonth.parse("2022-03"),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.KAPITALINNTEKT,
                ),
                opprettInntekt(YearMonth.parse("2022-04"), YearMonth.parse("2022-06"), ident = bmIdent, taMed = false),
                opprettInntekt(
                    YearMonth.parse("2022-04"),
                    null,
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.parse("2022-01"),
                    null,
                    ident = bmIdent,
                    gjelderBarn = barn2Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                ),
                opprettInntekt(
                    YearMonth.parse("2022-05"),
                    null,
                    ident = bmIdent,
                    gjelderBarn = barn2Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                ),
                opprettInntekt(
                    YearMonth.now().plusMonths(2),
                    null,
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                ),
            )
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody =
            commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
        assertSoftly(responseBody) {
            virkningstidspunkt shouldBe null
            inntekter shouldNotBe null
            sivilstand shouldBe null
            husstandsbarn shouldBe null
            assertSoftly(inntekter!!) {
                harFeil shouldBe true
                utvidetBarnetrygd shouldBe null
                småbarnstillegg shouldBe null
                kontantstøtte shouldBe null
                årsinntekter!! shouldHaveSize 1
                barnetillegg!! shouldHaveSize 1
                assertSoftly(barnetillegg!!.toList()[0]) {
                    overlappendePerioder shouldHaveSize 1
                    fremtidigPeriode shouldBe false
                    hullIPerioder shouldHaveSize 0
                    manglerPerioder shouldBe false
                    ingenLøpendePeriode shouldBe false
                    ident shouldBe bmIdent
                    gjelderBarn shouldBe barn2Ident
                }
                assertSoftly(årsinntekter!!.toList()[0]) {
                    overlappendePerioder shouldHaveSize 1
                    fremtidigPeriode shouldBe true
                    hullIPerioder shouldHaveSize 0
                    manglerPerioder shouldBe false
                    ingenLøpendePeriode shouldBe false
                    ident shouldBe bmIdent
                    gjelderBarn shouldBe null
                }
            }
        }
    }

    @Test
    fun `skal feile validering hvis inntekt perioder mangler`() {
        val behandling = opprettGyldigBehandling()
        behandling.inntekter =
            mutableSetOf()
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody =
            commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
        assertSoftly(responseBody) {
            virkningstidspunkt shouldBe null
            inntekter shouldNotBe null
            sivilstand shouldBe null
            husstandsbarn shouldBe null
            assertSoftly(inntekter!!) {
                harFeil shouldBe true
                utvidetBarnetrygd shouldBe null
                småbarnstillegg shouldBe null
                kontantstøtte shouldBe null
                årsinntekter!! shouldHaveSize 1
                barnetillegg shouldBe null
                assertSoftly(årsinntekter!!.toList()[0]) {
                    overlappendePerioder shouldHaveSize 0
                    fremtidigPeriode shouldBe false
                    hullIPerioder shouldHaveSize 0
                    manglerPerioder shouldBe true
                    ingenLøpendePeriode shouldBe false
                    ident shouldBe bmIdent
                    gjelderBarn shouldBe null
                }
            }
        }
    }

    fun opprettGyldigBehandling(): Behandling {
        val behandling = oppretteBehandling(1)
        behandling.virkningstidspunkt = LocalDate.parse("2022-01-01")
        behandling.roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
                opprettRolle(barnIdent, Rolletype.BARN),
                opprettRolle(barn2Ident, Rolletype.BARN),
            )
        behandling.husstandsbarn =
            mutableSetOf(
                opprettHusstandsbarn(
                    listOf(
                        Datoperiode(
                            YearMonth.parse("2022-01").atDay(1),
                            YearMonth.parse("2023-02").atEndOfMonth(),
                        ) to Bostatuskode.IKKE_MED_FORELDER,
                        Datoperiode(YearMonth.parse("2023-03").atDay(1), null) to Bostatuskode.MED_FORELDER,
                    ),
                    barnIdent,
                    fødselsdato = LocalDate.parse("2023-01-01"),
                ),
                opprettHusstandsbarn(
                    listOf(
                        Datoperiode(
                            YearMonth.parse("2022-01").atDay(1),
                            YearMonth.parse("2023-02").atEndOfMonth(),
                        ) to Bostatuskode.IKKE_MED_FORELDER,
                        Datoperiode(YearMonth.parse("2023-03").atDay(1), null) to Bostatuskode.MED_FORELDER,
                    ),
                    barn2Ident,
                    fødselsdato = LocalDate.parse("2023-01-01"),
                ),
            )
        behandling.sivilstand =
            opprettSivilstand(
                listOf(
                    Datoperiode(
                        YearMonth.parse("2022-01").atDay(1),
                        YearMonth.parse("2022-01").atEndOfMonth(),
                    ) to Sivilstandskode.GIFT_SAMBOER,
                    Datoperiode(
                        YearMonth.parse("2022-02").atDay(1),
                        YearMonth.parse("2022-05").atEndOfMonth(),
                    ) to Sivilstandskode.ENSLIG,
                    Datoperiode(
                        YearMonth.parse("2022-06").atDay(1),
                        YearMonth.parse("2024-01").atEndOfMonth(),
                    ) to Sivilstandskode.GIFT_SAMBOER,
                    Datoperiode(
                        YearMonth.parse("2024-02").atDay(1),
                        null,
                    ) to Sivilstandskode.ENSLIG,
                ),
            )
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    YearMonth.parse("2022-01"),
                    YearMonth.parse("2022-03"),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.parse("2022-01"),
                    YearMonth.parse("2022-03"),
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.KAPITALINNTEKT,
                ),
                opprettInntekt(YearMonth.parse("2022-04"), YearMonth.parse("2022-06"), ident = bmIdent, taMed = false),
                opprettInntekt(
                    YearMonth.parse("2022-04"),
                    null,
                    ident = bmIdent,
                    taMed = true,
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ),
                opprettInntekt(
                    YearMonth.parse("2022-01"),
                    null,
                    ident = bmIdent,
                    gjelderBarn = barn2Ident,
                    taMed = true,
                    type = Inntektsrapportering.BARNETILLEGG,
                    inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
                ),
            )

        return behandling
    }
}
