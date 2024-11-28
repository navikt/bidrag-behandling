package no.nav.bidrag.behandling.dto.v2.gebyr

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto

data class OppdaterManueltGebyrDto(
    val rolleId: Long,
    val begrunnelse: String,
    val overstyrtGebyr: ManueltOverstyrGebyrDto?,
)

data class OppdaterGebyrResponsDto(
    val rolle: RolleDto,
    val overstyrtGebyr: ManueltOverstyrGebyrDto?,
)

data class ManueltOverstyrGebyrDto(
    val begrunnelse: String? = null,
    @Schema(description = "Skal bare settes hvis det er avslag")
    val ilagtGebyr: Boolean? = null,
)
