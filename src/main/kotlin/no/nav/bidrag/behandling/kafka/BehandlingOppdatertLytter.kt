package no.nav.bidrag.behandling.kafka

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.dokument.Sporingsdata
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
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional(Transactional.TxType.REQUIRED)
    fun behandleBehandlingLagret(behandlingHendelse: BehandlingEndringHendelse) {
        val behandling = behandlingRepository.hentBehandlingInkludertSlettet(behandlingHendelse.behandlingId)!!
        val roller = behandlingRepository.hentRollerInkludertSlettet(behandlingHendelse.behandlingId)!!
        val erVedtakFattet = behandling.vedtakDetaljer?.vedtakstidspunkt != null
        val erBehandlingSlettet = behandling.deleted
        val hendelse =
            BehandlingHendelse(
                behandlingsid = behandlingHendelse.behandlingId,
                vedtakstype = behandling.vedtakstype,
                opprettetTidspunkt = behandling.opprettetTidspunkt,
                endretTidspunkt = LocalDateTime.now(),
                mottattDato = behandling.mottattdato,
                behandlerEnhet = behandling.behandlerEnhet,
                søknadsid = if (behandling.forholdsmessigFordeling == null) behandling.soknadsid else null,
                barn =
                    roller.filter { it.rolletype == Rolletype.BARN }.map {
                        BehandlingHendelseBarn(
                            søktAv = it.forholdsmessigFordeling?.søktAvType ?: behandling.soknadFra,
                            søktFraDato = it.forholdsmessigFordeling?.søknadFomDato ?: behandling.søktFomDato,
                            ident = it.ident!!,
                            søknadsid = it.forholdsmessigFordeling?.søknadsid ?: behandling.soknadsid,
                            behandlerEnhet = it.forholdsmessigFordeling?.behandlerEnhet?.verdi ?: behandling.behandlerEnhet,
                            saksnummer = it.forholdsmessigFordeling?.tilhørerSak ?: behandling.saksnummer,
                            behandlingstype = behandling.søknadstype ?: Behandlingstype.SØKNAD,
                            status =
                                when {
                                    it.deleted -> Behandlingstatus.FEILREGISTRERT
                                    erVedtakFattet -> Behandlingstatus.VEDTAK_FATTET
                                    else -> Behandlingstatus.UNDER_BEHANDLING
                                },
                        )
                    },
                sporingsdata =
                    Sporingsdata(
                        correlationId = null,
                        brukerident = TokenUtils.hentSaksbehandlerIdent(),
                        enhetsnummer = behandling.behandlerEnhet,
                        saksbehandlersNavn =
                            TokenUtils.hentSaksbehandlerIdent()?.let {
                                SaksbehandlernavnProvider.hentSaksbehandlernavn(it)
                            },
                    ),
                status =
                    when {
                        behandling.deleted -> BehandlingStatusType.AVBRUTT
                        behandling.vedtakDetaljer?.vedtakstidspunkt != null -> BehandlingStatusType.VEDTAK_FATTET
                        else -> BehandlingStatusType.UNDER_BEHANDLING
                    },
                type =
                    when {
                        behandling.opprettetTidspunkt.isAfter(LocalDateTime.now().minusMinutes(1)) -> BehandlingHendelseType.OPPRETTET
                        behandling.vedtakDetaljer?.vedtakstidspunkt != null -> BehandlingHendelseType.AVSLUTTET
                        erBehandlingSlettet -> BehandlingHendelseType.AVSLUTTET
                        else -> behandlingHendelse.type
                    },
            )

        behandlingKafkaPublisher.publiser(hendelse)
    }
}
