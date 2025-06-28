package no.nav.bidrag.behandling.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.dokument.JournalpostHendelse
import no.nav.bidrag.transport.dokument.JournalpostStatus
import no.nav.bidrag.transport.dokument.numeric
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Component
class JournalpostHendelseListener(
    val forsendelseService: ForsendelseService,
    val behandlingRepository: BehandlingRepository,
) {
    @KafkaListener(groupId = "bidrag-behandling", topics = ["\${TOPIC_JOURNALPOST}"])
    @Transactional
    fun prossesserJournalpostHendelse(melding: ConsumerRecord<String, String>) {
        val hendelse = parseJournalpostHendelse(melding)
        if (!hendelse.erForsendelse() && hendelse.status != JournalpostStatus.KLAR_FOR_DISTRIBUSJON) return
        val behandlinger = behandlingRepository.hentBehandlingerSomInneholderBestillingMedForsendelseId(hendelse.journalpostId.numeric)
        behandlinger.forEach { behandling ->
            val bestillinger = behandling.forsendelseBestillinger
            val bestillingForForsendelse = bestillinger.bestillinger.find { it.forsendelseId == hendelse.journalpostId.numeric }!!
            secureLogger.info { "Distribuerer forsendelse bestilling $bestillingForForsendelse i behandling ${behandling.id}" }
            forsendelseService.distribuerForsendelse(bestillingForForsendelse)
            behandlingRepository.save(behandling)
        }
    }

    private fun parseJournalpostHendelse(melding: ConsumerRecord<String, String>): JournalpostHendelse {
        try {
            return commonObjectmapper.readValue(melding.value(), JournalpostHendelse::class.java)
        } catch (e: Exception) {
            log.error(e) { "Det skjedde en feil ved konverting av melding fra hendelse" }
            throw KunneIkkeLeseMeldingFraHendelse(e.message, e)
        }
    }
}
