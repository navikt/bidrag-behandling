package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

class VirkningstidspunktControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Test
    fun `skal oppdatere virkningstidspunktdata`() {
        val behandling = behandlingRepository.save(BehandlingServiceTest.prepareBehandling())

        val req =
            OppdaterBehandlingRequest(
                virkningstidspunkt =
                    OppdaterVirkningstidspunkt(
                        årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT,
                        virkningstidspunkt = LocalDate.parse("2022-12-27"),
                        notat =
                            OppdaterNotat(
                                "KUN I NOTAT",
                                "MED I VEDTAK",
                            ),
                    ),
            )

        val respons =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}",
                HttpMethod.PUT,
                HttpEntity(req),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.CREATED, respons.statusCode)

        val body = respons.body!!
        Assertions.assertEquals("KUN I NOTAT", body.virkningstidspunkt.notat.kunINotat)
        Assertions.assertEquals("MED I VEDTAK", body.virkningstidspunkt.notat.medIVedtaket)
        Assertions.assertEquals("2022-12-27", body.virkningstidspunkt.virkningstidspunkt.toString())
    }
}
