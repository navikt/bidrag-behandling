package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class AvslagType(val description: String) {
    ANNET_AVSLAG("Annet avslag"),
    PGA_BARNEPENSJ("Pga barnepensj."),
    BARNS_EKTESKAP("Barns ekteskap"),
    BARNS_INNTEKT("Barns inntekt"),
    PGA_YTELSE_FTRL("Pga ytelse ftrl"), // folketrygdloven
    FULLT_UNDERH_OFF("Fullt underh.off."),
    IKKE_OMSORG("Ikke omsorg"),
    IKKE_OPPH_I_RIKET("Ikke opph i riket"),
    MANGL_DOK("Mangl dokumenter"),
    PGA_SAMMENFL("Pga sammenfl."), // sammenfletting
    OPPH_UTLAND("Opph.utland"),
    UTENL_YTELSE("Utenl.ytelse"),
}
