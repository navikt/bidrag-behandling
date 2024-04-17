package no.nav.bidrag.behandling.database.datamodell

import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype

fun String.tilÅrsakstype(): VirkningstidspunktÅrsakstype? {
    return try {
        VirkningstidspunktÅrsakstype.valueOf(this)
    } catch (e: IllegalArgumentException) {
        return VirkningstidspunktÅrsakstype.entries.find { it.legacyKode == this }
    }
}
