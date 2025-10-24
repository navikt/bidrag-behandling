package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import java.time.LocalDate
import java.time.YearMonth

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class ForholdsmessigFordelingConverter : JsonColumnConverter<ForholdsmessigFordeling>(ForholdsmessigFordeling::class)

data class ForholdsmessigFordeling(
    val behandlesAvBehandling: Long? = null,
    var erHovedbehandling: Boolean = false,
)

data class ForholdsmessigFordelingRolle(
    val tilhørerSak: String,
    val eierfogd: Enhetsnummer?,
    val mottattDato: LocalDate? = null,
    var søknadFomDato: LocalDate? = null,
    val søktAvType: SøktAvType? = null,
    val delAvOpprinneligBehandling: Boolean,
    var erRevurdering: Boolean,
    val harLøpendeBidrag: Boolean = true,
    val løperBidragFra: YearMonth? = null,
    val behandlingsid: Long? = null,
    var søknadsid: Long? = null,
    var søknadsidUtenInnkreving: Long? = null,
    var bidragsmottaker: String? = null,
)
