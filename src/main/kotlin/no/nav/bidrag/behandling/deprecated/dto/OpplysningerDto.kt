package no.nav.bidrag.behandling.deprecated.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.deprecated.modell.OpplysningerType
import no.nav.bidrag.behandling.deprecated.modell.tilOpplysningerType
import no.nav.bidrag.behandling.dto.grunnlag.GrunnlagDto
import java.time.LocalDate

data class OpplysningerDto(
    val id: Long,
    val behandlingId: Long,
    val type: OpplysningerType,
    val data: String,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val hentetDato: LocalDate,
)

fun Grunnlag.tilOpplysningerDto() =
    OpplysningerDto(
        id = this.id!!,
        behandlingId = this.behandling.id!!,
        type = OpplysningerType.valueOf(this.type.name),
        data = this.data.innhold,
        hentetDato = this.innhentet.toLocalDate(),
    )
