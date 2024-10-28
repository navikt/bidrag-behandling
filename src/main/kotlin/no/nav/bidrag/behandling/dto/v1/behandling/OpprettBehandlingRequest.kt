package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.behandling.transformers.bestemTypeBehandling
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
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
    var stønadstype: Stønadstype? = null,
    @Schema(required = true)
    var engangsbeløpstype: Engangsbeløptype? = null,
    @Schema(required = true)
    val søknadsid: Long,
    val søknadsreferanseid: Long? = null,
    val kategori: OpprettKategoriRequestDto? = null,
    val innkrevingstype: Innkrevingstype? = Innkrevingstype.MED_INNKREVING,
)

fun OpprettBehandlingRequest.tilKanBehandlesINyLøsningRequest(): KanBehandlesINyLøsningRequest =
    KanBehandlesINyLøsningRequest(
        saksnummer = this.saksnummer,
        roller = this.roller.map { SjekkRolleDto(it.rolletype, it.ident) },
        stønadstype = this.stønadstype,
        engangsbeløpstype = this.engangsbeløpstype,
    )

fun OpprettBehandlingRequest.tilType() = bestemTypeBehandling(stønadstype, engangsbeløpstype)

fun OpprettBehandlingRequest.erSærbidrag() = tilType() == TypeBehandling.SÆRBIDRAG

fun OpprettBehandlingRequest.erForskudd() = tilType() == TypeBehandling.FORSKUDD

data class OpprettKategoriRequestDto(
    @Schema(required = true)
    val kategori: String,
    @Schema(
        required = false,
        description = "Beskrivelse av kategorien som er valgt. Er påkrevd hvis kategori er ANNET ",
    )
    val beskrivelse: String? = null,
)
