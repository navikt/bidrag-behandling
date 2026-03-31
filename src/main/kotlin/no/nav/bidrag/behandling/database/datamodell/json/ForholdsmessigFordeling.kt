package no.nav.bidrag.behandling.database.datamodell.json

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
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
    // Har bidrag som løper i løpet av beregningsperioden. Det vil si at løperBidragFra < eldste søkt fra dato
    val harLøpendeBidrag: Boolean = true,
    val løperBidragFra: YearMonth? = null,
    val løperBidragTil: YearMonth? = null,
    var behandlingsid: Long? = null,
    var bidragsmottaker: String?,
    var søknader: MutableSet<ForholdsmessigFordelingSøknadBarn> = mutableSetOf(),
) {
    fun løperBidragEtterDato(dato: YearMonth): Boolean = løperBidragTil?.isAfter(dato) ?: true

    @get:JsonIgnore
    val søknaderUnderBehandling get() = søknader.filter { it.status == Behandlingstatus.UNDER_BEHANDLING || it.status == null }

    @get:JsonIgnore
    val eldsteSøknad get() = søknaderUnderBehandling.filter { it.søknadFomDato != null }.minByOrNull { it.søknadFomDato!! }

    @get:JsonIgnore
    val sisteOpprettetSøknad get() = søknader.filter { it.søknadFomDato != null }.maxByOrNull { it.søknadsid!! }

    @get:JsonIgnore
    val søknadsid get() = eldsteSøknad?.søknadsid
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ForholdsmessigFordelingSøknadBarn(
    var mottattDato: LocalDate,
    var søknadFomDato: LocalDate? = null,
    var søktAvType: SøktAvType,
    var søknadsid: Long? = null,
    // Flagg om det er overført fra påklaget vedtak. Fjerner søknadene etter opprettelse
    var erFraPåklagetVedtak: Boolean = false,
    var behandlingstype: Behandlingstype?,
    var behandlingstema: Behandlingstema?,
    val omgjørSøknadsid: Long? = null,
    val omgjørVedtaksid: Int? = null,
    var innkreving: Boolean = true,
    val enhet: String = "9999",
    // TODO: Er dette nødvendig? Kan BM/Barn være i flere saker?
    val saksnummer: String? = null,
    var status: Behandlingstatus? = null,
)
