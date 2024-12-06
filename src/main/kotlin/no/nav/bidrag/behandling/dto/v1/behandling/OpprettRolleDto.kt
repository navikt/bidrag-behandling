package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Rolle beskrivelse som er brukte til å opprette nye roller")
data class OpprettRolleDto(
    @Schema(required = true, enumAsRef = true)
    val rolletype: Rolletype,
    @Schema(
        type = "String",
        description = "F.eks fødselsnummer. Påkrevd for alle rolletyper utenom for barn som ikke inngår i beregning.",
        required = false,
        nullable = true,
    )
    val ident: Personident?,
    @Schema(
        type = "String",
        description = "Navn på rolleinnehaver hvis ident er ukjent. Gjelder kun barn som ikke inngår i beregning",
        required = false,
        nullable = true,
    )
    val navn: String? = null,
    @Schema(type = "String", format = "date", description = "F.eks fødselsdato")
    val fødselsdato: LocalDate?,
    val innbetaltBeløp: BigDecimal? = null,
    val erSlettet: Boolean = false,
    val erUkjent: Boolean = false,
    val harGebyrsøknad: Boolean = false,
)
