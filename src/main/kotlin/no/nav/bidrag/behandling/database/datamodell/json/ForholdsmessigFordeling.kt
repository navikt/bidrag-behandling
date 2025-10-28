package no.nav.bidrag.behandling.database.datamodell.json

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
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
    val delAvOpprinneligBehandling: Boolean,
    var erRevurdering: Boolean,
    val harLøpendeBidrag: Boolean = true,
    val løperBidragFra: YearMonth? = null,
    val behandlingsid: Long? = null,
    var søknadsidUtenInnkreving: Long? = null,
    var bidragsmottaker: String?,
    var søknader: MutableSet<ForholdsmessigFordelingSøknadBarn> = mutableSetOf(),
) {
    @get:JsonIgnore
    val eldsteSøknad get() = søknader.filter { it.søknadFomDato != null }.minBy { it.søknadFomDato!! }

    @get:JsonIgnore
    val søknadsid get() = eldsteSøknad.søknadsid
}

data class ForholdsmessigFordelingSøknadBarn(
    val mottattDato: LocalDate,
    var søknadFomDato: LocalDate? = null,
    val søktAvType: SøktAvType,
    var søknadsid: Long? = null,
    val behandlingstype: Behandlingstype?,
    val behandlingstema: Behandlingstema?,
    val omgjørSøknadsid: Long? = null,
    val omgjørVedtaksid: Int? = null,
    val innkreving: Boolean = true,
)
