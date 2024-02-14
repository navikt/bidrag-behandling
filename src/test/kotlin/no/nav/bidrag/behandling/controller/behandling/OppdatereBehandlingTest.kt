package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertNotNull

class OppdatereBehandlingTest : BehandlingControllerTest() {
    @Test
    fun `skal oppdatere behandling`() {
        // gitt
        val b = behandlingRepository.save(BehandlingServiceTest.prepareBehandling())

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequest(123L)),
                BehandlingDtoV2::class.java,
            )

        Assertions.assertEquals(HttpStatus.CREATED, behandlingRes.statusCode)

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(b.id!!)

        assertNotNull(oppdatertBehandling)
        Assertions.assertEquals(123L, oppdatertBehandling.get().grunnlagspakkeid)
    }

    @Test
    fun `skal oppdatere behandling for API v2`() {
        // gitt
        val b = behandlingRepository.save(BehandlingServiceTest.prepareBehandling())

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequest(123L)),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.CREATED, behandlingRes.statusCode)

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(b.id!!)

        assertNotNull(oppdatertBehandling)
        Assertions.assertEquals(123L, oppdatertBehandling.get().grunnlagspakkeid)
    }

    @Test
    fun `skal aktivere grunnlag`() {
        // gitt
        val behandling = behandlingRepository.save(BehandlingServiceTest.prepareBehandling())

        testdataManager.oppretteOgLagreGrunnlag<GrunnlagInntekt>(
            behandlingsid = behandling.id!!,
            grunnlagsdatatype = Grunnlagsdatatype.INNTEKT,
            innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
            aktiv = null,
        )

        val idTilIkkeAktiverteGrunnlag =
            grunnlagRepository.findAll().filter { it.aktiv == null }.map { it.id!! }.toSet()

        // hvis
        val respons =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.PUT,
                HttpEntity(
                    OppdaterBehandlingRequestV2(
                        aktivereGrunnlag = idTilIkkeAktiverteGrunnlag,
                        grunnlagspakkeId = 123L,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )

        Assertions.assertEquals(HttpStatus.CREATED, respons.statusCode)

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        assertSoftly {
            oppdatertBehandling.isPresent
            oppdatertBehandling.get().grunnlag.size shouldBe 1
            oppdatertBehandling.get().grunnlag.first().aktiv shouldNotBe null
            oppdatertBehandling.get().grunnlag.first().aktiv?.toLocalDate() shouldBe LocalDate.now()
        }
    }
}
