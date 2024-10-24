package no.nav.bidrag.behandling.transformers.underhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderhold
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun OppdatereUnderhold.validere(behandling: Behandling) {
    tilleggsstønad?.let { }
    faktiskTilsynsutgift?.let { }
    stønadTilBarnetilsyn?.let { }
}

fun SletteUnderholdselement.validere(behandling: Behandling) {
    val underhold = behandling.underholdskostnad.find { this.idUnderhold == it.id }
    if (underhold == null) {
        ressursIkkeFunnetException("Fant ikke underholdskostnad med id ${this.idUnderhold} i behandling ${behandling.id}")
    }

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

            if (underhold.barnetilsyn.isNotEmpty() || underhold.tilleggsstønad.isNotEmpty() || underhold.faktiskeTilsynsutgifter.isNotEmpty()) {
                throw HttpClientErrorException(
                    HttpStatus.BAD_REQUEST,
                    "Kan ikke slette barn med person.id ${this.idElement} fra underholdskostnad (id = ${this.idUnderhold}  i behandling ${behandling.id} " +
                            " så lenge det er reigstrert stønad til barnetilsyn, tilleggsstønad, eller faktiskte tilsysnsutgifter på barnet.",
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
