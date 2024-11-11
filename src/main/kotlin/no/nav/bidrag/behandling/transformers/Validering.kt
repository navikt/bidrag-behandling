package no.nav.bidrag.behandling.transformers

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.Ressurstype
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.finnBostatusperiode
import no.nav.bidrag.behandling.database.datamodell.henteAlleBostatusperioder
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.erSærbidrag
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereAndreVoksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.dto.v2.utgift.tilUtgiftstype
import no.nav.bidrag.behandling.dto.v2.validering.AndreVoksneIHusstandenPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.OverlappendeBostatusperiode
import no.nav.bidrag.behandling.dto.v2.validering.OverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandOverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandPeriodeseringsfeil
import no.nav.bidrag.behandling.finnesFraFørException
import no.nav.bidrag.behandling.husstandsmedlemIkkeFunnetException
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.requestManglerDataException
import no.nav.bidrag.behandling.ressursHarFeilKildeException
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.ressursIkkeTilknyttetBehandling
import no.nav.bidrag.behandling.transformers.utgift.kategorierSomKreverType
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

fun bestemRollerSomMåHaMinstEnInntekt(typeBehandling: TypeBehandling) =
    when (typeBehandling) {
        TypeBehandling.FORSKUDD -> listOf(Rolletype.BIDRAGSMOTTAKER)
        TypeBehandling.BIDRAG, TypeBehandling.SÆRBIDRAG -> listOf(Rolletype.BIDRAGSPLIKTIG, Rolletype.BIDRAGSMOTTAKER)
    }

fun bestemRollerSomKanHaInntekter(typeBehandling: TypeBehandling) =
    when (typeBehandling) {
        TypeBehandling.FORSKUDD -> listOf(Rolletype.BIDRAGSMOTTAKER)
        TypeBehandling.BIDRAG, TypeBehandling.SÆRBIDRAG -> listOf(Rolletype.BIDRAGSPLIKTIG, Rolletype.BIDRAGSMOTTAKER, Rolletype.BARN)
    }

private val inntekstrapporteringerSomKreverInnteksttype = listOf(Inntektsrapportering.BARNETILLEGG)
val Behandling.utgiftCuttofDato get() = mottattdato.minusYears(1)

fun Behandling.erDatoForUtgiftForeldet(utgiftDato: LocalDate) = utgiftDato < utgiftCuttofDato

fun Utgiftspost.erUtgiftForeldet() = utgift.behandling.erDatoForUtgiftForeldet(dato)

fun Behandling.validerKanOppdatere() {
    if (erVedtakFattet) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er fattet for behandling og kan derfor ikke oppdateres",
        )
    }
}

