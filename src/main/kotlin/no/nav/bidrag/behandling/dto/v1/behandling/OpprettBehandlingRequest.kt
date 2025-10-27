package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.SjekkRolleDto
import no.nav.bidrag.behandling.transformers.bestemTypeBehandling
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate

data class OpprettBehandlingRequest(
    val søknadstype: Behandlingstype? = null,
    val behandlingstype: Behandlingstype? = null,
    val behandlingstema: Behandlingstema? = null,
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
    val roller: Set<@Valid OpprettRolleDto>,
    @Schema(required = true)
    var stønadstype: Stønadstype? = null,
    @Schema(required = true)
    var engangsbeløpstype: Engangsbeløptype? = null,
    @Schema(required = true)
    val søknadsid: Long,
    val vedtaksid: Int? = null,
    val søknadsreferanseid: Long? = null,
    val kategori: OpprettKategoriRequestDto? = null,
    val innkrevingstype: Innkrevingstype? = Innkrevingstype.MED_INNKREVING,
)

fun OpprettBehandlingRequest.tilKanBehandlesINyLøsningRequest(): KanBehandlesINyLøsningRequest =
    KanBehandlesINyLøsningRequest(
        saksnummer = this.saksnummer,
        søknadstype = søknadstype,
        roller = this.roller.map { SjekkRolleDto(it.rolletype, it.ident, it.erUkjent) },
        stønadstype = this.stønadstype,
        engangsbeløpstype = this.engangsbeløpstype,
        vedtakstype = vedtakstype,
    )

fun OpprettBehandlingRequest.tilType() = bestemTypeBehandling(stønadstype, engangsbeløpstype)

fun OpprettBehandlingRequest.erSærbidrag() = tilType() == TypeBehandling.SÆRBIDRAG

fun OpprettBehandlingRequest.erForskudd() = tilType() == TypeBehandling.FORSKUDD

fun OpprettBehandlingRequest.erBidrag() = tilType() == TypeBehandling.BIDRAG || tilType() == TypeBehandling.BIDRAG_18_ÅR

fun OpprettBehandlingRequest.erKlage() = vedtakstype == Vedtakstype.KLAGE

data class OpprettKategoriRequestDto(
    @Schema(required = true)
    val kategori: String,
    @Schema(
        required = false,
        description = "Beskrivelse av kategorien som er valgt. Er påkrevd hvis kategori er ANNET ",
    )
    val beskrivelse: String? = null,
)
