package no.nav.bidrag.behandling.dto.v2.vedtak

import no.nav.bidrag.transport.behandling.felles.grunnlag.FatteVedtakRevurderingsbarn

data class FatteVedtakRequestDto(
    val fatteVedtakRevurderingsbarn: FatteVedtakRevurderingsbarn? = null,
    val skalIndeksreguleres: Map<String, Boolean>? = null,
    val innkrevingUtsattAntallDager: Long? = null,
    val enhet: String? = null,
)

data class OppdaterParagraf35cDetaljerDto(
    val ident: String,
    val vedtaksid: Int,
    val opprettP35c: Boolean = false,
)
