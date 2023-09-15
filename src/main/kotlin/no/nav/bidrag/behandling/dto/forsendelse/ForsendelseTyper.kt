package no.nav.bidrag.behandling.dto.forsendelse

import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType

data class BehandlingInfoDto(
    val vedtakId: Long? = null,
    val behandlingId: Long? = null,
    val soknadId: Long,
    val erFattetBeregnet: Boolean? = null,
    val erVedtakIkkeTilbakekreving: Boolean = false,
    val stonadType: StonadType? = null,
    val engangsBelopType: EngangsbelopType? = null,
    val behandlingType: String? = null,
    val soknadType: String? = null,
    val soknadFra: SoknadFraType? = null,
    val vedtakType: VedtakType? = null,
    val barnIBehandling: List<String> = emptyList(),
) {
    fun erBehandlingType(stonadType: StonadType?) = this.stonadType == stonadType
    fun erBehandlingType(engangsBelopType: EngangsbelopType?) =
        this.engangsBelopType == engangsBelopType

    fun erGebyr() =
        erBehandlingType(EngangsbelopType.GEBYR_SKYLDNER) || erBehandlingType(EngangsbelopType.GEBYR_MOTTAKER)

    fun erBehandlingType(behandlingType: String?) = this.behandlingType == behandlingType
    fun erVedtakFattet() = erFattetBeregnet != null || vedtakId != null
}

data class MottakerDto(
    val ident: String? = null,
)
