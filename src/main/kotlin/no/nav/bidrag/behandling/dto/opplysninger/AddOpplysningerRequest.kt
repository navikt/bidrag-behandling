package no.nav.bidrag.behandling.dto.opplysninger

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import java.time.LocalDate
import javax.validation.constraints.NotBlank

data class AddOpplysningerRequest(
    val behandlingId: Long,
    val aktiv: Boolean,
    val opplysningerType: OpplysningerType,

    @Schema(type = "string", description = "data", required = true, nullable = false)
    @field:NotBlank
    val data: String,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val hentetDato: LocalDate,
)
