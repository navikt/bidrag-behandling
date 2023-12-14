package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.rolle.Rolletype
import java.time.LocalDate

@Schema(description = "Rolle beskrivelse som er brukte til å opprette nye roller")
data class CreateRolleDto(
    @Schema(required = true, enumAsRef = true)
    val rolletype: Rolletype,
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
    val fødselsdato: LocalDate?,
    @Schema(type = "String", format = "date", description = "Opprettetdato")
    val opprettetdato: LocalDate?,
    val erSlettet: Boolean = false,
)
