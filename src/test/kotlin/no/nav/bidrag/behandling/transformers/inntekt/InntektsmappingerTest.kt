package no.nav.bidrag.behandling.transformers.inntekt

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
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
