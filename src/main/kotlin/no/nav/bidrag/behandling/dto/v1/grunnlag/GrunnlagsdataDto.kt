package no.nav.bidrag.behandling.dto.v1.grunnlag

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import java.time.LocalDateTime

data class GrunnlagsdataDto(
    val id: Long,
    val behandlingsid: Long,
    val grunnlagsdatatype: Grunnlagsdatatype,
    val data: String,
    @Schema(type = "string", format = "timestamp", example = "01.12.2025 12:00:00.000")
    val innhentet: LocalDateTime,
)
