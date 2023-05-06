package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.util.Date
import javax.persistence.EnumType
import javax.persistence.Enumerated

data class UpdateBehandlingRequestExtended(

    @Enumerated(EnumType.STRING)
    val soknadType: SoknadType,

    @Enumerated(EnumType.STRING)
    val soknadFraType: SoknadFraType,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val datoFom: Date,

    @Schema(type = "string", format = "date", example = "01.02.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val mottatDato: Date,
)
