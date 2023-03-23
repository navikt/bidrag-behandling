package no.nav.bidrag.behandling.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class UpdateBehandlingRequest(
    val begrunnelseMedIVedtakNotat: String? = null,
    val begrunnelseKunINotat: String? = null,

    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val virkningsDato: LocalDate? = null,
)
