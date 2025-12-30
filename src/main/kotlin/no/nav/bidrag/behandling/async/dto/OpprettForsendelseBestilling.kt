package no.nav.bidrag.behandling.async.dto

import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterRollerRequest

data class OpprettForsendelseBestilling(
    val behandlingId: Long,
    val waitForCommit: Boolean = true,
)
