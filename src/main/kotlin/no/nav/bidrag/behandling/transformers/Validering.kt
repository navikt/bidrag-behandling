package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.HusstandsbarnOverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.OverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandOverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandPeriodeseringsfeil
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.tid.Datoperiode
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val inntekstrapporteringerSomKreverInnteksttype = listOf(Inntektsrapportering.BARNETILLEGG)

fun OppdaterVirkningstidspunkt.valider(behandling: Behandling) {
    val feilListe = mutableListOf<String>()
    if (behandling.opprinneligVirkningstidspunkt != null &&
        avslag == null &&
        virkningstidspunkt?.isAfter(behandling.opprinneligVirkningstidspunkt) == true
    ) {
        feilListe.add("Virkningstidspunkt kan ikke være senere enn opprinnelig virkningstidspunkt")
    }

    if (feilListe.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av behandling: ${feilListe.joinToString(", ")}",
        )
    }
}

fun Set<Husstandsbarn>.validerBoforhold(virkniningstidspunkt: LocalDate): Set<BoforholdPeriodeseringsfeil> {
    val valideringsfeil = mutableListOf<BoforholdPeriodeseringsfeil>()

    groupBy { it.ident }.forEach {
        val husstandsbarn = it.value.first()
        val kanIkkeVæreSenereEnnDato =
            if (virkniningstidspunkt.isAfter(LocalDate.now())) {
                maxOf(husstandsbarn.foedselsdato, virkniningstidspunkt.withDayOfMonth(1))
            } else {
                LocalDate.now().withDayOfMonth(1)
            }
        val hullIPerioder =
            husstandsbarn.perioder.map {
                Datoperiode(it.datoFom!!, it.datoTom)
            }.finnHullIPerioder(maxOf(virkniningstidspunkt, husstandsbarn.foedselsdato))
        valideringsfeil.add(
            BoforholdPeriodeseringsfeil(
                husstandsbarn,
                hullIPerioder,
                overlappendePerioder = husstandsbarn.perioder.finnHusstandsbarnOverlappendePerioder(),
                manglerPerioder = husstandsbarn.perioder.isEmpty(),
                fremtidigPeriode = husstandsbarn.perioder.any { it.datoFom!!.isAfter(kanIkkeVæreSenereEnnDato) },
            ),
        )
    }

    return valideringsfeil.toSet()
}

fun Set<Sivilstand>.validerSivilstand(virkniningstidspunkt: LocalDate): SivilstandPeriodeseringsfeil {
    return SivilstandPeriodeseringsfeil(
        map { Datoperiode(it.datoFom!!, it.datoTom) }.finnHullIPerioder(virkniningstidspunkt),
        fremtidigPeriode = any { it.datoFom!!.isAfter(LocalDate.now().withDayOfMonth(1)) },
        manglerPerioder = isEmpty(),
        overlappendePerioder = finnSivilstandOverlappendePerioder(),
    )
}

private fun Set<Sivilstand>.finnSivilstandOverlappendePerioder() =
    sortedBy { it.datoFom }.flatMapIndexed { index, sivilstand ->
        sortedBy { it.datoFom }.drop(index + 1).mapNotNull { nesteSivilstand ->
            if (sivilstand.tilDatoperiode().overlapper(nesteSivilstand.tilDatoperiode())) {
                SivilstandOverlappendePeriode(
                    Datoperiode(
                        maxOf(sivilstand.datoFom!!, nesteSivilstand.datoFom!!),
                        minOfNullable(sivilstand.datoTom, nesteSivilstand.datoTom),
                    ),
                    setOf(sivilstand.sivilstand, nesteSivilstand.sivilstand),
                )
            } else {
                null
            }
        }
    }

private fun Set<Husstandsbarnperiode>.finnHusstandsbarnOverlappendePerioder() =
    sortedBy { it.datoFom }.flatMapIndexed { index, husstandsbarnPeriode ->
        sortedBy { it.datoFom }.drop(index + 1).filter { nesteHusstandsperiode ->
            nesteHusstandsperiode.tilDatoperiode().overlapper(husstandsbarnPeriode.tilDatoperiode())
        }
            .map { nesteHusstandsperiode ->
                HusstandsbarnOverlappendePeriode(
                    Datoperiode(
                        maxOf(husstandsbarnPeriode.datoFom!!, nesteHusstandsperiode.datoFom!!),
                        minOfNullable(husstandsbarnPeriode.datoTom, nesteHusstandsperiode.datoTom),
                    ),
                    setOf(husstandsbarnPeriode.bostatus, nesteHusstandsperiode.bostatus),
                )
            }
    }

