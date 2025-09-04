package no.nav.bidrag.behandling.database.datamodell.json

import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate
import java.time.LocalDateTime

@Converter(autoApply = true)
class KlageDetaljerConverter : JsonColumnConverter<Omgjøringsdetaljer>(Omgjøringsdetaljer::class)

data class Omgjøringsdetaljer(
    val klageMottattdato: LocalDate? = null,
    val soknadRefId: Long? = null,
    @JsonAlias("refVedtaksid", "opprinneligVedtaksid")
    var omgjørVedtakId: Int? = null,
    @JsonAlias("påklagetVedtak", "omgjørVedtak")
    val opprinneligVedtakId: Int? = null,
    val omgjortVedtakVedtakstidspunkt: LocalDateTime? = null,
    val opprinneligVirkningstidspunkt: LocalDate? = null,
    val opprinneligVedtakstidspunkt: MutableSet<LocalDateTime> = mutableSetOf(),
    var opprinneligVedtakstype: Vedtakstype? = null,
    var innkrevingstype: Innkrevingstype? = null,
    val fattetDelvedtak: List<FattetDelvedtak> = emptyList(),
    val paragraf35c: List<OpprettParagraf35C> = emptyList(),
) {
    val minsteVedtakstidspunkt get() = opprinneligVedtakstidspunkt.minOrNull()
}

data class OpprettParagraf35C(
    val rolleid: Long,
    val vedtaksid: Int,
    val opprettParagraf35c: Boolean,
)
