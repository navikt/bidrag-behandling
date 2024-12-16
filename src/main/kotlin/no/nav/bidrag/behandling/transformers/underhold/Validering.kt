package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdsperiode
import no.nav.bidrag.behandling.dto.v2.underhold.ValideringsfeilUnderhold
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.service.PersonService
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

fun OppdatereUnderholdRequest.validere() {
    if (this.harTilsynsordning == null && this.begrunnelse.isNullOrBlank()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Verken harTilsynsordning eller begrunnelse var satt.",
        )
    }
}

fun BarnDto.validere(
    behandling: Behandling,
    personService: PersonService,
) {
    if ((navn.isNullOrBlank() || fødselsdato == null) && (personident == null || personident.verdi.isEmpty())) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Personident eller navn og fødselsdato må oppgis for nytt barn i underholdskostnad.",
        )
    } else if (!navn.isNullOrBlank() && (personident != null && personident.verdi.isNotEmpty())) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Personident kan ikke oppgis sammen med med navn på barnet som skal legges til underholdskostnad.",
        )
    }

    if (id != null && id > 0) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Databaseid til barn skal ikke oppgis ved opprettelse av underholdskostnad.",
        )
    }

    if (this.annetBarnMedSammePersonidentEksistererFraFør(behandling)) {
        throw HttpClientErrorException(
            HttpStatus.CONFLICT,
            "Underhold for oppgitt barn eksisterer allerede i behandling ${behandling.id}).",
        )
    }

    if (this.annetBarnMedSammeNavnOgFødselsdatoEksistererFraFør(behandling)) {
        throw HttpClientErrorException(
            HttpStatus.CONFLICT,
            "Det finnes allerede underhold for barn med samme navn og fødselsdato i behandling ${behandling.id}.",
        )
    }

    this.personident?.let {
        if (personService.hentPerson(it.verdi) == null) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Fant ikke barn med oppgitt personident.")
        }
    }
}

fun SletteUnderholdselement.validere(behandling: Behandling) {
    val underhold = henteOgValidereUnderholdskostnad(behandling, this.idUnderhold)

    when (this.type) {
        Underholdselement.BARN -> {
            val rolle = underhold.barnetsRolleIBehandlingen
            if (rolle != null) {
                throw HttpClientErrorException(
                    HttpStatus.BAD_REQUEST,
                    "Barn med person.id ${this.idElement} har rolle ${rolle.rolletype} i behandling ${behandling.id}. Barnet kan derfor ikke slettes.",
                )
            }

            if (this.idElement != underhold.person.id) {
                ressursIkkeFunnetException("Fant ikke barn med person.id ${this.idElement} i behandling ${behandling.id}")
            }
        }

        Underholdselement.FAKTISK_TILSYNSUTGIFT -> {
            if (underhold.faktiskeTilsynsutgifter.find { this.idElement == it.id } == null) {
                ressursIkkeFunnetException("Fant ikke faktisk tilsynsutgift med id ${this.idElement} i behandling ${behandling.id}")
            }
        }

        Underholdselement.STØNAD_TIL_BARNETILSYN -> {
            if (underhold.barnetilsyn.find { this.idElement == it.id } == null) {
                ressursIkkeFunnetException("Fant ikke stønad til barnetilsyn med id ${this.idElement} i behandling ${behandling.id}")
            }
        }

        Underholdselement.TILLEGGSSTØNAD -> {
            if (underhold.tilleggsstønad.find { this.idElement == it.id } == null) {
                ressursIkkeFunnetException("Fant ikke tilleggsstønad med id ${this.idElement} i behandling ${behandling.id}")
            }
        }
    }
}

fun Set<Tilleggsstønad>.finneTilleggsstønadsperioderSomIkkeOverlapperMedFaktiskTilsynsutgiftsperioder(
    perioderTilsynsutgift: Set<FaktiskTilsynsutgift>,
): Set<Underholdsperiode> {
    val datoperioderTilsynsutgift = perioderTilsynsutgift.tilsynsutgiftTilDatoperioder()
    val datoperioderTillegsstønadSomIkkeOverlapperMedTilsynsutgift = mutableListOf<Underholdsperiode>()

    val tilleggsstønadsperioderSomIkkeErDekketAvTilsynsutgift =
        this.tilleggsstønadTilDatoperioder().redusereMed(datoperioderTilsynsutgift)

    tilleggsstønadsperioderSomIkkeErDekketAvTilsynsutgift.forEach { periode ->
        datoperioderTillegsstønadSomIkkeOverlapperMedTilsynsutgift.add(
            Underholdsperiode(
                Underholdselement.TILLEGGSSTØNAD,
                periode,
            ),
        )
    }

    return datoperioderTillegsstønadSomIkkeOverlapperMedTilsynsutgift.toSet()
}

