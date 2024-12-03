package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.behandling.henteAktiverteGrunnlag
import no.nav.bidrag.behandling.transformers.behandling.henteUaktiverteGrunnlag
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto

fun Barnetilsyn.tilStønadTilBarnetilsynDto(): StønadTilBarnetilsynDto =
    StønadTilBarnetilsynDto(
        id = this.id,
        periode = DatoperiodeDto(this.fom, this.tom),
        skolealder =
            when (this.under_skolealder) {
                true -> Skolealder.UNDER
                false -> Skolealder.OVER
                else -> null
            },
        tilsynstype =
            when (this.omfang) {
                Tilsynstype.IKKE_ANGITT -> null
                else -> this.omfang
            },
        kilde = this.kilde,
    )

fun Set<Barnetilsyn>.tilStønadTilBarnetilsynDtos() = map { it.tilStønadTilBarnetilsynDto() }.toSet()

fun Behandling.harAndreBarnIUnderhold() = this.underholdskostnader.find { it.person.rolle.isEmpty() } != null

fun Underholdskostnad.harIkkeBarnetilsynITabellFraFør(personident: String) =
    person.rolle
        .first()
        .personident
        ?.verdi == personident &&
        this.barnetilsyn.isEmpty()

fun BarnDto.annetBarnMedSammeNavnOgFødselsdatoEksistererFraFør(behandling: Behandling) =
    behandling.underholdskostnader
        .filter { it.person.ident == null }
        .find { it.person.navn == this.navn && it.person.fødselsdato == this.fødselsdato } != null

fun BarnDto.annetBarnMedSammePersonidentEksistererFraFør(behandling: Behandling) =
    behandling.underholdskostnader
        .filter { it.person.ident != null }
        .find { it.person.ident == this.personident?.verdi } != null

fun Set<BarnetilsynGrunnlagDto>.tilBarnetilsyn(u: Underholdskostnad) = this.map { it.tilBarnetilsyn(u) }.toSet()

fun BarnetilsynGrunnlagDto.tilBarnetilsyn(u: Underholdskostnad) =
    Barnetilsyn(
        underholdskostnad = u,
        fom = this.periodeFra,
        tom = this.periodeTil?.minusDays(1),
        kilde = Kilde.OFFENTLIG,
        omfang = this.tilsynstype ?: Tilsynstype.IKKE_ANGITT,
        under_skolealder =
            when (this.skolealder) {
                Skolealder.OVER -> false
                Skolealder.UNDER -> true
                else -> null
            },
    )

fun Set<Underholdskostnad>.justerePerioderEtterVirkningsdato() = forEach { it.justerePerioder() }

fun Grunnlag.justerePerioderForBearbeidaBarnetilsynEtterVirkningstidspunkt(overskriveAktiverte: Boolean = true) {
    val barnetilsyn = konvertereData<MutableSet<BarnetilsynGrunnlagDto>>()!!

    val virkningstidspunkt = behandling.virkningstidspunktEllerSøktFomDato

    barnetilsyn
        .groupBy { it.barnPersonId }
        .forEach { (gjelder, perioder) ->
            perioder
                .filter { it.periodeFra < virkningstidspunkt }
                .forEach { periode ->
                    if (virkningstidspunkt >= periode.periodeTil) {
                        barnetilsyn.remove(periode)
                    } else {
                        barnetilsyn.add(periode.copy(periodeFra = virkningstidspunkt))
                        barnetilsyn.remove(periode)
                    }
                }

            behandling.overskriveBearbeidaBarnetilsynsgrunnlag(gjelder, perioder, overskriveAktiverte)
        }
}

private fun Underholdskostnad.justerePerioder() {
    val virkningstidspunkt = behandling.virkningstidspunktEllerSøktFomDato

    barnetilsyn.filter { it.fom < virkningstidspunkt }.forEach { periode ->
        if (periode.tom != null && virkningstidspunkt >= periode.tom) {
            barnetilsyn.remove(periode)
        } else {
            periode.fom = virkningstidspunkt
        }
    }

    faktiskeTilsynsutgifter.filter { it.fom < virkningstidspunkt }.forEach { periode ->
        if (periode.tom != null && virkningstidspunkt >= periode.tom) {
            faktiskeTilsynsutgifter.remove(periode)
        } else {
            periode.fom = virkningstidspunkt
        }
    }

    tilleggsstønad.filter { it.fom < virkningstidspunkt }.forEach { periode ->
        if (periode.tom != null && virkningstidspunkt >= periode.tom) {
            tilleggsstønad.remove(periode)
        } else {
            periode.fom = virkningstidspunkt
        }
    }
}

private fun Behandling.overskriveBearbeidaBarnetilsynsgrunnlag(
    gjelder: String?,
    perioder: List<BarnetilsynGrunnlagDto>,
    overskriveAktiverte: Boolean = true,
) {
    val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN

    val grunnlagSomSkalOverskrives =
        if (overskriveAktiverte) {
            henteAktiverteGrunnlag(
                Grunnlagstype(grunnlagsdatatype, true),
                grunnlagsdatatype.innhentesForRolle(this)!!,
            )
        } else {
            henteUaktiverteGrunnlag(
                Grunnlagstype(grunnlagsdatatype, true),
                grunnlagsdatatype.innhentesForRolle(this)!!,
            )
        }

    grunnlagSomSkalOverskrives.find { it.gjelder == gjelder }?.let { it.data = tilJson(perioder) }
}
