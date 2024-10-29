package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.ValideringsfeilUnderhold
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun BarnDto.validere() {
    if (navn.isNullOrBlank() && (personident == null || personident.verdi.isEmpty())) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Personident eller navn må oppgis for nytt barn i underholdskostnad."
        )
    } else if (!navn.isNullOrBlank() && (personident != null && personident.verdi.isNotEmpty())) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Personident kan ikke oppgis sammen med med navn på barnet som skal legges til underholdskostnad."
        )
    }

    if (id != null && id > 0) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Databaseid til barn skal ikke oppgis ved opprettelse av underholdskostnad."
        )
    }
}

fun SletteUnderholdselement.validere(behandling: Behandling) {
    val underhold = henteOgValidereUnderholdskostnad(behandling, this.idUnderhold)

    when (this.type) {
        Underholdselement.BARN -> {
            val rolle = underhold.person.rolle.firstOrNull()
            if (rolle != null) {
                throw HttpClientErrorException(
                    HttpStatus.BAD_REQUEST,
                    "Barn med person.id ${this.idElement} har rolle ${rolle.rolletype} i behandling ${behandling.id}",
                )
            }

            if (this.idElement != underhold.person.id) {
                ressursIkkeFunnetException("Fant ikke barn med person.id ${this.idElement} i behandling ${behandling.id}")
            }

            if (underhold.barnetilsyn.isNotEmpty() ||
                underhold.tilleggsstønad.isNotEmpty() ||
                underhold.faktiskeTilsynsutgifter.isNotEmpty()
            ) {
                throw HttpClientErrorException(
                    HttpStatus.BAD_REQUEST,
                    "Kan ikke slette barn med person.id ${this.idElement} fra underholdskostnad " +
                            "(id = ${this.idUnderhold} i behandling ${behandling.id}  så lenge det er reigstrert stønad " +
                            "til barnetilsyn, tilleggsstønad, eller faktiskte tilsysnsutgifter på barnet.",
                )
            }
        }

        Underholdselement.FAKTISK_TILSYNSUGIFT -> {
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

fun Set<Barnetilsyn>.validerePerioder() = ValideringsfeilUnderhold(
    // TODO: bd-1920 - finne passende sjekk
    //hullIPerioder = map { Datoperiode(it.fom, it.tom)}.finnHullIPerioder(virkningsdato),
)

fun StønadTilBarnetilsynDto.validere(underholdskostnad: Underholdskostnad) {
    this.id?.let { id ->
        if (id > 0 && underholdskostnad.barnetilsyn.find { id == it.id } == null) {
            ressursIkkeFunnetException("Fant ikke barnetilsyn med id $id i behandling ${underholdskostnad.behandling.id}")
        }
    }
}

fun FaktiskTilsynsutgiftDto.validere(underholdskostnad: Underholdskostnad) {
    this.id?.let { id ->
        if (id > 0 && underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id } == null) {
            ressursIkkeFunnetException("Fant ikke faktisk tilsynsutgift med id $id i behandling ${underholdskostnad.behandling.id}")
        }
    }
}

fun TilleggsstønadDto.validere(underholdskostnad: Underholdskostnad) {
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
    val underhold = behandling.underholdskostnad.find { idUnderhold == it.id }
    if (underhold == null) {
        ressursIkkeFunnetException("Fant ikke underholdskostnad med id $idUnderhold i behandling ${behandling.id}")
    }

    return underhold
}
