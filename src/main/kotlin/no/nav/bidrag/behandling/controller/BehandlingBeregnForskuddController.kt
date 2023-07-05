package no.nav.bidrag.behandling.controller

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.beregning.ForskuddBeregning
import no.nav.bidrag.behandling.consumer.BeregnForskuddPayload
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
import no.nav.bidrag.behandling.service.BehandlingService
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestController
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer,
    private val forskuddBeregning: ForskuddBeregning,
) {

    @Suppress("unused")
    @PostMapping("/behandling/{behandlingId}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnForskudd(@PathVariable behandlingId: Long): Either<NonEmptyList<String>, List<ForskuddDto>> =
        either {
            val behandling = behandlingService.hentBehandlingById(behandlingId)
            val behandlingModel = forskuddBeregning.toBehandlingBeregningModel(behandling).bind()
            val results = behandling
                .roller
                .filter { RolleType.BARN == it.rolleType }
                .mapOrAccumulate {
                    val payload = forskuddBeregning.toPayload(behandlingModel, it)

                    if (false) printDebugPayload(payload)

                    try {
                        bidragBeregnForskuddConsumer.beregnForskudd(payload)
                    } catch (e: Exception) {
                        LOGGER.warn { e }
                        raise(e.message!!)
                    }
                }.bind()

            return@either results
        }

    private fun printDebugPayload(payload: BeregnForskuddPayload) {
        val message = HttpEntity(payload)
        val objectMapper = ObjectMapper()

        objectMapper.writeValue(System.out, message.body)
    }
}
