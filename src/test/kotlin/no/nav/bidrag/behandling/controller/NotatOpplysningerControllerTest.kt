package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.notat.NotatDto
import no.nav.bidrag.behandling.utils.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdataBM
import no.nav.bidrag.behandling.utils.testdataBarn1
import no.nav.bidrag.behandling.utils.testdataBarn2
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.YearMonth

class NotatOpplysningerControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal hente opplysninger for notat`() {
        val behandling = testdataManager.opprettBehandling()
        stubUtils.stubHentePersoninfo(
            personident = testdataBM[Rolle::ident.name] as String,
            navn = testdataBM[Rolle::navn.name] as String,
            shouldContaintPersonIdent = true,
        )
        stubUtils.stubHentePersoninfo(
            personident = testdataBarn1[Rolle::ident.name] as String,
            navn = testdataBarn1[Rolle::navn.name] as String,
            shouldContaintPersonIdent = true,
        )
        stubUtils.stubHentePersoninfo(
            personident = testdataBarn2[Rolle::ident.name] as String,
            navn = testdataBarn2[Rolle::navn.name] as String,
            shouldContaintPersonIdent = true,
        )
        val r1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/notat/${behandling.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                NotatDto::class.java,
            )

        r1.statusCode shouldBe HttpStatus.OK

        val notatResponse = r1.body!!

        assertSoftly {
            notatResponse.saksnummer shouldBe SAKSNUMMER
            notatResponse.saksbehandlerNavn shouldBe "Fornavn Etternavn"
            notatResponse.virkningstidspunkt.søktAv shouldBe SøktAvType.BIDRAGSMOTTAKER
            notatResponse.virkningstidspunkt.virkningstidspunkt shouldBe LocalDate.parse("2023-02-01")
            notatResponse.virkningstidspunkt.søktFraDato shouldBe YearMonth.parse("2022-08")
            notatResponse.virkningstidspunkt.mottattDato shouldBe YearMonth.parse("2023-03")
            notatResponse.virkningstidspunkt.notat.medIVedtaket shouldBe "notat virkning med i vedtak"
            notatResponse.virkningstidspunkt.notat.intern shouldBe "notat virkning"

            notatResponse.inntekter.inntekterPerRolle shouldHaveSize 3
            notatResponse.boforhold.barn shouldHaveSize 2
            notatResponse.boforhold.sivilstand.opplysningerBruktTilBeregning shouldHaveSize 2

            val barn1 =
                notatResponse.parterISøknad.find { it.personident?.verdi == testdataBarn1[Rolle::ident.name] as String }!!
            barn1.navn shouldBe testdataBarn1[Rolle::navn.name] as String
            barn1.rolle shouldBe Rolletype.BARN
            barn1.fødselsdato shouldBe testdataBarn1[Rolle::foedselsdato.name]

            val inntekterBM =
                notatResponse.inntekter.inntekterPerRolle.find { it.rolle == Rolletype.BIDRAGSMOTTAKER }!!
            inntekterBM.inntekterSomLeggesTilGrunn shouldHaveSize 3

            notatResponse.boforhold.barn[0].navn shouldBeIn
                listOf(
                    testdataBarn1[Rolle::navn.name] as String,
                    testdataBarn2[Rolle::navn.name] as String,
                )
            notatResponse.boforhold.barn[1].navn shouldBeIn
                listOf(
                    testdataBarn1[Rolle::navn.name] as String,
                    testdataBarn2[Rolle::navn.name] as String,
                )
        }
    }
}
