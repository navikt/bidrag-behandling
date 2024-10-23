package no.nav.bidrag.behandling.dto.v2.underhold

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
import java.time.LocalDate

data class OppdatereUnderhold(
    @Schema(description = "Barnet underholdskostnaden gjelder.")
    val gjelderBarn: BarnDto,
    val harTilsynsordning: Boolean? = null,
    val stønadTilBarnetilsyn: StønadTilBarnetilsynDto? = null,
    val faktiskTilsynsutgift: FaktiskTilsynsutgiftDto? = null,
    val tilleggsstønad: TilleggsstønadDto? = null,
    val slette: SletteUnderholdselement? = null,
)

data class SletteUnderholdselement(
    val idUnderhold: Long,
    val idElement: Long,
    val type: Underholdselement,
)

enum class Underholdselement { BARN, FAKTISK_TILSYNSUGIFT, STØNAD_TIL_BARNETILSYN, TILLEGGSSTØNAD }

data class BarnDto(
    val id: Long? = null,
    val personident: Personident? = null,
    val navn: String? = null,
)

data class UnderholdDto(
    val id: Long,
    val gjelderBarn: PersoninfoDto,
    val harTilsynsordning: Boolean = false,
    val stønadTilBarnetilsyn: Set<StønadTilBarnetilsynDto> = emptySet(),
    val faktiskeTilsynsutgifter: Set<FaktiskTilsynsutgiftDto>,
    val tilleggsstønad: Set<TilleggsstønadDto> = emptySet(),
    val underholdskostnad: Set<UnderholdskostnadDto>,
)

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
    val id: Long,
    val periode: DatoperiodeDto,
    val dagsats: BigDecimal,
) {
    // TODO: Bytte ut med resultat fra beregningsbibliotek når dette er klart
    // total = dagsats x 260/12 x 11/12
    val total get() = dagsats.multiply(BigDecimal(2860)).divide(BigDecimal(144))
}

data class FaktiskTilsynsutgiftDto(
    val id: Long,
    val periode: DatoperiodeDto,
    val utgift: BigDecimal,
    val kostpenger: BigDecimal = BigDecimal.ZERO,
    val kommentar: String? = null,
) {
    // TODO: Bytte ut med resultat fra beregningsbibliotek når dette er klart
    val total get() = utgift - kostpenger
}

data class StønadTilBarnetilsynDto(
    val id: Long,
    val periode: DatoperiodeDto,
    val skolealder: Skolealder,
    val tilsynstype: Tilsynstype,
    val klde: Kilde,
)

data class DatoperiodeDto(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)
