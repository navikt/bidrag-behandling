package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder

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

fun List<Barnetilsyn>.tilStønadTilBarnetilsynDtos() = map { it.tilStønadTilBarnetilsynDto() }.toSet()

fun Behandling.harAndreBarnIUnderhold() = this.underholdskostnader.find { it.barnetsRolleIBehandlingen == null } != null

fun BarnDto.annetBarnMedSammeNavnOgFødselsdatoEksistererFraFør(behandling: Behandling) =
    behandling.underholdskostnader
        .filter { it.person.ident == null }
        .find { it.person.navn == this.navn && it.person.fødselsdato == this.fødselsdato } != null

fun BarnDto.annetBarnMedSammePersonidentEksistererFraFør(behandling: Behandling) =
    behandling.underholdskostnader
        .filter { it.person.ident != null }
        .find { it.person.ident == this.personident?.verdi } != null
