package no.nav.bidrag.behandling.dto.inntekt

data class UpdateBehandlingInntekterRequest(
    val inntekter: Set<InntektDto>,
)
