package no.nav.bidrag.behandling.deprecated.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.domene.enums.rolle.Rolletype
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

fun Set<CreateRolleDto>.toCreateRolleDto(): Set<no.nav.bidrag.behandling.dto.behandling.CreateRolleDto> =
    this.map {
        no.nav.bidrag.behandling.dto.behandling.CreateRolleDto(
            rolletype = it.rolleType.toRolletype(),
            fødselsdato = it.fodtDato?.toLocalDate()!!,
            opprettetdato = it.opprettetDato?.toLocalDate(),
            ident = it.ident,
            navn = it.navn,
        )
    }.toSet()

fun CreateRolleRolleType.toRolletype(): Rolletype {
    return when (this) {
        CreateRolleRolleType.BIDRAGS_PLIKTIG -> Rolletype.BIDRAGSPLIKTIG
        CreateRolleRolleType.BIDRAGS_MOTTAKER -> Rolletype.BIDRAGSMOTTAKER
        CreateRolleRolleType.BARN -> Rolletype.BARN
        CreateRolleRolleType.REELL_MOTTAKER -> Rolletype.REELMOTTAKER
        CreateRolleRolleType.FEILREGISTRERT -> Rolletype.FEILREGISTRERT
    }
}

fun CreateRolleDto.toRolle(behandling: Behandling): Rolle =
    Rolle(
        behandling,
        rolletype = this.rolleType.toRolletype(),
        this.ident,
        this.fodtDato?.toLocalDate()!!,
        this.opprettetDato?.toLocalDate(),
        navn = this.navn,
    )
