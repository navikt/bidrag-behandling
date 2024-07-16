package no.nav.bidrag.behandling.transformers.boforhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.database.datamodell.henteGjeldendeBoforholdsgrunnlagForAndreVoksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.boforhold.BostatusperiodeDto
import no.nav.bidrag.behandling.service.BoforholdService.Companion.opprettDefaultPeriodeForAndreVoksneIHusstand
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequest
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.BoforholdVoksneRequest
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.boforhold.dto.Husstandsmedlemmer
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import java.time.LocalDate

private val log = KotlinLogging.logger {}

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdBarnRequest(behandling: Behandling) = this.toList().tilBoforholdBarnRequest(behandling)

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdVoksneRequest(): BoforholdVoksneRequest =
    BoforholdVoksneRequest(
        behandledeBostatusopplysninger = emptyList(),
        endreBostatus = null,
        innhentedeOffentligeOpplysninger = this.filter { !it.erBarn }.tilHusstandsmedlemmer(),
    )

fun List<RelatertPersonGrunnlagDto>.tilHusstandsmedlemmer() =
    this.map {
        Husstandsmedlemmer(
            gjelderPersonId = it.gjelderPersonId,
            fødselsdato = it.fødselsdato!!,
            relasjon = it.relasjon,
            borISammeHusstandListe = it.borISammeHusstandDtoListe.tilBostatus(it.relasjon, it.fødselsdato!!),
        )
    }

fun List<RelatertPersonGrunnlagDto>.tilBoforholdBarnRequest(behandling: Behandling): List<BoforholdBarnRequest> {
    val barnAvBmBpManglerFødselsdato = this.filter { it.erBarn }.filter { it.fødselsdato == null }
    if (barnAvBmBpManglerFødselsdato.isNotEmpty()) {
        secureLogger.warn {
            "Husstandsmedlem som er barn av BM eller BP (personident forelder: ${barnAvBmBpManglerFødselsdato.first().partPersonId}) mangler fødselsdato."
        }
    }

    return this.filter { it.erBarn }.filter { it.fødselsdato != null }.map { g ->
        BoforholdBarnRequest(
            innhentedeOffentligeOpplysninger =
                when (g.borISammeHusstandDtoListe.isNotEmpty()) {
                    true ->
                        g.borISammeHusstandDtoListe.tilBostatus(
                            Bostatuskode.MED_FORELDER,
                            Kilde.OFFENTLIG,
                        )

                    false ->
                        listOf(
                            Bostatus(
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = maxOf(g.fødselsdato!!, behandling.virkningstidspunktEllerSøktFomDato),
                                periodeTom = null,
                            ),
                        )
                },
            erBarnAvBmBp =
                if (behandling.husstandsmedlem.find {
                        it.ident != null && it.ident == g.gjelderPersonId
                    } != null
                ) {
                    true
                } else {
                    g.erBarn
                },
            fødselsdato = g.fødselsdato!!,
            relatertPersonPersonId = g.gjelderPersonId,
            behandledeBostatusopplysninger = emptyList(),
            endreBostatus = null,
        )
    }
}

fun Husstandsmedlem.tilBoforholdBarnRequest(
    endreBostatus: EndreBostatus? = null,
    erBarnAvBmBp: Boolean = true,
): BoforholdBarnRequest =
    BoforholdBarnRequest(
        relatertPersonPersonId = ident,
        fødselsdato = fødselsdato ?: rolle!!.fødselsdato,
        erBarnAvBmBp = erBarnAvBmBp,
        innhentedeOffentligeOpplysninger =
            henteOffentligePerioder().map { it.tilBostatus() }.sortedBy { it.periodeFom },
        behandledeBostatusopplysninger = perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
        endreBostatus = endreBostatus,
    )

fun Husstandsmedlem.tilBoforholdVoksneRequest(
    gjelderRolle: Rolle,
    endreBostatus: EndreBostatus? = null,
) = BoforholdVoksneRequest(
    innhentedeOffentligeOpplysninger = henteGjeldendeBoforholdsgrunnlagForAndreVoksneIHusstanden(gjelderRolle).tilHusstandsmedlemmer(),
    behandledeBostatusopplysninger = perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
    endreBostatus = endreBostatus,
)

