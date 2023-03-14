package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class SoknadType {
    ENDRING,
    EGET_TILTAK,
    SOKNAD,
    INNKREVET_GRUNNLAG,
    INDEKSREGULERING,
    KLAGE_BEGR_SATS,
    KLAGE,
    FOLGER_KLAGE,
    KORRIGERING,
    KONVERTERING,
    OPPHOR,
    PRIVAT_AVTALE,
    BEGR_REVURD,
    REVURDERING,
    KONVERTERT,
    MANEDLIG_PALOP,
}
