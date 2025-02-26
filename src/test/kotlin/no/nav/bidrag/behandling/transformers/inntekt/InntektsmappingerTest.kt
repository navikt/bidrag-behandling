package no.nav.bidrag.behandling.transformers.inntekt

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class InntektsmappingerTest {
    @Test
    fun `skal bestemme datoFom for offentlig utvidet barnetrygd`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2023-01"),
                opprinneligTom = YearMonth.parse("2023-06"),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2023-05").atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe YearMonth.parse("2023-06").atEndOfMonth()
    }

    @Test
    fun `skal bestemme datoTom for offentlig barnetillegg hvis flere barn med opphørsdato hvor ene barnet ikke har opphør`() {
        val behandling = oppretteBehandling()
        val søknadsbarn =
            Rolle(
                ident = testdataBarn1.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn1.fødselsdato,
                id = 5,
            )
        val søknadsbarn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 5,
            )
        behandling.roller.add(søknadsbarn)
        behandling.roller.add(søknadsbarn2)
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        søknadsbarn.opphørsdato = YearMonth.parse("2024-06").atDay(1)
        søknadsbarn2.opphørsdato = null
        assertSoftly {
            val inntekt =
                opprettInntekt(
                    behandling = behandling,
                    opprinneligFom = YearMonth.parse("2024-01"),
                    opprinneligTom = YearMonth.parse("2024-05"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = søknadsbarn.ident,
                    kilde = Kilde.OFFENTLIG,
                )
            inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2024-01").atDay(1)
            inntekt.bestemDatoTomForOffentligInntekt() shouldBe YearMonth.parse("2024-05").atEndOfMonth()
        }
        assertSoftly {
            val inntekt =
                opprettInntekt(
                    behandling = behandling,
                    opprinneligFom = YearMonth.now().minusMonths(7),
                    opprinneligTom = YearMonth.now().plusMonths(3),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = søknadsbarn2.ident,
                    kilde = Kilde.OFFENTLIG,
                )
            inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.now().minusMonths(7).atDay(1)
            inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
        }
    }

    @Test
    fun `skal bestemme datoTom for offentlig barnetillegg hvis flere barn med opphørsdato`() {
        val behandling = oppretteBehandling()
        val søknadsbarn =
            Rolle(
                ident = testdataBarn1.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn1.fødselsdato,
                id = 5,
            )
        val søknadsbarn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 5,
            )
        behandling.roller.add(søknadsbarn)
        behandling.roller.add(søknadsbarn2)
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        søknadsbarn.opphørsdato = YearMonth.parse("2024-06").atDay(1)
        søknadsbarn2.opphørsdato = YearMonth.parse("2023-12").atDay(1)
        assertSoftly {
            val inntekt =
                opprettInntekt(
                    behandling = behandling,
                    opprinneligFom = YearMonth.parse("2024-01"),
                    opprinneligTom = YearMonth.parse("2024-05"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = søknadsbarn.ident,
                    kilde = Kilde.OFFENTLIG,
                )
            inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2024-01").atDay(1)
            inntekt.bestemDatoTomForOffentligInntekt() shouldBe YearMonth.parse("2024-05").atEndOfMonth()
        }
        assertSoftly {
            val inntekt =
                opprettInntekt(
                    behandling = behandling,
                    opprinneligFom = YearMonth.parse("2023-07"),
                    opprinneligTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    gjelderBarn = søknadsbarn2.ident,
                    kilde = Kilde.OFFENTLIG,
                )
            inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2023-07").atDay(1)
            inntekt.bestemDatoTomForOffentligInntekt() shouldBe YearMonth.parse("2023-11").atEndOfMonth()
        }
    }

    @Test
    fun `skal bestemme datoTom for offentlig barnetillegg hvis opphørsdato`() {
        val behandling = oppretteBehandling()
        val søknadsbarn =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 5,
                opphørsdato = LocalDate.parse("2024-01-01"),
            )
        behandling.roller.add(søknadsbarn)
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        søknadsbarn.opphørsdato = YearMonth.parse("2024-06").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2024-01"),
                opprinneligTom = YearMonth.parse("2024-05"),
                type = Inntektsrapportering.BARNETILLEGG,
                gjelderBarn = søknadsbarn.ident,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2024-01").atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe YearMonth.parse("2024-05").atEndOfMonth()
    }

    @Test
    fun `skal bestemme fjerne for offentlig barnetillegg hvis opphørsdato er før datoFom`() {
        val behandling = oppretteBehandling()
        val søknadsbarn =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 5,
                opphørsdato = LocalDate.parse("2024-01-01"),
            )
        behandling.roller.add(søknadsbarn)
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        søknadsbarn.opphørsdato = YearMonth.parse("2024-04").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2024-05"),
                opprinneligTom = YearMonth.parse("2024-09"),
                type = Inntektsrapportering.BARNETILLEGG,
                gjelderBarn = søknadsbarn.ident,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.skalAutomatiskSettePeriode() shouldBe false
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe null
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme datoTom for offentlig utvidet barnetrygd hvis samme måned`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2023-06").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2023-01"),
                opprinneligTom = YearMonth.now(),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2023-06").atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme datoTom for offentlig utvidet barnetrygd hvis samme måned og virkningstidspunkt er fram i tid`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.now().plusMonths(1).atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.now().minusMonths(6),
                opprinneligTom = YearMonth.now().plusMonths(1),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe behandling.virkningstidspunkt
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme periode for offentlig ytelse hvis opprinnelig periode starter før virkningstidspunkt `() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2022-01"),
                opprinneligTom = YearMonth.parse("2030-01"),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2023-05").atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme periode for offentlig ytelse hvis opprinnelig periode er fram i tid `() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2024-01"),
                opprinneligTom = YearMonth.parse("2030-06"),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2024-01").atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme periode for offentlig ytelse hvis virkningstidspunkt er fram i tid`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.now().plusMonths(12).atDay(1)
        val periodeFom = YearMonth.now().plusMonths(8)
        val periodeTom = YearMonth.now().plusYears(5)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = periodeFom,
                opprinneligTom = periodeTom,
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe behandling.virkningstidspunkt
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme periode for offentlig ytelse hvis virkningstidspunkt er fram i tid og periode før`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.now().plusMonths(12).atDay(1)
        val periodeFom = YearMonth.parse("2023-01")
        val periodeTom = YearMonth.now().plusYears(5)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = periodeFom,
                opprinneligTom = periodeTom,
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe behandling.virkningstidspunkt
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme periode for offentlig ytelse hvis virkningstidspunkt tilbake i tid og periode tom er inneværende måned`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2022-01").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2023-08"),
                opprinneligTom = YearMonth.now(),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.parse("2023-08").atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal bestemme periode for offentlig ytelse hvis virkningstidspunkt tilbake i tid og periode fom er inneværende måned`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2022-01").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.now(),
                opprinneligTom = YearMonth.now().plusMonths(5),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe YearMonth.now().atDay(1)
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    @Disabled
    fun `skal ikke sette periode hvis offentlig periode er etter virkningstidspunkt 2`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2024-05").atDay(1)
        val periodeFom = YearMonth.parse("2024-08")
        val periodeTom = YearMonth.parse("2026-08")
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = periodeFom,
                opprinneligTom = periodeTom,
                type = Inntektsrapportering.SMÅBARNSTILLEGG,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.skalAutomatiskSettePeriode() shouldBe false
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe null
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal ikke sette periode hvis offentlig periode er før virkningstidspunkt`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2022-01"),
                opprinneligTom = YearMonth.parse("2023-01"),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.skalAutomatiskSettePeriode() shouldBe false
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe null
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }

    @Test
    fun `skal ikke sette periode hvis offentlig periode er etter virkningstidspunkt`() {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.parse("2023-05").atDay(1)
        val inntekt =
            opprettInntekt(
                behandling = behandling,
                opprinneligFom = YearMonth.parse("2022-01"),
                opprinneligTom = YearMonth.parse("2023-01"),
                type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                kilde = Kilde.OFFENTLIG,
            )
        inntekt.skalAutomatiskSettePeriode() shouldBe false
        inntekt.bestemDatoFomForOffentligInntekt() shouldBe null
        inntekt.bestemDatoTomForOffentligInntekt() shouldBe null
    }
}
