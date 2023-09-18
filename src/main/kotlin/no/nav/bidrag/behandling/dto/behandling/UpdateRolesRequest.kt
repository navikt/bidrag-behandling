package no.nav.bidrag.behandling.dto.behandling

data class UpdateRolesRequest(
    val behandlingId: Long,
    val ident: String,
    val status: String,
)