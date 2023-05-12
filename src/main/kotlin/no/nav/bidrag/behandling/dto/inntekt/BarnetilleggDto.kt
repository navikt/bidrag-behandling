package no.nav.bidrag.behandling.dto.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

data class BarnetilleggDto(
    val id: Long? = null,

    val ident: String,

    val barnetillegg: BigDecimal,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val datoFom: LocalDate,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val datoTom: LocalDate,
)
