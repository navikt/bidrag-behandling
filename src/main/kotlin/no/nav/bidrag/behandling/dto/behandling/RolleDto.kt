package no.nav.bidrag.behandling.dto.behandling

import no.nav.bidrag.behandling.database.datamodell.RolleType
import java.util.Date

data class RolleDto(
    val id: Long,
    val rolleType: RolleType,
    val ident: String,
    val opprettetDato: Date?,
)
