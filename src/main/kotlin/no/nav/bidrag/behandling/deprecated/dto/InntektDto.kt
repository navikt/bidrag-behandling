package no.nav.bidrag.behandling.deprecated.dto

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
    @Schema(required = true)
    val inntektPostListe: Set<InntektPost>,
)

fun Set<InntektDto>.toInntektDto(): Set<no.nav.bidrag.behandling.dto.inntekt.InntektDto> =
    this.map {
        no.nav.bidrag.behandling.dto.inntekt.InntektDto(
            id = it.id,
            taMed = it.taMed,
            inntektstype = Inntektsrapportering.valueOf(it.inntektType!!),
            beløp = it.belop,
            datoFom = it.datoFom!!,
            datoTom = it.datoTom,
            ident = it.ident,
            fraGrunnlag = it.fraGrunnlag,
            inntektsposter = it.inntektPostListe,
        )
    }.toSet()

fun Set<no.nav.bidrag.behandling.dto.inntekt.InntektDto>.toDepreactedInntektDto(): Set<InntektDto> =
    this.map {
        InntektDto(
            id = it.id,
            taMed = it.taMed,
            inntektType = it.inntektstype.name,
            belop = it.beløp,
            datoFom = it.datoFom!!,
            datoTom = it.datoTom,
            ident = it.ident,
            fraGrunnlag = it.fraGrunnlag,
            inntektPostListe = it.inntektsposter,
        )
    }.toSet()
