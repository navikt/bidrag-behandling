package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.fantIkkeRolleISak
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto

fun BidragssakDto.hentRolleMedFnr(fnr: String): RolleDto {
    return roller.firstOrNull { hentNyesteIdent(it.fødselsnummer?.verdi) == hentNyesteIdent(fnr) }
        ?: fantIkkeRolleISak(saksnummer.verdi, fnr)
}
