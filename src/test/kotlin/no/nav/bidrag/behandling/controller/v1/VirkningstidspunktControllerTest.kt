package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.dto.virkningstidspunkt.VirkningstidspunktResponse
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class VirkningstidspunktControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal oppdatere virknings tidspunkt data`() {
        val behandling = behandlingService.createBehandling(BehandlingServiceTest.prepareBehandling())

        val req =
            OppdatereVirkningsTidspunktRequestTest(
                "MED I VEDTAK",
                "KUN I NOTAT",
                ForskuddAarsakType.KF,
                "2025-12-27",
            )

        val respons =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(req),
                VirkningstidspunktResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, respons.statusCode)

        val body = respons.body!!
        Assertions.assertEquals("KUN I NOTAT", body.virkningstidspunktsbegrunnelseKunINotat)
        Assertions.assertEquals("MED I VEDTAK", body.virkningstidspunktsbegrunnelseIVedtakOgNotat)
        Assertions.assertEquals("2025-12-27", body.virkningsdato.toString())
    }
}

data class OppdatereVirkningsTidspunktRequestTest(
    val virkningstidspunktsbegrunnelseIVedtakOgNotat: String? = null,
    val virkningstidspunktsbegrunnelseKunINotat: String? = null,
    val årsak: ForskuddAarsakType? = null,
    val virkningsdato: String? = null,
)
