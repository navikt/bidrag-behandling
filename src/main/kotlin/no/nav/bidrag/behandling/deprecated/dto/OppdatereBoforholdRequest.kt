package no.nav.bidrag.behandling.deprecated.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto

data class OppdatereBoforholdRequest(
    @Deprecated("Bruk husstandsbarn")
    val husstandsBarn: Set<HusstandsbarnDto>,
    @Schema(required = true)
    val sivilstand: Set<SivilstandDto>,
    val boforholdsbegrunnelseIVedtakOgNotat: String? = null,
    val boforholdsbegrunnelseKunINotat: String? = null,

    )
