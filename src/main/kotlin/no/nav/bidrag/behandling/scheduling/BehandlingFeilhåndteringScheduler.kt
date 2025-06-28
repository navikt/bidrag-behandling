package no.nav.bidrag.behandling.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.commons.util.secureLogger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class BehandlingFeilhåndteringScheduler(
    private val forsendelseService: ForsendelseService,
    private val behandlingRepository: BehandlingRepository,
) {
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "opprettOgDistribuerForsendelserAldersjustering", lockAtLeastFor = "10m")
    @Transactional
    fun opprettOgDistribuerForsendelserAldersjustering() {
        val behandlinger = behandlingRepository.hentBehandlingerHvorDistribusjonAvForsendelseFeilet()
        log.info { "Fant ${behandlinger.size} behandlinger hvor distribusjon av forsendelse feilet" }
        behandlinger.forEach { behandling ->
            secureLogger.info {
                "Forsøker opprett og distribuer forsendelser for ${behandling.id} med bestilling ${behandling.forsendelseBestillinger}"
            }
            val bestillinger = behandling.forsendelseBestillinger
            bestillinger.bestillinger.forEach {
                it.antallForsøkOpprettEllerDistribuer += 1
            }
            try {
                forsendelseService.opprettForsendelseForAldersjustering(behandling)
                bestillinger.bestillinger.forEach {
                    forsendelseService.distribuerForsendelse(it)
                }
                behandlingRepository.save(behandling)
            } catch (e: Exception) {
                log.error(e) {
                    "Det skjedde en feil ved opprettelse av forsendelse og distribusjon for aldersjustering vedtak i behandling ${behandling.id}"
                }
            }
        }
    }
}
