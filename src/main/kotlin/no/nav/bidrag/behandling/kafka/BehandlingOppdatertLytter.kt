package no.nav.bidrag.behandling.kafka

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.hendelse.BehandlingHendelse
import no.nav.bidrag.transport.behandling.hendelse.BehandlingHendelseBarn
import no.nav.bidrag.transport.behandling.hendelse.BehandlingHendelseType
import no.nav.bidrag.transport.behandling.hendelse.BehandlingStatusType
import no.nav.bidrag.transport.dokument.Sporingsdata
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime
import kotlin.collections.filter

data class BehandlingEndringHendelse(
    val behandlingId: Long,
    val type: BehandlingHendelseType = BehandlingHendelseType.ENDRET,
)

@Component
class BehandlingOppdatertLytter(
    private val behandlingRepository: BehandlingRepository,
    private val behandlingKafkaPublisher: BehandlingKafkaPublisher,
) {
    //    fun sendBehandlingOppdatertHendelse(behandlingHendelse: BehandlingEndringHendelse) {
//    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional
    fun sendBehandlingOppdatertHendelse(
        behandlingId: Long,
        type: BehandlingHendelseType = BehandlingHendelseType.ENDRET,
    ) {
        if (!UnleashFeatures.SEND_BEHANDLING_HENDELSE.isEnabled) return
        val behandling = behandlingRepository.hentBehandlingInkludertSlettet(behandlingId)!!
        val roller = behandlingRepository.hentRollerInkludertSlettet(behandlingId)
        val erVedtakFattet = behandling.vedtakDetaljer?.vedtakstidspunkt != null
        val erBehandlingSlettet = behandling.deleted
        val hendelse =
            BehandlingHendelse(
                søknadsid = behandling.soknadsid,
                behandlingsid = behandling.id,
                omgjørSøknadsid = behandling.omgjøringsdetaljer?.soknadRefId,
                vedtakstype = behandling.vedtakstype,
                opprettetTidspunkt = behandling.opprettetTidspunkt,
                endretTidspunkt = LocalDateTime.now(),
                mottattDato = behandling.mottattdato,
                behandlerEnhet = behandling.behandlerEnhet,
                barn =
                    roller.filter { it.rolletype == Rolletype.BARN }.map {
                        BehandlingHendelseBarn(
                            søktAv = it.forholdsmessigFordeling?.søktAvType ?: behandling.soknadFra,
                            søktFraDato = it.forholdsmessigFordeling?.søknadFomDato ?: behandling.søktFomDato,
                            ident = it.ident!!,
                            stønadstype = it.stønadstype ?: behandling.stonadstype,
                            engangsbeløptype = behandling.engangsbeloptype,
                            behandlingstema = it.behandlingstema ?: behandling.behandlingstema ?: Behandlingstema.BIDRAG,
                            søknadsid = it.forholdsmessigFordeling?.søknadsid ?: behandling.soknadsid,
                            behandlerEnhet = it.forholdsmessigFordeling?.behandlerEnhet?.verdi ?: behandling.behandlerEnhet,
                            saksnummer = it.forholdsmessigFordeling?.tilhørerSak ?: behandling.saksnummer,
                            behandlingstype = behandling.søknadstype ?: Behandlingstype.SØKNAD,
                            status =
                                when {
                                    it.deleted -> Behandlingstatus.FEILREGISTRERT
                                    erVedtakFattet -> Behandlingstatus.VEDTAK_FATTET
                                    else -> it.behandlingstatus ?: Behandlingstatus.UNDER_BEHANDLING
                                },
                        )
                    },
                sporingsdata =
                    Sporingsdata(
                        correlationId = "${LocalDateTime.now().toCompactString()}_behandling_søknadshendelse_${behandling.soknadsid}",
                        brukerident = TokenUtils.hentSaksbehandlerIdent(),
                        enhetsnummer = behandling.behandlerEnhet,
                        saksbehandlersNavn =
                            TokenUtils.hentSaksbehandlerIdent()?.let {
                                SaksbehandlernavnProvider.hentSaksbehandlernavn(it)
                            },
                    ),
                status =
                    when {
                        erBehandlingSlettet -> BehandlingStatusType.AVBRUTT
                        behandling.vedtakDetaljer?.vedtakstidspunkt != null -> BehandlingStatusType.VEDTAK_FATTET
                        else -> BehandlingStatusType.UNDER_BEHANDLING
                    },
                type =
                    when {
                        behandling.opprettetTidspunkt.isAfter(LocalDateTime.now().minusMinutes(1)) -> BehandlingHendelseType.OPPRETTET
                        behandling.vedtakDetaljer?.vedtakstidspunkt != null -> BehandlingHendelseType.AVSLUTTET
                        erBehandlingSlettet -> BehandlingHendelseType.AVSLUTTET
                        else -> type
                    },
            )

        behandlingKafkaPublisher.publiser(hendelse)
    }
}