fun List<Datoperiode>.finnHullIPerioder(virkniningstidspunkt: LocalDate): List<Datoperiode> {
    val hullPerioder = mutableListOf<Datoperiode>()
    val perioderSomSkalSjekkes = sortedBy { it.fom }
    val førstePeriode = perioderSomSkalSjekkes.firstOrNull()
    if (førstePeriode != null && virkniningstidspunkt.isBefore(førstePeriode.fom)) {
        hullPerioder.add(Datoperiode(virkniningstidspunkt, førstePeriode.fom))
    }
    var senesteTilPeriode = førstePeriode?.til ?: LocalDate.MAX
    perioderSomSkalSjekkes.forEachIndexed { index, periode ->
        val forrigePeriode = perioderSomSkalSjekkes.getOrNull(index - 1)
        if (forrigePeriode?.til != null &&
            periode.fom.isAfter(forrigePeriode.til!!.plusDays(1)) &&
            periode.fom.isAfter(senesteTilPeriode.plusDays(1)) &&
            virkniningstidspunkt.isBefore(periode.fom)
        ) {
            hullPerioder.add(Datoperiode(forrigePeriode.til!!.plusDays(1), periode.fom))
        } else if (periode.til == null) {
            // Vil ikke være noe hull i perioder videre pga at neste periode er løpende
            return hullPerioder
        }
        senesteTilPeriode = maxOf(senesteTilPeriode, periode.til ?: LocalDate.MAX)
    }
    if (perioderSomSkalSjekkes.none { it.til == null }) {
        val sistePeriode = perioderSomSkalSjekkes.lastOrNull()
        if (sistePeriode?.til != null) {
            hullPerioder.add(Datoperiode(sistePeriode.til!!, null as LocalDate?))
        }
    }
    return hullPerioder
}

fun List<Inntekt>.finnOverlappendePerioder(): Set<OverlappendePeriode> {
    val inntekterSomSkalSjekkes = filter { it.taMed }.sortedBy { it.datoFom }
    val overlappendePerioder =
        inntekterSomSkalSjekkes.flatMapIndexed { index, inntekt ->
            inntekterSomSkalSjekkes.drop(index + 1).mapIndexedNotNull { indexNesteInntekt, nesteInntekt ->
                inntekt.validerOverlapperMedInntekt(nesteInntekt)
            }
        }

    return overlappendePerioder.toSet().mergePerioder()
}

fun finnSenesteDato(
    dato1: LocalDate?,
    dato2: LocalDate?,
): LocalDate? {
    return if (dato1 == null || dato2 == null) {
        null
    } else {
        maxOf(
            dato1,
            dato2,
        )
    }
}

fun finnSenesteDato(
    dato1: LocalDate?,
    dato2: LocalDate?,
    dato3: LocalDate?,
): LocalDate? {
    return if (dato1 == null || dato2 == null || dato3 == null) {
        null
    } else {
        maxOf(
            dato1,
            dato2,
            dato3,
        )
    }
}

@JvmName("finnHullIPerioderInntekt")
fun List<Inntekt>.finnHullIPerioder(virkniningstidspunkt: LocalDate): List<Datoperiode> {
    val perioderSomSkalSjekkes =
        filter { it.taMed && !it.kanHaHullIPerioder() }.sortedBy { it.datoFom }
            .map { Datoperiode(it.datoFom, it.datoTom) }
    return perioderSomSkalSjekkes.finnHullIPerioder(virkniningstidspunkt)
}

val Inntekt.inntektstypeListe
    get() =
        if (type == Inntektsrapportering.BARNETILLEGG) {
            inntektsposter.mapNotNull {
                it.inntektstype
            }
        } else if (kilde == Kilde.MANUELL || inntektsposter.isEmpty()) {
            type.inneholderInntektstypeListe
        } else {
            inntektsposter.mapNotNull {
                it.inntektstype
            }.ifEmpty { type.inneholderInntektstypeListe }
        }

fun Inntekt.validerOverlapperMedInntekt(sammenlignMedInntekt: Inntekt): OverlappendePeriode? {
    val inntektsposterSomOverlapper = inntektstypeListe.filter { sammenlignMedInntekt.inntektstypeListe.contains(it) }
    val kanOverlappe = inntektsposterSomOverlapper.isEmpty()
    val inntektPeriode = Datoperiode(datoFom, datoTom)
    val sammenlignMedInntektPeriode = Datoperiode(sammenlignMedInntekt.datoFom, sammenlignMedInntekt.datoTom)
    val perioderOverlapper = inntektPeriode.overlapper(sammenlignMedInntektPeriode) && !kanOverlappe
    if (perioderOverlapper) {
        val datoFom = maxOf(sammenlignMedInntekt.datoFom, datoFom)
        val senesteDatoTom = minOfNullable(datoTom, sammenlignMedInntekt.datoTom)
        return OverlappendePeriode(
            Datoperiode(datoFom, senesteDatoTom),
            mutableSetOf(id!!, sammenlignMedInntekt.id!!),
            mutableSetOf(type, sammenlignMedInntekt.type),
            inntektsposterSomOverlapper.toMutableSet(),
        )
    }
    return null
}

