package no.nav.bidrag.behandling.dto.inntekt

import io.swagger.v3.oas.annotations.media.Schema

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
