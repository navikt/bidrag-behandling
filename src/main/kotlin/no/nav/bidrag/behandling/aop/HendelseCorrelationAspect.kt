package no.nav.bidrag.behandling.aop

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.commons.CorrelationId.Companion.CORRELATION_ID_HEADER
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.*

private val log = KotlinLogging.logger {}

@Component
@Aspect
class HendelseCorrelationAspect(private val objectMapper: ObjectMapper) {
    @Before(value = "execution(* no.nav.bidrag.behandling.kafka.VedtakHendelseListener.prossesserVedtakHendelse(..)) && args(hendelse)")
    fun leggSporingFraVedtakHendelseTilMDC(joinPoint: JoinPoint, hendelse: ConsumerRecord<String, String>) {
        hentSporingFraHendelse(hendelse)?.let {
            val correlationId = CorrelationId.existing(it)
            MDC.put(CORRELATION_ID_HEADER, correlationId.get())
        } ?: run {
            val tilfeldigVerdi = UUID.randomUUID().toString().subSequence(0, 8)
            val korrelasjonsId = "${tilfeldigVerdi}_prossesserDokumentHendelse"
            MDC.put(CORRELATION_ID_HEADER, CorrelationId.existing(korrelasjonsId).get())
        }
    }

    private fun hentSporingFraHendelse(hendelse: ConsumerRecord<String, String>): String? {
        return try {
            val vedtakHendelse = objectMapper.readValue(hendelse.value(), VedtakHendelse::class.java)
            vedtakHendelse.sporingsdata.correlationId
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            null
        }
    }

    @After(value = "execution(* no.nav.bidrag.behandling.kafka.VedtakHendelseListener.*(..))")
    fun clearCorrelationIdFromKafkaListener(joinPoint: JoinPoint) {
        MDC.clear()
    }
}
