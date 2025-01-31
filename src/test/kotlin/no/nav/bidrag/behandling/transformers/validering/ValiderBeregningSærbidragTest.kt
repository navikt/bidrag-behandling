package no.nav.bidrag.behandling.transformers.validering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteUtgift
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

val virkningstidspunkt = YearMonth.now()

class ValiderBeregningSærbidragTest {
    lateinit var validering: ValiderBeregning

    @BeforeEach
    fun initMocks() {
        stubSjablonProvider()
        validering = ValiderBeregning()
    }

    @Test
    fun `skal validere behandling`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()

            assertDoesNotThrow { behandling.validerForBeregningSærbidrag() }
        }

    @Test
    fun `skal validere behandling hvis avslag`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.husstandsmedlem = mutableSetOf()
            behandling.inntekter = mutableSetOf()
            behandling.utgift = Utgift(behandling)
            behandling.avslag = Resultatkode.PRIVAT_AVTALE

            assertDoesNotThrow { behandling.validerForBeregningSærbidrag() }
        }

    @Test
    fun `skal feile validering hvis utgift ikke er satt`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.utgift = Utgift(behandling)
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                utgift.shouldNotBe(null)
                utgift!!.manglerUtgifter shouldBe true
                inntekter shouldBe null
                husstandsmedlem shouldBe null
                andreVoksneIHusstanden shouldBe null
            }
        }

    @Test
    fun `skal feile validering hvis utgift har ugyldig utgiftspost`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.utgift =
                Utgift(
                    behandling,
                    utgiftsposter =
                        mutableSetOf(
                            Utgiftspost(
                                kommentar = null,
                                dato = LocalDate.now().minusDays(2),
                                godkjentBeløp = BigDecimal.valueOf(5000),
                                kravbeløp = BigDecimal.valueOf(2000),
                                type = Utgiftstype.KLÆR.name,
                                utgift = Utgift(behandling),
                            ),
                        ),
                )
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                utgift.shouldNotBe(null)
                utgift!!.ugyldigUtgiftspost shouldBe true
                utgift!!.manglerUtgifter shouldBe false
                inntekter shouldBe null
                husstandsmedlem shouldBe null
                andreVoksneIHusstanden shouldBe null
            }
        }

    @Test
    fun `skal feile validering hvis utgift maks godkjent beløp ikke er satt`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.utgift!!.maksGodkjentBeløpTaMed = true
            behandling.utgift!!.maksGodkjentBeløp = null
            behandling.utgift!!.maksGodkjentBeløpBegrunnelse = null
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                utgift.shouldNotBe(null)
                utgift!!.maksGodkjentBeløp shouldNotBe null
                utgift.maksGodkjentBeløp!!.manglerBegrunnelse shouldBe true
                utgift.maksGodkjentBeløp!!.manglerBeløp shouldBe true
            }
        }

    @Test
    fun `skal feile validering hvis utgift maks godkjent beløp begrunnelse ikke er satt`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.utgift!!.maksGodkjentBeløpTaMed = true
            behandling.utgift!!.maksGodkjentBeløp = BigDecimal(10000)
            behandling.utgift!!.maksGodkjentBeløpBegrunnelse = null
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                utgift.shouldNotBe(null)
                utgift!!.maksGodkjentBeløp shouldNotBe null
                utgift.maksGodkjentBeløp!!.manglerBegrunnelse shouldBe true
            }
        }

    @Test
    fun `skal ikke feile validering hvis utgift maks godkjent beløp ikke er tatt med`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.utgift!!.maksGodkjentBeløpTaMed = false
            behandling.utgift!!.maksGodkjentBeløp = null
            behandling.utgift!!.maksGodkjentBeløpBegrunnelse = null
            assertDoesNotThrow { behandling.validerForBeregningSærbidrag() }
        }

    @Test
    fun `skal feile validering hvis mangler bosstatusperiode for søknasdbarn`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.husstandsmedlem =
                mutableSetOf(
                    opprettHusstandsmedlem(
                        listOf(
                            Datoperiode(
                                virkningstidspunkt,
                                null,
                            ) to Bostatuskode.BOR_MED_ANDRE_VOKSNE,
                        ),
                        rolle = testdataBP.tilRolle(behandling),
                    ),
                )
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                inntekter shouldBe null
                husstandsmedlem!! shouldHaveSize 1
                andreVoksneIHusstanden shouldBe null
                assertSoftly(husstandsmedlem!![0]) {
                    harFeil shouldBe true
                    hullIPerioder shouldHaveSize 0
                    overlappendePerioder shouldHaveSize 0
                    fremtidigPeriode shouldBe false
                    manglerPerioder shouldBe true
                    ingenLøpendePeriode shouldBe false
                }
            }
        }

    @Test
    fun `skal feile validering hvis mangler bosstatus for andre voksne i husstanden`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.husstandsmedlem =
                mutableSetOf(
                    opprettHusstandsmedlem(
                        listOf(
                            Datoperiode(
                                virkningstidspunkt,
                                null,
                            ) to Bostatuskode.MED_FORELDER,
                        ),
                        barnIdent,
                        fødselsdato = LocalDate.parse("2023-01-01"),
                    ),
                )
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                inntekter shouldBe null
                husstandsmedlem shouldBe null
                andreVoksneIHusstanden shouldNotBe null
                assertSoftly(andreVoksneIHusstanden!!) {
                    harFeil shouldBe true
                    hullIPerioder shouldHaveSize 0
                    overlappendePerioder shouldHaveSize 0
                    fremtidigPeriode shouldBe false
                    manglerPerioder shouldBe true
                    ingenLøpendePeriode shouldBe false
                }
            }
        }

    @Test
    fun `skal feile validering hvis andre voksne i husstanden har tom liste med perioder`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.husstandsmedlem =
                mutableSetOf(
                    opprettHusstandsmedlem(
                        listOf(
                            Datoperiode(
                                virkningstidspunkt,
                                null,
                            ) to Bostatuskode.MED_FORELDER,
                        ),
                        barnIdent,
                        fødselsdato = LocalDate.parse("2023-01-01"),
                    ),
                    opprettHusstandsmedlem(
                        listOf(),
                        rolle = testdataBP.tilRolle(behandling),
                    ),
                )
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                inntekter shouldBe null
                husstandsmedlem shouldBe null
                andreVoksneIHusstanden shouldNotBe null
                assertSoftly(andreVoksneIHusstanden!!) {
                    harFeil shouldBe true
                    hullIPerioder shouldHaveSize 0
                    overlappendePerioder shouldHaveSize 0
                    fremtidigPeriode shouldBe false
                    manglerPerioder shouldBe true
                    ingenLøpendePeriode shouldBe false
                }
            }
        }

    @Test
    fun `skal feile validering hvis BP mangler inntekter`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.inntekter =
                mutableSetOf(
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    ),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        ident = bmIdent,
                        taMed = true,
                        type = Inntektsrapportering.KAPITALINNTEKT,
                    ),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        ident = bpIdent,
                        taMed = false,
                        type = Inntektsrapportering.AINNTEKT,
                    ),
                )
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                virkningstidspunkt shouldBe null
                inntekter shouldNotBe null
                sivilstand shouldBe null
                husstandsmedlem shouldBe null
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
                        ident shouldBe bpIdent
                        gjelderBarn shouldBe null
                        rolle!!.rolletype shouldBe Rolletype.BIDRAGSPLIKTIG
                    }
                }
            }
        }

    @Test
    fun `skal feile validering hvis BM mangler inntekter`(): Unit =
        validering.run {
            val behandling = opprettGyldigBehandling()
            behandling.inntekter =
                mutableSetOf(
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        ident = bmIdent,
                        taMed = false,
                        type = Inntektsrapportering.AINNTEKT,
                    ),
                    opprettInntekt(
                        virkningstidspunkt,
                        null,
                        ident = bpIdent,
                        taMed = true,
                        type = Inntektsrapportering.AINNTEKT,
                    ),
                )
            val resultat = assertThrows<HttpClientErrorException> { behandling.validerForBeregningSærbidrag() }

            resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
            val responseBody =
                commonObjectmapper.readValue(resultat.responseBodyAsString, BeregningValideringsfeil::class.java)
            assertSoftly(responseBody) {
                virkningstidspunkt shouldBe null
                inntekter shouldNotBe null
                sivilstand shouldBe null
                husstandsmedlem shouldBe null
                assertSoftly(inntekter!!) {
                    harFeil shouldBe true
                    utvidetBarnetrygd shouldBe null
                    småbarnstillegg shouldBe null
                    kontantstøtte shouldBe null
                    barnetillegg shouldBe null
                    årsinntekter!! shouldHaveSize 1
                    assertSoftly(årsinntekter!!.toList()[0]) {
                        overlappendePerioder shouldHaveSize 0
                        fremtidigPeriode shouldBe false
                        hullIPerioder shouldHaveSize 0
                        manglerPerioder shouldBe true
                        ingenLøpendePeriode shouldBe false
                        ident shouldBe bmIdent
                        gjelderBarn shouldBe null
                        rolle!!.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
                    }
                }
            }
        }

    @Nested
    inner class ValideringTekniskSærbidrag {
        @Test
        fun `skal validere behandling`(): Unit =
            validering.run {
                val behandling = opprettGyldigBehandling()

                assertDoesNotThrow { behandling.validerTekniskForBeregningAvSærbidrag() }
            }

        @Test
        fun `skal feile hvis engangsbeløptype ikke er SÆRBIDRAG`(): Unit =
            validering.run {
                val behandling = opprettGyldigBehandling()
                behandling.engangsbeloptype = Engangsbeløptype.ETTERGIVELSE
                val resultat = assertThrows<HttpClientErrorException> { behandling.validerTekniskForBeregningAvSærbidrag() }

                resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
                val responseBody: List<String> =
                    commonObjectmapper.readValue(resultat.responseBodyAsString)
                responseBody shouldContain "Engangsbeløptype ETTERGIVELSE er ikke SÆRBIDRAG. "
            }

        @Test
        fun `skal feile hvis flere enn en søknasdbarn`(): Unit =
            validering.run {
                val behandling = opprettGyldigBehandling()
                behandling.roller.add(opprettRolle(barn2Ident, Rolletype.BARN))
                val resultat = assertThrows<HttpClientErrorException> { behandling.validerTekniskForBeregningAvSærbidrag() }

                resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
                val responseBody: List<String> =
                    commonObjectmapper.readValue(resultat.responseBodyAsString)
                responseBody shouldContain "Det er flere enn ett søknadsbarn. Dette er ikke gyldig for beregning av særbidrag."
            }

        @Test
        fun `skal feile hvis virkningstidspunkt ikke er lik inneværende måned`(): Unit =
            validering.run {
                val behandling = opprettGyldigBehandling()
                behandling.virkningstidspunkt = LocalDate.now().minusMonths(2)
                val resultat = assertThrows<HttpClientErrorException> { behandling.validerTekniskForBeregningAvSærbidrag() }

                resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
                val responseBody: List<String> =
                    commonObjectmapper.readValue(resultat.responseBodyAsString)
                responseBody shouldContain
                    "Virkningstidspunkt ${behandling.virkningstidspunkt} er ikke første dag i inneværende måned. Dette er ikke gyldig for beregning av særbidrag."
            }

        @Test
        fun `skal feile hvis kategori ANNET ikke har beskrivelse`(): Unit =
            validering.run {
                val behandling = opprettGyldigBehandling()
                behandling.kategori = Særbidragskategori.ANNET.name
                behandling.kategoriBeskrivelse = null
                val resultat = assertThrows<HttpClientErrorException> { behandling.validerTekniskForBeregningAvSærbidrag() }

                resultat.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
                val responseBody: List<String> =
                    commonObjectmapper.readValue(resultat.responseBodyAsString)
                responseBody shouldContain "Kategori beskrivelse må settes når kategori er satt til ANNET."
            }
    }
}

