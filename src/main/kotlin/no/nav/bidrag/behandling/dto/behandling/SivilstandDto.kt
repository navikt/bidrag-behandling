package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import java.time.LocalDate

data class SivilstandDto(
    val id: Long? = null,

    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val gyldigFraOgMed: LocalDate?,

    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,

    val sivilstandType: SivilstandType,
)
