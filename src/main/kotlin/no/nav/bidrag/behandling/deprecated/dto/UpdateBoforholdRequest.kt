package no.nav.bidrag.behandling.deprecated.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto

data class UpdateBoforholdRequest(
    @Schema(required = true)
    val husstandsBarn: Set<HusstandsbarnDto>,
    @Schema(required = true)
    val sivilstand: Set<SivilstandDto>,
    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
)
