package no.nav.bidrag.behandling.transformers.boforhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.database.datamodell.henteGjeldendeBoforholdsgrunnlagForAndreVoksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.boforhold.BostatusperiodeDto
import no.nav.bidrag.behandling.service.BoforholdService.Companion.opprettDefaultPeriodeForAndreVoksneIHusstand
import no.nav.bidrag.behandling.transformers.erSærbidrag
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequestV3
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.BoforholdVoksneRequest
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.boforhold.dto.Husstandsmedlemmer
import no.nav.bidrag.boforhold.utils.justerBoforholdPerioderForOpphørsdato
import no.nav.bidrag.boforhold.utils.justerBostatusPerioderForOpphørsdato
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import java.time.LocalDate

private val log = KotlinLogging.logger {}

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdBarnRequest(
    behandling: Behandling,
    leggeTilManglendeSøknadsbarn: Boolean = false,
) = this.toList().tilBoforholdBarnRequest(behandling, leggeTilManglendeSøknadsbarn)

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdVoksneRequest(behandling: Behandling): BoforholdVoksneRequest =
    BoforholdVoksneRequest(
        behandledeBostatusopplysninger = emptyList(),
        endreBostatus = null,
        innhentedeOffentligeOpplysninger =
            this
                .filter { !it.erBarn }
                .tilHusstandsmedlemmer()
                .filtrerUtUrelevantePerioder(behandling),
    )

fun List<RelatertPersonGrunnlagDto>.tilHusstandsmedlemmer() =
    this.map {
        Husstandsmedlemmer(
            gjelderPersonId = it.gjelderPersonId,
            fødselsdato = it.fødselsdato ?: LocalDate.parse("9999-12-01"),
            relasjon = it.relasjon,
            borISammeHusstandListe = it.borISammeHusstandDtoListe.tilBostatus(it.relasjon, it.fødselsdato ?: LocalDate.now()),
        )
    }

private fun Behandling.leggeInnManglendeSøknadsbarnSomHusstandsbarn(
    grunnlag: MutableList<RelatertPersonGrunnlagDto>,
): List<RelatertPersonGrunnlagDto> {
    this.søknadsbarn.forEach { søknadsbarn ->
        søknadsbarn.ident?.let { identSøknadsbarn ->
            if (!grunnlag.map { it.gjelderPersonId }.contains(identSøknadsbarn)) {
                grunnlag.add(
                    RelatertPersonGrunnlagDto(
                        partPersonId = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(this)?.ident,
                        gjelderPersonId = identSøknadsbarn,
                        borISammeHusstandDtoListe = emptyList(),
                        fødselsdato = søknadsbarn.fødselsdato,
                        navn = søknadsbarn.navn,
                        erBarnAvBmBp = true,
                    ),
                )
            }
        }
    }

    return grunnlag
}

fun List<RelatertPersonGrunnlagDto>.tilBoforholdBarnRequest(
    behandling: Behandling,
    leggeTilManglendeSøknadsbarn: Boolean = false,
): List<BoforholdBarnRequestV3> {
    val grunnlag: List<RelatertPersonGrunnlagDto> =
        when (leggeTilManglendeSøknadsbarn) {
            true -> behandling.leggeInnManglendeSøknadsbarnSomHusstandsbarn(this.toMutableList())
            false -> this
        }
    val barnAvBmBpManglerFødselsdato = grunnlag.filter { it.erBarn }.filter { it.fødselsdato == null }
    if (barnAvBmBpManglerFødselsdato.isNotEmpty()) {
        secureLogger.warn {
            "Husstandsmedlem som er barn av BM eller BP (personident forelder: ${barnAvBmBpManglerFødselsdato.first().partPersonId}) mangler fødselsdato."
        }
    }

    return grunnlag.filter { it.erBarn }.filter { it.fødselsdato != null }.map { g ->
        val barnetsRolle = behandling.roller.find { it.ident == g.gjelderPersonId }
        BoforholdBarnRequestV3(
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
                                periodeFom =
                                    maxOf(
                                        g.fødselsdato!!.plusMonths(1).withDayOfMonth(1),
                                        minOf(behandling.virkningstidspunktEllerSøktFomDato, LocalDate.now().withDayOfMonth(1)),
                                    ),
                                periodeTom = null,
                            ),
                        )
                },
            relasjon =
                if (behandling.husstandsmedlem.find {
                        it.ident != null && it.ident == g.gjelderPersonId
                    } != null
                ) {
                    Familierelasjon.BARN
                } else {
                    g.relasjon
                },
            fødselsdato = g.fødselsdato!!,
            gjelderPersonId = g.gjelderPersonId,
            behandledeBostatusopplysninger = emptyList(),
            endreBostatus = null,
            erSøknadsbarn = barnetsRolle?.rolletype == Rolletype.BARN,
        )
    }
}

