package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import java.math.BigDecimal

fun FaktiskTilsynsutgift.tilFaktiskTilsynsutgiftDto() =
    FaktiskTilsynsutgiftDto(
        id = this.id!!,
        periode = DatoperiodeDto(this.fom, this.tom),
        utgift = this.tilsynsutgift,
        kostpenger = this.kostpenger ?: BigDecimal.ZERO,
        kommentar = this.kommentar,
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