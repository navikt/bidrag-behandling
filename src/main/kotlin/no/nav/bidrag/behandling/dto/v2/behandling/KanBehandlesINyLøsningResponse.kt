package no.nav.bidrag.behandling.dto.v2.behandling

data class KanBehandlesINyLøsningResponse(
    val begrunnelser: List<String> = emptyList(),
    val detaljer: List<KanIkkeBehandlesDetaljer> = emptyList(),
)

data class KanIkkeBehandlesDetaljer(
    val melding: String,
    val begrunnelse: KanIkkkeBehandlesBegrunnelse,
)

enum class KanIkkkeBehandlesBegrunnelse {
    FORHOLDSMESSIG_FORDELING_STARTET,
}