fun Set<DatoperiodeDto>.finneFremtidigePerioder(underholdselement: Underholdselement) =
    this
        .filter {
            it.fom.isAfter(LocalDate.now().withDayOfMonth(1)) ||
                it.tom?.isAfter(
                    LocalDate.now().withDayOfMonth(1).minusDays(1),
                ) ?: false
        }.map { Underholdsperiode(underholdselement = underholdselement, periode = it) }
        .toSet()

fun finneOverlappendePerioder(
    perioder: Set<Underholdsperiode>,
    indeks: Int = 0,
    overlappendePerioder: MutableMap<Underholdsperiode, Set<Underholdsperiode>> = mutableMapOf(),
): Map<Underholdsperiode, Set<Underholdsperiode>> {
    if (perioder.size > 1) {
        val gjeldendePeriode = perioder.minBy { it.periode.fom }
        val nestePeriodesett = perioder.minus(gjeldendePeriode).sortedBy { it.periode.fom }.toSet()
        val perioderSomOverlapperGjeldendePeriode =
            nestePeriodesett
                .filter {
                    it.periode.tilDatoperiode().overlapper(gjeldendePeriode.periode.tilDatoperiode())
                }.toSet()
        if (perioderSomOverlapperGjeldendePeriode.isNotEmpty()) {
            overlappendePerioder[gjeldendePeriode] = perioderSomOverlapperGjeldendePeriode
            finneOverlappendePerioder(nestePeriodesett, indeks + 1, overlappendePerioder)
        } else {
            return overlappendePerioder
        }
    }

    return overlappendePerioder
}

fun Set<Barnetilsyn>.validerePerioder() =
    if (isEmpty()) {
        null
    } else {
        ValideringsfeilUnderhold(
            gjelderUnderholdskostnad = this.first().underholdskostnad,
            overlappendePerioder =
                finneOverlappendePerioder(this.barnetilsynTilUnderholdsperioder()),
            fremtidigePerioder =
                this.barnetilsynTilDatoperioder().finneFremtidigePerioder(Underholdselement.STØNAD_TIL_BARNETILSYN),
        )
    }

fun Set<FaktiskTilsynsutgift>.validerePerioderFaktiskTilsynsutgift() =
    if (isEmpty()) {
        null
    } else {
        ValideringsfeilUnderhold(
            gjelderUnderholdskostnad = this.first().underholdskostnad,
            fremtidigePerioder =
                this.tilsynsutgiftTilDatoperioder().finneFremtidigePerioder(Underholdselement.FAKTISK_TILSYNSUTGIFT),
        )
    }

fun Set<Tilleggsstønad>.validerePerioderTilleggsstønad(u: Underholdskostnad) =
    if (isEmpty()) {
        null
    } else {
        val fremtidigePerioder =
            this.tilleggsstønadTilDatoperioder().finneFremtidigePerioder(Underholdselement.FAKTISK_TILSYNSUTGIFT)
        val perioderSomManglerTilsynsutgift =
            this.finneTilleggsstønadsperioderSomIkkeOverlapperMedFaktiskTilsynsutgiftsperioder(
                u.faktiskeTilsynsutgifter,
            )

        if (fremtidigePerioder.isNotEmpty() || perioderSomManglerTilsynsutgift.isNotEmpty()) {
            ValideringsfeilUnderhold(
                gjelderUnderholdskostnad = this.first().underholdskostnad,
                fremtidigePerioder = fremtidigePerioder,
                tilleggsstønadsperioderUtenFaktiskTilsynsutgift = perioderSomManglerTilsynsutgift,
            )
        } else {
            null
        }
    }

fun Underholdskostnad.validerePerioder(perioderUnderholdskostnadDto: Set<UnderholdskostnadDto>) =
    ValideringsfeilUnderhold(
        gjelderUnderholdskostnad = this,
        overlappendePerioder =
            finneOverlappendePerioder(
                this.barnetilsyn.barnetilsynTilUnderholdsperioder(),
            ) +
                finneOverlappendePerioder(
                    this.tilleggsstønad.tilleggsstønadTilUnderholdsperioder(),
                ),
        tilleggsstønadsperioderUtenFaktiskTilsynsutgift =
            this.tilleggsstønad.finneTilleggsstønadsperioderSomIkkeOverlapperMedFaktiskTilsynsutgiftsperioder(
                this.faktiskeTilsynsutgifter,
            ),
        fremtidigePerioder =
            this.barnetilsyn
                .barnetilsynTilDatoperioder()
                .finneFremtidigePerioder(Underholdselement.STØNAD_TIL_BARNETILSYN) +
                this.faktiskeTilsynsutgifter
                    .tilsynsutgiftTilDatoperioder()
                    .finneFremtidigePerioder(Underholdselement.FAKTISK_TILSYNSUTGIFT) +
                this.tilleggsstønad
                    .tilleggsstønadTilDatoperioder()
                    .finneFremtidigePerioder(Underholdselement.TILLEGGSSTØNAD),
        harIngenPerioder = this.barnetsRolleIBehandlingen?.let { perioderUnderholdskostnadDto.isEmpty() } ?: false,
        manglerPerioderForTilsynsutgifter =
            this.harTilsynsordning?.let {
                this.barnetilsyn.isEmpty() &&
                    this.faktiskeTilsynsutgifter.isEmpty() &&
                    this.tilleggsstønad.isEmpty()
            }
                ?: false,
    )

