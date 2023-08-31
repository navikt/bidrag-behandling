package no.nav.bidrag.behandling.dto.behandling

import no.nav.bidrag.behandling.database.datamodell.RolleType
import java.util.Date

data class RolleDto(
    val id: Long,
    val rolleType: RolleType, // TODO Erstatt med enum no.nav.bidrag.domain.enums.Rolletype istedenfor
    val ident: String,
    val fodtDato: Date?,
    val opprettetDato: Date?,
)
