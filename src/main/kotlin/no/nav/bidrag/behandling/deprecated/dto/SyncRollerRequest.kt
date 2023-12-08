package no.nav.bidrag.behandling.deprecated.dto

import io.swagger.v3.oas.annotations.media.Schema

data class SyncRollerRequest(
    @Schema(required = true) val roller: List<CreateRolleDto>,
)
