package no.nav.bidrag.behandling.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.time.LocalDate

data class BehandlingDto(
    val id: Long,
    val behandlingType: BehandlingType,
    val soknadType: SoknadType,

    @Schema(type = "string", format = "date", example = "01-12-2025")
    @JsonFormat(pattern = "dd-MM-yyyy")
    val datoFom: LocalDate,

    @Schema(type = "string", format = "date", example = "01-12-2025")
    @JsonFormat(pattern = "dd-MM-yyyy")
    val datoTom: LocalDate,

    @Schema(type = "string", format = "date", example = "01-12-2025")
    @JsonFormat(pattern = "dd-MM-yyyy")
    val mottatDato: LocalDate,

    val soknadFraType: SoknadFraType,
    val saksnummer: String,
    val behandlerEnhet: String,
    val roller: Set<RolleDto>,

    @Schema(type = "string", format = "date", example = "01-12-2025")
    @JsonFormat(pattern = "dd-MM-yyyy")
    val virkningsDato: LocalDate? = null,

    val aarsak: ForskuddBeregningKodeAarsakType? = null,
    val avslag: String? = null,
    val begrunnelseMedIVedtakNotat: String? = null,
    val begrunnelseKunINotat: String? = null,
)
