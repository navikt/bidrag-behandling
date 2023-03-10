package no.nav.bidrag.behandling.controller

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.SpringTestRunner
import no.nav.bidrag.behandling.dto.HentPersonResponse
import no.nav.domain.ident.PersonIdent
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.Ignore

@Ignore
class ExampleControllerTest : SpringTestRunner() {

    @Test
    fun `Skal hente persondata`() {
        val httpEntity = HttpEntity(PersonIdent("22496818540"))
        stubUtils.stubBidragPersonResponse(HentPersonResponse("22496818540", "Navn Navnesen", "213213213"))

        val response = httpHeaderTestRestTemplate.exchange("${rootUri()}/person", HttpMethod.POST, httpEntity, HentPersonResponse::class.java)

        response.statusCode shouldBe HttpStatus.OK
    }
}
