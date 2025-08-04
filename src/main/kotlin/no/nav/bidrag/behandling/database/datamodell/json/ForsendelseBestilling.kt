package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.diverse.Språk
import no.nav.bidrag.domene.enums.rolle.Rolletype
import java.time.LocalDateTime

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class ForsendelseBestillingerConverter : JsonColumnConverter<ForsendelseBestillinger>(ForsendelseBestillinger::class)

fun ForsendelseBestillinger.finnForGjelderOgMottaker(
    gjelder: String?,
    mottaker: String?,
    rolletype: Rolletype?,
) = bestillinger.find { it.gjelder == gjelder && it.mottaker == mottaker && it.rolletype == rolletype }

data class ForsendelseBestillinger(
    val bestillinger: MutableSet<ForsendelseBestilling> = mutableSetOf(),
)

data class ForsendelseBestilling(
    var forsendelseId: Long? = null,
    var journalpostId: Long? = null,
    val rolletype: Rolletype?,
    val gjelder: String? = null,
    val mottaker: String? = null,
    val språkkode: Språk? = null,
    val dokumentmal: String? = null,
    val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    var forsendelseOpprettetTidspunkt: LocalDateTime? = null,
    var distribuertTidspunkt: LocalDateTime? = null,
    var feilBegrunnelse: String? = null,
    var antallForsøkOpprettEllerDistribuer: Int = 1,
)
