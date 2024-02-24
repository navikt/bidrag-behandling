package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class OppdatereBehandlingTest : BehandlingControllerTest() {
    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `skal oppdatere behandling`() {
        // gitt
        val b = testdataManager.opprettBehandling()

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequestV2(123L)),
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
        val b = testdataManager.opprettBehandling()

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequestV2(123L)),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.CREATED, behandlingRes.statusCode)

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(b.id!!)

        assertNotNull(oppdatertBehandling)
        Assertions.assertEquals(123L, oppdatertBehandling.get().grunnlagspakkeid)
    }

    @Test
    @Transactional
    @Ignore("Gir 404 - lagret behandling ikke tilgjengelig")
    fun `skal aktivere grunnlag`() {
        // gitt
        val behandling = testdataManager.opprettBehandling()

        testdataManager.oppretteOgLagreGrunnlag<GrunnlagInntekt>(
            behandling = behandling,
            grunnlagsdatatype = Grunnlagsdatatype.INNTEKT,
            innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
            aktiv = null,
        )

        entityManager.persist(behandling)

        val idTilIkkeAktiverteGrunnlag = behandling.grunnlag.filter { it.aktiv == null }.map { it.id!! }.toSet()

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
