package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class SoknadType {
    INDEKSREGULERING,

    ALDERSJUSTERING,

    OPPHØR,

    ALDERSOPPHØR,

    REVURDERING,

    FASTSETTELSE,

    INNKREVING,

    KLAGE,

    ENDRING,

    ENDRING_MOTTAKER,
}
