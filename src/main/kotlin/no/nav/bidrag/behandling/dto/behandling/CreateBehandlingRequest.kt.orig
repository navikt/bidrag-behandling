package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
<<<<<<< HEAD
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.time.LocalDate
=======
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.util.Date
>>>>>>> main

data class CreateBehandlingRequest(
    @Schema(required = true)
    val behandlingstype: Behandlingstype,
    @Schema(required = true)
    val søknadstype: Soknadstype,
    @Schema(required = true)
    val datoFom: LocalDate,
    @Schema(required = true)
    val datoTom: LocalDate,
    @Schema(required = true)
    val mottattdato: LocalDate,
    @Schema(required = true)
    val søknadFra: SøktAvType,
    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, min = 7, message = "Saksnummer skal ha sju tegn")
    val saksnummer: String,
    @field:NotBlank(message = "Enhet kan ikke være blank")
    @field:Size(min = 4, max = 4, message = "Enhet må være 4 tegn")
    val behandlerenhet: String,
    @field:Size(min = 2, message = "Sak må ha minst to roller involvert")
    val roller: Set<@Valid CreateRolleDto>,
    @Schema(required = true)
    var stønadstype: Stønadstype?,
    @Schema(required = true)
    var engangsbeløpstype: Engangsbeløptype?,
    @Schema(required = true)
    val søknadsid: Long,
    val søknadsreferanseid: Long? = null,
)
