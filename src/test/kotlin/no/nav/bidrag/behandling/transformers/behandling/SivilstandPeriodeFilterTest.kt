package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.sivilstand.response.SivilstandV1
import no.nav.bidrag.sivilstand.response.Status
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class SivilstandPeriodeFilterTest : AktivGrunnlagTestFelles() {
    @Test
    fun `skal filtrere bort perioder som kommer før virkningstidspunkt`() {
        val sivilstandGrunnlagDtoList =
            listOf(
                SivilstandGrunnlagDto(
                    gyldigFom = YearMonth.of(1978, 1).atDay(1),
                    historisk = true,
                    master = "FREG",
                    bekreftelsesdato = null,
                    personId = "",
                    registrert = null,
                    type = SivilstandskodePDL.UGIFT,
                ),
                SivilstandGrunnlagDto(
                    gyldigFom = YearMonth.of(2018, 1).atDay(1),
                    historisk = true,
                    master = "FREG",
                    bekreftelsesdato = null,
                    personId = "",
                    registrert = null,
                    type = SivilstandskodePDL.GIFT,
                ),
                SivilstandGrunnlagDto(
                    gyldigFom = YearMonth.of(2023, 1).atDay(1),
                    historisk = true,
                    master = "FREG",
                    bekreftelsesdato = null,
                    personId = "",
                    registrert = null,
                    type = SivilstandskodePDL.SKILT,
                ),
            )
        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.parse("2023-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 1
            this[0].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.parse("2022-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 2
            this[0].gyldigFom!! shouldBe YearMonth.of(2018, 1).atDay(1)
            this[1].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.parse("2017-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 3
            this[0].gyldigFom!! shouldBe YearMonth.of(1978, 1).atDay(1)
            this[1].gyldigFom!! shouldBe YearMonth.of(2018, 1).atDay(1)
            this[2].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }
        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.now().plusMonths(3))
                .toList(),
        ) {
            this shouldHaveSize 1
            this[0].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }

    }


    @Test
    fun `skal filtrere bort perioder som kommer før virkningstidspunkt hvis en av periodene inneholder null gyldigFom`() {
        val sivilstandGrunnlagDtoList =
            listOf(
                SivilstandGrunnlagDto(
                    gyldigFom = null,
                    historisk = true,
                    master = "FREG",
                    bekreftelsesdato = null,
                    personId = "",
                    registrert = null,
                    type = SivilstandskodePDL.UGIFT,
                ),
                SivilstandGrunnlagDto(
                    gyldigFom = YearMonth.of(2018, 1).atDay(1),
                    historisk = true,
                    master = "FREG",
                    bekreftelsesdato = null,
                    personId = "",
                    registrert = null,
                    type = SivilstandskodePDL.GIFT,
                ),
                SivilstandGrunnlagDto(
                    gyldigFom = YearMonth.of(2023, 1).atDay(1),
                    historisk = true,
                    master = "FREG",
                    bekreftelsesdato = null,
                    personId = "",
                    registrert = null,
                    type = SivilstandskodePDL.SKILT,
                ),
            )
        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.parse("2023-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 2
            this[0].gyldigFom.shouldBeNull()
            this[1].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.parse("2022-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 3
            this[0].gyldigFom.shouldBeNull()
            this[1].gyldigFom!! shouldBe YearMonth.of(2018, 1).atDay(1)
            this[2].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.parse("2017-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 3
            this[0].gyldigFom.shouldBeNull()
            this[1].gyldigFom!! shouldBe YearMonth.of(2018, 1).atDay(1)
            this[2].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(LocalDate.now().plusMonths(5))
                .toList(),
        ) {
            this shouldHaveSize 2
            this[0].gyldigFom.shouldBeNull()
            this[1].gyldigFom!! shouldBe YearMonth.of(2023, 1).atDay(1)
        }
    }

    @Test
    fun `skal filtrere bort perioder som kommer før virkningstidspunkt for beregnet`() {
        val sivilstandGrunnlagDtoList =
            listOf(
                Sivilstand(
                    periodeFom = YearMonth.of(2020, 1).atDay(1),
                    periodeTom = YearMonth.of(2020, 12).atEndOfMonth(),
                    sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                    kilde = Kilde.MANUELL,
                ),
                Sivilstand(
                    periodeFom = YearMonth.of(2021, 1).atDay(1),
                    periodeTom = YearMonth.of(2022, 1).atEndOfMonth(),
                    sivilstandskode = Sivilstandskode.ENSLIG,
                    kilde = Kilde.MANUELL,
                ),
                Sivilstand(
                    periodeFom = YearMonth.of(2023, 1).atDay(1),
                    periodeTom = null,
                    sivilstandskode = Sivilstandskode.ENSLIG,
                    kilde = Kilde.MANUELL,
                ),
            )
        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(LocalDate.parse("2023-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 1
            this[0].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(LocalDate.parse("2022-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 2
            this[0].periodeFom shouldBe YearMonth.of(2021, 1).atDay(1)
            this[1].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(LocalDate.parse("2020-05-01"))
                .toList(),
        ) {
            this shouldHaveSize 3
            this[0].periodeFom shouldBe YearMonth.of(2020, 1).atDay(1)
            this[1].periodeFom shouldBe YearMonth.of(2021, 1).atDay(1)
            this[2].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(LocalDate.now().plusMonths(56))
                .toList(),
        ) {
            this shouldHaveSize 1
            this[0].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }
    }

    @Test
    fun `skal filtrere bort perioder som kommer før virkningstidspunkt for beregnet v1 api`() {
        val sivilstandGrunnlagDtoList =
            SivilstandBeregnet(
                sivilstandListe =
                listOf(
                    SivilstandV1(
                        periodeFom = YearMonth.of(2020, 1).atDay(1),
                        periodeTom = YearMonth.of(2020, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                    ),
                    SivilstandV1(
                        periodeFom = YearMonth.of(2021, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 1).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.ENSLIG,
                    ),
                    SivilstandV1(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.ENSLIG,
                    ),
                ),
                status = Status.OK,
            )

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(LocalDate.parse("2023-05-01")),
        ) {
            sivilstandListe shouldHaveSize 1
            sivilstandListe[0].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(LocalDate.parse("2022-05-01")),
        ) {
            sivilstandListe shouldHaveSize 2
            sivilstandListe[0].periodeFom shouldBe YearMonth.of(2021, 1).atDay(1)
            sivilstandListe[1].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(LocalDate.parse("2020-05-01")),
        ) {
            sivilstandListe shouldHaveSize 3
            sivilstandListe[0].periodeFom shouldBe YearMonth.of(2020, 1).atDay(1)
            sivilstandListe[1].periodeFom shouldBe YearMonth.of(2021, 1).atDay(1)
            sivilstandListe[2].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }

        assertSoftly(
            sivilstandGrunnlagDtoList.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(LocalDate.now().plusMonths(5)),
        ) {
            sivilstandListe shouldHaveSize 1
            sivilstandListe[0].periodeFom shouldBe YearMonth.of(2023, 1).atDay(1)
        }
    }
}
