package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.util.Date
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class CreateBehandlingRequest(
    val behandlingType: BehandlingType,

    val soknadType: SoknadType,

    val datoFom: Date,

    val datoTom: Date,

    val mottatDato: Date,

    val soknadFra: SoknadFraType,

    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, message = "Saks nummer kan ikke være lengre enn 7 tegn")
    val saksnummer: String,

    @field:NotBlank(message = "Enhet kan ikke være blank")
    @field:Size(min = 4, max = 4, message = "Enhet må være 4 tegn")
    val behandlerEnhet: String,

    @field:Size(min = 2, message = "Sak må ha minst to roller involvert")
    val roller: Set<@Valid CreateRolleDto>,
)
