package no.nav.bidrag.behandling.dto.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import java.math.BigDecimal
import java.time.LocalDate

data class InntektDto(
    val id: Long? = null,
    @Schema(required = true)
    val taMed: Boolean,
    @Schema(required = true)
    val inntektstype: Inntektsrapportering,
    @Schema(required = true)
    val beløp: BigDecimal,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @Schema(required = true)
    val ident: String,
    @Schema(required = true)
    val fraGrunnlag: Boolean,
    @Schema(required = true)
    val inntektsposter: Set<InntektPost>,
)
