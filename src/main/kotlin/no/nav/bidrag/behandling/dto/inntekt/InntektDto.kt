package no.nav.bidrag.behandling.dto.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

data class InntektDto(
    val id: Long? = null,

    @Schema(required = true)
    val taMed: Boolean,

    val inntektType: String?,

    @Schema(required = true)
    val belop: BigDecimal,

    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,

    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,

    @Schema(required = true)
    val ident: String,

    @Schema(required = true)
    val fraGrunnlag: Boolean,
)
