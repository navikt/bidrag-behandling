package no.nav.bidrag.behandling.dto.v2.utgift

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.PositiveOrZero
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereNotat
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
    @Schema(
        type = "Boolean",
        description = "Angre siste endring som ble gjort. Siste endring kan ikke angres hvis avslag er satt",
    )
    val angreSisteEndring: Boolean? = false,
    val oppdatereNotat: OppdatereNotat? = null,
) {
    @Deprecated("Bruk oppdatereNotat i stedet")
    @Schema(description = "Bruk oppdatereNotat i stedet", deprecated = true)
    val notat: OppdatereNotat? = oppdatereNotat

    fun henteOppdatereNotat(): OppdatereNotat? = oppdatereNotat ?: notat
}

data class OppdatereUtgiftResponse(
    @Schema(description = "Utgiftspost som ble oppdatert")
    val oppdatertUtgiftspost: UtgiftspostDto? = null,
    val utgiftposter: List<UtgiftspostDto> = emptyList(),
    val oppdatertNotat: String? = null,
    val beregning: UtgiftBeregningDto? = null,
    val avslag: Resultatkode? = null,
    val valideringsfeil: UtgiftValideringsfeilDto?,
)

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
    @Schema(description = "Begrunnelse for hvorfor godkjent beløp avviker fra kravbeløp. Må settes hvis godkjent beløp er ulik kravbeløp")
    val begrunnelse: String? = null,
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
