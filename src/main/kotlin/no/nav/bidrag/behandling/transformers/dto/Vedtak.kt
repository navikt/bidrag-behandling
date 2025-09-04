package no.nav.bidrag.behandling.transformers.dto

import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import java.time.LocalDateTime
import java.time.YearMonth

data class PåklagetVedtak(
    val vedtaksid: Int,
    val gjelderBarn: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val virkningstidspunkt: YearMonth?,
    val vedtakstype: Vedtakstype,
)

internal data class OrkestrertVedtak(
    val vedtak: VedtakDto,
    val erOrkestrertVedtak: Boolean,
    val referertVedtak: VedtakDto?,
) {
    val opprinneligVedtak get() = referertVedtak ?: vedtak
}