fun OpprettBehandlingRequest.valider() {
    val feilliste = mutableListOf<String>()
    (stønadstype == null && engangsbeløpstype == null).ifTrue {
        feilliste.add("Stønadstype eller engangsbeløpstype må settes")
    }
    if (erSærbidrag()) {
        when {
            roller.none { it.rolletype == Rolletype.BIDRAGSPLIKTIG } ->
                feilliste.add("Behandling av typen $engangsbeløpstype må ha en rolle av typen ${Rolletype.BIDRAGSPLIKTIG}")

            roller.none { it.rolletype == Rolletype.BIDRAGSMOTTAKER } ->
                feilliste.add("Behandling av typen $engangsbeløpstype må ha en rolle av typen ${Rolletype.BIDRAGSMOTTAKER}")

            kategori?.kategori.isNullOrEmpty() ->
                feilliste.add(
                    "Kategori må settes for ${Engangsbeløptype.SÆRBIDRAG}",
                )

            Særbidragskategori.entries.none { it.name == kategori?.kategori } ->
                feilliste.add(
                    "Kategori ${kategori?.kategori} er ikke en gyldig særbidrag kategori",
                )

            kategori?.kategori == Særbidragskategori.ANNET.name && kategori.beskrivelse.isNullOrEmpty() ->
                feilliste.add("Beskrivelse må settes hvis kategori er ${Særbidragskategori.ANNET}")
        }
    }
    roller
        .any { it.rolletype == Rolletype.BARN && (it.ident?.verdi.isNullOrBlank() && it.navn.isNullOrBlank()) }
        .ifTrue { feilliste.add("Barn må ha enten ident eller navn") }

    roller
        .any { it.rolletype != Rolletype.BARN && it.ident?.verdi.isNullOrBlank() }
        .ifTrue { feilliste.add("Voksne må ha ident") }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved opprettelse av behandling: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdatereUtgiftRequest.valider(behandling: Behandling) {
    val feilliste = mutableListOf<String>()
    if (Engangsbeløptype.SÆRBIDRAG != behandling.engangsbeloptype) {
        feilliste.add("Kan ikke oppdatere utgift for behandling som ikke er av typen ${Engangsbeløptype.SÆRBIDRAG}")
    }
    val erAvslag = (avslag != null || behandling.avslag != null)
    if (erAvslag && (nyEllerEndretUtgift != null || sletteUtgift != null)) {
        feilliste.add("Kan ikke oppdatere eller opprette utgift hvis avslag er satt")
    }
    val utgift = behandling.utgift
    if (sletteUtgift != null && (utgift == null || utgift.utgiftsposter.none { it.id == sletteUtgift })) {
        feilliste.add("Utgiftspost med id $sletteUtgift finnes ikke i behandling ${behandling.id}")
    }

    if (nyEllerEndretUtgift != null) {
        feilliste.addAll(nyEllerEndretUtgift.validerUtgiftspost(behandling))
    }

    if (maksGodkjentBeløp != null && utgift != null) {
        if (behandling.kategori != Særbidragskategori.KONFIRMASJON.name) {
            feilliste.add("Kan ikke sette maks godkjent beløp for behandling som ikke er av kategori ${Særbidragskategori.KONFIRMASJON}")
        }
        if (utgift.utgiftsposter.isEmpty()) {
            feilliste.add("Kan ikke sette maks godkjent beløp for behandling uten utgiftsposter")
        }

        val maksGodkjentBeløpVerdi = maksGodkjentBeløp.beløp ?: BigDecimal.ZERO
        if (maksGodkjentBeløpVerdi < BigDecimal.ZERO) {
            feilliste.add("Maks godkjent beløp kan ikke være negativ")
        }
    }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av utgift: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdatereUtgift.validerUtgiftspost(behandling: Behandling): List<String> {
    val feilliste = mutableListOf<String>()

    if (dato >= LocalDate.now()) {
        feilliste.add("Dato for utgift kan ikke være senere enn eller lik dagens dato")
    }
    if (godkjentBeløp != kravbeløp &&
        kommentar.isNullOrEmpty() &&
        !behandling.erDatoForUtgiftForeldet(
            dato,
        )
    ) {
        feilliste.add("Begrunnelse må settes hvis kravbeløp er ulik godkjent beløp")
    }
    if (!behandling.erDatoForUtgiftForeldet(dato) && kravbeløp <= BigDecimal.ZERO) {
        feilliste.add("Kravbeløp kan ikke være 0 eller negativt")
    }

    if (godkjentBeløp > kravbeløp) {
        feilliste.add("Godkjent beløp kan ikke være høyere enn kravbeløp")
    }

    if (behandling.erDatoForUtgiftForeldet(dato) && godkjentBeløp > BigDecimal.ZERO) {
        feilliste.add("Godkjent beløp må være 0 når dato på utgiften er 1 år etter mottatt dato (utgiften er foreldet)")
    }
    val utgift = behandling.utgift
    if (id != null && (utgift == null || utgift.utgiftsposter.none { it.id == id })) {
        feilliste.add("Utgiftspost med id $id finnes ikke i behandling ${behandling.id}")
    }

    if (kategorierSomKreverType.contains(behandling.særbidragKategori) && type.isNullOrEmpty()) {
        feilliste.add("Type må settes hvis behandling har kategori ${behandling.særbidragKategori}")
    } else if (!kategorierSomKreverType.contains(behandling.særbidragKategori) && !type.isNullOrEmpty()) {
        feilliste.add("Type kan ikke settes hvis behandling har kategori ${behandling.særbidragKategori}")
    }

    if (behandling.særbidragKategori == Særbidragskategori.KONFIRMASJON) {
        if (type?.tilUtgiftstype()?.kategori != Særbidragskategori.KONFIRMASJON) {
            feilliste.add(
                "Type $type er ikke gyldig for" +
                    " behandling med kategori ${Særbidragskategori.KONFIRMASJON}",
            )
        }
    }

    return feilliste
}

fun OppdatereVirkningstidspunkt.valider(behandling: Behandling) {
    val feilliste = mutableListOf<String>()
    if (avslag != null && årsak != null) {
        feilliste.add("Kan ikke sette både avslag og årsak samtidig")
    }
    if (behandling.opprinneligVirkningstidspunkt != null &&
        avslag == null &&
        virkningstidspunkt?.isAfter(behandling.opprinneligVirkningstidspunkt) == true
    ) {
        feilliste.add("Virkningstidspunkt kan ikke være senere enn opprinnelig virkningstidspunkt")
    }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av virkningstidspunkt: ${feilliste.joinToString(", ")}",
        )
    }
}

