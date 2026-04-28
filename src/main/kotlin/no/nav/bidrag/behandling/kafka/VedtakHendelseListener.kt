package no.nav.bidrag.behandling.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.ForholdsmessigFordelingService
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.service.NotatOpplysningerService
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.vedtak.engangsbeløptype
import no.nav.bidrag.behandling.transformers.vedtak.stønadstype
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.vedtak.Stønadsendring
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import no.nav.bidrag.transport.behandling.vedtak.behandlingId
import no.nav.bidrag.transport.behandling.vedtak.erDelvedtak
import no.nav.bidrag.transport.behandling.vedtak.erFattetGjennomBidragBehandling
import no.nav.bidrag.transport.behandling.vedtak.saksnummer
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
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
    val grunnlagService: GrunnlagService,
    val behandlingRepository: BehandlingRepository,
    val forholdsmessigFordelingService: ForholdsmessigFordelingService,
) {
    @KafkaListener(groupId = "bidrag-behandling", topics = ["\${TOPIC_VEDTAK}"])
    @Transactional
    fun prossesserVedtakHendelse(melding: ConsumerRecord<String, String>) {
        val vedtak = parseVedtakHendelse(melding)
        vedtak.oppdaterÅpenFFBehandlingHvisOpphørEllerInnkreving()

        if (!vedtak.erFattetGjennomBidragBehandling()) {
            log.info {
                "Mottok hendelse for vedtak ${vedtak.id} med type ${vedtak.type}. " +
                    "Vedtak er ikke fattet gjennom bidrag-behandling. Ignorer hendelse"
            }
            return
        }
        if (vedtak.erDelvedtak()) {
            log.info { "Vedtak ${vedtak.id} er delvedtak, oppretter ikke forsendelse eller oppdaterer behandling" }
            return
        }

        secureLogger.info {
            "Mottok hendelse for vedtak ${vedtak.id} med type ${vedtak.type}, " +
                "behandlingId ${vedtak.behandlingId} og saksnummer ${vedtak.saksnummer}: $vedtak"
        }
        val behandling = behandlingService.hentBehandlingById(vedtak.behandlingId!!)

        if (vedtak.erInnkrevingAvBidragOmgjøringsvedtak(behandling)) {
            log.info {
                "Vedtak ${vedtak.id} er innkreving av omgjøringsvedtak ${behandling.vedtakDetaljer?.vedtaksid}, avslutter behandling av vedtakhendelse"
            }
            return
        }

        // Dette gjøres synkront etter fatte vedtak
//        opprettNotat(behandling)

        log.info {
            "Mottok hendelse for vedtak ${vedtak.id}. Lagrer vedtakId ${vedtak.id} på behandling ${vedtak.behandlingId} og oppretter forsendelse vedtaket"
        }
        opprettForsendelse(vedtak, behandling)
        behandlingService.oppdaterVedtakFattetStatus(
            vedtak.behandlingId!!,
            vedtaksid = vedtak.id,
            vedtak.enhetsnummer?.verdi ?: behandling.behandlerEnhet,
        )
    }

    private fun VedtakHendelse.oppdaterÅpenFFBehandlingHvisOpphørEllerInnkreving() {
        if ((type != Vedtakstype.OPPHØR && type != Vedtakstype.INNKREVING) ||
            !UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG.isEnabled
        ) {
            return
        }
        val stønadsendringerBidrag =
            stønadsendringListe?.filter { it.type == Stønadstype.BIDRAG || it.type == Stønadstype.BIDRAG18AAR } ?: emptyList()
        if (stønadsendringerBidrag.isEmpty()) return
        stønadsendringerBidrag.forEach { stønadsendring ->

            val bp = stønadsendring.skyldner
            val behandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBpMedFF(bp.verdi)
            behandlinger.forEach { behandling ->
                behandleBehandlingHvisOpphorEllerInnkreving(stønadsendring, behandling)
            }
        }
    }

    private fun VedtakHendelse.behandleBehandlingHvisOpphorEllerInnkreving(
        stønadsendring: Stønadsendring,
        behandling: Behandling,
    ) {
        // Hent grunnlag beløpshistorikk slik at det er oppdatert
        grunnlagService.lagreBeløpshistorikkGrunnlag(behandling)
        grunnlagService.lagreBeløpshistorikkFraOpprinneligVedtakstidspunktGrunnlag(behandling)
        forholdsmessigFordelingService.oppdaterSøknadStatuserForAlleRoller(behandling)
        if (type == Vedtakstype.OPPHØR) {
            val opphørsperiode =
                stønadsendring.periodeListe
                    .filter { it.beløp == null }
                    .maxByOrNull {
                        it.periode.fom
                    } ?: return
            forholdsmessigFordelingService
                .oppdaterBarnEtterOpphør(
                    behandling,
                    stønadsendring.kravhaver,
                    stønadsendring.type,
                    opphørsperiode,
                )
        } else {
            if (behandling.søknadsbarn.none { it.erSammeRolle(stønadsendring.kravhaver.verdi, stønadsendring.type) }) {
                // Henter og legger til barn som revurderingsbarn
                behandling.privatAvtale.removeIf {
                    it.rolle == null &&
                        (it.rolle!!.erSammeRolle(stønadsendring.kravhaver.verdi, stønadsendring.type))
                }
                forholdsmessigFordelingService.opprettEllerOppdaterForholdsmessigFordeling(behandling.id!!)
                forholdsmessigFordelingService.synkroniserSøknadsbarnOgRevurderingsbarnForFFBehandling(behandling)
            } else {
                forholdsmessigFordelingService.oppdaterBarnEtterInnkrevingsvedtak(
                    behandling,
                    stønadsendring.kravhaver,
                )
            }
        }
    }

    private fun VedtakHendelse.erInnkrevingAvBidragOmgjøringsvedtak(behandling: Behandling): Boolean {
        if (type != Vedtakstype.INNKREVING) return false
        return behandling.innkrevingstype == Innkrevingstype.UTEN_INNKREVING && behandling.erKlageEllerOmgjøring && behandling.erBidrag()
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
        if (vedtak.type == Vedtakstype.ALDERSJUSTERING) return
        behandling.saker.forEach { sak ->
            val søknader = behandling.søknadForSak(sak)

            søknader.forEach { opprettForSøknad ->

                forsendelseService.slettEllerOpprettForsendelse(
                    InitalizeForsendelseRequest(
                        saksnummer = sak,
                        enhet = vedtak.enhetsnummer?.verdi ?: behandling.behandlerEnhet,
                        behandlingInfo =
                            BehandlingInfoDto(
                                soknadId = opprettForSøknad.søknadsid.toString(),
                                vedtakId = vedtak.id.toString(),
                                behandlingId = behandling.id!!.toString(),
                                soknadFra = opprettForSøknad.søktAvType,
                                stonadType = vedtak.stønadstype,
                                engangsBelopType = if (vedtak.stønadstype == null) vedtak.engangsbeløptype else null,
                                erFattetBeregnet = true,
                                vedtakType = vedtak.type,
                            ),
                        roller = behandling.tilForsendelseRolleDto(sak, opprettForSøknad),
                    ),
                )
            }
        }
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
