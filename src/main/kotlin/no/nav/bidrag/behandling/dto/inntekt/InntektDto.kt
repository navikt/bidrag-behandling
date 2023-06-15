package no.nav.bidrag.behandling.dto.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

data class InntektDto(
    val id: Long? = null,
    val taMed: Boolean,
    val beskrivelse: String,
    val belop: BigDecimal,

    @Schema(type = "string", format = "date", example = "2025.01.25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate,

    @Schema(type = "string", format = "date", example = "2025.01.25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate,

    val ident: String,
    val fraGrunnlag: Boolean,
)
