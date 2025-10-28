package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema

data class OppdaterRollerRequest(
    @Schema(required = true) val roller: List<OpprettRolleDto>,
    val søknadsid: Long? = null,
    val saksnummer: String? = null,
)

data class OppdaterRollerResponse(
    @Schema(required = true) val status: OppdaterRollerStatus,
)

enum class OppdaterRollerStatus {
    BEHANDLING_SLETTET,
    ROLLER_OPPDATERT,
}
