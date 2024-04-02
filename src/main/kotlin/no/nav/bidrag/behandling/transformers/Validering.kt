package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.database.datamodell.valider
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.HusstandsbarnOverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.OverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandOverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.YtelseInntektValideringsfeil
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
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

fun Behandling.validerInntekterForBeregning(type: Inntektsrapportering? = null): List<String> {
    val feilListe = mutableListOf<String>()
    if (type == null) {
        val inntektValideringsfeil = inntekter.mapValideringsfeilForÅrsinntekter(virkningstidspunktEllerSøktFomDato, roller)
        inntektValideringsfeil.forEach { valideringsfeil ->
            valideringsfeil.hullIPerioder.forEach {
                if (it.til != null) {
                    feilListe.add("Det er et hull i perioden ${it.fom} - ${it.til} for ident ${valideringsfeil.ident}")
                }
            }

            valideringsfeil.manglerPerioder.ifTrue {
                feilListe.add("Mangler perioder for ident ${valideringsfeil.ident}")
            }

            valideringsfeil.ingenLøpendePeriode.ifTrue {
                feilListe.add(
                    "Det er ingen løpende inntektsperiode for ident ${valideringsfeil.ident}",
                )
            }
        }
        feilListe.addAll(inntektValideringsfeil.validerInntekterFelles(type))
    } else if (inntekstrapporteringerSomKreverGjelderBarn.contains(type)) {
        val inntektValideringsfeil = inntekter.mapValideringsfeilForYtelseSomGjelderBarn(type, virkningstidspunktEllerSøktFomDato)
        feilListe.addAll(inntektValideringsfeil.validerInntekterFelles(type))
    } else {
        inntekter.mapValideringsfeilForYtelse(type, virkningstidspunktEllerSøktFomDato)?.let {
            feilListe.addAll(setOf(it).validerInntekterFelles(type))
        }
    }
    return feilListe
}

fun Set<InntektValideringsfeil>.validerInntekterFelles(type: Inntektsrapportering? = null): List<String> {
    val feilListe = mutableListOf<String>()

    forEach { valideringsfeil ->
        val gjelderBarn = if (valideringsfeil is YtelseInntektValideringsfeil) valideringsfeil.gjelderBarn else null
        valideringsfeil.overlappendePerioder.forEach {
            feilListe.add(
                "Det er en overlappende periode fra ${it.periode.fom} til ${it.periode.til} for ident ${valideringsfeil.ident}" +
                    "${type?.let { " og type $it" }}${gjelderBarn?.let { " og gjelder barn $it" }}",
            )
        }

        valider(!valideringsfeil.fremtidigPeriode) {
            feilListe.add(
                "Det er periodisert fremover i tid for inntekt som gjelder ident ${valideringsfeil.ident}" +
                    "${type?.let { " og type $it" }}${gjelderBarn?.let { " og gjelder barn $it" }}",
            )
        }
    }

    return feilListe
}

fun Set<Husstandsbarn>.validerBoforholdForBeregning(virkniningstidspunkt: LocalDate): List<String> {
    val feilListe = mutableListOf<String>()
    validerBoforhold(virkniningstidspunkt).forEach {
        val identifikator = "${it.husstandsbarn?.hentNavn()}/${it.husstandsbarn?.foedselsdato?.toDDMMYYYY()}"
        valider(!it.fremtidigPeriode) {
            feilListe.add(
                "Det er periodisert fremover i tid for husstandsbarn $identifikator",
            )
        }
        valider(!it.ingenLøpendePeriode) {
            feilListe.add(
                "Det er ingen løpende periode for husstandsbarn $identifikator",
            )
        }
        valider(!it.manglerPerioder) {
            feilListe.add(
                "Mangler perioder for husstandsbarn $identifikator",
            )
        }

        it.hullIPerioder.forEach {
            if (it.til != null) {
                feilListe.add(
                    "Det er et hull i perioden ${it.fom} - ${it.til} for husstandsbarn $identifikator",
                )
            }
        }

        it.overlappendePerioder.forEach {
            feilListe.add(
                "Det er en overlappende periode fra ${it.periode.fom} til ${it.periode.til}",
            )
        }
    }
    return feilListe
}

fun Set<Sivilstand>.validerSivilstandForBeregning(virkniningstidspunkt: LocalDate): List<String> {
    val feilListe = mutableListOf<String>()
    val it = validerSivilstand(virkniningstidspunkt)
    valider(!it.fremtidigPeriode) {
        feilListe.add(
            "Det er periodisert fremover i tid for sivilstand",
        )
    }
    valider(!it.ingenLøpendePeriode) {
        feilListe.add(
            "Det er ingen løpende periode for sivilstand",
        )
    }
    valider(!it.manglerPerioder) {
        feilListe.add(
            "Mangler perioder for sivilstand",
        )
    }

    it.hullIPerioder.forEach {
        if (it.til != null) {
            feilListe.add(
                "Det er et hull i perioden ${it.fom} - ${it.til} for sivilstand",
            )
        }
    }
    it.overlappendePerioder.forEach {
        feilListe.add(
            "Det er en overlappende periode fra ${it.periode.fom} til ${it.periode.til}",
        )
    }
    return feilListe
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

    return overlappendePerioder.toSet()
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
