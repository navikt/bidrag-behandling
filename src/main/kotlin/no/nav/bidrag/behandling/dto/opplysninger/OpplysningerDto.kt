package no.nav.bidrag.behandling.dto.opplysninger

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import java.time.LocalDate

data class OpplysningerDto(
    val id: Long,
    val behandlingId: Long,
    val aktiv: Boolean,
    val opplysningerType: OpplysningerType,
    val data: String,

    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "dd.MM.yyyy")
    val hentetDato: LocalDate,
)
