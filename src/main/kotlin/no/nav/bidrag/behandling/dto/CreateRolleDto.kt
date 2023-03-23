package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.RolleType
import java.util.Date

data class CreateRolleDto(
    val rolleType: RolleType,
    val ident: String? = "UKJENT",
    val opprettetDato: Date,
)
