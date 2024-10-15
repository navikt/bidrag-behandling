package no.nav.bidrag.behandling.dto.v2.samvær

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Samværskalkulator
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.tid.Datoperiode

data class OppdaterSamværDto(
    val gjelderBarn: String,
    val periode: OppdaterSamværsperiodeDto? = null,
    val slettPeriode: Long? = null,
)

data class OppdaterSamværResponsDto(
    @Schema(description = "Samvær som ble oppdatert")
    val oppdatertSamvær: SamværDto? = null,
    @Schema(description = "Saksbehandlers begrunnelse")
    val begrunnelse: String? = null,
    val valideringsfeil: SamværValideringsfeilDto?,
)

data class OppdaterSamværsperiodeDto(
    val id: Long? = null,
    val periode: Datoperiode,
    val samværsklasse: Samværsklasse,
    val beregning: Samværskalkulator? = null,
    val slettBeregning: Boolean? = null,
)

data class SamværDto(
    val gjelderBarn: String,
    val perioder: List<SamværsperiodeDto> = emptyList(),
) {
    data class SamværsperiodeDto(
        val periode: Datoperiode,
        val samværsklasse: Samværsklasse,
        val beregning: Samværskalkulator? = null,
    )
}
