package no.nav.bidrag.behandling.dto.v2.underhold

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.validering.OverlappendeBostatusperiode
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class OppdatereUnderholdResponse(
    val stønadTilBarnetilsyn: StønadTilBarnetilsynDto? = null,
    val faktiskTilsynsutgift: FaktiskTilsynsutgiftDto? = null,
    val tilleggsstønad: TilleggsstønadDto? = null,
    val underholdskostnad: Set<UnderholdskostnadDto>,
    val valideringsfeil: ValideringsfeilUnderhold? = null,
)

data class SletteUnderholdselement(
    val idUnderhold: Long,
    val idElement: Long,
    val type: Underholdselement,
)

enum class Underholdselement { BARN, FAKTISK_TILSYNSUGIFT, STØNAD_TIL_BARNETILSYN, TILLEGGSSTØNAD }

data class BarnDto(
    @Parameter(description = "Unik databaseid for person. Skal være null ved opprettelse av underholdskostand for nytt barn")
    val id: Long? = null,
    @Parameter(description = "Personident til barnet som skal legges til underholdskostnad. Kan ikke oppgis sammen med navn.")
    val personident: Personident? = null,
    @Parameter(description = "Navn på barnet som skal legges til underholdskostnad. Kan ikke oppgis sammen med personident.")
    val navn: String? = null,
)

data class UnderholdDto(
    val id: Long,
    val gjelderBarn: PersoninfoDto,
    val harTilsynsordning: Boolean? = null,
    val stønadTilBarnetilsyn: Set<StønadTilBarnetilsynDto> = emptySet(),
    val faktiskeTilsynsutgifter: Set<FaktiskTilsynsutgiftDto>,
    val tilleggsstønad: Set<TilleggsstønadDto> = emptySet(),
    val underholdskostnad: Set<UnderholdskostnadDto>,
)

data class ValideringsfeilUnderhold(
    val hullIPerioder: List<Datoperiode> = emptyList(),
    val overlappendePerioder: List<OverlappendeBostatusperiode> = emptyList(),
    @Schema(description = "Er sann hvis det finnes en eller flere perioder som starter senere enn starten av dagens måned.")
    val fremtidigPeriode: Boolean = false,
    @Schema(description = """Er sann hvis antall perioder er 0."""")
    val harIngenPerioder: Boolean = false,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            hullIPerioder.isNotEmpty() ||
                    overlappendePerioder.isNotEmpty() ||
                    fremtidigPeriode ||
                    harIngenPerioder
}

data class UnderholdskostnadDto(
    val periode: DatoperiodeDto,
    val forbruk: BigDecimal = BigDecimal.ZERO,
    val boutgifter: BigDecimal = BigDecimal.ZERO,
    val stønadTilBarnetilsyn: BigDecimal = BigDecimal.ZERO,
    val tilsynsutgifter: BigDecimal = BigDecimal.ZERO,
    val barnetrygd: BigDecimal = BigDecimal.ZERO,
) {
    // TODO: Bytte ut med resultat fra beregningsbibliotek når dette er klart
    val total get() = forbruk + boutgifter + tilsynsutgifter + stønadTilBarnetilsyn - barnetrygd
}

data class TilleggsstønadDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val dagsats: BigDecimal,
) {
    // TODO: Bytte ut med resultat fra beregningsbibliotek når dette er klart
    // total = dagsats x 260/12 x 11/12
    val total get() = dagsats.multiply(BigDecimal(2860)).divide(BigDecimal(144), RoundingMode.HALF_UP)
}

data class FaktiskTilsynsutgiftDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val utgift: BigDecimal,
    val kostpenger: BigDecimal = BigDecimal.ZERO,
    val kommentar: String? = null,
) {
    // TODO: Bytte ut med resultat fra beregningsbibliotek når dette er klart
    val total get() = utgift - kostpenger
}

data class StønadTilBarnetilsynDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val skolealder: Skolealder,
    val tilsynstype: Tilsynstype,
    val kilde: Kilde = Kilde.MANUELL,
)

data class DatoperiodeDto(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)
