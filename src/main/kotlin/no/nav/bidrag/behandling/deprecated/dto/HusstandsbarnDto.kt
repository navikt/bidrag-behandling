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
    val navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val foedselsdato: LocalDate? = null,
)

fun Set<HusstandsbarnDto>.toDomain(behandling: Behandling) =
    this.map {
        val barn =
            Husstandsbarn(
                behandling,
                it.medISak,
                it.id,
                it.ident,
                it.navn,
                it.foedselsdato!!,
            )
        barn.perioder = it.perioder.toDomain(barn).toMutableSet()
        barn
    }.toMutableSet()
