package no.nav.bidrag.behandling.async

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.async.dto.BehandlingOppdateringBestilling
import no.nav.bidrag.behandling.async.dto.GrunnlagInnhentingBestilling
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.GrunnlagService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val log = KotlinLogging.logger {}

@Component
class BestillAsyncJobListener(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
) {
    @EventListener
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @Async
    fun bestillInnhentingAvGrunnlag(bestilling: GrunnlagInnhentingBestilling) {
        if (bestilling.waitForCommit) return
        log.info { "Async: Henter grunnlag for behandling ${bestilling.behandlingId}" }
        grunnlagService.oppdatereGrunnlagForBehandling(bestilling.behandlingId)
    }

    @EventListener
    @Async
    fun behandleBestillingAvOppdateringAvRoller(bestilling: BehandlingOppdateringBestilling) {
        log.info { "Async: Oppdaterer roller for behandling ${bestilling.behandlingId}" }
        behandlingService.oppdaterRoller(bestilling.behandlingId, bestilling.request)
    }
}
