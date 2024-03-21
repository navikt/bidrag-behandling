package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.utils.hentInntektForBarn
import no.nav.bidrag.behandling.utils.testdata.TestDataPerson
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.opprettRolle
import no.nav.bidrag.behandling.utils.testdata.opprettSivilstand
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate

class HentBehandlingTest : BehandlingControllerTest() {
    @Test
    fun `skal hente behandling`() {
        // gitt
        stubUtils.stubHenteGrunnlagOk(
            navnResponsfil = "grunnlagresponse.json",
            rolle = testdataBM.tilRolle(),
        )
        stubUtils.stubHenteGrunnlagOk(
            tomRespons = true,
            rolle = testdataBarn1.tilRolle(),
        )
        stubUtils.stubHenteGrunnlagOk(
            tomRespons = true,
            rolle = testdataBarn2.tilRolle(),
        )

        val behandling = testdataManager.lagreBehandling(opprettBehandling())

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                BehandlingDtoV2::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly(behandlingRes.body!!) {
            it.inntekter.beregnetInntekter shouldHaveSize 3
            val inntekterAlle =
                it.inntekter.beregnetInntekter.find { it.inntektGjelderBarnIdent == null }
            val inntekterBarn1 =
                it.inntekter.beregnetInntekter.hentInntektForBarn(testdataBarn1.ident)
            val inntekterBarn2 =
                it.inntekter.beregnetInntekter.hentInntektForBarn(testdataBarn2.ident)
            inntekterAlle.shouldNotBeNull()
            inntekterBarn1.shouldNotBeNull()
            inntekterBarn2.shouldNotBeNull()

            assertSoftly(inntekterAlle) {
                summertInntektListe shouldHaveSize 3
                summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(55000)
                summertInntektListe[0].barnetillegg shouldBe null
                summertInntektListe[0].kontantstøtte shouldBe null
            }
            assertSoftly(inntekterBarn2) {
                summertInntektListe shouldHaveSize 3
                summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(55000)
                summertInntektListe[0].barnetillegg shouldBe null
                summertInntektListe[0].kontantstøtte shouldBe null
            }
            assertSoftly(inntekterBarn1) {
                summertInntektListe shouldHaveSize 3
                summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(55000)
                summertInntektListe[0].barnetillegg shouldBe BigDecimal(5000)
                summertInntektListe[0].kontantstøtte shouldBe null
            }
        }
    }

    private fun opprettBehandling(): Behandling {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "notat virkning med i vedtak"
        behandling.virkningstidspunktbegrunnelseKunINotat = "notat virkning"
        behandling.husstandsbarn =
            mutableSetOf(
                opprettHusstandsbarn(behandling, testdataBarn1),
                opprettHusstandsbarn(behandling, testdataBarn2),
            )
        behandling.roller =
            mutableSetOf(
                opprettRolle(behandling, testdataBarn1),
                opprettRolle(behandling, testdataBarn2),
                opprettRolle(behandling, testdataBM),
            )
        behandling.sivilstand =
            mutableSetOf(
                opprettSivilstand(
                    behandling,
                    LocalDate.parse("2023-01-01"),
                    LocalDate.parse("2023-05-31"),
                    Sivilstandskode.BOR_ALENE_MED_BARN,
                ),
                opprettSivilstand(
                    behandling,
                    LocalDate.parse("2023-06-01"),
                    null,
                    Sivilstandskode.BOR_ALENE_MED_BARN,
                ),
            )
        behandling.inntekter = opprettInntekter(behandling, testdataBM, testdataBarn1)
        return behandling
    }

    private fun opprettInntekter(
        behandling: Behandling,
        gjelder: TestDataPerson,
        barn: TestDataPerson? = null,
    ): MutableSet<Inntekt> {
        val inntekter = mutableSetOf<Inntekt>()
        val inntekt1 =
            Inntekt(
                Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                BigDecimal.valueOf(45000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2023-07-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = false,
                behandling = behandling,
            )
        inntekt1.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    beløp = BigDecimal.valueOf(5000),
                    kode = "fisking",
                    inntektstype = null,
                    inntekt = inntekt1,
                ),
                Inntektspost(
                    beløp = BigDecimal.valueOf(40000),
                    kode = "krypto",
                    inntektstype = null,
                    inntekt = inntekt1,
                ),
            )
        inntekter.add(inntekt1)

        val inntekt2 =
            Inntekt(
                Inntektsrapportering.LIGNINGSINNTEKT,
                BigDecimal.valueOf(33000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2024-01-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = true,
                behandling = behandling,
            )
        inntekt2.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    beløp = BigDecimal.valueOf(5000),
                    kode = "",
                    inntektstype = Inntektstype.NÆRINGSINNTEKT,
                    inntekt = inntekt2,
                ),
                Inntektspost(
                    beløp = BigDecimal.valueOf(28000),
                    kode = "",
                    inntektstype = Inntektstype.LØNNSINNTEKT,
                    inntekt = inntekt2,
                ),
            )
        inntekter.add(inntekt2)

        inntekter.add(
            Inntekt(
                Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                BigDecimal.valueOf(55000),
                LocalDate.parse("2022-01-01"),
                LocalDate.parse("2022-12-31"),
                gjelder.ident,
                Kilde.MANUELL,
                true,
                behandling = behandling,
            ),
        )
        if (barn != null) {
            val inntekt3 =
                Inntekt(
                    Inntektsrapportering.BARNETILLEGG,
                    BigDecimal.valueOf(5000),
                    LocalDate.parse("2022-01-01"),
                    LocalDate.parse("2022-12-31"),
                    gjelder.ident,
                    Kilde.OFFENTLIG,
                    true,
                    opprinneligFom = LocalDate.parse("2023-01-01"),
                    opprinneligTom = LocalDate.parse("2024-01-01"),
                    gjelderBarn = barn.ident,
                    behandling = behandling,
                )
            inntekt3.inntektsposter =
                mutableSetOf(
                    Inntektspost(
                        beløp = BigDecimal.valueOf(5000),
                        kode = "",
                        inntektstype = Inntektstype.BARNETILLEGG_PENSJON,
                        inntekt = inntekt3,
                    ),
                )
            inntekter.add(inntekt3)

            val inntekt4 =
                Inntekt(
                    Inntektsrapportering.KONTANTSTØTTE,
                    BigDecimal.valueOf(5000),
                    LocalDate.parse("2023-01-01"),
                    LocalDate.parse("2023-12-31"),
                    gjelder.ident,
                    Kilde.OFFENTLIG,
                    true,
                    opprinneligFom = LocalDate.parse("2023-01-01"),
                    opprinneligTom = LocalDate.parse("2024-01-01"),
                    gjelderBarn = testdataBarn1.ident,
                    behandling = behandling,
                )
            inntekt4.inntektsposter =
                mutableSetOf(
                    Inntektspost(
                        beløp = BigDecimal.valueOf(5000),
                        kode = "",
                        inntektstype = Inntektstype.KONTANTSTØTTE,
                        inntekt = inntekt4,
                    ),
                )
            inntekter.add(inntekt4)
        }
        return inntekter
    }
}
