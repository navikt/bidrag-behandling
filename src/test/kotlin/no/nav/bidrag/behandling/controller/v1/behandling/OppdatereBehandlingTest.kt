package no.nav.bidrag.behandling.controller.v1.behandling

import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class OppdatereBehandlingTest : BehandlingControllerTest() {
    @Test
    fun `skal oppdatere behandling`() {
        val b = behandlingRepository.save(BehandlingServiceTest.prepareBehandling())

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequest(123L)),
                Void::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val updatedBehandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${b.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                BehandlingDto::class.java,
            )

        assertNotNull(updatedBehandling.body)
        Assertions.assertEquals(123L, updatedBehandling.body!!.grunnlagspakkeid)
    }

    @Test
    @Ignore
    fun `skal oppdatere behandling for API v2`() {
        val b = behandlingRepository.save(BehandlingServiceTest.prepareBehandling())

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequest(123L)),
                Void::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val updatedBehandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${b.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                BehandlingDto::class.java,
            )

        assertNotNull(updatedBehandling.body)
        Assertions.assertEquals(123L, updatedBehandling.body!!.grunnlagspakkeid)
    }
}
