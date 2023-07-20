package no.nav.bidrag.behandling.dto.inntekt

data class InntekterResponse(
    val inntekter: Set<InntektDto>,
    val barnetillegg: Set<BarnetilleggDto>,
    val utvidetbarnetrygd: Set<UtvidetbarnetrygdDto>,
    val inntektBegrunnelseMedIVedtakNotat: String? = null,
    val inntektBegrunnelseKunINotat: String? = null,
)
