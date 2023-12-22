package no.nav.bidrag.behandling.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.bidrag.behandling.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
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
            OppdaterBehandlingRequest(
                vedtaksid = vedtak.id.toLong()
            )
        ) // Lagre vedtakId i tilfelle respons i frontend timet ut (eller nettverksfeil osv) slik at vedtakId ikke ble lagret på behandling.
        opprettForsendelse(vedtak, behandling)
    }

    private fun opprettForsendelse(
        vedtak: VedtakHendelse,
        behandling: Behandling,
    ) {
        forsendelseService.slettEllerOpprettForsendelse(
            InitalizeForsendelseRequest(
                saksnummer = vedtak.saksnummer,
                enhet = vedtak.enhetsnummer?.verdi,
                behandlingInfo =
                BehandlingInfoDto(
                    soknadId = vedtak.soknadId ?: behandling.soknadsid,
                    vedtakId = vedtak.id.toLong(),
                    soknadFra = behandling.soknadFra,
                    stonadType = vedtak.stonadType,
                    engangsBelopType = vedtak.engangsbelopType,
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
            log.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            throw KunneIkkeLeseMeldingFraHendelse(e.message, e)
        }
    }
}

val VedtakHendelse.stonadType get() = this.stønadsendringListe?.firstOrNull()?.type
val VedtakHendelse.engangsbelopType get() = this.engangsbeløpListe?.firstOrNull()?.type
val VedtakHendelse.soknadId
    get() =
        this.behandlingsreferanseListe?.find {
            it.kilde == BehandlingsrefKilde.BISYS_SØKNAD.name
        }?.referanse?.toLong()
val VedtakHendelse.behandlingId
    get() =
        this.behandlingsreferanseListe?.find {
            it.kilde == BehandlingsrefKilde.BEHANDLING_ID.name
        }?.referanse?.toLong()

fun VedtakHendelse.erFattetFraBidragBehandling() = behandlingId != null

val VedtakHendelse.saksnummer
    get(): String =
        stønadsendringListe?.firstOrNull()?.sak?.verdi
            ?: engangsbeløpListe?.firstOrNull()?.sak?.verdi
            ?: throw RuntimeException("Vedtak hendelse med id $id mangler saksnummer")
