package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import no.nav.bidrag.behandling.database.datamodell.RolleType
import java.util.Date

@Schema(description = "Rolle beskrivelse som er brukte til å opprette nye roller")
data class CreateRolleDto(
    @Schema(required = true)
    val rolleType: RolleType,

    @Schema(type = "string", description = "F.eks fødselsnummer", required = true, nullable = false)
    @field:NotBlank
    val ident: String,

    @Schema(type = "string", format = "date", description = "F.eks fødselsdato")
    val fodtDato: Date?,

    @Schema(type = "string", format = "date", description = "Opprettet dato")
    val opprettetDato: Date?,
)
