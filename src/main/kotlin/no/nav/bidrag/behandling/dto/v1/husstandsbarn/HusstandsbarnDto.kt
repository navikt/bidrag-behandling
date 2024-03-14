package no.nav.bidrag.behandling.dto.v1.husstandsbarn

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Kilde
import java.time.LocalDate

data class HusstandsbarnDto(
    val id: Long?,
    val kilde: Kilde? = null,
    @Schema(required = true)
    val medISak: Boolean,
    val perioder: Set<HusstandsbarnperiodeDto>,
    val ident: String? = null,
    val navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val fødselsdato: LocalDate,
)
