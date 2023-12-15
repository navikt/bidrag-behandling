package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.deprecated.modell.OpplysningerType
import no.nav.bidrag.behandling.dto.opplysninger.GrunnlagDto
import java.time.LocalDate

data class OpplysningerDto(
    val id: Long,
    val behandlingId: Long,
    val opplysningerType: OpplysningerType,
    val data: String,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val hentetDato: LocalDate,
)

fun GrunnlagDto.tilOpplysningerDto(): OpplysningerDto =
    OpplysningerDto(
        id = this.id,
        behandlingId = this.behandlingsid,
        opplysningerType = OpplysningerType.valueOf(this.grunnlagstype.name),
        data = this.data,
        hentetDato = this.innhentet.toLocalDate(),
    )
