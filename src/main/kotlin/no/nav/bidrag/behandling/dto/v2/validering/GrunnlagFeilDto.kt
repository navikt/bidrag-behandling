package no.nav.bidrag.behandling.dto.v2.validering

import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.grunnlag.HentGrunnlagFeiltype
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import java.time.LocalDate

data class GrunnlagFeilDto(
    val grunnlagstype: GrunnlagRequestType? = null,
    val personId: String?,
    val periodeFra: LocalDate? = null,
    val periodeTil: LocalDate? = null,
    val feiltype: HentGrunnlagFeiltype,
    val feilmelding: String?,
)

fun FeilrapporteringDto.tilGrunnlagFeilDto() =
    GrunnlagFeilDto(
        grunnlagstype = grunnlagstype,
        periodeTil = periodeTil,
        personId = personId,
        periodeFra = periodeFra,
        feiltype = feiltype,
        feilmelding = feilmelding,
    )
