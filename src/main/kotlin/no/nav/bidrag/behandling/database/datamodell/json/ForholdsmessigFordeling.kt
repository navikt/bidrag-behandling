package no.nav.bidrag.behandling.database.datamodell.json

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
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

data class ForholdsmessigFordelingRolle(
    var tilhørerSak: String,
    @JsonAlias("eierfogd")
    val behandlerenhet: String?,
    var delAvOpprinneligBehandling: Boolean,
    var erRevurdering: Boolean,
    val harLøpendeBidrag: Boolean = true,
    val løperBidragFra: YearMonth? = null,
    var behandlingsid: Long? = null,
    var søknadsidUtenInnkreving: Long? = null,
    var bidragsmottaker: String?,
    var søknader: MutableSet<ForholdsmessigFordelingSøknadBarn> = mutableSetOf(),
) {
    @get:JsonIgnore
    val søknaderUnderBehandling get() = søknader.filter { it.status == Behandlingstatus.UNDER_BEHANDLING }

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
    val enhet: String = "9999",
    var status: Behandlingstatus = Behandlingstatus.UNDER_BEHANDLING,
)
