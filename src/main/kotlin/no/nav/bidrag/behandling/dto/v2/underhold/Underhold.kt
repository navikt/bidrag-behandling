package no.nav.bidrag.behandling.dto.v2.underhold

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
import java.time.LocalDate

data class OpprettUnderholdskostnadBarnResponse(
    val underholdskostnad: UnderholdDto,
    val valideringsfeil: Set<UnderholdskostnadValideringsfeil>? = null,
    val beregnetUnderholdskostnader: Set<BeregnetUnderholdskostnad>,
)

data class OppdatereUnderholdResponse(
    val stønadTilBarnetilsyn: StønadTilBarnetilsynDto? = null,
    val faktiskTilsynsutgift: FaktiskTilsynsutgiftDto? = null,
    val tilleggsstønad: TilleggsstønadDto? = null,
    @Deprecated("Bruk beregnetUnderholdskostnader")
    val underholdskostnad: Set<UnderholdskostnadDto>,
    val valideringsfeil: Set<UnderholdskostnadValideringsfeil>? = null,
    val beregnetUnderholdskostnader: Set<BeregnetUnderholdskostnad>,
)

data class SletteUnderholdselement(
    val idUnderhold: Long,
    val idElement: Long,
    val type: Underholdselement,
)

enum class Underholdselement { BARN, FAKTISK_TILSYNSUTGIFT, STØNAD_TIL_BARNETILSYN, TILLEGGSSTØNAD }

data class BarnDto(
    @Parameter(description = "Unik databaseid for person. Skal være null ved opprettelse av underholdskostand for nytt barn")
    val id: Long? = null,
    @Parameter(description = "Personident til barnet som skal legges til underholdskostnad. Kan ikke oppgis sammen med navn.")
    val personident: Personident? = null,
    @Parameter(description = "Navn på barnet som skal legges til underholdskostnad. Kan ikke oppgis sammen med personident.")
    val navn: String? = null,
    @Parameter(description = "Fødselsdato til barnet som skal legges til underholdskostnad. Påkrevd dersom barnet oppgis uten personident.")
    val fødselsdato: LocalDate? = null,
)

data class UnderholdDto(
    val id: Long,
    val gjelderBarn: PersoninfoDto,
    val harTilsynsordning: Boolean? = null,
    val stønadTilBarnetilsyn: Set<StønadTilBarnetilsynDto> = emptySet(),
    val faktiskTilsynsutgift: Set<FaktiskTilsynsutgiftDto>,
    val tilleggsstønad: Set<TilleggsstønadDto> = emptySet(),
    val underholdskostnad: Set<UnderholdskostnadDto>,
    val begrunnelse: String? = null,
    val beregnetUnderholdskostnad: Set<UnderholdskostnadDto>,
    val valideringsfeil: UnderholdskostnadValideringsfeil?,
)

data class OppdatereUnderholdRequest(
    val harTilsynsordning: Boolean? = null,
    val begrunnelse: String? = null,
)

data class OppdatereBegrunnelseRequest(
    @Schema(description = "Id til underhold begrunnelsen gjelder for hvis søknadsbarn. Null for andre barn.")
    val underholdsid: Long? = null,
    val begrunnelse: String,
)

data class Periodiseringsfeil(
    val gjelderTabell: Underholdselement,
    val overlappendePerioder: Map<DatoperiodeDto, Set<DatoperiodeDto>>,
    val harFremtidigPeriode: Boolean,
    val harIngenPerioder: Boolean,
)

data class Underholdsperiode(
    val underholdselement: Underholdselement,
    val periode: DatoperiodeDto,
)

data class UnderholdskostnadValideringsfeil(
    @JsonIgnore
    val gjelderUnderholdskostnad: Underholdskostnad,
    val tilleggsstønad: UnderholdskostnadValideringsfeilTabell? = null,
    val faktiskTilsynsutgift: UnderholdskostnadValideringsfeilTabell? = null,
    val stønadTilBarnetilsyn: UnderholdskostnadValideringsfeilTabell? = null,
    @Schema(description = "Tilleggsstønadsperioder som ikke overlapper fullstendig med faktiske tilsynsutgifter.")
    val tilleggsstønadsperioderUtenFaktiskTilsynsutgift: Set<Underholdsperiode> = emptySet(),
    val manglerPerioderForTilsynsutgifter: Boolean = false,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            tilleggsstønad?.harFeil == true ||
                faktiskTilsynsutgift?.harFeil == true ||
                stønadTilBarnetilsyn?.harFeil == true ||
                tilleggsstønadsperioderUtenFaktiskTilsynsutgift.isNotEmpty() ||
                manglerPerioderForTilsynsutgifter
    val underholdskostnadsid get() = gjelderUnderholdskostnad.id
    val barn
        get() =
            UnderholdBarnDto(
                navn = gjelderUnderholdskostnad.person.navn,
                ident = gjelderUnderholdskostnad.person.personident?.verdi,
                fødselsdato = gjelderUnderholdskostnad.person.henteFødselsdato!!,
                medIBehandling = gjelderUnderholdskostnad.barnetsRolleIBehandlingen != null,
            )

    data class UnderholdBarnDto(
        val navn: String?,
        val ident: String?,
        val fødselsdato: LocalDate,
        val medIBehandling: Boolean,
    )
}

