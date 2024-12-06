package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.Periodiseringsfeil
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.transformers.finneOverlappendeDatoperioder
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

fun Set<Barnetilsyn>.validerePerioder() =
    if (isEmpty()) {
        null
    } else {
        Periodiseringsfeil(
            gjelderTabell = Underholdselement.STØNAD_TIL_BARNETILSYN,
            harFremtidigPeriode = this.find { it.fom.isAfter(LocalDate.now()) } != null,
            harIngenPerioder = false,
            overlappendePerioder = finneOverlappendeDatoperioder(this.tilDatoperioder()),
        )
    }

fun Set<FaktiskTilsynsutgift>.validerePerioderFaktiskTilsynsutgift() =
    if (isEmpty()) {
        null
    } else {
        /*
        ValideringsfeilUnderhold(
            underholdskostnad = first().underholdskostnad,
            // TODO: bd-1920 - finne passende sjekk
            // hullIPerioder = map { Datoperiode(it.fom, it.tom)}.finnHullIPerioder(virkningsdato),
        )*/
        null
    }

fun Set<Tilleggsstønad>.validerePerioderTilleggsstønad() =
    if (isEmpty()) {
        null
    } else {
        /*
        ValideringsfeilUnderhold(
            underholdskostnad = first().underholdskostnad,
            // TODO: bd-1920 - finne passende sjekk
            // hullIPerioder = map { Datoperiode(it.fom, it.tom)}.finnHullIPerioder(virkningsdato),
        )
         */
        null
    }

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
