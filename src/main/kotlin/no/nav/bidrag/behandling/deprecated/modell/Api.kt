package no.nav.bidrag.behandling.deprecated.modell

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype

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
enum class OpplysningerType {
    /**Typer for opplysninger som er bearbeidet av frontend eller bidrag-inntekt*/
    INNTEKT_BEARBEIDET,
    BOFORHOLD_BEARBEIDET,

    /**Typer for opplysninger hentet fra bidrag-grunnlag*/
    INNTEKT,
    ARBEIDSFORHOLD,
    HUSSTANDSMEDLEMMER,
    SIVILSTAND,

    @Deprecated("", replaceWith = ReplaceWith("BOFORHOLD_BEARBEIDET"))
    BOFORHOLD,

    @Deprecated("", replaceWith = ReplaceWith("INNTEKT_BEARBEIDET"))
    INNTEKTSOPPLYSNINGER,
}

fun Grunnlagsdatatype.tilOpplysningerType(): OpplysningerType = OpplysningerType.valueOf(this.name)

fun OpplysningerType.tilGrunnlagstype(): Grunnlagsdatatype = Grunnlagsdatatype.valueOf(this.name)