fun StønadTilBarnetilsynDto.validerePerioderStønadTilBarnetilsyn(underholdskostnad: Underholdskostnad) {
    this.id?.let { id ->
        if (id > 0 && underholdskostnad.barnetilsyn.find { id == it.id } == null) {
            ressursIkkeFunnetException("Fant ikke barnetilsyn med id $id i behandling ${underholdskostnad.behandling.id}")
        }
    }
}

fun OppdatereFaktiskTilsynsutgiftRequest.validere(underholdskostnad: Underholdskostnad) {
    this.id?.let { id ->
        if (id > 0 && underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id } == null) {
            ressursIkkeFunnetException("Fant ikke faktisk tilsynsutgift med id $id i behandling ${underholdskostnad.behandling.id}")
        }
    }
}

fun OppdatereTilleggsstønadRequest.validere(underholdskostnad: Underholdskostnad) {
    this.id?.let { id ->
        if (id > 0 && underholdskostnad.tilleggsstønad.find { id == it.id } == null) {
            ressursIkkeFunnetException("Fant ikke tilleggsstønad med id $id i behandling ${underholdskostnad.behandling.id}")
        }
    }
}

fun henteOgValidereUnderholdskostnad(
    behandling: Behandling,
    idUnderhold: Long,
): Underholdskostnad {
    val underhold = behandling.underholdskostnader.find { idUnderhold == it.id }
    if (underhold == null) {
        ressursIkkeFunnetException("Fant ikke underholdskostnad med id $idUnderhold i behandling ${behandling.id}")
    }

    return underhold
}

private fun Set<DatoperiodeDto>.redusereMed(perioderSomTrekkesFra: Set<DatoperiodeDto>): Set<DatoperiodeDto> {
    val maksTillatteTomdato = LocalDate.now().withDayOfMonth(1).plusDays(1)

    if (perioderSomTrekkesFra.isEmpty()) return this

    val start = this.minBy { it.fom }.fom
    val slutt = this.maxBy { it.tom ?: maksTillatteTomdato }.tom ?: maksTillatteTomdato

    val minsteDatoSomTrekkesFra = perioderSomTrekkesFra.minBy { it.fom }.fom
    val størsteDatoSomTrekkesFra =
        perioderSomTrekkesFra.maxBy { it.tom ?: maksTillatteTomdato }.tom ?: maksTillatteTomdato

    val datoerIkkeIPerioderSomTrekkesFra: MutableSet<LocalDate> = mutableSetOf()
    if (start < minsteDatoSomTrekkesFra) {
        leggeTilDatoer(start, minsteDatoSomTrekkesFra, datoerIkkeIPerioderSomTrekkesFra)
    }
    if (slutt > størsteDatoSomTrekkesFra) {
        leggeTilDatoer(størsteDatoSomTrekkesFra, slutt, datoerIkkeIPerioderSomTrekkesFra)
    }

    val alleDatoerMellomStartOgSlutt =
        perioderSomTrekkesFra
            .sortedBy { it.fom }
            .flatMap {
                leggeTilDatoer(it.fom, it.tom ?: maksTillatteTomdato).toMutableSet()
            }.toMutableSet()

    perioderSomTrekkesFra.forEach {
        alleDatoerMellomStartOgSlutt.removeAll(leggeTilDatoer(it.fom, it.tom ?: maksTillatteTomdato))
    }

    // legger til datoer som ikke finnes i perioder som trekkes fra
    datoerIkkeIPerioderSomTrekkesFra.addAll(alleDatoerMellomStartOgSlutt)

    if (datoerIkkeIPerioderSomTrekkesFra.isEmpty()) return emptySet()

    val perioderSomInneholderDatoerSomIkkeFinnesIPeriodeneSomTrekkesFra: MutableSet<DatoperiodeDto> = mutableSetOf()
    this.forEach {
        if (it.fom >= datoerIkkeIPerioderSomTrekkesFra.min() &&
            (
                it.tom
                    ?: maksTillatteTomdato
            ) >= datoerIkkeIPerioderSomTrekkesFra.max()
        ) {
            perioderSomInneholderDatoerSomIkkeFinnesIPeriodeneSomTrekkesFra.add(it)
        }
    }
    return perioderSomInneholderDatoerSomIkkeFinnesIPeriodeneSomTrekkesFra
}

private fun leggeTilDatoer(
    start: LocalDate,
    slutt: LocalDate,
    datoer: MutableSet<LocalDate> = mutableSetOf(),
): Set<LocalDate> {
    var indeksdato = start
    while (indeksdato < slutt.plusDays(1)) {
        datoer.add(indeksdato)
        indeksdato = indeksdato.plusDays(1)
    }

    return datoer
}
