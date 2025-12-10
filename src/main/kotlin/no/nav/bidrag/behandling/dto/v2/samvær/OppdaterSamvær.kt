package no.nav.bidrag.behandling.dto.v2.samvær

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import java.math.BigDecimal

data class OppdaterSamværDto(
    val sammeForAlle: Boolean = false,
    val gjelderBarn: String,
    @field:Valid
    val periode: OppdaterSamværsperiodeDto? = null,
    @Schema(description = "Oppdatere saksbehandlers begrunnelse")
    val oppdatereBegrunnelse: OppdatereBegrunnelse? = null,
)

data class OppdaterSamværResponsDto(
    @Schema(description = "Samvær som ble oppdatert", deprecated = true)
    val oppdatertSamvær: SamværBarnDto? = null,
    val samværBarn: List<SamværBarnDto> = emptyList(),
)

data class OppdaterSamværsperiodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val samværsklasse: Samværsklasse? = null,
    @field:Valid
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

data class SamværDtoV2(
    val erSammeForAlle: Boolean,
    val barn: List<SamværBarnDto>,
)

data class SamværBarnDto(
    val id: Long,
    val gjelderBarn: String,
    val begrunnelse: BegrunnelseDto?,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
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
