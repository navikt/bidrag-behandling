package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema

data class OppdaterRollerRequest(
    @Schema(required = true) val roller: List<no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto>,
)
