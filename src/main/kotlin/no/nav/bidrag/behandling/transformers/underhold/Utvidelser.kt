package no.nav.bidrag.behandling.transformers.underhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentGrunnlagForType
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.behandling.henteAktiverteGrunnlag
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIBarnetilsyn
import no.nav.bidrag.behandling.transformers.behandling.henteUaktiverteGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.henteNyesteGrunnlag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.beregn.core.util.sluttenAvForrigeMåned
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

const val ALDER_VED_SKOLESTART = 6L

fun DatoperiodeDto.tilDatoperiode() = Datoperiode(fom = this.fom, til = this.tom?.plusDays(1))

fun Set<Barnetilsyn>.barnetilsynTilDatoperioder() = this.map { DatoperiodeDto(it.fom, it.tom) }

fun Set<Barnetilsyn>.barnetilsynTilUnderholdsperioder() = this.map { DatoperiodeDto(it.fom, it.tom) }

fun Set<FaktiskTilsynsutgift>.tilsynsutgiftTilDatoperioder() = this.map { DatoperiodeDto(it.fom, it.tom) }

fun Set<Tilleggsstønad>.tilleggsstønadTilDatoperioder() = this.map { DatoperiodeDto(it.fom, it.tom) }

fun Set<Tilleggsstønad>.tilleggsstønadTilUnderholdsperioder() = this.map { DatoperiodeDto(it.fom, it.tom) }

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

fun Set<Barnetilsyn>.tilStønadTilBarnetilsynDtos() = sortedBy { it.fom }.map { it.tilStønadTilBarnetilsynDto() }.toSet()

fun Behandling.harAndreBarnIUnderhold() = this.underholdskostnader.find { it.rolle == null } != null

fun BarnDto.annetBarnMedSammeNavnOgFødselsdatoEksistererFraFør(behandling: Behandling) =
    behandling.underholdskostnader
        .filter { it.personIdent == null }
        .find { it.personNavn == this.navn && it.personFødselsdato == this.fødselsdato } != null

fun BarnDto.annetBarnMedSammePersonidentEksistererFraFør(behandling: Behandling) =
    behandling.underholdskostnader
        .filter { it.personIdent != null }
        .find { it.personIdent == this.personident?.verdi } != null

fun Set<BarnetilsynGrunnlagDto>.tilBarnetilsyn(u: Underholdskostnad) = this.map { it.tilBarnetilsyn(u) }.toSet()

fun BarnetilsynGrunnlagDto.tilBarnetilsyn(u: Underholdskostnad): Barnetilsyn {
    fun erUnderSkolealder(fødselsdato: LocalDate) = fødselsdato.plusYears(ALDER_VED_SKOLESTART).year > LocalDate.now().year

    val justerForDato = maxOf(u.behandling.virkningstidspunkt!!, LocalDate.now().withDayOfMonth(1))
    val tilOgMedDato = this.periodeTil?.minusDays(1)
    return Barnetilsyn(
        underholdskostnad = u,
        fom = this.periodeFra,
        tom = if (tilOgMedDato != null && tilOgMedDato.isAfter(justerForDato)) null else tilOgMedDato,
        kilde = Kilde.OFFENTLIG,
        omfang = this.tilsynstype ?: Tilsynstype.IKKE_ANGITT,
        under_skolealder = erUnderSkolealder(u.personFødselsdato),
    )
}

fun Grunnlag.justerePerioderForBearbeidaBarnetilsynEtterVirkningstidspunkt(overskriveAktiverte: Boolean = true) {
    val barnetilsyn = konvertereData<MutableSet<BarnetilsynGrunnlagDto>>()!!

    val virkningstidspunkt = behandling.virkningstidspunktEllerSøktFomDato

    barnetilsyn
        .groupBy { it.barnPersonId }
        .forEach { (gjelder, perioder) ->
            perioder
                .filter { it.periodeFra < virkningstidspunkt }
                .forEach { periode ->
                    if (periode.periodeTil != null && virkningstidspunkt >= periode.periodeTil) {
                        barnetilsyn.remove(periode)
                    } else {
                        barnetilsyn.add(periode.copy(periodeFra = virkningstidspunkt))
                        barnetilsyn.remove(periode)
                    }
                }

            behandling.overskriveBearbeidaBarnetilsynsgrunnlag(gjelder, barnetilsyn, overskriveAktiverte)
        }
}

