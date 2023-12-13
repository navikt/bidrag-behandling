package no.nav.bidrag.behandling.dto.virkningstidspunkt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import java.time.LocalDate

data class VirkningstidspunktResponse(
    val virkningstidspunktsbegrunnelseIVedtakOgNotat: String? = null,
    val virkningstidspunktsbegrunnelseKunINotat: String? = null,
    @Schema(name = "årsak")
    val årsak: ForskuddAarsakType? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningsdato: LocalDate? = null,
)
