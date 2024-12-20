package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBarnetilsyn
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OverlappendePeriode
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadValideringsfeil
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadValideringsfeilTabell
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.domene.enums.diverse.Kilde
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
): Set<DatoperiodeDto> {
    val datoperioderTilsynsutgift = perioderTilsynsutgift.tilsynsutgiftTilDatoperioder()
    val datoperioderTillegsstønadSomIkkeOverlapperMedTilsynsutgift = mutableListOf<DatoperiodeDto>()

    val tilleggsstønadsperioderSomIkkeErDekketAvTilsynsutgift =
        this.tilleggsstønadTilDatoperioder().redusereMed(datoperioderTilsynsutgift)

    tilleggsstønadsperioderSomIkkeErDekketAvTilsynsutgift.forEach { periode ->
        datoperioderTillegsstønadSomIkkeOverlapperMedTilsynsutgift.add(periode)
    }

    return datoperioderTillegsstønadSomIkkeOverlapperMedTilsynsutgift.toSet()
}

fun List<DatoperiodeDto>.finneFremtidigePerioder() =
    this
        .filter {
            it.fom.isAfter(LocalDate.now().withDayOfMonth(1)) ||
                it.tom?.isAfter(
                    LocalDate.now().withDayOfMonth(1).minusDays(1),
                ) ?: false
        }.map { it }

fun List<DatoperiodeDto>.finneOverlappendePerioder(): List<OverlappendePeriode> {
    val allePerioderSomOverlapper = mutableListOf<DatoperiodeDto>()
    return sortedBy { it.fom }.mapIndexedNotNull { index, gjeldendePeriode ->
        val overlappendePerioder: MutableList<DatoperiodeDto> = mutableListOf()
        sortedBy { it.fom }.drop(index + 1).forEachIndexed { indexNestePeriode, nestePeriode ->
            // Korrigerer feil i no.nav.bidrag.domene.tid.Periode.overlapper (per 17.12.2024)
            if (allePerioderSomOverlapper.contains(nestePeriode)) return@forEachIndexed
            val korrigertPeriode =
                gjeldendePeriode.tilDatoperiode().copy(til = gjeldendePeriode.tom?.minusDays(1))
            val overlapper = nestePeriode.tilDatoperiode().overlapper(korrigertPeriode)
            if (overlapper) {
                allePerioderSomOverlapper.add(nestePeriode)
                overlappendePerioder.add(nestePeriode)
            }
        }
        if (overlappendePerioder.isNotEmpty()) OverlappendePeriode(gjeldendePeriode, overlappendePerioder) else null
    }
}

fun Set<Barnetilsyn>.validerePerioderBarnetilsyn() =

    UnderholdskostnadValideringsfeilTabell(
        overlappendePerioder = barnetilsynTilDatoperioder().finneOverlappendePerioder(),
        fremtidigePerioder = barnetilsynTilDatoperioder().finneFremtidigePerioder(),
    )

fun Set<FaktiskTilsynsutgift>.validerePerioderFaktiskTilsynsutgift(): UnderholdskostnadValideringsfeilTabell =
    UnderholdskostnadValideringsfeilTabell(
        fremtidigePerioder = tilsynsutgiftTilDatoperioder().finneFremtidigePerioder(),
    )

fun Set<Underholdskostnad>.valider() = this.map { it.valider() }.filter { it.harFeil }.toSet()

fun Underholdskostnad.valider(): UnderholdskostnadValideringsfeil =
    UnderholdskostnadValideringsfeil(
        gjelderUnderholdskostnad = this,
        stønadTilBarnetilsyn = barnetilsyn.validerePerioderBarnetilsyn().takeIf { it.harFeil },
        tilleggsstønad = tilleggsstønad.validerePerioderTilleggsstønad().takeIf { it.harFeil },
        faktiskTilsynsutgift = faktiskeTilsynsutgifter.validerePerioderFaktiskTilsynsutgift().takeIf { it.harFeil },
        tilleggsstønadsperioderUtenFaktiskTilsynsutgift =
            tilleggsstønad.finneTilleggsstønadsperioderSomIkkeOverlapperMedFaktiskTilsynsutgiftsperioder(
                faktiskeTilsynsutgifter,
            ),
        manglerPerioderForTilsynsordning = manglerPerioderForTilsynsordning(),
        manglerBegrunnelse = manglerBegrunnelse(),
    )

fun Underholdskostnad.manglerBegrunnelse(): Boolean {
    val begrunnelse =
        NotatService.henteUnderholdsnotat(
            this.behandling,
            barnetsRolleIBehandlingen ?: this.behandling.bidragsmottaker!!,
        )
    if (!begrunnelse.isNullOrEmpty()) return false
    return this.harTilsynsordning == true &&
        (
            this.barnetilsyn.any { it.kilde == Kilde.MANUELL } ||
                this.faktiskeTilsynsutgifter.isNotEmpty() ||
                this.tilleggsstønad.isNotEmpty()
        )
}

fun Underholdskostnad.manglerPerioderForTilsynsordning(): Boolean {
    val harOffentligeOpplysninger = hentSisteBearbeidetBarnetilsyn()?.isNotEmpty() == true
    if (harOffentligeOpplysninger || barnetsRolleIBehandlingen == null) return false
    return this.harTilsynsordning == true &&
        this.barnetilsyn.isEmpty() &&
        this.faktiskeTilsynsutgifter.isEmpty() &&
        this.tilleggsstønad.isEmpty()
}

fun Set<Tilleggsstønad>.validerePerioderTilleggsstønad() =
    UnderholdskostnadValideringsfeilTabell(
        fremtidigePerioder = tilleggsstønadTilDatoperioder().finneFremtidigePerioder(),
        overlappendePerioder = tilleggsstønadTilUnderholdsperioder().finneOverlappendePerioder(),
    )

/*
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

 */

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

private fun List<DatoperiodeDto>.redusereMed(perioderSomTrekkesFra: List<DatoperiodeDto>): List<DatoperiodeDto> {
    val maksTillatteTomdato = LocalDate.now().withDayOfMonth(1).plusDays(1)

    if (perioderSomTrekkesFra.isEmpty()) return this

    val alleDatoerMellomStartOgSlutt =
        perioderSomTrekkesFra
            .sortedBy { it.fom }
            .flatMap {
                leggeTilDatoer(it.fom, it.tom ?: maksTillatteTomdato).toMutableSet()
            }.toMutableSet()

    val datoerIkkeIPerioderSomTrekkesFra =
        this
            .sortedBy { it.fom }
            .flatMap {
                leggeTilDatoer(it.fom, it.tom ?: maksTillatteTomdato).toMutableSet()
            }.toMutableSet()

    datoerIkkeIPerioderSomTrekkesFra.removeAll(alleDatoerMellomStartOgSlutt)

    if (datoerIkkeIPerioderSomTrekkesFra.isEmpty()) return emptyList()

    val perioderSomInneholderDatoerSomIkkeFinnesIPeriodeneSomTrekkesFra: MutableList<DatoperiodeDto> = mutableListOf()
    this.forEach periode@{
        val datoer = leggeTilDatoer(it.fom, it.tom ?: maksTillatteTomdato)
        datoer.forEach { dato ->
            if (datoerIkkeIPerioderSomTrekkesFra.contains(dato)) {
                perioderSomInneholderDatoerSomIkkeFinnesIPeriodeneSomTrekkesFra.add(it)
                return@periode
            }
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
