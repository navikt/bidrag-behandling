package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.tid.Datoperiode
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val inntekstrapporteringerSomKreverGjelderBarn =
    listOf(Inntektsrapportering.BARNETILLEGG, Inntektsrapportering.KONTANTSTØTTE, Inntektsrapportering.BARNETILSYN)
private val inntekstrapporteringerSomKreverInnteksttype = listOf(Inntektsrapportering.BARNETILLEGG)

data class OverlappendePeriode(
    val periode: Datoperiode,
    val idListe: MutableSet<Long>,
    val rapporteringTyper: MutableSet<Inntektsrapportering>,
    val inntektstyper: MutableSet<Inntektstype>,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is OverlappendePeriode) return false
        return periode == other.periode && idListe == other.idListe && rapporteringTyper == other.rapporteringTyper &&
            (inntektstyper + other.inntektstyper).size == inntektstyper.size
    }

    override fun hashCode(): Int {
        return periode.hashCode() + idListe.hashCode() + rapporteringTyper.hashCode()
    }
}

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

fun Behandling.validerInntekterForBeregning(type: Inntektsrapportering? = null): List<String> {
    val feilListe = mutableListOf<String>()

    val inntekterTaMed =
        inntekter.filter {
            it.taMed && if (type == null) !eksplisitteYtelser.contains(it.type) else it.type == type
        }.sortedByDescending { it.datoFom }
    val hullPerioder = inntekterTaMed.finnHullIPerioder(virkningstidspunktEllerSøktFomDato)
    val overlappendePerioder = inntekterTaMed.finnOverlappendePerioder()

    hullPerioder.forEach {
        if (it.til == null) {
            feilListe.add("Det er ingen løpende inntektsperiode. Rediger en av periodene eller legg til en ny periode.")
        } else {
            feilListe.add("Det er et hull i perioden ${it.fom} - ${it.til}")
        }
    }

    overlappendePerioder.forEach {
        feilListe.add(
            "Det er en overlappende periode fra ${it.periode.fom} til ${it.periode.til}",
        )
    }

    return feilListe
}

fun List<Inntekt>.finnOverlappendePerioder(): Set<OverlappendePeriode> {
    val inntekterSomSkalSjekkes = filter { it.taMed }.sortedBy { it.datoFom }
    val overlappendePerioder =
        inntekterSomSkalSjekkes.flatMapIndexed { index, inntekt ->
            inntekterSomSkalSjekkes.drop(index + 1).mapIndexedNotNull { indexNesteInntekt, nesteInntekt ->
                inntekt.validerOverlapperMedInntekt(nesteInntekt)
            }
        }

    return overlappendePerioder.toSet()
}

fun Set<OverlappendePeriode>.mergePerioder(): Set<OverlappendePeriode> {
    val sammenstiltePerioder = mutableListOf<OverlappendePeriode>()
    forEachIndexed { index, overlappendePeriode ->
        val eksisterende =
            drop(index + 1).find {
                it.periode.overlapper(overlappendePeriode.periode) &&
                    it.rapporteringTyper.any {
                        overlappendePeriode.rapporteringTyper.contains(
                            it,
                        )
                    }
            }

        if (eksisterende != null) {
            sammenstiltePerioder.add(
                eksisterende.copy(
                    periode =
                        Datoperiode(
                            minOf(eksisterende.periode.fom, overlappendePeriode.periode.fom),
                            finnSenesteDato(eksisterende.periode.til, overlappendePeriode.periode.til),
                        ),
                    rapporteringTyper = (eksisterende.rapporteringTyper + overlappendePeriode.rapporteringTyper).sorted().toMutableSet(),
                    idListe = (eksisterende.idListe + overlappendePeriode.idListe).sorted().toMutableSet(),
                    inntektstyper = (eksisterende.inntektstyper + overlappendePeriode.inntektstyper).sorted().toMutableSet(),
                ),
            )
        } else if (sammenstiltePerioder.none { it.idListe.containsAll(overlappendePeriode.idListe) }) {
            sammenstiltePerioder.add(overlappendePeriode)
        }
    }
    return sammenstiltePerioder.toSet()
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

fun List<Inntekt>.finnHullIPerioder(virkniningstidspunkt: LocalDate): List<Datoperiode> {
    val hullPerioder = mutableListOf<Datoperiode>()
    val inntekterSomSkalSjekkes = filter { it.taMed && !it.kanHaHullIPerioder() }.sortedBy { it.datoFom }
    val førstePeriode = inntekterSomSkalSjekkes.firstOrNull()
    if (førstePeriode != null && virkniningstidspunkt.isBefore(førstePeriode.datoFom)) {
        hullPerioder.add(Datoperiode(virkniningstidspunkt, førstePeriode.datoFom))
    }
    if (size > 1) {
        inntekterSomSkalSjekkes.forEachIndexed { index, inntekt ->
            inntekterSomSkalSjekkes.drop(index + 1).forEachIndexed { indexNesteInntekt, nesteInntekt ->
                if (inntekt.datoTom != null && nesteInntekt.datoFom.isAfter(inntekt.datoTom!!.plusDays(1))) {
                    hullPerioder.add(Datoperiode(inntekt.datoTom!!.plusDays(1), nesteInntekt.datoFom))
                } else if (nesteInntekt.datoTom == null) {
                    // Vil ikke være noe hull i perioder videre pga at neste inntekt er løpende
                    return hullPerioder
                }
            }
        }
    }
    if (inntekterSomSkalSjekkes.none { it.datoTom == null }) {
        val sistePeriode = inntekterSomSkalSjekkes.lastOrNull()
        if (sistePeriode?.datoTom != null) {
            hullPerioder.add(Datoperiode(sistePeriode.datoTom!!, null as LocalDate?))
        }
    }
    return hullPerioder
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
        val datoFom = minOf(sammenlignMedInntekt.datoFom, datoFom)
        val senesteDatoTom =
            if (sammenlignMedInntekt.datoTom == null || datoTom == null) {
                null
            } else {
                maxOf(
                    datoTom!!,
                    sammenlignMedInntekt.datoTom!!,
                )
            }
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
