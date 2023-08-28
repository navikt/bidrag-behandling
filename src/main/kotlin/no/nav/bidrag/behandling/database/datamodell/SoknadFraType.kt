package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

// TODO Flytt dette til bidrag-domain
@Schema(enumAsRef = true)
enum class SoknadFraType {
    BM_I_ANNEN_SAK,
    BARN_18_AAR,
    NAV_BIDRAG, // TK
    FYLKESNEMDA,
    NAV_INTERNASJONAL,
    KOMMUNE,
    KONVERTERING, // Trenger vi dette?
    BIDRAGSMOTTAKER,
    NORSKE_MYNDIGHET,
    BIDRAGSPLIKTIG,
    UTENLANDSKE_MYNDIGHET,
    VERGE,
    TRYGDEETATEN_INNKREVING,
    KLAGE_ANKE,
}
