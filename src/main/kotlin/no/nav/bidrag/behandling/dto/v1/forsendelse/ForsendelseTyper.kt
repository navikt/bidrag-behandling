package no.nav.bidrag.behandling.dto.v1.forsendelse

import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype

data class BehandlingInfoDto(
    val vedtakId: Long? = null,
    val behandlingId: Long? = null,
    val soknadId: Long?,
    val erFattetBeregnet: Boolean? = null,
    val erVedtakIkkeTilbakekreving: Boolean = false,
    val stonadType: Stønadstype? = null,
    val engangsBelopType: Engangsbeløptype? = null,
    val behandlingType: String? = null,
    val soknadType: String? = null,
    val soknadFra: SøktAvType? = null,
    val vedtakType: Vedtakstype? = null,
    val barnIBehandling: List<String> = emptyList(),
) {
    fun erBehandlingType(stonadType: Stønadstype?) = this.stonadType == stonadType

    fun erBehandlingType(engangsBelopType: Engangsbeløptype?) = this.engangsBelopType == engangsBelopType

    fun erGebyr() = erBehandlingType(Engangsbeløptype.GEBYR_SKYLDNER) || erBehandlingType(Engangsbeløptype.GEBYR_MOTTAKER)

    fun erBehandlingType(behandlingType: String?) = this.behandlingType == behandlingType

    fun erVedtakFattet() = erFattetBeregnet != null || vedtakId != null
}

data class MottakerDto(
    val ident: String? = null,
)
