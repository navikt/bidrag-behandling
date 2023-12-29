package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

class VirkningstidspunktControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal oppdatere virknings tidspunkt data`() {
        val behandling =
            behandlingService.opprettBehandling(BehandlingServiceTest.prepareBehandling())

        val req =
            OppdaterBehandlingRequest(
                virkningstidspunkt =
                    OppdaterVirkningstidspunkt(
                        årsak = ForskuddAarsakType.KF,
                        virkningsdato = LocalDate.parse("2025-12-27"),
                        notat =
                            OppdaterNotat(
                                "KUN I NOTAT",
                                "MED I VEDTAK",
                            ),
                    ),
            )

        val respons =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}",
                HttpMethod.PUT,
                HttpEntity(req),
                BehandlingDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, respons.statusCode)

        val body = respons.body!!
        Assertions.assertEquals("KUN I NOTAT", body.virkningstidspunkt.notat.kunINotat)
        Assertions.assertEquals("MED I VEDTAK", body.virkningstidspunkt.notat.medIVedtaket)
        Assertions.assertEquals("2025-12-27", body.virkningstidspunkt.virkningsdato.toString())
    }
}
