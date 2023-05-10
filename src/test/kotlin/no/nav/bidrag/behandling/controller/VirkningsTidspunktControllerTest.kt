package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.dto.virkningstidspunkt.UpdateVirkningsTidspunktRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class VirkningsTidspunktControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal oppdatere virknings tidspunkt data`() {
        val behandling = behandlingService.createBehandling(BehandlingServiceTest.prepareBehandling())

        val req: UpdateVirkningsTidspunktRequest = UpdateVirkningsTidspunktRequest(
            "MED I VEDTAK",
            "KUN I NOTAT",
            AvslagType.IKKE_OPPH_I_RIKET,
            ForskuddBeregningKodeAarsakType.KF,
            null,
        )

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/${behandling.id}/virkningstidspunkt", HttpMethod.PUT, HttpEntity(req), Void::class.java)
        Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

//    @Test
//    fun `skal validere aarsak type not blank`() {
//        val updateReq = UpdateBehandlingRequestTest(aarsak = "")
//        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/123", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)
//
//        Assertions.assertEquals(HttpStatus.BAD_REQUEST, updatedBehandling.statusCode)
//    }

//    @Test
//    fun `skal ignorere felt som ikke eksisterer i backend`() {
//        val roller = setOf(
//            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
//            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
//        )
//        val createBehandling = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)
//
//        val behandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(createBehandling), CreateBehandlingResponse::class.java)
//        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)
//
//        val updateReq = UpdateBehandlingRequestNonExistingFieldTest(avslag = AvslagType.MANGL_DOK.name, begrunnelseMedIVedtakNotat = "Some text")
//        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/${behandling.body!!.id}", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)
//
//        Assertions.assertEquals(HttpStatus.OK, updatedBehandling.statusCode)
//    }

//    @Test
//    fun `skal validere avlag type not blank`() {
//        val updateReq = UpdateBehandlingRequestTest(avslag = " ")
//        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/123", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)
//
//        Assertions.assertEquals(HttpStatus.BAD_REQUEST, updatedBehandling.statusCode)
//    }

//    @Test
//    fun `skal validere datoer`() {
//        val updateReq = UpdateBehandlingRequestTest(avslag = AvslagType.MANGL_DOK.name, virkningsDato = "1.2.2023")
//        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/123", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)
//
//        Assertions.assertEquals(HttpStatus.BAD_REQUEST, updatedBehandling.statusCode)
//    }
}
