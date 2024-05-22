package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.domene.enums.person.Bostatuskode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class BoforholdPeriodeFilterTest : AktivGrunnlagTestFelles() {
    @Test
    fun `skal filtrere perioder som kommer før virkningstidspunkt`() {
        val behandling = byggBehandling()
        val aktivBoforholdGrunnlagListeBarn1 =
            listOf(
                BoforholdResponse(
                    periodeFom = YearMonth.of(2021, 1).atDay(1),
                    periodeTom = YearMonth.of(2022, 1).atEndOfMonth(),
                    bostatus = Bostatuskode.MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2022, 2).atDay(1),
                    periodeTom = YearMonth.of(2023, 6).atEndOfMonth(),
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2023, 7).atDay(1),
                    periodeTom = YearMonth.of(2023, 12).atEndOfMonth(),
                    bostatus = Bostatuskode.MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2024, 1).atDay(1),
                    periodeTom = null,
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
            )

        val husstandsmedlemmer = opprettHusstandsmedlemmer(behandling)

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2022-05-01"),
            ).toList(),
        ) {
            this shouldHaveSize 3
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 3
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2022-05-01")
        }

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2023-08-01"),
            ).toList(),
        ) {
            this shouldHaveSize 2
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 2
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2023-08-01")
        }

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2021-05-01"),
            ).toList(),
        ) {
            this shouldHaveSize 4
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 4
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2021-05-01")
        }

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2020-01-01"),
            ).toList(),
        ) {
            this shouldHaveSize 4
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 4
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2021-01-01")
        }
    }

    @Test
    fun `skal filtrere perioder som kommer før virkningstidspunkt eller barnets fødselsdato`() {
        val behandling = byggBehandling()
        val aktivBoforholdGrunnlagListeBarn1 =
            listOf(
                BoforholdResponse(
                    periodeFom = YearMonth.of(2023, 7).atDay(1),
                    periodeTom = YearMonth.of(2023, 12).atEndOfMonth(),
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn2.fødselsdato,
                    relatertPersonPersonId = testdataBarn2.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2024, 1).atDay(1),
                    periodeTom = YearMonth.of(2024, 7).atEndOfMonth(),
                    bostatus = Bostatuskode.MED_FORELDER,
                    fødselsdato = testdataBarn2.fødselsdato,
                    relatertPersonPersonId = testdataBarn2.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2024, 8).atDay(1),
                    periodeTom = null,
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn2.fødselsdato,
                    relatertPersonPersonId = testdataBarn2.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2022, 1).atDay(1),
                    periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2023, 1).atDay(1),
                    periodeTom = YearMonth.of(2023, 7).atEndOfMonth(),
                    bostatus = Bostatuskode.MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2023, 8).atDay(1),
                    periodeTom = YearMonth.of(2024, 1).atEndOfMonth(),
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
                BoforholdResponse(
                    periodeFom = YearMonth.of(2024, 2).atDay(1),
                    periodeTom = null,
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relatertPersonPersonId = testdataBarn1.ident,
                ),
            )

        val husstandsmedlemmer = opprettHusstandsmedlemmer(behandling)

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2023-01-01"),
            ).toList(),
        ) {
            this shouldHaveSize 6

            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 3
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2023-01-01")

            val barn2Perioder = it.filter { it.relatertPersonPersonId == testdataBarn2.ident }
            barn2Perioder shouldHaveSize 3
            barn2Perioder.first().periodeFom shouldBe LocalDate.parse("2023-07-01")
        }

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2023-12-01"),
            ).toList(),
        ) {
            this shouldHaveSize 5
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 2
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2023-12-01")

            val barn2Perioder = it.filter { it.relatertPersonPersonId == testdataBarn2.ident }
            barn2Perioder shouldHaveSize 3
            barn2Perioder.first().periodeFom shouldBe LocalDate.parse("2023-12-01")
        }

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2024-02-01"),
            ).toList(),
        ) {
            this shouldHaveSize 3
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 1
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2024-02-01")

            val barn2Perioder = it.filter { it.relatertPersonPersonId == testdataBarn2.ident }
            barn2Perioder shouldHaveSize 2
            barn2Perioder.first().periodeFom shouldBe LocalDate.parse("2024-02-01")
        }

        assertSoftly(
            aktivBoforholdGrunnlagListeBarn1.filtrerPerioderEtterVirkningstidspunkt(
                husstandsmedlemmer,
                LocalDate.parse("2024-01-01"),
            ).toList(),
        ) {
            this shouldHaveSize 4
            val barn1Perioder = it.filter { it.relatertPersonPersonId == testdataBarn1.ident }
            barn1Perioder shouldHaveSize 2
            barn1Perioder.first().periodeFom shouldBe LocalDate.parse("2024-01-01")

            val barn2Perioder = it.filter { it.relatertPersonPersonId == testdataBarn2.ident }
            barn2Perioder shouldHaveSize 2
            barn2Perioder.first().periodeFom shouldBe LocalDate.parse("2024-01-01")
        }
    }
}
