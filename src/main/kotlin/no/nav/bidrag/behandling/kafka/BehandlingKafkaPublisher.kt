package no.nav.bidrag.behandling.kafka

import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class BehandlingKafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${TOPIC_BEHANDLING}") val topic: String,
) {
    @Retryable(value = [Exception::class], maxAttempts = 10, backoff = Backoff(delay = 1000, maxDelay = 12000, multiplier = 2.0))
    fun publiser(hendelse: BehandlingHendelse) {
        secureLogger.info { "Publiserer behandling hendelse $hendelse" }
        kafkaTemplate.send(topic, hendelse.behandlingsid.toString(), commonObjectmapper.writeValueAsString(hendelse))
    }
}
