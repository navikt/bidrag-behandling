package no.nav.bidrag.behandling.async

import no.nav.bidrag.behandling.async.dto.BehandlingHendelseBestilling
import no.nav.bidrag.behandling.async.dto.BehandlingOppdateringBestilling
import no.nav.bidrag.behandling.async.dto.GrunnlagInnhentingBestilling
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BestillAsyncJobService(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun bestillInnhentingAvGrunnlag(bestilling: GrunnlagInnhentingBestilling) {
        applicationEventPublisher.publishEvent(bestilling)
    }

    @Async
    fun bestillInnhentingAvGrunnlagAsync(bestilling: GrunnlagInnhentingBestilling) {
        applicationEventPublisher.publishEvent(bestilling)
    }

    @Async
    fun bestillOppdateringAvRoller(bestilling: BehandlingOppdateringBestilling) {
        applicationEventPublisher.publishEvent(bestilling)
    }

    fun bestillHendelse(bestilling: BehandlingHendelseBestilling) {
        applicationEventPublisher.publishEvent(bestilling)
    }
}
