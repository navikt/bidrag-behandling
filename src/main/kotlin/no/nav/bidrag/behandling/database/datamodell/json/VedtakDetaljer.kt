package no.nav.bidrag.behandling.database.datamodell.json

import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.persistence.Converter
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Converter(autoApply = true)
class VedtakDetaljerConverter : JsonColumnConverter<VedtakDetaljer>(VedtakDetaljer::class)

data class VedtakDetaljer(
    val vedtaksid: Int? = null,
    val vedtakFattetAvEnhet: String? = null,
    val vedtakstidspunkt: LocalDateTime? = null,
    val vedtakFattetAv: String? = null,
    val unikreferanse: String? = null,
    @JsonAlias("fattetDelvedtak")
    val fattetVedtak: Set<FattetVedtak> = emptySet(),
)

data class FattetVedtak(
    val fattetTidspunkt: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/Oslo")),
    val vedtaksid: Int,
    val vedtakstype: Vedtakstype? = null,
    val referanse: String,
)
