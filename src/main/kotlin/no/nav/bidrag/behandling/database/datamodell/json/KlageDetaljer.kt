package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate
import java.time.LocalDateTime

@Converter(autoApply = true) // Set to true if you want it to apply to all KlageDetaljer fields automatically
class KlageDetaljerConverter : JsonColumnConverter<KlageDetaljer>(KlageDetaljer::class)

data class KlageDetaljer(
    val klageMottattdato: LocalDate? = null,
    val soknadRefId: Long? = null,
    val refVedtaksid: Int? = null,
    val påklagetVedtak: Int? = null,
    val opprinneligVirkningstidspunkt: LocalDate? = null,
    val opprinneligVedtakstidspunkt: MutableSet<LocalDateTime> = mutableSetOf(),
    var opprinneligVedtakstype: Vedtakstype? = null,
    val fattetDelvedtak: List<FattetDelvedtak> = emptyList(),
)

data class FattetDelvedtak(
    val beregnetFraDato: LocalDate,
    val vedtaksid: Int,
)
