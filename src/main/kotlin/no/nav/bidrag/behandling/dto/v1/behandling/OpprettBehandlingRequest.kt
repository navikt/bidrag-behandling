package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.bestemTypeBehandling
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate

data class OpprettBehandlingRequest(
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
    @field:Size(min = 2, message = "Sak må ha minst to roller involvert")
    val roller: Set<@Valid OpprettRolleDto>,
    @Schema(required = true)
    var stønadstype: Stønadstype?,
    @Schema(required = true)
    var engangsbeløpstype: Engangsbeløptype?,
    @Schema(required = true)
    val søknadsid: Long,
    val søknadsreferanseid: Long? = null,
)

fun OpprettBehandlingRequest.tilType() = bestemTypeBehandling(stønadstype, engangsbeløpstype)

fun OpprettBehandlingRequest.erSærligeUtgifter() = tilType() == TypeBehandling.SÆRLIGE_UTGIFTER

fun OpprettBehandlingRequest.erForskudd() = tilType() == TypeBehandling.FORSKUDD
