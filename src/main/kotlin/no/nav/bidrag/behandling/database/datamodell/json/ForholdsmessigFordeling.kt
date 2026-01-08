package no.nav.bidrag.behandling.database.datamodell.json

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate
import java.time.YearMonth

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class ForholdsmessigFordelingConverter : JsonColumnConverter<ForholdsmessigFordeling>(ForholdsmessigFordeling::class)

data class ForholdsmessigFordeling(
    val behandlesAvBehandling: Long? = null,
    var erHovedbehandling: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ForholdsmessigFordelingRolle(
    var tilhørerSak: String,
    @JsonAlias("eierfogd")
    val behandlerenhet: String?,
    var delAvOpprinneligBehandling: Boolean,
    var erRevurdering: Boolean,
    val harLøpendeBidrag: Boolean = true,
    val løperBidragFra: YearMonth? = null,
    val løperBidragTil: YearMonth? = null,
    var behandlingsid: Long? = null,
    var bidragsmottaker: String?,
    var søknader: MutableSet<ForholdsmessigFordelingSøknadBarn> = mutableSetOf(),
) {
    @get:JsonIgnore
    val søknaderUnderBehandling get() = søknader.filter { it.status == Behandlingstatus.UNDER_BEHANDLING }

    @get:JsonIgnore
    val eldsteSøknad get() = søknaderUnderBehandling.filter { it.søknadFomDato != null }.minBy { it.søknadFomDato!! }

    @get:JsonIgnore
    val søknadsid get() = eldsteSøknad.søknadsid
}

@JsonIgnoreProperties(ignoreUnknown = true)
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
    val enhet: String = "9999",
    // TODO: Er dette nødvendig? Kan BM/Barn være i flere saker?
    val saksnummer: String? = null,
    var status: Behandlingstatus = Behandlingstatus.UNDER_BEHANDLING,
)
