package no.nav.bidrag.behandling.dto.v2.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag

data class OmgjortBehandlingFraVedtak(
    val behandling: Behandling,
    val vedtakId: Long,
    val opplysninger: List<BehandlingGrunnlag>,
)
