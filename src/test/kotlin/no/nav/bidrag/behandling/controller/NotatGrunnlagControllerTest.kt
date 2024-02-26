package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.bidrag.behandling.dto.v1.notat.NotatDto
import no.nav.bidrag.behandling.service.hentPerson
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.person.PersonDto
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.YearMonth

@Import(AppContext::class)
class NotatGrunnlagControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal hente opplysninger for notat`() {
        val behandling = testdataManager.opprettBehandling(true)
        mockkStatic(::hentPerson)
        every { hentPerson(testdataBM.ident) } returns
            PersonDto(
                ident = Personident(testdataBM.ident),
                fødselsdato = LocalDate.now().minusMonths(500),
                visningsnavn = testdataBM.navn,
            )
        every { hentPerson(testdataBarn1.ident) } returns
            PersonDto(
                ident = Personident(testdataBarn1.ident),
                fødselsdato = LocalDate.now().minusMonths(500),
                visningsnavn = testdataBarn1.navn,
            )
        every { hentPerson(testdataBarn2.ident) } returns
            PersonDto(
                ident = Personident(testdataBarn2.ident),
                fødselsdato = LocalDate.now().minusMonths(500),
                visningsnavn = testdataBarn2.navn,
            )
        val r1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/notat/${behandling.id}",
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
            notatResponse.virkningstidspunkt.søktFraDato shouldBe YearMonth.parse("2022-02")
            notatResponse.virkningstidspunkt.mottattDato shouldBe YearMonth.parse("2023-03")
            notatResponse.virkningstidspunkt.notat.medIVedtaket shouldBe "notat virkning med i vedtak"
            notatResponse.virkningstidspunkt.notat.intern shouldBe "notat virkning"

            notatResponse.inntekter.inntekterPerRolle shouldHaveSize 3
            notatResponse.boforhold.barn shouldHaveSize 2
            notatResponse.boforhold.sivilstand.opplysningerBruktTilBeregning shouldHaveSize 2

            val barn1 =
                notatResponse.parterISøknad.find { it.personident?.verdi == testdataBarn1.ident }!!
            barn1.navn shouldBe testdataBarn1.navn
            barn1.rolle shouldBe Rolletype.BARN
            barn1.fødselsdato shouldBe testdataBarn1.foedselsdato

            val inntekterBM =
                notatResponse.inntekter.inntekterPerRolle.find { it.rolle == Rolletype.BIDRAGSMOTTAKER }!!
            inntekterBM.inntekterSomLeggesTilGrunn shouldHaveSize 3

            notatResponse.boforhold.barn[0].navn shouldBeIn
                listOf(
                    testdataBarn1.navn,
                    testdataBarn2.navn,
                )
            notatResponse.boforhold.barn[1].navn shouldBeIn
                listOf(
                    testdataBarn1.navn,
                    testdataBarn2.navn,
                )
        }
    }
}