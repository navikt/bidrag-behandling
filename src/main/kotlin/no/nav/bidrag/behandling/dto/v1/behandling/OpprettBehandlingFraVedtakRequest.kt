package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate

data class OpprettBehandlingFraVedtakRequest(
    @Schema(required = true)
    val vedtakstype: Vedtakstype,
    @Schema(required = true)
    val søktFomDato: LocalDate,
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
    @Schema(required = true)
    val søknadsid: Long,
    val søknadsreferanseid: Long? = null,
)
