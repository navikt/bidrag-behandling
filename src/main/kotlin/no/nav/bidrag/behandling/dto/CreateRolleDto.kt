package no.nav.bidrag.behandling.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.RolleType
import java.util.Date

data class CreateRolleDto(
    val rolleType: RolleType,

    @Schema(type = "string", description = "Fødselsdato")
    val ident: String?,
    val opprettetDato: Date,
)
