package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.dto.behandlingbarn.BehandlingBarnDto
import java.time.LocalDate

data class UpdateBehandlingRequest(
    val behandlingBarn: Set<BehandlingBarnDto>? = emptySet(),
    val virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,
    val virkningsTidspunktBegrunnelseKunINotat: String? = null,
    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
    val inntektBegrunnelseMedIVedtakNotat: String? = null,
    val inntektBegrunnelseKunINotat: String? = null,

    val avslag: AvslagType? = null,
    val aarsak: ForskuddBeregningKodeAarsakType? = null,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val virkningsDato: LocalDate? = null,
)
