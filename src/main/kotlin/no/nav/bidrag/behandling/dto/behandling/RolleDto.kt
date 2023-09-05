package no.nav.bidrag.behandling.dto.behandling

import no.nav.bidrag.domain.enums.Rolletype
import java.util.Date

data class RolleDto(
    val id: Long,
    val rolleType: Rolletype,
    val ident: String,
    val fodtDato: Date?,
    val opprettetDato: Date?,
)
