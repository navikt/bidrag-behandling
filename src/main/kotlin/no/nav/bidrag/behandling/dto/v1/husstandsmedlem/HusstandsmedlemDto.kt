package no.nav.bidrag.behandling.dto.v1.husstandsmedlem

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.diverse.Kilde
import java.time.LocalDate

data class HusstandsmedlemDto(
    val id: Long?,
    val kilde: Kilde? = null,
    @Schema(required = true)
    val medISak: Boolean,
    val perioder: Set<BostatusperiodeDto>,
    val ident: String? = null,
    val navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val fødselsdato: LocalDate,
)
