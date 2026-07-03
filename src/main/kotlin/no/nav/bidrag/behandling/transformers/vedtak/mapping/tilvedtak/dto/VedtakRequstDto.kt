package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.dto

import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto

data class VedtakRequstDto(
    val requests: List<OpprettVedtakRequestDto>,
    val erForholdsmessigFordelingHvorBPHarFullEvneIAllePerioder: Boolean,
)
