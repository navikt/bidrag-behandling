package no.nav.bidrag.behandling.dto.v1.husstandsbarn

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.boforhold.dto.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import java.time.LocalDate

data class HusstandsbarnperiodeDto(
    val id: Long?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @Schema(required = true)
    val bostatus: Bostatuskode,
    @Schema(required = true)
    val kilde: Kilde,
)
