package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import java.time.LocalDate

data class AddOpplysningerRequest(
    @Schema(required = true, nullable = false)
    val behandlingId: Long,
    @Schema(required = true, nullable = false)
    val aktiv: Boolean,
    @Schema(required = true, nullable = false)
    @JsonAlias("opplysningerType")
    val grunnlagstype: Grunnlagsdatatype,
    @Schema(type = "string", description = "data", required = true, nullable = false)
    @field:NotBlank
    val data: String,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val hentetDato: LocalDate,
)
