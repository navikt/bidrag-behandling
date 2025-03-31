package no.nav.bidrag.behandling.database.datamodell

import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype

@OptIn(ExperimentalStdlibApi::class)
fun String.tilÅrsakstype(): VirkningstidspunktÅrsakstype? {
    return try {
        VirkningstidspunktÅrsakstype.valueOf(this)
    } catch (e: IllegalArgumentException) {
        return VirkningstidspunktÅrsakstype.entries.find { it.legacyKode.contains(this) }
    }
}
