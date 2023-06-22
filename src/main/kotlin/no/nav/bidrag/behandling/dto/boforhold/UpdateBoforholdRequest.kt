package no.nav.bidrag.behandling.dto.boforhold

import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnDto

data class UpdateBoforholdRequest(
    val husstandsBarn: Set<HusstandsBarnDto>,
    val sivilstand: Set<SivilstandDto>,

    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
)
