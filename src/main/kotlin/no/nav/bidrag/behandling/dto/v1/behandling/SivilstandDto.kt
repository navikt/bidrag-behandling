package no.nav.bidrag.behandling.dto.v1.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.boforhold.dto.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import java.time.LocalDate

data class SivilstandDto(
    val id: Long? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    val sivilstand: Sivilstandskode,
    val kilde: Kilde,
)
