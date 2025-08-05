package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate
import java.time.LocalDateTime

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class KlageDetaljerConverter : JsonColumnConverter<Klagedetaljer>(Klagedetaljer::class)

data class Klagedetaljer(
    val klageMottattdato: LocalDate? = null,
    val soknadRefId: Long? = null,
    var refVedtaksid: Int? = null,
    val påklagetVedtak: Int? = null,
    val opprinneligVirkningstidspunkt: LocalDate? = null,
    val opprinneligVedtakstidspunkt: MutableSet<LocalDateTime> = mutableSetOf(),
    var opprinneligVedtakstype: Vedtakstype? = null,
    val fattetDelvedtak: List<FattetDelvedtak> = emptyList(),
)
