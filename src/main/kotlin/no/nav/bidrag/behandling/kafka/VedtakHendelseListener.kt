package no.nav.bidrag.behandling.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.service.NotatOpplysningerService
import no.nav.bidrag.behandling.service.OppgaveService
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.vedtak.engangsbeløptype
import no.nav.bidrag.behandling.transformers.vedtak.stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import no.nav.bidrag.transport.behandling.vedtak.behandlingId
import no.nav.bidrag.transport.behandling.vedtak.erFattetGjennomBidragBehandling
import no.nav.bidrag.transport.behandling.vedtak.saksnummer
import no.nav.bidrag.transport.behandling.vedtak.søknadId
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class VedtakHendelseListener(
    val objectMapper: ObjectMapper,
    val forsendelseService: ForsendelseService,
    val behandlingService: BehandlingService,
    val notatOpplysningerService: NotatOpplysningerService,
    val oppgaveService: OppgaveService,
) {
    @KafkaListener(groupId = "bidrag-behandling", topics = ["\${TOPIC_VEDTAK}"])
    fun prossesserVedtakHendelse(melding: ConsumerRecord<String, String>) {
        val vedtak = parseVedtakHendelse(melding)
        if (vedtak.kilde != Vedtakskilde.AUTOMATISK) {
            opprettRevurderForskuddOppgaveVedBehov(vedtak)
        }
        if (!vedtak.erFattetGjennomBidragBehandling()) {
            log.info {
                "Mottok hendelse for vedtak ${vedtak.id} med type ${vedtak.type}. " +
                    "Vedtak er ikke fattet gjennom bidrag-behandling. Ignorer hendelse"
            }
            return
        }
        log.info {
            "Mottok hendelse for vedtak ${vedtak.id} med type ${vedtak.type}, " +
                "behandlingId ${vedtak.behandlingId} og saksnummer ${vedtak.saksnummer}"
        }
        val behandling = behandlingService.hentBehandlingById(vedtak.behandlingId!!)
        log.info {
            "Lagrer vedtakId ${vedtak.id} på behandling ${vedtak.behandlingId} og " +
                "oppretter forsendelse og notat for vedtaket"
        }

        // Dette gjøres synkront etter fatte vedtak
//        opprettNotat(behandling)
        opprettForsendelse(vedtak, behandling)
        behandlingService.oppdaterVedtakFattetStatus(
            vedtak.behandlingId!!,
            vedtaksid = vedtak.id.toLong(),
            vedtak.enhetsnummer?.verdi ?: behandling.behandlerEnhet,
        )
    }

    private fun opprettRevurderForskuddOppgaveVedBehov(vedtak: VedtakHendelse) {
        try {
            oppgaveService.opprettRevurderForskuddOppgave(vedtak)
        } catch (e: Exception) {
            log.error(e) { "Det skjedde en feil ved opprettelse av revurder forskudd oppgave for vedtak ${vedtak.id}" }
        }
    }

    private fun opprettNotat(behandling: Behandling) {
        try {
            notatOpplysningerService.opprettNotat(behandling.id!!)
        } catch (e: Exception) {
            log.error(
                e,
            ) { "Det skjedde en feil ved opprettelse av notat for behandling ${behandling.id} og vedtaksid ${behandling.vedtaksid}" }
        }
    }

    private fun opprettForsendelse(
        vedtak: VedtakHendelse,
        behandling: Behandling,
    ) {
        forsendelseService.slettEllerOpprettForsendelse(
            InitalizeForsendelseRequest(
                saksnummer = vedtak.saksnummer!!,
                enhet = vedtak.enhetsnummer?.verdi,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = vedtak.søknadId ?: behandling.soknadsid,
                        vedtakId = vedtak.id.toLong(),
                        behandlingId = behandling.id!!,
                        soknadFra = behandling.soknadFra,
                        stonadType = vedtak.stønadstype,
                        engangsBelopType = if (vedtak.stønadstype == null) vedtak.engangsbeløptype else null,
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
