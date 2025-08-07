package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate
import java.time.LocalDateTime

@Converter(autoApply = true)
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
    val paragraf35c: List<OpprettParagraf35C> = emptyList(),
)

data class OpprettParagraf35C(
    val rolleid: Long,
    val vedtaksid: Int,
    val opprettParagraf35c: Boolean,
)
