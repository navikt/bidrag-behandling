package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.domene.enums.vedtak.Stønadstype

fun Behandling.finnRolle(
    fødselsnummer: String,
    stønadstype: Stønadstype? = null,
) = roller.find {
    it.ident == fødselsnummer &&
        (stønadstype == null || it.stønadstype == stønadstype)
}
