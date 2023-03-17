package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class SoknadFraType {
    BM_I_ANNEN_SAK,
    BARN_18,
    TK,
    FTK,
    FYLKESNEMDA,
    KONVERTERING,
    BM,
    NORSKE_MYNDIGH,
    BP,
    TI,
    UTENLANDSKE_MYNDIGH,
    VERGE,
    KOMMUNE,
}
