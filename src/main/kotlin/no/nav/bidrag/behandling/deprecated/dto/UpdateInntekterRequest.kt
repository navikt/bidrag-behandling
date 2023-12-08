package no.nav.bidrag.behandling.deprecated.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto

data class UpdateInntekterRequest(
    @Schema(required = true)
    val inntekter: Set<InntektDto>,
    @Schema(required = true)
    val barnetillegg: Set<BarnetilleggDto>,
    @Schema(required = true)
    val utvidetbarnetrygd: Set<UtvidetbarnetrygdDto>,
    val inntektBegrunnelseMedIVedtakNotat: String? = null,
    val inntektBegrunnelseKunINotat: String? = null,
)
