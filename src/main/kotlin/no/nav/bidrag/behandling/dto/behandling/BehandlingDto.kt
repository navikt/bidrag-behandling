package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnDto
import java.time.LocalDate
import java.time.LocalDateTime

data class BehandlingDto(
    val id: Long,
    val behandlingType: BehandlingType,
    val soknadType: SoknadType,
    val erVedtakFattet: Boolean,

    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate,

    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate,

    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottatDato: LocalDate,

    val soknadFraType: SoknadFraType,
    val saksnummer: String,
    val soknadId: Long,
    val behandlerEnhet: String,
    val roller: Set<RolleDto>,
    val husstandsBarn: Set<HusstandsBarnDto>,
    val sivilstand: Set<SivilstandDto>,

    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningsDato: LocalDate? = null,
    val opprettetTidspunkt: LocalDateTime? = null,
    val soknadRefId: Long? = null,
    val aarsak: ForskuddAarsakType? = null,
    val virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,
    val virkningsTidspunktBegrunnelseKunINotat: String? = null,
    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
    val inntektBegrunnelseMedIVedtakNotat: String? = null,
    val inntektBegrunnelseKunINotat: String? = null,
)
