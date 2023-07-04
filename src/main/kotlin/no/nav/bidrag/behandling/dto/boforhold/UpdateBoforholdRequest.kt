package no.nav.bidrag.behandling.dto.boforhold

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnDto

data class UpdateBoforholdRequest(
    @Schema(required = true)
    val husstandsBarn: Set<HusstandsBarnDto>,
    @Schema(required = true)
    val sivilstand: Set<SivilstandDto>,

    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
)
