package no.nav.bidrag.behandling.dto

data class UpdateBehandlingRequest(
    val begrunnelseMedIVedtakNotat: String? = null,
    val begrunnelseKunINotat: String? = null,
)
