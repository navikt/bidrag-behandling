package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Date

enum class CreateRolleRolleType {
    BIDRAGS_PLIKTIG,
    BIDRAGS_MOTTAKER,
    BARN,
    REELL_MOTTAKER,
    FEILREGISTRERT,
}

@Schema(description = "Rolle beskrivelse som er brukte til å opprette nye roller")
data class CreateRolleDto(
    @Schema(required = true, enumAsRef = true)
    val rolleType: CreateRolleRolleType,
    @Schema(
        type = "String",
        description = "F.eks fødselsnummer. Påkrevd for alle rolletyper utenom for barn som ikke inngår i beregning.",
        required = false,
        nullable = true,
    )
    val ident: String?,
    @Schema(
        type = "String",
        description = "Navn på rolleinnehaver hvis ident er ukjent. Gjelder kun barn som ikke inngår i beregning",
        required = false,
        nullable = true,
    )
    val navn: String?,
    @Schema(type = "String", format = "date", description = "F.eks fødselsdato")
    val fodtDato: Date?,
    @Schema(type = "String", format = "date", description = "Opprettet dato")
    val opprettetDato: Date?,
    val erSlettet: Boolean = false,
)