private fun Inntekt.kanHaHullIPerioder() = inntekterSomKanHaHullIPerioder.contains(this.type)

fun OppdatereInntekterRequestV2.valider() {
    val feilListe = mutableListOf<String>()
    oppdatereManuelleInntekter.forEach { oppdaterInntekt ->
        if (inntekstrapporteringerSomKreverGjelderBarn.contains(oppdaterInntekt.type)) {
            oppdaterInntekt.validerHarGjelderBarn(
                feilListe,
            )
        }
        if (inntekstrapporteringerSomKreverInnteksttype.contains(oppdaterInntekt.type)) {
            oppdaterInntekt.validerHarInnteksttype(
                feilListe,
            )
        }
    }

    if (feilListe.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av inntekter: ${feilListe.joinToString(", ")}",
        )
    }
}

fun OppdatereInntektRequest.valider() {
    val feilListe = mutableListOf<String>()
    if (inntekstrapporteringerSomKreverGjelderBarn.contains(this.oppdatereManuellInntekt?.type)) {
        this.oppdatereManuellInntekt?.validerHarGjelderBarn(feilListe)
    }
    if (inntekstrapporteringerSomKreverInnteksttype.contains(this.oppdatereManuellInntekt?.type)) {
        this.oppdatereManuellInntekt?.validerHarInnteksttype(feilListe)
    }

    if (feilListe.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av inntekter: ${feilListe.joinToString(", ")}",
        )
    }
}

fun OppdatereManuellInntekt.validerHarGjelderBarn(feilListe: MutableList<String>) {
    if (gjelderBarn == null || gjelderBarn.verdi.isEmpty()) {
        feilListe.add("$type må ha gyldig ident for gjelder barn")
    }
}

fun OppdatereManuellInntekt.validerHarInnteksttype(feilListe: MutableList<String>) {
    if (inntektstype == null) {
        feilListe.add("Barnetillegg må ha gyldig inntektstype")
    }
}

fun Sivilstand.tilDatoperiode() = Datoperiode(datoFom!!, datoTom)

fun Husstandsbarnperiode.tilDatoperiode() = Datoperiode(datoFom!!, datoTom)

fun Set<OverlappendePeriode>.mergePerioder(): Set<OverlappendePeriode> {
    val sammenstiltePerioder = mutableListOf<OverlappendePeriode>()
    val sortertePerioder = sortedBy { it.periode.fom }
    sortertePerioder.forEachIndexed { index, overlappendePeriode ->
        val annenOverlappendePeriode =
            sortertePerioder.drop(index + 1).find {
                it.periode.overlapper(overlappendePeriode.periode) &&
                    it.rapporteringTyper.any {
                        overlappendePeriode.rapporteringTyper.contains(
                            it,
                        )
                    }
            }

        val sammenstiltePerioderSomInneholderOverlappende =
            sammenstiltePerioder.find {
                it.idListe.containsAll(
                    overlappendePeriode.idListe,
                )
            }
        if (annenOverlappendePeriode != null) {
            if (sammenstiltePerioderSomInneholderOverlappende != null) {
                sammenstiltePerioder.remove(sammenstiltePerioderSomInneholderOverlappende)
                sammenstiltePerioder.add(
                    sammenstiltePerioderSomInneholderOverlappende.copy(
                        periode =
                            Datoperiode(
                                minOf(
                                    annenOverlappendePeriode.periode.fom,
                                    overlappendePeriode.periode.fom,
                                    sammenstiltePerioderSomInneholderOverlappende.periode.fom,
                                ),
                                finnSenesteDato(
                                    annenOverlappendePeriode.periode.til,
                                    overlappendePeriode.periode.til,
                                    sammenstiltePerioderSomInneholderOverlappende.periode.til,
                                ),
                            ),
                    ),
                )
            } else {
                sammenstiltePerioder.add(
                    annenOverlappendePeriode.copy(
                        periode =
                            Datoperiode(
                                minOf(annenOverlappendePeriode.periode.fom, overlappendePeriode.periode.fom),
                                finnSenesteDato(annenOverlappendePeriode.periode.til, overlappendePeriode.periode.til),
                            ),
                        rapporteringTyper =
                            (annenOverlappendePeriode.rapporteringTyper + overlappendePeriode.rapporteringTyper).sorted()
                                .toMutableSet(),
                        idListe = (annenOverlappendePeriode.idListe + overlappendePeriode.idListe).sorted().toMutableSet(),
                        inntektstyper =
                            (annenOverlappendePeriode.inntektstyper + overlappendePeriode.inntektstyper).sorted()
                                .toMutableSet(),
                    ),
                )
            }
        } else if (sammenstiltePerioderSomInneholderOverlappende == null) {
            sammenstiltePerioder.add(overlappendePeriode)
        }
    }
    return sammenstiltePerioder.toSet()
}