data class UnderholdskostnadValideringsfeilTabell(
    @Schema(description = "Overlappende perioder i stønad til barnetilsyn eller tillegsstønad.")
    val overlappendePerioder: Map<Underholdsperiode, Set<Underholdsperiode>> = emptyMap(),
    @Schema(description = "Perioder som starter senere enn starten av dagens måned.")
    val fremtidigePerioder: Set<Underholdsperiode> = emptySet(),
    @Schema(description = """Er sann hvis antall perioder er 0."""")
    val harIngenPerioder: Boolean = false,
    @Schema(description = "Er sann hvis det er satt at BM har tilsynsordning for barnet men det mangler perioder for tilsynsutgifter.")
    val manglerPerioderForTilsynsutgifter: Boolean = false,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            overlappendePerioder.isNotEmpty() ||
                fremtidigePerioder.isNotEmpty() ||
                manglerPerioderForTilsynsutgifter ||
                harIngenPerioder
}

data class BeregnetUnderholdskostnad(
    val gjelderBarn: PersoninfoDto,
    val perioder: Set<UnderholdskostnadDto>,
)

data class UnderholdskostnadDto(
    val periode: DatoperiodeDto,
    val forbruk: BigDecimal = BigDecimal.ZERO,
    val boutgifter: BigDecimal = BigDecimal.ZERO,
    val stønadTilBarnetilsyn: BigDecimal = BigDecimal.ZERO,
    val tilsynsutgifter: BigDecimal = BigDecimal.ZERO,
    val barnetrygd: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal,
    val beregningsdetaljer: UnderholdskostnadPeriodeBeregningsdetaljer? = null,
) {
    data class UnderholdskostnadPeriodeBeregningsdetaljer(
        val tilsynsutgifterBarn: List<TilsynsutgiftBarn> = emptyList(),
        val sjablonMaksTilsynsutgift: BigDecimal,
        val sjablonMaksFradrag: BigDecimal,
        val antallBarnBMUnderTolvÅr: Int,
        val skattesatsFaktor: BigDecimal,
        val totalTilsynsutgift: BigDecimal,
        val sumTilsynsutgifter: BigDecimal,
        val bruttoTilsynsutgift: BigDecimal,
        val justertBruttoTilsynsutgift: BigDecimal,
        val nettoTilsynsutgift: BigDecimal,
        val erBegrensetAvMaksTilsyn: Boolean,
        val fordelingFaktor: BigDecimal,
        val skattefradragPerBarn: BigDecimal,
        val maksfradragAndel: BigDecimal,
        val skattefradrag: BigDecimal,
        val skattefradragMaksFradrag: BigDecimal,
        val skattefradragTotalTilsynsutgift: BigDecimal,
    )

    data class TilsynsutgiftBarn(
        val gjelderBarn: PersoninfoDto,
        val totalTilsynsutgift: BigDecimal,
        val beløp: BigDecimal,
        val kostpenger: BigDecimal? = null,
        val tilleggsstønadDagsats: BigDecimal? = null,
        val tilleggsstønad: BigDecimal? = null,
    )
}

data class OppdatereTilleggsstønadRequest(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val dagsats: BigDecimal,
)

data class TilleggsstønadDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val dagsats: BigDecimal,
    val total: BigDecimal,
)

data class OppdatereFaktiskTilsynsutgiftRequest(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val utgift: BigDecimal,
    val kostpenger: BigDecimal? = null,
    val kommentar: String? = null,
)

data class FaktiskTilsynsutgiftDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val utgift: BigDecimal,
    val kostpenger: BigDecimal? = null,
    val kommentar: String? = null,
    val total: BigDecimal,
)

data class StønadTilBarnetilsynDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val skolealder: Skolealder? = null,
    val tilsynstype: Tilsynstype? = null,
    val kilde: Kilde = Kilde.MANUELL,
)

data class DatoperiodeDto(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)
