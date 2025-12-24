package no.nav.bidrag.behandling.async.dto

import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest
import no.nav.bidrag.transport.behandling.hendelse.BehandlingHendelseType

data class BehandlingHendelseBestilling(
    val behandlingId: Long,
    val type: BehandlingHendelseType = BehandlingHendelseType.ENDRET,
)
