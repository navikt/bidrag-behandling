package no.nav.bidrag.behandling.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.service.NotatOpplysningerService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class NotatFeilhåndteringScheduler(
    private val notatService: NotatOpplysningerService,
    private val behandlingRepository: BehandlingRepository,
) {
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "opprettNotatHvisFeilet", lockAtLeastFor = "10m")
    @Transactional
    fun oppdaterStatusPaFerdigstilteDokumenterSkeduler() {
        val behandlingerSomManglerNotater = behandlingRepository.hentBehandlingerSomManglerNotater()
        log.info { "Fant ${behandlingerSomManglerNotater.size} behandlinger som mangler notat" }
        behandlingerSomManglerNotater.forEach { behandling ->
            log.info { "Oppretter notat for behandling ${behandling.id}" }
            try {
                notatService.opprettNotat(behandling.id!!)
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved opprettelse av notat for behandling ${behandling.id}" }
            }
        }
    }
}
