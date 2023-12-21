package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
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
    @JsonAlias("foedselsdato")
    val fødselsdato: LocalDate,
)

fun Set<HusstandsbarnDto>.toHustandsbarndDto(): Set<no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto> =
    this.map { it.toHusstandsbarnDto() }.toSet()

fun HusstandsbarnDto.toHusstandsbarnDto(): no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto =
    no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto(
        id = this.id,
        medISak = this.medISak,
        perioder = this.perioder.toHusstandsbarnperiodeDto(),
        ident = this.ident,
        navn = this.navn,
        fødselsdato = this.fødselsdato,
    )
