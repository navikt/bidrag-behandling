package no.nav.bidrag.behandling.dto

import java.util.Date

data class BehandlingBarnDto(
    val id: Long?,
    val medISaken: Boolean,
    val perioder: Set<BehandlingBarnPeriodeDto>,
    val ident: String? = null,
    val navn: String? = null,
    val foedselsDato: Date? = null,
)
