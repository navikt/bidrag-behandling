package no.nav.bidrag.behandling.dto.v2.underhold

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Periode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import java.math.BigDecimal
import java.time.LocalDate

data class OppdatereUnderholdskostnad(
    @Schema(description = "Barnet underholdskostnaden gjelder.")
    val gjelderBarn: BarnDto,
    val harTilsynsordning: Boolean? = null,
    val stønadTilBarnetilsynDto: StønadTilBarnetilsynDto? = null,
    val faktiskTilsynsutgift: FaktiskTilsynsutgiftDto? = null,
    val tilleggsstønad: TilleggsstønadDto? = null,
    val slette: SletteUnderholdselement? = null
)

data class SletteUnderholdselement(
    val id: Long,
    val type: Underholdselement,
)

enum class Underholdselement {BARN, FAKTISK_TILSYNSUGIFT, STØNAD_TIL_BARNETILSYN, TILLEGGSSTØNAD, UNDERHOLDSKOSTNAD}

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
    val faktiskeTilsynsutgifter: Set<FaktiskTilsynsutgiftDto> = emptySet(),
    val tilleggsstønad: Set<TilleggsstønadDto> = emptySet(),
    val underholdskostnand: UnderholdskostnadDto,
)

data class UnderholdskostnadDto(
    val periode: ÅrMånedsperiode,
    val forbruk: BigDecimal = BigDecimal.ZERO,
    val boutgifter: BigDecimal = BigDecimal.ZERO,
    val stønadTilBarnetilsyn: BigDecimal = BigDecimal.ZERO,
    val tilsynsutgifter: BigDecimal = BigDecimal.ZERO,
    val barnetrygd: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal = BigDecimal.ZERO,
)

data class TilleggsstønadDto(
    val id: Long,
    val periode: Periode<LocalDate>,
    val dagsats: BigDecimal,
    val total: BigDecimal,
)

data class FaktiskTilsynsutgiftDto(
    val id: Long,
    val periode: Periode<LocalDate>,
    val utgift: BigDecimal,
    val kostpenger: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal,
    val kommentar: String? = null,
)

data class StønadTilBarnetilsynDto(
    val id: Long,
    val periode: Periode<LocalDate>,
    val skolealder: Skolealder,
    val tilsynstype: Tilsynstype,
    val klde: Kilde,
)