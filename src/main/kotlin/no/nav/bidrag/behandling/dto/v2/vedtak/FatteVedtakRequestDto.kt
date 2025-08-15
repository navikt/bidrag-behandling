package no.nav.bidrag.behandling.dto.v2.vedtak

data class FatteVedtakRequestDto(
    val innkrevingUtsattAntallDager: Long? = null,
    val enhet: String? = null,
)

data class OppdaterParagraf35cDetaljerDto(
    val ident: String,
    val vedtaksid: Int,
    val opprettP35c: Boolean = false,
)
