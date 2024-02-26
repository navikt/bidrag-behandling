package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype

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
    AINNTEKT,
    SKATTEGRUNNLAG,
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

fun String.tilÅrsakstype(): VirkningstidspunktÅrsakstype? {
    return try {
        VirkningstidspunktÅrsakstype.valueOf(this)
    } catch (e: IllegalArgumentException) {
        return VirkningstidspunktÅrsakstype.entries.find { it.legacyKode == this }
//        VirkningstidspunktÅrsakstype.entries.find { it.legacyKode == this }
//            ?: when (ForskuddAarsakType.valueOf(this)) {
//                ForskuddAarsakType.PGA_BARNEPENSJ -> VirkningstidspunktÅrsakstype.PÅ_GRUNN_AV_BARNEPENSJON
//                ForskuddAarsakType.BARNS_EKTESKAP -> VirkningstidspunktÅrsakstype.BARNETS_EKTESKAP
//                ForskuddAarsakType.BARNS_INNTEKT -> VirkningstidspunktÅrsakstype.BARNETS_INNTEKT
//                ForskuddAarsakType.PGA_YTELSE_FTRL -> VirkningstidspunktÅrsakstype.PÅ_GRUNN_AV_YTELSE_FRA_FOLKETRYGDEN
//                ForskuddAarsakType.FULLT_UNDERH_OFF -> VirkningstidspunktÅrsakstype.FULLT_UNDERHOLDT_AV_OFFENTLIG
//                ForskuddAarsakType.IKKE_OMSORG -> VirkningstidspunktÅrsakstype.IKKE_OMSORG
//                ForskuddAarsakType.IKKE_OPPH_I_RIKET -> VirkningstidspunktÅrsakstype.IKKE_OPPHOLD_I_RIKET
//                ForskuddAarsakType.MANGL_DOK -> VirkningstidspunktÅrsakstype.MANGLENDE_DOKUMENTASJON
//                ForskuddAarsakType.PGA_SAMMENFL -> VirkningstidspunktÅrsakstype.PÅ_GRUNN_AV_SAMMENFLYTTING
//                ForskuddAarsakType.OPPH_UTLAND -> VirkningstidspunktÅrsakstype.OPPHOLD_I_UTLANDET
//                ForskuddAarsakType.UTENL_YTELSE -> VirkningstidspunktÅrsakstype.UTENLANDSK_YTELSE
//                else -> null
//            }
    }
}
