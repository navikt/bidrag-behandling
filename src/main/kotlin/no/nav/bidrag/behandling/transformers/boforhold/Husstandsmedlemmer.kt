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
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.service.BoforholdService.Companion.opprettDefaultPeriodeForAndreVoksneIHusstand
import no.nav.bidrag.behandling.transformers.erSærbidrag
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.behandling.transformers.tilTypeBoforhold
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequestV3
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.BoforholdVoksneRequest
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.boforhold.dto.Husstandsmedlemmer
import no.nav.bidrag.boforhold.utils.justerBoforholdPerioderForOpphørsdatoOgBeregnTilDato
import no.nav.bidrag.boforhold.utils.justerBostatusPerioderForOpphørsdatoOgBeregnTilDato
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.diverse.TypeEndring
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
                    true -> {
                        g.borISammeHusstandDtoListe.tilBostatus(
                            Bostatuskode.MED_FORELDER,
                            Kilde.OFFENTLIG,
                        )
                    }

                    false -> {
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
                    }
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
    behandledeBostatusopplysninger =
        perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
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

            else -> {
                null
            }
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
    if (this.find { it.gjelderPersonId == husstandsmedlem.ident } != null) {
        map { boforhold ->
            boforhold.tilPeriode(husstandsmedlem)
        }.toMutableSet()
    } else {
        setOf()
    }

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
                    rolle = behandling.søknadsbarn.find { sb -> sb.ident == it.key },
                    fødselsdato = finnFødselsdato(it.key, fødselsdatoFraRespons) ?: fødselsdatoFraRespons,
                )
            // TODO: Hva skjer hvis søknadsbarn finnes to ganger (18 år og ordinær bidrag)?
            husstandsmedlem.overskriveMedBearbeidaPerioder(it.value)
            husstandsmedlem
        }.toSet()

private fun bestemmeNyBostatus(nyEllerOppdatertBostatusperiode: Bostatusperiode? = null): Bostatus? =
    nyEllerOppdatertBostatusperiode?.let {
        Bostatus(
            periodeFom = it.datoFom,
            periodeTom = it.datoTom,
            bostatus = it.bostatus,
            kilde = it.kilde,
        )
    }

private fun bestemmeEndringstype(
    nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
    sletteHusstandsmedlemsperiode: Long? = null,
): TypeEndring {
    nyEllerOppdatertBostatusperiode?.let {
        if (it.id != null) {
            return TypeEndring.ENDRET
        }
        return TypeEndring.NY
    }

    if (sletteHusstandsmedlemsperiode != null) {
        return TypeEndring.SLETTET
    }

    throw IllegalArgumentException(
        "Mangler data til å avgjøre endringstype. Motttok input: nyEllerOppdatertHusstandsmedlemperiode: " +
            "$nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemperiode: $sletteHusstandsmedlemsperiode",
    )
}

fun Husstandsmedlem.overskriveMedBearbeidaPerioder(nyePerioder: List<BoforholdResponseV2>) {
    perioder.clear()
    perioder.addAll(
        nyePerioder
            .justerBoforholdPerioderForOpphørsdatoOgBeregnTilDato(
                null, // rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                behandling.finnBeregnTilDatoBehandling(rolle),
            ).tilPerioder(this),
    )
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    }
}

private fun Husstandsmedlem.bestemmeOriginalBostatus(
    nyBostatusperiode: Bostatusperiode? = null,
    sletteHusstansmedlemsperiode: Long? = null,
): Bostatus? {
    nyBostatusperiode?.id?.let {
        return perioder.find { nyBostatusperiode.id == it.id }?.tilBostatus()
    }
    sletteHusstansmedlemsperiode.let { id -> return perioder.find { id == it.id }?.tilBostatus() }
}

