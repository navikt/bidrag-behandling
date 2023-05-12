package no.nav.bidrag.behandling.dto.virkningstidspunkt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import java.time.LocalDate

data class VirkningsTidspunktResponse(
    val virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,
    val virkningsTidspunktBegrunnelseKunINotat: String? = null,

    val avslag: AvslagType? = null,
    val aarsak: ForskuddBeregningKodeAarsakType? = null,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val virkningsDato: LocalDate? = null,
)
