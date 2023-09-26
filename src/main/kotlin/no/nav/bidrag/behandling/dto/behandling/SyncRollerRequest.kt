package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema

data class SyncRollerRequest(
    @Schema(required = true) val roller: List<CreateRolleDto>,
)
