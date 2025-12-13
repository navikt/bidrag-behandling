package no.nav.bidrag.behandling.async.dto

import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest

data class BehandlingOppdateringBestilling(
    val behandlingId: Long,
    val request: OppdaterRollerRequest,
)
