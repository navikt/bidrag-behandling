package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.transformers.toDomain
import java.time.LocalDate

data class HusstandsbarnDto(
    val id: Long?,
    @Schema(required = true)
    val medISak: Boolean,
    @Schema(required = true)
    val perioder: Set<HusstandsBarnPeriodeDto>,
    val ident: String? = null,
    var navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val foedselsdato: LocalDate,
    val fødselsdato: LocalDate = foedselsdato,
)

fun Set<HusstandsbarnDto>.toHustandsbarndDto() :  Set<no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto> =

    this.map {
        no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto(
            id = it.id,
            medISak = it.medISak,
            perioder = it.perioder.toHusstandsbarnperiodeDto(),
            ident = it.ident,
            navn = it.navn,
            fødselsdato = it.fødselsdato
        )
    }.toSet()

