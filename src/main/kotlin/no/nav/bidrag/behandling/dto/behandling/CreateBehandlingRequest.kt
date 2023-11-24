package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.domene.enums.Engangsbeløptype
import no.nav.bidrag.domene.enums.Stønadstype
import no.nav.bidrag.domene.enums.SøktAvType
import java.util.Date

data class CreateBehandlingRequest(
    @Schema(required = true)
    val behandlingType: Behandlingstype,
    @Schema(required = true)
    val soknadType: SoknadType,
    @Schema(required = true)
    val datoFom: Date,
    @Schema(required = true)
    val datoTom: Date,
    @Schema(required = true)
    val mottatDato: Date,
    @Schema(required = true)
    val soknadFra: SøktAvType,
    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, message = "Saks nummer kan ikke være lengre enn 7 tegn")
    val saksnummer: String,
    @field:NotBlank(message = "Enhet kan ikke være blank")
    @field:Size(min = 4, max = 4, message = "Enhet må være 4 tegn")
    val behandlerEnhet: String,
    @field:Size(min = 2, message = "Sak må ha minst to roller involvert")
    val roller: Set<@Valid CreateRolleDto>,
    @Schema(required = true)
    var stonadType: Stønadstype?,
    @Schema(required = true)
    var engangsbelopType: Engangsbeløptype?,
    @Schema(required = true)
    val soknadId: Long,
    val soknadRefId: Long? = null,
)
