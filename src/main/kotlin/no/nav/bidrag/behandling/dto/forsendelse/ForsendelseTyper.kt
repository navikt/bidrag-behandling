package no.nav.bidrag.behandling.dto.forsendelse

import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType

data class DokumentDto (
    val tittel: String? = null,
    val dokumentmalId: String? = null,
    val bestillDokument: Boolean? = null,
    val språk: String? = null,
    val arkivsystem: String? = null
)
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
    val vedtakType: VedtakType? = null
){
    fun erBehandlingType(stonadType: StonadType?) = this.stonadType == stonadType
    fun erBehandlingType(engangsBelopType: EngangsbelopType?) = this.engangsBelopType == engangsBelopType
    fun erBehandlingType(behandlingType: String?) = this.behandlingType == behandlingType
}
data class MottakerDto (
    val ident: String? = null,
)