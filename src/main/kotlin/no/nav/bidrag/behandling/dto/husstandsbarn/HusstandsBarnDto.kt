package no.nav.bidrag.behandling.dto.husstandsbarn

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class HusstandsBarnDto(
    val id: Long?,
    @Schema(required = true)
    val medISaken: Boolean,
    @Schema(required = true)
    val perioder: Set<HusstandsBarnPeriodeDto>,
    val ident: String? = null,
    val navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val foedselsDato: LocalDate? = null,
)
