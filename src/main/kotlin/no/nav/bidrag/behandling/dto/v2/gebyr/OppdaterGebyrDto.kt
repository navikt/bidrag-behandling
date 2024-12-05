package no.nav.bidrag.behandling.dto.v2.gebyr

import io.swagger.v3.oas.annotations.media.Schema

data class OppdaterGebyrDto(
    val rolleId: Long,
    @Schema(description = "Om gebyr skal overstyres. Settes til motsatte verdi av beregnet verdi")
    val overstyrGebyr: Boolean = false,
    val begrunnelse: String? = null,
)

data class ManueltOverstyrGebyrDto(
    val begrunnelse: String? = null,
    @Schema(description = "Skal bare settes hvis det er avslag")
    val ilagtGebyr: Boolean? = null,
)
