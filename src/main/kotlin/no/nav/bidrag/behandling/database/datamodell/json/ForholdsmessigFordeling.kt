package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.organisasjon.Enhetsnummer

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class ForholdsmessigFordelingConverter : JsonColumnConverter<ForholdsmessigFordeling>(ForholdsmessigFordeling::class)

data class ForholdsmessigFordeling(
    val behandlesAvBehandling: Long? = null,
    val erHovedbehandling: Boolean = false,
)

data class ForholdsmessigFordelingRolle(
    val tilhørerSak: String,
    val sakBehandlerEnhet: Enhetsnummer?,
    val delAvOpprinneligBehandling: Boolean,
    val overførtFraBehandling: Long? = null,
    val overførtFraSøknad: Long? = null,
)