fun Husstandsmedlem.tilBoforholdBarnRequest(endreBostatus: EndreBostatus? = null): BoforholdBarnRequestV3 =
    BoforholdBarnRequestV3(
        gjelderPersonId = ident,
        fødselsdato = fødselsdato ?: rolle!!.fødselsdato,
        relasjon = Familierelasjon.BARN,
        innhentedeOffentligeOpplysninger =
            henteOffentligePerioder().map { it.tilBostatus() }.sortedBy { it.periodeFom },
        behandledeBostatusopplysninger =
            perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
        endreBostatus = endreBostatus,
        erSøknadsbarn = erSøknadsbarn,
    )

fun Husstandsmedlem.tilBoforholdVoksneRequest(
    gjelderRolle: Rolle,
    endreBostatus: EndreBostatus? = null,
) = BoforholdVoksneRequest(
    innhentedeOffentligeOpplysninger =
        henteGjeldendeBoforholdsgrunnlagForAndreVoksneIHusstanden(
            gjelderRolle,
        ).tilHusstandsmedlemmer().filtrerUtUrelevantePerioder(behandling),
    behandledeBostatusopplysninger = perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
    endreBostatus = endreBostatus,
)

fun List<Husstandsmedlemmer>.filtrerUtUrelevantePerioder(behandling: Behandling): List<Husstandsmedlemmer> =
    this.map { hm ->
        if (behandling.erSærbidrag()) {
            hm
//            hm.copy(
//                borISammeHusstandListe =
//                    hm.borISammeHusstandListe.filter {
//                        it.periodeFom == null ||
//                            // Ønsker ikke å ta med perioder som kommer etter virkningstidspunkt i særbidrag pga at det gjelder bare for en måned. Det som kommer senere er ikke relevant.
//                            // Dette er relevant i klagesaker hvor virkningstidspunkt kan være tilbake i tid enn inneværende måned
//                            it.periodeFom!! <
//                            behandling.virkningstidspunktEllerSøktFomDato.plusMonths(1).withDayOfMonth(1)
//                    },
//            )
        } else {
            hm
        }
    }

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

fun List<BoforholdResponseV2>.tilPerioder(husstandsmedlem: Husstandsmedlem) =
    this.find { it.gjelderPersonId == husstandsmedlem.ident }?.let {
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

fun BoforholdResponseV2.tilPeriode(husstandsmedlem: Husstandsmedlem) =
    Bostatusperiode(
        bostatus = bostatus,
        datoFom = periodeFom,
        datoTom = periodeTom,
        kilde = kilde,
        husstandsmedlem = husstandsmedlem,
    )

fun List<BoforholdResponseV2>.tilHusstandsmedlem(behandling: Behandling): Set<Husstandsmedlem> =
    this
        .groupBy { it.gjelderPersonId }
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

fun Husstandsmedlem.overskriveMedBearbeidaPerioder(nyePerioder: List<BoforholdResponseV2>) {
    perioder.clear()
    perioder.addAll(
        nyePerioder
            .justerBoforholdPerioderForOpphørsdato(
                rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                behandling.finnBeregnTilDatoBehandling(rolle),
            ).tilPerioder(this),
    )
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    }
}

fun Husstandsmedlem.overskriveAndreVoksneIHusstandMedBearbeidaPerioder(nyePerioder: List<Bostatus>) {
    perioder.clear()
    perioder.addAll(
        nyePerioder
            .justerBostatusPerioderForOpphørsdato(
                behandling.globalOpphørsdato,
                behandling.finnBeregnTilDatoBehandling(),
            ).tilPerioder(this),
    )
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
        datoTom = rolle?.opphørTilDato ?: behandling.opphørTilDato,
        bostatus = Bostatuskode.IKKE_MED_FORELDER,
        kilde = Kilde.OFFENTLIG,
    )