fun Husstandsmedlem.validereAndreVoksneIHusstanden(virkniningstidspunkt: LocalDate): AndreVoksneIHusstandenPeriodeseringsfeil {
    val hullIPerioder =
        this.perioder
            .map {
                Datoperiode(it.datoFom!!, it.datoTom)
            }.finnHullIPerioder(virkniningstidspunkt)
    return AndreVoksneIHusstandenPeriodeseringsfeil(
        hullIPerioder,
        overlappendePerioder = this.perioder.finneOverlappendeBostatusperioder(),
        manglerPerioder = this.perioder.isEmpty(),
        fremtidigPeriode = this.inneholderFremtidigeBoforholdsperioder(),
    )
}

fun Husstandsmedlem.validereBoforhold(
    virkniningstidspunkt: LocalDate,
    valideringsfeil: MutableList<BoforholdPeriodeseringsfeil>,
    validerePerioder: Boolean = true,
): Set<BoforholdPeriodeseringsfeil> {
    val hullIPerioder =
        this.perioder
            .map {
                Datoperiode(it.datoFom!!, it.datoTom)
            }.finnHullIPerioder(maxOf(virkniningstidspunkt, this.fødselsdato ?: this.rolle!!.fødselsdato))
    if (validerePerioder) {
        valideringsfeil.add(
            BoforholdPeriodeseringsfeil(
                this,
                hullIPerioder,
                overlappendePerioder = this.perioder.finneOverlappendeBostatusperioder(),
                manglerPerioder = this.perioder.isEmpty(),
                fremtidigPeriode = this.inneholderFremtidigeBoforholdsperioder(),
            ),
        )
    }

    return valideringsfeil.toSet()
}

fun Set<Husstandsmedlem>.validerBoforhold(virkniningstidspunkt: LocalDate): Set<BoforholdPeriodeseringsfeil> {
    val valideringsfeil = mutableListOf<BoforholdPeriodeseringsfeil>()

    forEach {
        it.validereBoforhold(virkniningstidspunkt, valideringsfeil)
    }

    firstOrNull()?.behandling?.let { b ->
        b.søknadsbarn.forEach { rolle ->
            if (this.none { it.ident == rolle.ident }) {
                valideringsfeil.add(
                    BoforholdPeriodeseringsfeil(
                        manglerPerioder = true,
                        husstandsmedlem =
                            Husstandsmedlem(
                                b,
                                fødselsdato = rolle.fødselsdato,
                                navn = rolle.navn,
                                ident = rolle.ident,
                                kilde = Kilde.OFFENTLIG,
                            ),
                    ),
                )
            }
        }
    }
    return valideringsfeil.toSet()
}

