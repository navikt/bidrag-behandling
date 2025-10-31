package no.nav.bidrag.behandling.kafka

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.transformers.erSærbidrag
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.EnhetProvider
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
                    roller.filter { it.rolletype == Rolletype.BARN }.flatMap { barn ->
                        val hendelseBarn =
                            BehandlingHendelseBarn(
                                søktAv = behandling.soknadFra,
                                søktFraDato = behandling.søktFomDato,
                                ident = barn.ident!!,
                                stønadstype = barn.stønadstype ?: behandling.stonadstype,
                                engangsbeløptype = behandling.engangsbeloptype,
                                behandlingstema = behandling.behandlingstema ?: Behandlingstema.BIDRAG,
                                søknadsid = behandling.soknadsid,
                                omgjørSøknadsid = behandling.omgjøringsdetaljer?.soknadRefId,
                                behandlerEnhet = barn.forholdsmessigFordeling?.behandlerenhet ?: behandling.behandlerEnhet,
                                saksnummer = barn.forholdsmessigFordeling?.tilhørerSak ?: behandling.saksnummer,
                                behandlingstype = behandling.søknadstype ?: Behandlingstype.SØKNAD,
                                særbidragskategori = if (behandling.erSærbidrag()) behandling.særbidragKategori else null,
                                status =
                                    when {
                                        barn.deleted -> Behandlingstatus.FEILREGISTRERT
                                        erVedtakFattet -> Behandlingstatus.VEDTAK_FATTET
                                        else -> barn.behandlingstatus ?: Behandlingstatus.UNDER_BEHANDLING
                                    },
                            )
                        barn.forholdsmessigFordeling?.søknader?.map {
                            hendelseBarn.copy(
                                søktAv = it.søktAvType,
                                søktFraDato = it.søknadFomDato ?: behandling.søktFomDato,
                                søknadsid = it.søknadsid ?: behandling.soknadsid,
                                omgjørSøknadsid = it.omgjørSøknadsid,
                                behandlingstema = it.behandlingstema ?: behandling.behandlingstema ?: Behandlingstema.BIDRAG,
                            )
                        } ?: listOf(hendelseBarn)
                    },
                sporingsdata =
                    Sporingsdata(
                        correlationId = "${LocalDateTime.now().toCompactString()}_behandling_søknadshendelse_${behandling.soknadsid}",
                        brukerident = TokenUtils.hentSaksbehandlerIdent(),
                        enhetsnummer = behandling.behandlerEnhet,
                        saksbehandlersNavn =
                            TokenUtils.hentSaksbehandlerIdent()?.let {
                                EnhetProvider.hentSaksbehandlernavn(it)
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
