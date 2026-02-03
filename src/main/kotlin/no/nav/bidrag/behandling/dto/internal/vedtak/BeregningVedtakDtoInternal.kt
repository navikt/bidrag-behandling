@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.internal.vedtak

import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto

data class BeregningVedtakResultat(
    val requests: List<Pair<Int, OpprettVedtakRequestDto>>,
    val vedtaksidHovedVedtak: Int,
)
