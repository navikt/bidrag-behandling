package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype

@Schema(enumAsRef = true)
enum class Kilde {
    MANUELL,
    OFFENTLIG,
}

fun String.tilÅrsakstype(): VirkningstidspunktÅrsakstype? {
    return try {
        VirkningstidspunktÅrsakstype.valueOf(this)
    } catch (e: IllegalArgumentException) {
        return VirkningstidspunktÅrsakstype.entries.find { it.legacyKode == this }
    }
}
