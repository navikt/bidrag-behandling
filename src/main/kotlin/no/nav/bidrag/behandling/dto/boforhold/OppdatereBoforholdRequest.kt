package no.nav.bidrag.behandling.dto.boforhold

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto

data class OppdatereBoforholdRequest(
    @Deprecated("Bruk husstandsbarn")
    val husstandsBarn: Set<HusstandsbarnDto>,
    @Schema(required = true)
    val husstandsbarn: Set<HusstandsbarnDto> = husstandsBarn,
    @Schema(required = true)
    val sivilstand: Set<SivilstandDto>,
    val boforholdsbegrunnelseIVedtakOgNotat: String? = null,
    val boforholdsbegrunnelseKunINotat: String? = null,
)
