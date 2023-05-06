package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Date

data class AddOpplysningerRequest(
    val behandlingId: Long,
    val aktiv: Boolean,
    val opplysningerType: OpplysningerType,
    val data: String,
    val hentetDato: String,
)

class OpplysningerControllerTest : KontrollerTestRunner() {

    @Test
    fun `skal opprette og oppdatere opplysninger`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), CreateBehandlingResponse::class.java)
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        val opp = skalOppretteOpplysninger(behandlingId, "opp", false)
        skalOppretteOpplysninger(behandlingId, "opp1", true)

        // 3. Assert that opp1 is active
        val oppAktivResult1 = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/$behandlingId/opplysninger/aktiv", HttpMethod.GET, HttpEntity.EMPTY, OpplysningerDto::class.java)
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult1.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult1.body!!.behandlingId)
        Assertions.assertEquals("opp1", oppAktivResult1.body!!.data)
        Assertions.assertTrue(oppAktivResult1.body!!.aktiv)

        // 4. Set opp as active
        val oppAktiv = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/$behandlingId/opplysninger/${opp.id}/aktiv", HttpMethod.PUT, HttpEntity.EMPTY, Void::class.java)

        Assertions.assertEquals(HttpStatus.OK, oppAktiv.statusCode)

        // 4. Assert that opp is active now
        val oppAktivResult = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/$behandlingId/opplysninger/aktiv", HttpMethod.GET, HttpEntity.EMPTY, OpplysningerDto::class.java)

        Assertions.assertEquals(HttpStatus.OK, oppAktivResult.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult.body!!.behandlingId)
        Assertions.assertEquals("opp", oppAktivResult.body!!.data)
        Assertions.assertTrue(oppAktivResult.body!!.aktiv)
    }

    private fun skalOppretteOpplysninger(behandlingId: Long, data: String, aktiv: Boolean): OpplysningerDto {
        val opplysninger = createOpplysninger(behandlingId, data, aktiv)

        val opp = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/$behandlingId/opplysninger",
            HttpMethod.POST,
            HttpEntity(opplysninger),
            OpplysningerDto::class.java,
        )

        Assertions.assertEquals(HttpStatus.OK, opp.statusCode)
        val body = opp.body!!
        Assertions.assertEquals(behandlingId, body.behandlingId)
        Assertions.assertEquals(aktiv, body.aktiv)

        return body
    }

    private fun createOpplysninger(behandlingId: Long, data: String, aktiv: Boolean): AddOpplysningerRequest {
        val opplysninger =
            AddOpplysningerRequest(behandlingId, aktiv, OpplysningerType.BOFORHOLD, data, "01.02.2025")
        return opplysninger
    }
}
