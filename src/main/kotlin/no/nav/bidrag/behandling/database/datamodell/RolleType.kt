package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema
//TODO Erstatt med enum no.nav.bidrag.domain.enums.Rolletype istedenfor
@Schema(enumAsRef = true)
enum class RolleType {
    BIDRAGS_PLIKTIG, // TODO: Endre til BIDRAGSPLIKTIG
    BIDRAGS_MOTTAKER, // TODO: Endre til BIDRAGSMOTTAKER
    BARN,
    REELL_MOTTAKER,
    FEILREGISTRERT,
}
