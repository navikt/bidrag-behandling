package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.inntekt.UtvidetBarnetrygdDto
import java.math.BigDecimal
import java.time.LocalDate

data class UtvidetbarnetrygdDto(
    val id: Long? = null,
    @Schema(required = true)
    val deltBoSted: Boolean,
    @Schema(required = true)
    val belop: BigDecimal,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
)

fun Set<UtvidetbarnetrygdDto>.toUtvidetBarnetrygdDto(): Set<UtvidetBarnetrygdDto> =
    this.map {
        UtvidetBarnetrygdDto(
            id = it.id,
            deltBosted = it.deltBoSted,
            beløp = it.belop,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
        )
    }.toSet()

fun Set<UtvidetBarnetrygdDto>.toUtvidetbarnetrygdDto(): Set<UtvidetbarnetrygdDto> =
    this.map {
        UtvidetbarnetrygdDto(
            id = it.id,
            deltBoSted = it.deltBosted,
            belop = it.beløp,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
        )
    }.toSet()
