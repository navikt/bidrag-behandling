package no.nav.bidrag.behandling.dto.v2.utgift

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.PositiveOrZero
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.behandling.dto.v2.behandling.TotalBeregningUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.validering.UtgiftValideringsfeilDto
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import java.math.BigDecimal
import java.time.LocalDate

data class OppdatereUtgiftRequest(
    @Schema(
        description =
            "Oppdater avslag.",
        enumAsRef = true,
    )
    val avslag: Resultatkode? = null,
    val beløpDirekteBetaltAvBp: BigDecimal? = null,
    val maksGodkjentBeløp: MaksGodkjentBeløpDto? = null,
    @Schema(
        description =
            "Legg til eller endre en utgift. Utgift kan ikke endres eller oppdateres hvis avslag er satt",
    )
    val nyEllerEndretUtgift: OppdatereUtgift? = null,
    @Schema(
        description =
            "Slette en utgift. Utgift kan ikke endres eller oppdateres hvis avslag er satt",
    )
    val sletteUtgift: Long? = null,
    @Schema(description = "Oppdatere saksbehandlers begrunnelse")
    val oppdatereBegrunnelse: OppdatereBegrunnelse? = null,
    @Schema(description = "Deprekert - bruk oppdatereBegrunnelse i stedet")
    val notat: OppdatereBegrunnelse? = null,
) {
    // TODO: Fjerne når migrering til oppdatereBegrunnelse er fullført
    fun henteOppdatereNotat(): OppdatereBegrunnelse? = oppdatereBegrunnelse ?: notat
}

data class MaksGodkjentBeløpDto(
    val taMed: Boolean = true,
    val beløp: BigDecimal? = null,
    val begrunnelse: String? = null,
)

data class OppdatereUtgiftResponse(
    @Schema(description = "Utgiftspost som ble oppdatert")
    val oppdatertUtgiftspost: UtgiftspostDto? = null,
    val utgiftposter: List<UtgiftspostDto> = emptyList(),
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = true)
    val begrunnelse: String? = null,
    val beregning: UtgiftBeregningDto? = null,
    val maksGodkjentBeløp: MaksGodkjentBeløpDto? = null,
    val avslag: Resultatkode? = null,
    val valideringsfeil: UtgiftValideringsfeilDto?,
    val totalBeregning: List<TotalBeregningUtgifterDto> = emptyList(),
) {
    @Deprecated("Erstattes av begrunnelse")
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = true)
    val oppdatertNotat: String? = begrunnelse
}

data class OppdatereUtgift(
    @Schema(description = "Når utgifter gjelder. Kan være feks dato på kvittering")
    @field:PastOrPresent(message = "Dato for utgiftspost kan ikke være fram i tid")
    val dato: LocalDate,
    @Schema(
        description =
            "Type utgift. Kan feks være hva som ble kjøpt for kravbeløp (bugnad, klær, sko, etc). " +
                "Skal bare settes for kategori konfirmasjon",
        oneOf = [Utgiftstype::class, String::class],
    )
    val type: String? = null,
    @Schema(description = "Beløp som er betalt for utgiften det gjelder")
    @field:PositiveOrZero(message = "Kravbeløp kan ikke være negativ")
    val kravbeløp: BigDecimal,
    @Schema(description = "Beløp som er godkjent for beregningen")
    @field:PositiveOrZero(message = "Godkjent beløp kan ikke være negativ")
    val godkjentBeløp: BigDecimal = kravbeløp,
    @Schema(
        description =
            "Kommentar kan brukes til å legge inn nærmere informasjon om utgiften f.eks. fakturanr., butikk det er handlet i," +
                " informasjon om hvorfor man ikke har godkjent hele kravbeløpet",
    )
    val kommentar: String? = null,
    @Schema(description = "Om utgiften er betalt av BP")
    val betaltAvBp: Boolean = false,
    val id: Long? = null,
)

fun String.tilUtgiftstype(): Utgiftstype? =
    try {
        Utgiftstype.valueOf(this)
    } catch (e: Exception) {
        null
    }
