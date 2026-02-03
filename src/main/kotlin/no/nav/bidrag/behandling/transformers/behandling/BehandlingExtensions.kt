package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.domene.enums.vedtak.Stønadstype

fun Behandling.finnRolle(
    ident: String,
    stønadstype: Stønadstype? = null,
) = roller.find {
    it.erSammeRolle(ident, stønadstype)
}

fun List<Pair<String?, Stønadstype?>>.finnes(
    ident: String,
    stønadstype: Stønadstype?,
) = any {
    it.first == ident &&
        (stønadstype == null || it.second == null || it.second == stønadstype)
}
