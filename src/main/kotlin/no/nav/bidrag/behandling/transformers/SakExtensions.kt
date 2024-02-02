package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto

fun BidragssakDto.hentRolleMedFnr(fnr: String): RolleDto {
    return roller.first { it.fødselsnummer?.verdi == fnr }
}
