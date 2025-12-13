package no.nav.bidrag.behandling.async

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.async.dto.GrunnlagInnhentingBestilling
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BestillAsyncJobAfterCommitListener(
    private val bestillAsyncJobListener: BestillAsyncJobService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun bestillInnhentingAvGrunnlag(bestilling: GrunnlagInnhentingBestilling) {
        if (!bestilling.waitForCommit) return
        bestillAsyncJobListener.bestillInnhentingAvGrunnlagAsync(bestilling.copy(waitForCommit = false))
    }
}