private fun Husstandsmedlem.tilEndreBostatus(
    nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
    sletteHusstandsmedlemsperiode: Long? = null,
): EndreBostatus? {
    try {
        if (nyEllerOppdatertBostatusperiode == null && sletteHusstandsmedlemsperiode == null) {
            return null
        }

        return EndreBostatus(
            typeEndring = bestemmeEndringstype(nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemsperiode),
            nyBostatus = bestemmeNyBostatus(nyEllerOppdatertBostatusperiode),
            originalBostatus =
                bestemmeOriginalBostatus(
                    nyEllerOppdatertBostatusperiode,
                    sletteHusstandsmedlemsperiode,
                ),
        )
    } catch (_: IllegalArgumentException) {
        log.warn {
            "Mottok mangelfulle opplysninger ved oppdatering av boforhold i behandling ${this.behandling.id}. " +
                "Mottatt input: nyEllerOppdatertHusstandsmedlemsperiode=$nyEllerOppdatertBostatusperiode, " +
                "sletteHusstansmedlemsperiode=$sletteHusstandsmedlemsperiode"
        }
        oppdateringAvBoforholdFeilet(
            "Oppdatering av boforhold i behandling ${this.behandling.id} feilet pga mangelfulle inputdata",
        )
    }
}

fun Husstandsmedlem.oppdaterePerioderVoksne(
    gjelderRolle: Rolle,
    nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
    sletteHusstandsmedlemsperiode: Long? = null,
) {
    val endreBostatus = tilEndreBostatus(nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemsperiode)
    val periodiseringsrequest = tilBoforholdVoksneRequest(gjelderRolle, endreBostatus)

    val borMedAndreVoksneperioder =
        BoforholdApi.beregnBoforholdAndreVoksne(
            behandling.eldsteVirkningstidspunkt,
            periodiseringsrequest,
            behandling.globalOpphørsdato,
            behandling.finnBeregnTilDatoBehandling(),
        )

    this.overskriveMedBearbeidaBostatusperioder(borMedAndreVoksneperioder)
}

fun Husstandsmedlem.oppdaterePerioder(
    nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
    sletteHusstandsmedlemsperiode: Long? = null,
) {
    val endreBostatus = tilEndreBostatus(nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemsperiode)

    val periodiseringsrequest = tilBoforholdBarnRequest(endreBostatus)

    try {
        this.overskriveMedBearbeidaPerioder(
            BoforholdApi.beregnBoforholdBarnV3(
                behandling.eldsteVirkningstidspunkt,
                null,
//            rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                behandling.finnBeregnTilDatoBehandling(rolle),
                behandling.tilTypeBoforhold(),
                listOf(periodiseringsrequest),
            ),
        )
    } catch (_: NullPointerException) {
        // Noen caser hvor det er skjedd en feil i periodiseringen pga ugydlig data eller bug. Ruller tilbake til offentlige opplysninger
        this.overskriveMedBearbeidaPerioder(
            BoforholdApi.beregnBoforholdBarnV3(
                behandling.eldsteVirkningstidspunkt,
                null,
//            rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                behandling.finnBeregnTilDatoBehandling(rolle),
                behandling.tilTypeBoforhold(),
                listOf(
                    periodiseringsrequest.copy(
                        behandledeBostatusopplysninger = emptyList(),
                    ),
                ),
            ),
        )
    }
}

fun Husstandsmedlem.overskriveAndreVoksneIHusstandMedBearbeidaPerioder(nyePerioder: List<Bostatus>) {
    perioder.clear()
    perioder.addAll(
        nyePerioder
            .justerBostatusPerioderForOpphørsdatoOgBeregnTilDato(
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

fun Husstandsmedlem.opprettDefaultPeriodeForOffentligHusstandsmedlem(): Bostatusperiode {
    val datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato ?: rolle!!.fødselsdato).withDayOfMonth(1)
    return Bostatusperiode(
        husstandsmedlem = this,
        datoFom = datoFom,
        datoTom =
            (rolle?.opphørTilDato ?: behandling.opphørTilDato)?.takeIf {
                it.isAfter(datoFom)
            },
        bostatus = Bostatuskode.IKKE_MED_FORELDER,
        kilde = Kilde.OFFENTLIG,
    )
}
