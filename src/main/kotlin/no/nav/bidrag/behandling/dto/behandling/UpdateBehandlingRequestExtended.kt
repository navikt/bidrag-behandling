package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.util.Date

data class UpdateBehandlingRequestExtended(

    @Enumerated(EnumType.STRING)
    val soknadType: SoknadType,

    @Enumerated(EnumType.STRING)
    val soknadFraType: SoknadFraType,

    @Schema(type = "string", format = "date", example = "2025.01.25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: Date,

    @Schema(type = "string", format = "date", example = "2025.01.25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottatDato: Date,
)
