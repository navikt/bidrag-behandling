package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import java.time.LocalDate

data class HusstandsBarnPeriodeDto(
    val id: Long?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @Schema(required = true)
    val bostatus: Bostatuskode?,
    @Schema(required = true)
    val kilde: Kilde,
)

fun Set<HusstandsBarnPeriodeDto>.toDomain(husstandsBarn: Husstandsbarn) =
    this.map {
        Husstandsbarnperiode(
            husstandsBarn,
            it.datoFom,
            it.datoTom,
            it.bostatus!!,
            it.kilde,
        )
    }.toSet()
