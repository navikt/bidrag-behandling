package no.nav.bidrag.behandling.controller

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.notat.NotatDto
import no.nav.bidrag.behandling.utils.SAKSNUMMER
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

        val r1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/notat/${behandling.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                NotatDto::class.java,
            )

        r1.statusCode shouldBe HttpStatus.OK

        val notatResponse = r1.body!!

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
    }
}
