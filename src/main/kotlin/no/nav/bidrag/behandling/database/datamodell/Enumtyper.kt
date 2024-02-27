package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype

@Schema(enumAsRef = true)
enum class Kilde {
    MANUELL,
    OFFENTLIG,
}

data class Grunnlagstype(
    val type: Grunnlagsdatatype,
    val erBearbeidet: Boolean,
)

@Schema(enumAsRef = true, name = "OpplysningerType")
enum class Grunnlagsdatatype {
    INNTEKT,
    ARBEIDSFORHOLD,
    BARNETILLEGG,
    BARNETILSYN,
    BOFORHOLD,
    KONTANTSTØTTE,
    SIVILSTAND,
    UTVIDET_BARNETRYGD,
    SMÅBARNSTILLEGG,

    @Deprecated("Erstattes av BOFORHOLD i kombiansjon med erBearbeidet = true")
    BOFORHOLD_BEARBEIDET,

    @Deprecated("Erstattes av BOFORHOLD i kombinasjon med erBearbeidet = false")
    HUSSTANDSMEDLEMMER,

    @Deprecated("Erstattes av INNTEKT i kombinasjon med erBearbeidet = true")
    INNTEKT_BEARBEIDET,

    @Deprecated("Erstattes av INNTEKT i kombinasjon med erBearbeidet = false")
    INNTEKTSOPPLYSNINGER,

}

fun String.tilÅrsakstype(): VirkningstidspunktÅrsakstype? {
    return try {
        VirkningstidspunktÅrsakstype.valueOf(this)
    } catch (e: IllegalArgumentException) {
        return VirkningstidspunktÅrsakstype.entries.find { it.legacyKode == this }
    }
}
