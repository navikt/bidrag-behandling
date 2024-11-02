package no.nav.bidrag.behandling.dto.v2.samvær

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import java.math.BigDecimal

data class OppdaterSamværDto(
    val gjelderBarn: String,
    val periode: OppdaterSamværsperiodeDto? = null,
    @Schema(description = "Oppdatere saksbehandlers begrunnelse")
    val oppdatereBegrunnelse: OppdatereBegrunnelse? = null,
)

data class OppdaterSamværResponsDto(
    @Schema(description = "Samvær som ble oppdatert")
    val oppdatertSamvær: SamværDto? = null,
)

data class OppdaterSamværsperiodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val samværsklasse: Samværsklasse? = null,
    val beregning: SamværskalkulatorDetaljer? = null,
)

data class OppdaterSamværskalkulatorBeregningDto(
    val gjelderBarn: String,
    val samværsperiodeId: Long,
    val beregning: SamværskalkulatorDetaljer,
    val samværsklasse: Samværsklasse?,
)

data class SletteSamværsperiodeElementDto(
    val gjelderBarn: String,
    val samværsperiodeId: Long,
)

data class SamværDto(
    val id: Long,
    val gjelderBarn: String,
    val begrunnelse: BegrunnelseDto?,
    val valideringsfeil: SamværValideringsfeilDto?,
    val perioder: List<SamværsperiodeDto> = emptyList(),
) {
    data class SamværsperiodeDto(
        val id: Long? = null,
        val periode: DatoperiodeDto,
        val samværsklasse: Samværsklasse,
        val gjennomsnittligSamværPerMåned: BigDecimal,
        val beregning: SamværskalkulatorDetaljer? = null,
    )
}
