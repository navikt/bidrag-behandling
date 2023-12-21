package no.nav.bidrag.behandling.dto.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

// TODO: Ikke sett gjelderBarn lik ident etter depractated apier er fjernet. Dette gjøres for bakoverkompatibilitet
data class BarnetilleggDto(
    val id: Long? = null,
    @Schema(
        required = true,
        description = "Bidragsmottaker eller bidragspliktig som mottar barnetillegget",
    )
    val ident: String,
    @Schema(required = true, description = "Hvilken barn barnetillegget mottas for")
    val gjelderBarn: String = ident,
    @Schema(required = true)
    val barnetillegg: BigDecimal,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
)
