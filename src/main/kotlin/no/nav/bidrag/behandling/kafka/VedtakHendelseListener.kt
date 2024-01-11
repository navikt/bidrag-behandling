package no.nav.bidrag.behandling.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.transformers.behandlingId
import no.nav.bidrag.behandling.transformers.engangsbeløptype
import no.nav.bidrag.behandling.transformers.erFattetFraBidragBehandling
import no.nav.bidrag.behandling.transformers.saksnummer
import no.nav.bidrag.behandling.transformers.stønadstype
import no.nav.bidrag.behandling.transformers.søknadsid
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class VedtakHendelseListener(
    val objectMapper: ObjectMapper,
    val forsendelseService: ForsendelseService,
    val behandlingService: BehandlingService,
) {
    @KafkaListener(groupId = "bidrag-behandling", topics = ["\${TOPIC_VEDTAK}"])
    fun prossesserVedtakHendelse(melding: ConsumerRecord<String, String>) {
        val vedtak = parseVedtakHendelse(melding)

        log.info { "Mottok hendelse for vedtak ${vedtak.id} med type ${vedtak.type}" }

        if (!vedtak.erFattetFraBidragBehandling()) return
        val behandling = behandlingService.hentBehandlingById(vedtak.behandlingId!!)
        log.info {
            "Mottok hendelse for vedtak ${vedtak.id} med type ${vedtak.type}. Lagrer vedtakId på behandling og " +
                "oppretter forsendelser for vedtaket"
        }

        behandlingService.oppdaterBehandling(
            vedtak.behandlingId!!,
            no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest(
                vedtaksid = vedtak.id.toLong(),
            ),
        ) // Lagre vedtakId i tilfelle respons i frontend timet ut (eller nettverksfeil osv) slik at vedtakId ikke ble lagret på behandling.
        opprettForsendelse(vedtak, behandling)
    }

    private fun opprettForsendelse(
        vedtak: VedtakHendelse,
        behandling: Behandling,
    ) {
        forsendelseService.slettEllerOpprettForsendelse(
            no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest(
                saksnummer = vedtak.saksnummer,
                enhet = vedtak.enhetsnummer?.verdi,
                behandlingInfo =
                    no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingInfoDto(
                        soknadId = vedtak.søknadsid ?: behandling.soknadsid,
                        vedtakId = vedtak.id.toLong(),
                        soknadFra = behandling.soknadFra,
                        stonadType = vedtak.stønadstype,
                        engangsBelopType = vedtak.engangsbeløptype,
                        erFattetBeregnet = true,
                        vedtakType = vedtak.type,
                    ),
                roller = behandling.tilForsendelseRolleDto(),
            ),
        )
    }

    private fun parseVedtakHendelse(melding: ConsumerRecord<String, String>): VedtakHendelse {
        try {
            return objectMapper.readValue(melding.value(), VedtakHendelse::class.java)
        } catch (e: Exception) {
            log.error(e) { "Det skjedde en feil ved konverting av melding fra hendelse" }
            throw KunneIkkeLeseMeldingFraHendelse(e.message, e)
        }
    }
}
