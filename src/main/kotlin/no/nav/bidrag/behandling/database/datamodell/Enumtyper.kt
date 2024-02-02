package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class Bostatustype {
    IKKE_REGISTRERT_PA_ADRESSE,
    REGISTRERT_PA_ADRESSE,
}

@Schema(enumAsRef = true)
enum class Kilde {
    MANUELL,
    OFFENTLIG,
}

@Schema(enumAsRef = true, name = "OpplysningerType")
enum class Grunnlagsdatatype {
    /**Typer for opplysninger som er bearbeidet av frontend eller bidrag-inntekt*/
    INNTEKT_BEARBEIDET,
    BOFORHOLD_BEARBEIDET,

    /**Typer for opplysninger hentet fra bidrag-grunnlag*/
    INNTEKT,
    ARBEIDSFORHOLD,
    BARNETILLEGG,
    BARNETILSYN,
    HUSSTANDSMEDLEMMER,
    KONTANTSTØTTE,
    SIVILSTAND,
    UTVIDET_BARNETRYGD,
    SMÅBARNSTILLEGG,

    @Deprecated("", replaceWith = ReplaceWith("BOFORHOLD_BEARBEIDET"))
    BOFORHOLD,

    @Deprecated("", replaceWith = ReplaceWith("INNTEKT_BEARBEIDET"))
    INNTEKTSOPPLYSNINGER,
}

fun Grunnlagsdatatype.getOrMigrate() =
    when (this) {
        Grunnlagsdatatype.BOFORHOLD -> Grunnlagsdatatype.BOFORHOLD_BEARBEIDET
        Grunnlagsdatatype.INNTEKTSOPPLYSNINGER -> Grunnlagsdatatype.INNTEKT_BEARBEIDET
        else -> this
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
enum class Soknadstype {
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
    PA("Privat avtale"),
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

val årsakskoderAvslag =
    listOf(
        ForskuddAarsakType.ANNET_AVSLAG,
        ForskuddAarsakType.PGA_BARNEPENSJ,
        ForskuddAarsakType.BARNS_EKTESKAP,
        ForskuddAarsakType.BARNS_INNTEKT,
        ForskuddAarsakType.PGA_YTELSE_FTRL,
        ForskuddAarsakType.FULLT_UNDERH_OFF,
        ForskuddAarsakType.IKKE_OMSORG,
        ForskuddAarsakType.IKKE_OPPH_I_RIKET,
        ForskuddAarsakType.MANGL_DOK,
        ForskuddAarsakType.PGA_SAMMENFL,
        ForskuddAarsakType.OPPH_UTLAND,
        ForskuddAarsakType.UTENL_YTELSE,
    )
