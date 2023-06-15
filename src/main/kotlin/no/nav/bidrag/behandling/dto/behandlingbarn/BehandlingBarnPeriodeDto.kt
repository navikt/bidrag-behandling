package no.nav.bidrag.behandling.dto.behandlingbarn

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import java.time.LocalDate

data class BehandlingBarnPeriodeDto(
    val id: Long?,

    @Schema(type = "string", format = "date", example = "2025.01.25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val fraDato: LocalDate,

    @Schema(type = "string", format = "date", example = "2025.01.25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val tilDato: LocalDate,

    val boStatus: BoStatusType,
    val kilde: String,
)
