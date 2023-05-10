package no.nav.bidrag.behandling.dto.boforhold

import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.behandlingbarn.BehandlingBarnDto

data class UpdateBoforholdRequest(
    val behandlingBarn: Set<BehandlingBarnDto>,
    val sivilstand: Set<SivilstandDto>,

    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
)
