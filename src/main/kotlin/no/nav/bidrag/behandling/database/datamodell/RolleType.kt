package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class RolleType {
    BIDRAGS_PLIKTIG,
    BIDRAGS_MOTTAKER,
    BARN,
    REELL_MOTTAKER,
    FEILREGISTRERT,
}
