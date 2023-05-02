package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import java.util.Date

data class SivilstandDto(
    val id: Long? = null,
    val gyldigFraOgMed: Date,
    val bekreftelsesdato: Date,
    val sivilstandType: SivilstandType,
)