fun Underholdskostnad.erstatteOffentligePerioderIBarnetilsynstabellMedOppdatertGrunnlag() {
    val barnetilsynFraGrunnlag =
        behandling.grunnlag
            .hentSisteAktiv()
            .find { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet }
            .konvertereData<Set<BarnetilsynGrunnlagDto>>()
            ?.filter { this.personIdent == it.barnPersonId }

    barnetilsynFraGrunnlag?.let { g ->
        barnetilsyn.removeAll(barnetilsyn.filter { Kilde.OFFENTLIG == it.kilde })
        g.forEach { barnetilsyn.add(it.tilBarnetilsyn(this)) }
    }
}

fun Underholdskostnad.justerePerioder(forrigeVirkningstidspunkt: LocalDate? = null) {
    val virkningsdato = behandling.virkningstidspunktEllerSøktFomDato

    barnetilsyn.filter { it.fom < virkningsdato }.forEach { periode ->
        if (periode.tom != null && virkningsdato >= periode.tom) {
            barnetilsyn.remove(periode)
        } else {
            periode.fom = virkningsdato
        }
    }

    barnetilsyn.filter { it.fom == forrigeVirkningstidspunkt && it.fom > virkningsdato }.forEach { periode ->
        periode.fom = virkningsdato
    }

    faktiskeTilsynsutgifter.filter { it.fom < virkningsdato }.forEach { periode ->
        if (periode.tom != null && virkningsdato >= periode.tom) {
            faktiskeTilsynsutgifter.remove(periode)
        } else {
            periode.fom = virkningsdato
        }
    }
    faktiskeTilsynsutgifter.filter { it.fom == forrigeVirkningstidspunkt && it.fom > virkningsdato }.forEach { periode ->
        periode.fom = virkningsdato
    }
    tilleggsstønad.filter { it.fom < virkningsdato }.forEach { periode ->
        if (periode.tom != null && virkningsdato >= periode.tom) {
            tilleggsstønad.remove(periode)
        } else {
            periode.fom = virkningsdato
        }
    }
    tilleggsstønad.filter { it.fom == forrigeVirkningstidspunkt && it.fom > virkningsdato }.forEach { periode ->
        periode.fom = virkningsdato
    }
}

fun Underholdskostnad.justerPerioderForOpphørsdato(
    opphørSlettet: Boolean = false,
    forrigeOpphørsdato: LocalDate? = null,
) {
    if (opphørsdato != null || opphørSlettet) {
        val opphørsdato = this.rolle?.opphørsdato ?: behandling.globalOpphørsdato
        val beregnTilDato = behandling.finnBeregnTilDatoBehandling(this.rolle)

        barnetilsyn
            .filter { opphørsdato == null || it.fom > beregnTilDato }
            .forEach { periode ->
                barnetilsyn.remove(periode)
            }
        barnetilsyn
            .filter { periode ->
                periode.tom == null || periode.tom!!.isAfter(beregnTilDato) || periode.tom == forrigeOpphørsdato?.sluttenAvForrigeMåned
            }.maxByOrNull { it.fom }
            ?.let {
                it.tom = justerPeriodeTomOpphørsdato(opphørsdato)
            }

        faktiskeTilsynsutgifter
            .filter { opphørsdato == null || it.fom > beregnTilDato }
            .forEach { periode ->
                faktiskeTilsynsutgifter.remove(periode)
            }
        faktiskeTilsynsutgifter
            .filter { periode ->
                periode.tom == null || periode.tom!!.isAfter(beregnTilDato) || periode.tom == forrigeOpphørsdato.sluttenAvForrigeMåned
            }.maxByOrNull { it.fom }
            ?.let {
                it.tom = justerPeriodeTomOpphørsdato(opphørsdato)
            }

        tilleggsstønad
            .filter { opphørsdato == null || it.fom > beregnTilDato }
            .forEach { periode ->
                tilleggsstønad.remove(periode)
            }
        tilleggsstønad
            .filter { periode ->
                periode.tom == null || periode.tom!!.isAfter(beregnTilDato) || periode.tom == forrigeOpphørsdato.sluttenAvForrigeMåned
            }.maxByOrNull { it.fom }
            ?.let {
                it.tom = justerPeriodeTomOpphørsdato(opphørsdato)
            }
    }
}