fun Husstandsmedlem.henteOffentligePerioder(): Set<Bostatusperiode> =
    hentSisteBearbeidetBoforhold()?.tilPerioder(this) ?: if (kilde == Kilde.OFFENTLIG) {
        log.warn {
            "Fant ikke originale bearbeidet perioder for offentlig husstandsmedlem $id i behandling ${behandling.id}. Lagt til initiell periode "
        }
        setOf(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    } else {
        setOf()
    }

fun Set<Bostatus>.tilBostatusperiodeDto() =
    this
        .map {
            BostatusperiodeDto(
                id = null,
                datoFom = it.periodeFom,
                datoTom = it.periodeTom,
                bostatus = it.bostatus!!,
                kilde = Kilde.OFFENTLIG,
            )
        }.toSet()

fun Bostatusperiode.tilBostatus() =
    Bostatus(
        bostatus = this.bostatus,
        kilde = this.kilde,
        periodeFom = this.datoFom,
        periodeTom = this.datoTom,
    )

fun Set<Bostatus>.tilBostatusperiode(husstandsmedlem: Husstandsmedlem) =
    this
        .filter { it.bostatus != null }
        .map {
            Bostatusperiode(
                husstandsmedlem = husstandsmedlem,
                bostatus = it.bostatus!!,
                datoFom = it.periodeFom,
                datoTom = it.periodeTom,
                kilde = it.kilde,
            )
        }.toMutableSet()

fun List<BorISammeHusstandDto>.tilBostatus(
    bostatus: Bostatuskode,
    kilde: Kilde,
) = this.map {
    Bostatus(
        bostatus = bostatus,
        kilde = kilde,
        periodeFom = it.periodeFra,
        periodeTom = it.periodeTil,
    )
}

fun List<BorISammeHusstandDto>.tilBostatus(
    relasjon: Familierelasjon,
    fødselsdato: LocalDate,
): List<Bostatus> {
    val førsteDagIMånedenEtterFylteAttenÅr = fødselsdato.plusMonths(1).withDayOfMonth(1).plusYears(18)

    val bostatus =
        when (relasjon) {
            Familierelasjon.BARN -> {
                if (LocalDate.now().isBefore(førsteDagIMånedenEtterFylteAttenÅr)) {
                    Bostatuskode.MED_FORELDER
                } else {
                    Bostatuskode.REGNES_IKKE_SOM_BARN
                }
            }

            else -> null
        }

    return this.map {
        Bostatus(
            bostatus = bostatus,
            kilde = Kilde.OFFENTLIG,
            periodeFom = it.periodeFra,
            periodeTom = it.periodeTil,
        )
    }
}

fun List<BoforholdResponse>.tilPerioder(husstandsmedlem: Husstandsmedlem) =
    this.find { it.relatertPersonPersonId == husstandsmedlem.ident }?.let {
        map { boforhold ->
            boforhold.tilPeriode(husstandsmedlem)
        }.toMutableSet()
    } ?: setOf()

fun List<Bostatus>.tilPerioder(husstandsmedlem: Husstandsmedlem) =
    this.map {
        Bostatusperiode(
            husstandsmedlem = husstandsmedlem,
            bostatus = it.bostatus!!,
            datoFom = it.periodeFom,
            datoTom = it.periodeTom,
            kilde = it.kilde,
        )
    }

fun BoforholdResponse.tilPeriode(husstandsmedlem: Husstandsmedlem) =
    Bostatusperiode(
        bostatus = bostatus,
        datoFom = periodeFom,
        datoTom = periodeTom,
        kilde = kilde,
        husstandsmedlem = husstandsmedlem,
    )

fun List<BoforholdResponse>.tilHusstandsmedlem(behandling: Behandling): Set<Husstandsmedlem> =
    this
        .groupBy { it.relatertPersonPersonId }
        .map {
            val fødselsdatoFraRespons = it.value.first().fødselsdato
            val husstandsmedlem =
                Husstandsmedlem(
                    behandling = behandling,
                    kilde = it.value.first().kilde,
                    ident = it.key,
                    fødselsdato = finnFødselsdato(it.key, fødselsdatoFraRespons) ?: fødselsdatoFraRespons,
                )
            husstandsmedlem.overskriveMedBearbeidaPerioder(it.value)
            husstandsmedlem
        }.toSet()

fun Husstandsmedlem.overskriveMedBearbeidaPerioder(nyePerioder: List<BoforholdResponse>) {
    perioder.clear()
    perioder.addAll(nyePerioder.tilPerioder(this))
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    }
}

fun Husstandsmedlem.overskriveAndreVoksneIHusstandMedBearbeidaPerioder(nyePerioder: List<Bostatus>) {
    perioder.clear()
    perioder.addAll(nyePerioder.tilPerioder(this))
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForAndreVoksneIHusstand())
    }
}

fun Husstandsmedlem.overskriveMedBearbeidaBostatusperioder(nyePerioder: List<Bostatus>) {
    perioder.clear()
    perioder.addAll(nyePerioder.tilPerioder(this))
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    }
}

fun Husstandsmedlem.opprettDefaultPeriodeForOffentligHusstandsmedlem() =
    Bostatusperiode(
        husstandsmedlem = this,
        datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato ?: rolle!!.fødselsdato),
        datoTom = null,
        bostatus = Bostatuskode.IKKE_MED_FORELDER,
        kilde = Kilde.OFFENTLIG,
    )
