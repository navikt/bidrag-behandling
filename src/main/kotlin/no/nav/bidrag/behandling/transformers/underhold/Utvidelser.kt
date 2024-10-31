package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.service.UnderholdService.Companion.beregneUnderholdskostnad
import no.nav.bidrag.behandling.transformers.person.tilPersoninfoDto
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import java.math.BigDecimal

fun Underholdskostnad.tilUnderholdDto(underholdselement: Underholdselement = Underholdselement.STØNAD_TIL_BARNETILSYN) =
    UnderholdDto(
        id = this.id!!,
        gjelderBarn = this.person.tilPersoninfoDto(this.behandling),
        faktiskeTilsynsutgifter = this.faktiskeTilsynsutgifter.tilFaktiskeTilsynsutgiftDtos(),
        stønadTilBarnetilsyn = this.barnetilsyn.tilStønadTilBarnetilsynDtos(),
        tilleggsstønad = this.tilleggsstønad.tilTilleggsstønadDtos(),
        underholdskostnad = beregneUnderholdskostnad(this, underholdselement),
    )

fun FaktiskTilsynsutgift.tilFaktiskTilsynsutgiftDto() =
    FaktiskTilsynsutgiftDto(
        id = this.id!!,
        periode = DatoperiodeDto(this.fom, this.tom),
        utgift = this.tilsynsutgift,
        kostpenger = this.kostpenger ?: BigDecimal.ZERO,
    )

fun Set<FaktiskTilsynsutgift>.tilFaktiskeTilsynsutgiftDtos() = this.map { it.tilFaktiskTilsynsutgiftDto() }.toSet()

fun Barnetilsyn.tilStønadTilBarnetilsynDto(): StønadTilBarnetilsynDto =
    StønadTilBarnetilsynDto(
        id = this.id,
        periode = DatoperiodeDto(this.fom, this.tom),
        skolealder =
        when (this.under_skolealder) {
            true -> Skolealder.UNDER
            false -> Skolealder.OVER
            else -> Skolealder.IKKE_ANGITT
        },
        tilsynstype = this.omfang,
        kilde = this.kilde,
    )

fun Set<Barnetilsyn>.tilStønadTilBarnetilsynDtos() = map { it.tilStønadTilBarnetilsynDto() }.toSet()

fun Tilleggsstønad.tilTilleggsstønadDto() =
    TilleggsstønadDto(
        id = this.id!!,
        periode = DatoperiodeDto(this.fom, this.tom),
        dagsats = this.dagsats,
    )

fun Set<Tilleggsstønad>.tilTilleggsstønadDtos() = this.map { it.tilTilleggsstønadDto() }.toSet()