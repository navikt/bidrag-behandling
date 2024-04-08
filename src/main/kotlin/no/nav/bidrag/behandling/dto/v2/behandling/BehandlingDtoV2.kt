package no.nav.bidrag.behandling.dto.v2.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.time.LocalDate

data class BehandlingDtoV2(
    val id: Long,
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype? = null,
    val engangsbeløptype: Engangsbeløptype? = null,
    val erVedtakFattet: Boolean,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val søktFomDato: LocalDate,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottattdato: LocalDate,
    val søktAv: SøktAvType,
    val saksnummer: String,
    val søknadsid: Long,
    val søknadRefId: Long? = null,
    val behandlerenhet: String,
    val roller: Set<RolleDto>,
    val grunnlagspakkeid: Long? = null,
    val virkningstidspunkt: VirkningstidspunktDto,
    val inntekter: InntekterDtoV2,
    val boforhold: BoforholdDtoV2,
    val aktiveGrunnlagsdata: AktiveGrunnlagsdata,
    val ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
)

data class AktiveGrunnlagsdata(
    val arbeidsforhold: Set<ArbeidsforholdGrunnlagDto>,
    val husstandsbarn: Set<HusstandsbarnDtoV2>,
    val sivilstand: Set<SivilstandGrunnlagDto>,
)

data class IkkeAktiveGrunnlagsdata(
    val inntekter: IkkeAktiveInntekter,
    val husstandsbarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
)

data class IkkeAktiveInntekter(
    val barnetillegg: Set<InntektDtoV2> = emptySet(),
    val utvidetBarnetrygd: Set<InntektDtoV2> = emptySet(),
    val kontantstøtte: Set<InntektDtoV2> = emptySet(),
    val månedsinntekter: Set<InntektDtoV2> = emptySet(),
    val småbarnstillegg: Set<InntektDtoV2> = emptySet(),
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<InntektDtoV2> = emptySet(),
)

data class Grunnlagstype(
    val type: Grunnlagsdatatype,
    val erBearbeidet: Boolean,
)

@Schema(enumAsRef = true, name = "OpplysningerType")
enum class Grunnlagsdatatype {
    ARBEIDSFORHOLD,
    BARNETILLEGG,
    BARNETILSYN,
    BOFORHOLD,
    KONTANTSTØTTE,
    SIVILSTAND,
    UTVIDET_BARNETRYGD,
    SMÅBARNSTILLEGG,
    SKATTEPLIKTIGE_INNTEKTER,
    SUMMERTE_MÅNEDSINNTEKTER,

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER")
    AINNTEKT,

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER")
    SKATTEGRUNNLAG,

    @Deprecated("Erstattes av BOFORHOLD i kombiansjon med erBearbeidet = true")
    BOFORHOLD_BEARBEIDET,

    @Deprecated("Erstattes av BOFORHOLD i kombinasjon med erBearbeidet = false")
    HUSSTANDSMEDLEMMER,

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER i kombinasjon med erBearbeidet = true")
    INNTEKT_BEARBEIDET,

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER i kombinasjon med erBearbeidet = false")
    INNTEKTSOPPLYSNINGER,

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER i kombinasjon med erBearbeidet = true")
    SUMMERTE_ÅRSINNTEKTER,
}

fun Grunnlagsdatatype.getOrMigrate() =
    when (this) {
        Grunnlagsdatatype.AINNTEKT, Grunnlagsdatatype.SKATTEGRUNNLAG, Grunnlagsdatatype.INNTEKTSOPPLYSNINGER,
        Grunnlagsdatatype.INNTEKT_BEARBEIDET,
        -> Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER

        Grunnlagsdatatype.HUSSTANDSMEDLEMMER, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET -> Grunnlagsdatatype.BOFORHOLD
        else -> this
    }
