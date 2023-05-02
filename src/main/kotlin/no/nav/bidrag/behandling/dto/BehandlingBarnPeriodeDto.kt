package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import java.util.Date

data class BehandlingBarnPeriodeDto(
    val id: Long?,
    val fraDato: Date,
    val tilDato: Date,
    val boStatus: BoStatusType,
    val kilde: String,
)
