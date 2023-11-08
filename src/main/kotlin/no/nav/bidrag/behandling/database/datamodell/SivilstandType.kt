package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

// TODO Bruk Sivilstandstype fra bidrag-domain istedenfor
@Schema(enumAsRef = true)
enum class SivilstandType {
    BOR_ALENE_MED_BARN,
    GIFT,
}
