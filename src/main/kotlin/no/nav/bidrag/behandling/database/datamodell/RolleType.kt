package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema
@Schema(enumAsRef = true)
enum class RolleType {
    BIDRAGSPLIKTIG,
    BIDRAGSMOTTAKER,
    BARN,
    REELLMOTTAKER,
    FEILREGISTRERT,
}
