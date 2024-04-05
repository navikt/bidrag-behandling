package no.nav.bidrag.behandling.dto.v1.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Deprecated("Bruk InntektDto")
data class UtvidetBarnetrygdDto(
    val id: Long? = null,
    @Schema(required = true)
    val deltBosted: Boolean,
    @Schema(required = true)
    val beløp: BigDecimal,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
)
