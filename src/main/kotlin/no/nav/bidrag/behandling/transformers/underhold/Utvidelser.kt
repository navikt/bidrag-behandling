package no.nav.bidrag.behandling.transformers.underhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentGrunnlagForType
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
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

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

fun Behandling.harAndreBarnIUnderhold() = this.underholdskostnader.find { it.barnetsRolleIBehandlingen == null } != null

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

private fun Underholdskostnad.justerePerioder() {
    val virkningsdato = behandling.virkningstidspunktEllerSøktFomDato

    barnetilsyn.filter { it.fom < virkningsdato }.forEach { periode ->
        if (periode.tom != null && virkningsdato >= periode.tom) {
            barnetilsyn.remove(periode)
        } else {
            periode.fom = virkningsdato
        }
    }

    faktiskeTilsynsutgifter.filter { it.fom < virkningsdato }.forEach { periode ->
        if (periode.tom != null && virkningsdato >= periode.tom) {
            faktiskeTilsynsutgifter.remove(periode)
        } else {
            periode.fom = virkningsdato
        }
    }

    tilleggsstønad.filter { it.fom < virkningsdato }.forEach { periode ->
        if (periode.tom != null && virkningsdato >= periode.tom) {
            tilleggsstønad.remove(periode)
        } else {
            periode.fom = virkningsdato
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

    grunnlagSomSkalOverskrives.find { it.gjelder == gjelder }?.let { it.data = tilJson(perioder) }
}

fun Behandling.aktivereBarnetilsynHvisIngenEndringerMåAksepteres() {
    val ikkeAktiveGrunnlag = grunnlag.hentAlleIkkeAktiv()
    val aktiveGrunnlag = grunnlag.hentAlleAktiv().toSet()
    if (ikkeAktiveGrunnlag.isEmpty()) return
    val endringerSomMåBekreftes = ikkeAktiveGrunnlag.henteEndringerIBarnetilsyn(aktiveGrunnlag, this)
    val rolleInnhentetFor = Grunnlagsdatatype.BARNETILSYN.innhentesForRolle(this)

    underholdskostnader
        .filter { it.person.rolle.isNotEmpty() }
        .filter { u ->
            endringerSomMåBekreftes?.stønadTilBarnetilsyn?.none { it.key == u.person.personident } ?: true
        }.forEach { u ->
            val ikkeaktivtGrunnlag =
                ikkeAktiveGrunnlag
                    .hentGrunnlagForType(
                        Grunnlagsdatatype.BARNETILSYN,
                        rolleInnhentetFor?.personident!!.verdi,
                    ).find { it.gjelder != null && it.gjelder == u.person.personident!!.verdi } ?: return@forEach

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