private fun Behandling.overskriveBearbeidaBarnetilsynsgrunnlag(
    gjelder: String?,
    perioder: Set<BarnetilsynGrunnlagDto>,
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

    grunnlagSomSkalOverskrives.find { it.gjelder == gjelder }?.let {
        it.data = tilJson(perioder)
        it.aktiv = it.aktiv?.let { LocalDateTime.now() }
    }
}

fun Behandling.aktivereBarnetilsynHvisIngenEndringerMåAksepteres() {
    val ikkeAktiveGrunnlag = grunnlag.hentAlleIkkeAktiv()
    val aktiveGrunnlag = grunnlag.hentAlleAktiv().toSet()
    if (ikkeAktiveGrunnlag.isEmpty()) return
    val endringerSomMåBekreftes = ikkeAktiveGrunnlag.henteEndringerIBarnetilsyn(aktiveGrunnlag, this)
    val rolleInnhentetFor = Grunnlagsdatatype.BARNETILSYN.innhentesForRolle(this)

    underholdskostnader
        .filter { it.rolle != null }
        .filter { u ->
            endringerSomMåBekreftes?.stønadTilBarnetilsyn?.none { it.key.verdi == u.personIdent } ?: true
        }.forEach { u ->
            val ikkeaktivtGrunnlag =
                ikkeAktiveGrunnlag
                    .hentGrunnlagForType(
                        Grunnlagsdatatype.BARNETILSYN,
                        rolleInnhentetFor?.personident!!.verdi,
                    ).find { it.gjelder != null && it.gjelder == u.personIdent } ?: return@forEach

            log.info {
                "Ikke-aktive grunnlag type ${Grunnlagsdatatype.BOFORHOLD} med id ${ikkeaktivtGrunnlag.id} " +
                    " for rolle ${rolleInnhentetFor.rolletype} i behandling ${this.id} har ingen " +
                    "endringer som må aksepteres av saksbehandler. Aktiverer automatisk det nyinnhenta grunnlaget."
            }

            ikkeaktivtGrunnlag.aktiv = LocalDateTime.now()
        }

    aktivereOriginaltBarnetilsynsgrunnlagHvisAktivertForAlleBarn()
}

private fun Behandling.aktivereOriginaltBarnetilsynsgrunnlagHvisAktivertForAlleBarn() {
    val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN
    val nyesteOriginaleBarnetilsynsgrunnlag =
        grunnlagsdatatype.innhentesForRolle(this)?.let {
            this.henteNyesteGrunnlag(Grunnlagstype(grunnlagsdatatype, false), it)
        }

    nyesteOriginaleBarnetilsynsgrunnlag?.let {
        if (nyesteOriginaleBarnetilsynsgrunnlag.aktiv == null &&
            this.erSamtligeBearbeidaBarnetilsynsgrunnlagAktivert(it)
        ) {
            nyesteOriginaleBarnetilsynsgrunnlag.aktiv = LocalDateTime.now()
        }
    }
}

private fun Behandling.erSamtligeBearbeidaBarnetilsynsgrunnlagAktivert(bmsNyesteOriginaleBarnetilsynsgrunnlag: Grunnlag): Boolean {
    val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN
    bmsNyesteOriginaleBarnetilsynsgrunnlag
        .konvertereData<Set<BarnetilsynGrunnlagDto>>()
        ?.groupBy {
            it.barnPersonId
        }?.forEach {
            val nyesteBearbeidaBarnetilsynForBarn =
                this.henteNyesteGrunnlag(
                    Grunnlagstype(grunnlagsdatatype, true),
                    grunnlagsdatatype.innhentesForRolle(this)!!,
                    Personident(it.key),
                )

            if (nyesteBearbeidaBarnetilsynForBarn?.aktiv == null) {
                return false
            }
        }
    return true
}
