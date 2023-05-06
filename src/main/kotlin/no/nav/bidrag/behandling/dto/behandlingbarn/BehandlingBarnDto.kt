package no.nav.bidrag.behandling.dto.behandlingbarn

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class BehandlingBarnDto(
    val id: Long?,
    val medISaken: Boolean,
    val perioder: Set<BehandlingBarnPeriodeDto>,
    val ident: String? = null,
    val navn: String? = null,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val foedselsDato: LocalDate? = null,
)