fun Set<Sivilstand>.validereSivilstand(virkningstidspunkt: LocalDate): SivilstandPeriodeseringsfeil {
    val kanIkkeVæreSenereEnnDato = maxOf(LocalDate.now().withDayOfMonth(1), virkningstidspunkt.withDayOfMonth(1))
    return SivilstandPeriodeseringsfeil(
        map { Datoperiode(it.datoFom!!, it.datoTom) }.finnHullIPerioder(virkningstidspunkt),
        fremtidigPeriode = any { it.datoFom!!.isAfter(kanIkkeVæreSenereEnnDato) },
        manglerPerioder = isEmpty(),
        overlappendePerioder = finnSivilstandOverlappendePerioder(),
        ugyldigStatus = any { it.sivilstand == Sivilstandskode.UKJENT },
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

private fun Set<Bostatusperiode>.finneOverlappendeBostatusperioder() =
    sortedBy { it.datoFom }.flatMapIndexed { index, bostatusperiode ->
        sortedBy { it.datoFom }
            .drop(index + 1)
            .filter { nesteHusstandsperiode ->
                nesteHusstandsperiode.tilDatoperiode().overlapper(bostatusperiode.tilDatoperiode())
            }.map { nesteBostatusperiode ->
                OverlappendeBostatusperiode(
                    Datoperiode(
                        maxOf(bostatusperiode.datoFom!!, nesteBostatusperiode.datoFom!!),
                        minOfNullable(bostatusperiode.datoTom, nesteBostatusperiode.datoTom),
                    ),
                    setOf(bostatusperiode.bostatus, nesteBostatusperiode.bostatus),
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

@JvmName("finnHullIPerioderInntekt")
fun List<Inntekt>.finnHullIPerioder(virkniningstidspunkt: LocalDate): List<Datoperiode> {
    val perioderSomSkalSjekkes =
        filter { it.taMed && !it.kanHaHullIPerioder() }
            .sortedBy { it.datoFom }
            .map { Datoperiode(it.datoFom!!, it.datoTom) }
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
            inntektsposter
                .mapNotNull {
                    it.inntektstype
                }.ifEmpty { type.inneholderInntektstypeListe }
        }

fun Inntekt.validerOverlapperMedInntekt(sammenlignMedInntekt: Inntekt): OverlappendePeriode? {
    val inntektsposterSomOverlapper = inntektstypeListe.filter { sammenlignMedInntekt.inntektstypeListe.contains(it) }
    val kanOverlappe = inntektsposterSomOverlapper.isEmpty()
    val inntektPeriode = Datoperiode(datoFom!!, datoTom)
    val sammenlignMedInntektPeriode = Datoperiode(sammenlignMedInntekt.datoFom!!, sammenlignMedInntekt.datoTom)
    val perioderOverlapper = inntektPeriode.overlapper(sammenlignMedInntektPeriode) && !kanOverlappe
    if (perioderOverlapper) {
        val datoFom = maxOf(sammenlignMedInntekt.datoFom!!, datoFom!!)
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
    val feilliste = mutableListOf<String>()
    oppdatereManuelleInntekter.forEach { oppdaterInntekt ->
        if (inntekstrapporteringerSomKreverGjelderBarn.contains(oppdaterInntekt.type)) {
            oppdaterInntekt.validerHarGjelderBarn(
                feilliste,
            )
        }
        if (inntekstrapporteringerSomKreverInnteksttype.contains(oppdaterInntekt.type)) {
            oppdaterInntekt.validerHarInnteksttype(
                feilliste,
            )
        }
    }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av inntekter: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdatereInntektRequest.valider() {
    val feilliste = mutableListOf<String>()
    if (inntekstrapporteringerSomKreverGjelderBarn.contains(this.oppdatereManuellInntekt?.type)) {
        this.oppdatereManuellInntekt?.validerHarGjelderBarn(feilliste)
    }
    if (inntekstrapporteringerSomKreverInnteksttype.contains(this.oppdatereManuellInntekt?.type)) {
        this.oppdatereManuellInntekt?.validerHarInnteksttype(feilliste)
    }

    this.henteOppdatereBegrunnelse?.let {
        if (it.rolleid == null) {
            log.warn { "Rolleid skal være satt ved oppdatering av inntektsnotat. Setter rolle til bidragsmottaker" }
            // TODO: Fjerne utkommentering når front-end angir rolle ved oppdatering av inntektsnotat
            // feilliste.add("Rolleid må være satt ved oppdatering av inntektsnotat.")
        }
    }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av inntekter: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdatereHusstandsmedlem.validere(behandling: Behandling) {
    this.opprettHusstandsmedlem?.let {
        if (this.opprettHusstandsmedlem.navn == null &&
            this.opprettHusstandsmedlem.personident == null
        ) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke opprette husstandsmedlem som mangler både navn og personident.",
            )
        } else if (this.opprettHusstandsmedlem.personident != null) {
            val eksisterendeHusstandsmedlem =
                behandling.husstandsmedlem.find { it.ident != null && it.ident == this.opprettHusstandsmedlem.personident.verdi }

            if (eksisterendeHusstandsmedlem != null) {
                finnesFraFørException(behandling.id!!)
            }
        }
    }

    this.oppdaterPeriode?.let {
        val husstandsmedlem = behandling.husstandsmedlem.find { this.oppdaterPeriode.idHusstandsmedlem == it.id }

        if (husstandsmedlem == null) {
            husstandsmedlemIkkeFunnetException(this.oppdaterPeriode.idHusstandsmedlem, behandling.id!!)
        }

        husstandsmedlem.validereNyPeriode(it.datoFom, it.datoTom)

        if (it.idPeriode != null) {
            val bostatusperiode = behandling.finnBostatusperiode(it.idPeriode)
            if (bostatusperiode == null) {
                ressursIkkeFunnetException("Fant ikke bostatusperiode med id ${it.idPeriode}.")
            } else if (husstandsmedlem.id != bostatusperiode.husstandsmedlem.id) {
                ressursIkkeTilknyttetBehandling(
                    "Bostatusperiode ${it.idPeriode} er ikke tilknyttet husstandsmedlem ${husstandsmedlem.id}" +
                        " i behandling ${behandling.id}.",
                )
            }
        }
    }

    this.slettPeriode?.let { id ->
        val husstandsmedlemsperiode = behandling.henteAlleBostatusperioder().find { it.id == id }
        if (husstandsmedlemsperiode == null) {
            ressursIkkeFunnetException("Fant ikke husstandsmedlemsperiode med id $id.")
        } else if (husstandsmedlemsperiode.husstandsmedlem.perioder.none { it.id != id }) {
            ressursIkkeTilknyttetBehandling(
                "Kan ikke slette alle perioder " +
                    "fra husstandsmedlem ${husstandsmedlemsperiode.husstandsmedlem.id} i behandling ${behandling.id}.",
            )
        } else if (behandling.id != husstandsmedlemsperiode.husstandsmedlem.behandling.id) {
            ressursIkkeTilknyttetBehandling(
                "Husstandsmedlemperiode $id hører ikke til behandling med id" +
                    "${behandling.id}.",
            )
        }
    }

    this.slettHusstandsmedlem?.let {
        val husstandsmedlem = behandling.husstandsmedlem.find { this.slettHusstandsmedlem == it.id }
        if (husstandsmedlem == null) {
            husstandsmedlemIkkeFunnetException(this.slettHusstandsmedlem, behandling.id!!)
        } else if (Kilde.OFFENTLIG == husstandsmedlem.kilde) {
            ressursHarFeilKildeException(
                "Husstandsmedlem med id ${husstandsmedlem.id} i behandling ${husstandsmedlem.behandling.id} " +
                    "kommer fra offentlige registre, og kan derfor ikke slettes.",
            )
        } else if (behandling.id != husstandsmedlem.behandling.id) {
            ressursIkkeTilknyttetBehandling(
                "Husstandsmedlem $it hører ikke til behandling med id" +
                    "${behandling.id}.",
            )
        }
    }

    this.tilbakestillPerioderForHusstandsmedlem?.let {
        val husstandsmedlem = behandling.husstandsmedlem.find { this.tilbakestillPerioderForHusstandsmedlem == it.id }
        if (husstandsmedlem == null) {
            husstandsmedlemIkkeFunnetException(it, behandling.id!!)
        } else if (husstandsmedlem.kilde == Kilde.MANUELL) {
            oppdateringAvBoforholdFeilet("Kan ikke tilbakestille manuell lagt inn husstandsmedlem til offentlige perioder")
        } else if (behandling.id != husstandsmedlem.behandling.id) {
            ressursIkkeTilknyttetBehandling(
                "Husstandsmedlem $it hører ikke til behandling med id" +
                    "${behandling.id}.",
            )
        }
    }

    this.angreSisteStegForHusstandsmedlem?.let {
        val husstandsmedlem = behandling.husstandsmedlem.find { this.angreSisteStegForHusstandsmedlem == it.id }
        if (husstandsmedlem == null) {
            husstandsmedlemIkkeFunnetException(it, behandling.id!!)
        } else if (husstandsmedlem.forrigePerioder.isNullOrEmpty()) {
            oppdateringAvBoforholdFeilet("Kan ikke angre siste steg for husstandsmedlem. Det mangler informasjon om siste steg")
        } else if (behandling.id != husstandsmedlem.behandling.id) {
            ressursIkkeTilknyttetBehandling(
                "Husstandsmedlem $it hører ikke til behandling med id" +
                    "${behandling.id}.",
            )
        }
    }
}

fun OppdatereAndreVoksneIHusstanden.validere(behandling: Behandling) {
    if (behandling.tilType() == TypeBehandling.FORSKUDD) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig behandlingstype for oppdatering av andre voksne i husstanden: ${behandling.tilType()} " +
                "(behandlingsid: ${behandling.id}). Behandlingstype må være SÆRBIDRAG.",
        )
    }

    val rolleMedAndreVoksneIHusstanden = behandling.bidragspliktig

    var husstandsmedlem = behandling.husstandsmedlem.voksneIHusstanden
    if (husstandsmedlem == null) {
        husstandsmedlem =
            Husstandsmedlem(
                behandling = behandling,
                kilde = Kilde.OFFENTLIG,
                rolle = rolleMedAndreVoksneIHusstanden,
            )
        behandling.husstandsmedlem.add(husstandsmedlem)
    }

    this.oppdaterePeriode?.let { oppdatereAndreVoksne ->
        oppdatereAndreVoksne.idPeriode?.let { periodeid ->
            val periodeSomSkalOppdateres = husstandsmedlem.perioder.find { periodeid == it.id }

            if (husstandsmedlem.perioder.isEmpty() || periodeSomSkalOppdateres == null) {
                throw HttpClientErrorException(
                    HttpStatus.NOT_FOUND,
                    "Fant ikke oppgitt bor-med-andre-voksne-periode (id $periodeid) i ${behandling.id}. Ingen endring" +
                        " å utføre.",
                )
            }
        }
    }

    this.slettePeriode?.let { sletteId ->
        val periodeSomSkalSlettes = husstandsmedlem.perioder.find { sletteId == it.id }
        if (periodeSomSkalSlettes == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ikke oppgitt bor-med-andre-voksne-periode (periodeid $sletteId). Sletter ingen perioder fra " +
                    "behandling ${behandling.id}.",
            )
        }
    }
}

fun OppdatereSivilstand.validere(behandling: Behandling) {
    this.sletteSivilstandsperiode?.let { id ->
        val sivilstand = behandling.sivilstand.find { this.sletteSivilstandsperiode == it.id }
        if (sivilstand == null) {
            ressursIkkeFunnetException("Fant ikke sivilstandsperiode med id $id.")
        } else if (behandling.sivilstand.none { id != it.id }) {
            ressursIkkeTilknyttetBehandling("Kan ikke slette det eneste innslaget i sivilstandshistorikken til behandling ${behandling.id}")
        }
    }

    this.nyEllerEndretSivilstandsperiode?.id?.let { id ->
        if (behandling.sivilstand.find { id == it.id } == null) {
            ressursIkkeFunnetException("Fant ikke sivilstandsinnlsag med id $id.")
        }
    }

    if (this.sletteSivilstandsperiode == null &&
        this.nyEllerEndretSivilstandsperiode == null &&
        !this.tilbakestilleHistorikk &&
        !this.angreSisteEndring
    ) {
        requestManglerDataException(behandling.id!!, Ressurstype.SIVILSTAND)
    }
}

fun OppdatereManuellInntekt.validerHarGjelderBarn(feilliste: MutableList<String>) {
    if (gjelderBarn == null || gjelderBarn.verdi.isEmpty()) {
        feilliste.add("$type må ha gyldig ident for gjelder barn")
    }
}

fun OppdatereManuellInntekt.validerHarInnteksttype(feilliste: MutableList<String>) {
    if (inntektstype == null) {
        feilliste.add("Barnetillegg må ha gyldig inntektstype")
    }
}

fun Sivilstand.tilDatoperiode() = Datoperiode(datoFom!!, datoTom)

fun Bostatusperiode.tilDatoperiode() = Datoperiode(datoFom!!, datoTom)

fun finnSenesteDato(
    dato1: LocalDate?,
    dato2: LocalDate?,
): LocalDate? =
    if (dato1 == null || dato2 == null) {
        null
    } else {
        maxOf(
            dato1,
            dato2,
        )
    }

fun finnSenesteDato(
    dato1: LocalDate?,
    dato2: LocalDate?,
    dato3: LocalDate?,
): LocalDate? =
    if (dato1 == null || dato2 == null || dato3 == null) {
        null
    } else {
        maxOf(
            dato1,
            dato2,
            dato3,
        )
    }

private fun Set<OverlappendePeriode>.mergePerioder(): Set<OverlappendePeriode> {
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
                it.idListe.any { overlappendePeriode.idListe.contains(it) }
            }
        if (annenOverlappendePeriode != null) {
            if (sammenstiltePerioderSomInneholderOverlappende != null) {
                sammenstiltePerioder.remove(sammenstiltePerioderSomInneholderOverlappende)
                sammenstiltePerioder.add(
                    sammenstiltePerioderSomInneholderOverlappende.copy(
                        rapporteringTyper =
                            (
                                annenOverlappendePeriode.rapporteringTyper + overlappendePeriode.rapporteringTyper +
                                    sammenstiltePerioderSomInneholderOverlappende.rapporteringTyper
                            ).sorted()
                                .toMutableSet(),
                        idListe =
                            (
                                annenOverlappendePeriode.idListe + overlappendePeriode.idListe +
                                    sammenstiltePerioderSomInneholderOverlappende.idListe
                            ).sorted().toMutableSet(),
                        inntektstyper =
                            (
                                annenOverlappendePeriode.inntektstyper + overlappendePeriode.inntektstyper +
                                    sammenstiltePerioderSomInneholderOverlappende.inntektstyper
                            ).sorted()
                                .toMutableSet(),
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
                            (annenOverlappendePeriode.rapporteringTyper + overlappendePeriode.rapporteringTyper)
                                .sorted()
                                .toMutableSet(),
                        idListe =
                            (annenOverlappendePeriode.idListe + overlappendePeriode.idListe)
                                .sorted()
                                .toMutableSet(),
                        inntektstyper =
                            (annenOverlappendePeriode.inntektstyper + overlappendePeriode.inntektstyper)
                                .sorted()
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

private fun Husstandsmedlem.inneholderFremtidigeBoforholdsperioder(): Boolean {
    val kanIkkeVæreSenereEnnDato = this.senestePeriodeFomDato()

    return this.perioder.any {
        it.datoFom!!.isAfter(kanIkkeVæreSenereEnnDato) ||
            (
                it.datoTom != null &&
                    it.datoTom!!.isAfter(
                        kanIkkeVæreSenereEnnDato.plusMonths(1).minusDays(1),
                    )
            )
    }
}

private fun Husstandsmedlem.validereNyPeriode(
    nyFomDato: LocalDate?,
    nyTomDato: LocalDate?,
) {
    val kanIkkeVæreSenereEnnDato = this.senestePeriodeFomDato()

    if (nyFomDato != null &&
        nyFomDato.isAfter(kanIkkeVæreSenereEnnDato) ||
        (nyTomDato != null && nyTomDato.isAfter(kanIkkeVæreSenereEnnDato.plusMonths(1).minusDays(1)))
    ) {
        oppdateringAvBoforholdFeilet(
            "Oppdatering av boforhold feilet for behanding ${behandling.id} pga" +
                " fremtidig periode: [$nyFomDato, $nyTomDato]",
        )
    }
}

private fun Husstandsmedlem.senestePeriodeFomDato(): LocalDate {
    val virkningsdato = this.behandling.virkningstidspunktEllerSøktFomDato
    return if (virkningsdato.isAfter(LocalDate.now())) {
        maxOf(this.fødselsdato ?: this.rolle!!.fødselsdato, virkningsdato.withDayOfMonth(1))
    } else {
        LocalDate.now().withDayOfMonth(1)
    }
}
