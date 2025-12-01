package no.nav.bidrag.behandling.dto.v2.behandling

data class KanBehandlesINyLøsningResponse(
    val begrunnelser: List<String> = emptyList(),
)

data class KanBehandlesSjekkResponse(
    val begrunnelser: List<String> = emptyList(),
    val detaljer: List<KanIkkeBehandlesDetaljer> = emptyList(),
)

data class KanIkkeBehandlesDetaljer(
    val system: BehandlingSystem,
    val melding: String,
    val begrunnelse: KanIkkeBehandlesBegrunnelse,
)

enum class BehandlingSystem {
    BIDRAG,
    BISYS,
}

enum class KanIkkeBehandlesBegrunnelse {
    FORHOLDSMESSIG_FORDELING_OPPRETTET,
}
