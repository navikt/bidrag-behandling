package no.nav.bidrag.behandling.dto.inntekt

data class UpdateBehandlingInntekterRequest(
    val inntekter: Set<InntektDto>,
    // val barnetillegg
    // val utvidetBarnetrygd
    val inntektBegrunnelseMedIVedtakNotat: String? = null,
    val inntektBegrunnelseKunINotat: String? = null,
)
