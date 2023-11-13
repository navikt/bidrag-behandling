package no.nav.bidrag.behandling.dto.boforhold

import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto

data class BoforholdResponse(
    val husstandsBarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
)
