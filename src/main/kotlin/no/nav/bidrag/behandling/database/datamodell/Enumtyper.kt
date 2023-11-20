package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class BoStatusType {
    IKKE_REGISTRERT_PA_ADRESSE,
    REGISTRERT_PA_ADRESSE,
}

@Schema(enumAsRef = true)
enum class Kilde {
    MANUELL,
    OFFENTLIG,
}

@Schema(enumAsRef = true)
enum class OpplysningerType {
    INNTEKTSOPPLYSNINGER,
    BOFORHOLD,
}

// TODO Bruk Sivilstandstype fra bidrag-domain istedenfor
@Schema(enumAsRef = true)
enum class SivilstandType {
    BOR_ALENE_MED_BARN,
    GIFT,
}

@Schema(enumAsRef = true)
enum class Behandlingstype {
    BIDRAG,
    FORSKUDD,
    BIDRAG18AAR,
    EKTEFELLEBIDRAG,
    MOTREGNING,
    OPPFOSTRINGSBIDRAG,
}

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

@Schema(enumAsRef = true)
enum class ForskuddAarsakType(val beskrivelse: String) {
    SF("Annet"),
    NF("Endring 3 måneder tilbake"),
    OF("Endring 3 års regelen"),
    AF("Fra barnets fødsel"),
    CF("Fra barnets flyttemåned"),
    DF("Fra kravfremsettelse"),
    LF("Fra måneden etter inntekten økte"),
    GF("Fra oppholdstillatelse"),
    HF("Fra søknadstidspunkt"),
    BF("Fra samlivsbrudd"),
    KF("Fra samme måned som inntekten ble redusert"),
    QF("Revurdering måneden etter"),
    MF("Søknadstidspunkt endring"),
    PF("Tidligere feilaktig avslag"),
    EF("3 måneder tilbake"),
    FF("3 års regelen"),

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
