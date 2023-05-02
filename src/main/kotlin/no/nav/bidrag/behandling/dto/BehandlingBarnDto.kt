package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import java.util.Date

data class BehandlingBarnDto(
    val id: Long?,
    val medISaken: Boolean,

    val fraDato: Date,

    val tilDato: Date,

    val boStatus: BoStatusType,

    val kilde: String,

    val ident: String? = null,
    val navn: String? = null,
    val foedselsDato: Date? = null,
)
