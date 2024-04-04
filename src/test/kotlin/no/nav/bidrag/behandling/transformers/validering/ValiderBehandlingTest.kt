package no.nav.bidrag.behandling.transformers.validering

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.validerForBeregning
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeilList
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeilType
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
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

class ValiderBehandlingTest {
    val bmIdent = "31233123"
    val barnIdent = "21333123"
    val barn2Ident = "44444"

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
        val responseBody = commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeilList::class.java)
        responseBody!! shouldHaveSize 1
        assertSoftly(responseBody[0]) {
            type shouldBe BeregningValideringsfeilType.VIRKNINGSTIDSPUNKT
            feilListe shouldHaveSize 2
            feilListe shouldContainAll listOf("Mangler virkningstidspunkt", "Årsak eller avslag må velges")
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
        val responseBody = commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeilList::class.java)
        responseBody!! shouldHaveSize 1
        assertSoftly(responseBody[0]) {
            type shouldBe BeregningValideringsfeilType.SIVILSTAND
            feilListe shouldHaveSize 3
            feilListe shouldContainAll
                listOf(
                    "Det er ingen løpende periode for sivilstand",
                    "Det er et hull i perioden 2022-01-01 - 2022-02-01 for sivilstand",
                    "Det er en overlappende periode fra 2022-03-01 til 2022-03-31",
                )
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
        val responseBody = commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeilList::class.java)
        responseBody!! shouldHaveSize 1
        assertSoftly(responseBody[0]) {
            type shouldBe BeregningValideringsfeilType.BOFORHOLD
            feilListe shouldHaveSize 3
            feilListe shouldContainAll
                listOf(
                    "Det er ingen løpende periode for husstandsbarn 21333123/01.01.2023",
                    "Det er en overlappende periode fra 2023-03-01 til 2023-03-31",
                    "Søknadsbarn Test 1/01.01.2020 mangler informasjon om boforhold",
                )
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
        val responseBody = commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeilList::class.java)
        responseBody!! shouldHaveSize 1
        assertSoftly(responseBody[0]) {
            type shouldBe BeregningValideringsfeilType.INNTEKT
            feilListe shouldHaveSize 4
            feilListe shouldContainAll
                listOf(
                    "Det er en overlappende periode fra 2022-04-01 til 2022-06-30 for 31233123/BIDRAGSMOTTAKER",
                    "Det er en overlappende periode fra 2024-06-01 til null for 31233123/BIDRAGSMOTTAKER",
                    "Det er periodisert fremover i tid for inntekt som gjelder 31233123/BIDRAGSMOTTAKER",
                    "Det er en overlappende periode fra 2022-05-01 til null for 31233123/BIDRAGSMOTTAKER og type BARNETILLEGG/BARNETILLEGG_PENSJON og gjelder barn 44444",
                )
        }
    }

    @Test
    fun `skal feile validering hvis inntekt perioder mangler`() {
        val behandling = opprettGyldigBehandling()
        behandling.inntekter =
            mutableSetOf()
        val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregning() }

        resultat.message shouldContain "Feil ved validering av behandling for beregning"
        val responseBody = commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeilList::class.java)
        responseBody!! shouldHaveSize 1
        assertSoftly(responseBody[0]) {
            type shouldBe BeregningValideringsfeilType.INNTEKT
            feilListe shouldHaveSize 1
            feilListe shouldContainAll
                listOf(
                    "Mangler perioder for ident 31233123/BIDRAGSMOTTAKER",
                )
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
