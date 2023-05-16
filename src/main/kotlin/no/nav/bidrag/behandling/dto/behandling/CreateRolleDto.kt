package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.RolleType
import java.util.Date
import javax.validation.constraints.NotBlank

@Schema(description = "Rolle beskrivelse som er brukte til å opprette nye roller")
data class CreateRolleDto(
    @Schema(required = true)
    val rolleType: RolleType,

    @Schema(type = "string", description = "Fødselsdato", required = true, nullable = false)
    @field:NotBlank
    val ident: String,

    val opprettetDato: Date?,
)
