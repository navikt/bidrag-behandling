package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class BoStatusType {
    IKKE_REGISTRERT_PA_ADRESSE,
    REGISTRERT_PA_ADRESSE,
}