private fun opprettGyldigBehandling(): Behandling {
    val behandling = oppretteBehandling(1)
    val virkningstidspunkt = YearMonth.now()
    behandling.virkningstidspunkt = virkningstidspunkt.atDay(1)
    behandling.roller =
        mutableSetOf(
            opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER),
            opprettRolle(bpIdent, Rolletype.BIDRAGSPLIKTIG),
            opprettRolle(barnIdent, Rolletype.BARN),
        )
    behandling.kategori = Særbidragskategori.KONFIRMASJON.name
    behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
    behandling.stonadstype = null
    behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
    behandling.husstandsmedlem =
        mutableSetOf(
            opprettHusstandsmedlem(
                listOf(
                    Datoperiode(
                        virkningstidspunkt,
                        null,
                    ) to Bostatuskode.MED_FORELDER,
                ),
                barnIdent,
                fødselsdato = LocalDate.parse("2023-01-01"),
            ),
            opprettHusstandsmedlem(
                listOf(
                    Datoperiode(
                        virkningstidspunkt,
                        null,
                    ) to Bostatuskode.IKKE_MED_FORELDER,
                ),
                husstandsmedlem1Ident,
                fødselsdato = LocalDate.now().minusYears(19),
            ),
            opprettHusstandsmedlem(
                listOf(
                    Datoperiode(
                        virkningstidspunkt,
                        null,
                    ) to Bostatuskode.BOR_MED_ANDRE_VOKSNE,
                ),
                rolle = testdataBP.tilRolle(behandling),
            ),
        )
    behandling.inntekter =
        mutableSetOf(
            opprettInntekt(
                virkningstidspunkt,
                null,
                ident = bmIdent,
                taMed = true,
                type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
            ),
            opprettInntekt(
                virkningstidspunkt,
                null,
                ident = bpIdent,
                taMed = true,
                type = Inntektsrapportering.KAPITALINNTEKT,
            ),
            opprettInntekt(
                virkningstidspunkt,
                null,
                ident = bpIdent,
                taMed = true,
                type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
            ),
            opprettInntekt(
                virkningstidspunkt,
                null,
                ident = bpIdent,
                gjelderBarn = barnIdent,
                taMed = true,
                type = Inntektsrapportering.BARNETILLEGG,
                inntektstyper = listOf(Inntektstype.BARNETILLEGG_PENSJON),
            ),
        )

    return behandling
}
