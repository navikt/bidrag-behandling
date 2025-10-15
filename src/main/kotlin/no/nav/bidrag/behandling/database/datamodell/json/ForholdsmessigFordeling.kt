package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import java.time.LocalDate

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class ForholdsmessigFordelingConverter : JsonColumnConverter<ForholdsmessigFordeling>(ForholdsmessigFordeling::class)

data class ForholdsmessigFordeling(
    val behandlesAvBehandling: Long? = null,
    val erHovedbehandling: Boolean = false,
)

data class ForholdsmessigFordelingRolle(
    val tilhørerSak: String,
    val behandlerEnhet: Enhetsnummer?,
    val mottattDato: LocalDate? = null,
    val søknadFomDato: LocalDate? = null,
    val søktAvType: SøktAvType? = null,
    val delAvOpprinneligBehandling: Boolean,
    val behandlingsid: Long? = null,
    val søknadsid: Long? = null,
)
