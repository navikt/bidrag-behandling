package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.behandling.transformers.bestemTypeBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident

data class KanBehandlesINyLøsningRequest(
    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, min = 7, message = "Saksnummer skal ha sju tegn")
    val saksnummer: String,
    @field:Size(min = 2, message = "Sak må ha minst to roller involvert")
    val roller: List<SjekkRolleDto>,
    @Schema(required = true)
    var stønadstype: Stønadstype? = null,
    @Schema(required = true)
    var engangsbeløpstype: Engangsbeløptype? = null,
)

@Schema(description = "Rolle beskrivelse som er brukte til å opprette nye roller")
data class SjekkRolleDto(
    @Schema(required = true, enumAsRef = true)
    val rolletype: Rolletype,
    @Schema(
        type = "String",
        description = "F.eks fødselsnummer. Påkrevd for alle rolletyper utenom for barn som ikke inngår i beregning.",
        required = false,
        nullable = true,
    )
    val ident: Personident?,
)

fun KanBehandlesINyLøsningRequest.tilType() = bestemTypeBehandling(stønadstype, engangsbeløpstype)
