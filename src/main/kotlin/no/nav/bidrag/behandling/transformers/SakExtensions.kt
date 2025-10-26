package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.fantIkkeRolleISak
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto

fun BidragssakDto.hentRolleMedFnr(fnr: String): RolleDto =
    roller.firstOrNull { hentNyesteIdent(it.fødselsnummer?.verdi) == hentNyesteIdent(fnr) }
        ?: fantIkkeRolleISak(saksnummer.verdi, fnr)

val BidragssakDto.barn get() = roller.filter { it.type == Rolletype.BARN }
