package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.boforhold.BoforholdResponse
import no.nav.bidrag.behandling.dto.boforhold.UpdateBoforholdRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Date

class BoforholdControllerTest : KontrollerTestRunner() {

    @Test
    fun `skal lagre boforhold data`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            CreateBehandlingResponse::class.java,
        )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        // 2.
        val boforholdData = UpdateBoforholdRequest(emptySet(), emptySet(), "med i vedtak", "kun i notat") //
        val boforholdResponse = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/${behandling.body!!.id}/boforhold",
            HttpMethod.PUT,
            HttpEntity(boforholdData),
            BoforholdResponse::class.java,
        )
    }
}
