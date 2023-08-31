package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

// TODO Bruk Sivilstandstype fra bidrag-domain istedenfor
@Schema(enumAsRef = true)
enum class SivilstandType {
    ENKE_ELLER_ENKEMANN,
    GIFT,
    GJENLEVENDE_PARTNER,
    REGISTRERT_PARTNER,
    SEPARERT,
    SEPARERT_PARTNER,
    SKILT,
    SKILT_PARTNER,
    UGIFT,
    UOPPGITT,
}
