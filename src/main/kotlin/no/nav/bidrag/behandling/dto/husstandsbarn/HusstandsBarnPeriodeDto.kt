package no.nav.bidrag.behandling.dto.husstandsbarn

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import java.time.LocalDate

data class HusstandsBarnPeriodeDto(
    val id: Long?,

    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,

    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,

    @Schema(required = true)
    val boStatus: BoStatusType,

    @Schema(required = true)
    val kilde: String,
)
