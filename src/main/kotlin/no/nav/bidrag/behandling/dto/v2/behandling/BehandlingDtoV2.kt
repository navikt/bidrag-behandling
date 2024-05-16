package no.nav.bidrag.behandling.dto.v2.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.sivilstand.response.Status
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class BehandlingDtoV2(
    val id: Long,
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype? = null,
    val engangsbeløptype: Engangsbeløptype? = null,
    val erVedtakFattet: Boolean,
    val erKlageEllerOmgjøring: Boolean,
    val opprettetTidspunkt: LocalDateTime,
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
    val vedtakRefId: Long? = null,
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
    val husstandsbarn: Set<HusstandsbarnGrunnlagDto>,
    val sivilstand: SivilstandAktivGrunnlagDto?,
)

data class IkkeAktiveGrunnlagsdata(
    val inntekter: IkkeAktiveInntekter = IkkeAktiveInntekter(),
    val husstandsbarn: Set<HusstandsbarnGrunnlagDto> = emptySet(),
    val sivilstand: SivilstandIkkeAktivGrunnlagDto? = null,
)

data class IkkeAktiveInntekter(
    val barnetillegg: Set<IkkeAktivInntektDto> = emptySet(),
    val utvidetBarnetrygd: Set<IkkeAktivInntektDto> = emptySet(),
    val kontantstøtte: Set<IkkeAktivInntektDto> = emptySet(),
    val småbarnstillegg: Set<IkkeAktivInntektDto> = emptySet(),
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<IkkeAktivInntektDto> = emptySet(),
) {
    @get:JsonIgnore
    val ingenEndringer
        get() =
            barnetillegg.isEmpty() && utvidetBarnetrygd.isEmpty() &&
                kontantstøtte.isEmpty() && småbarnstillegg.isEmpty() && årsinntekter.isEmpty()
}

@Schema(enumAsRef = true)
enum class GrunnlagInntektEndringstype {
    ENDRING,
    INGEN_ENDRING,
    SLETTET,
    NY,
}

data class IkkeAktivInntektDto(
    val originalId: Long?,
    val innhentetTidspunkt: LocalDateTime,
    val endringstype: GrunnlagInntektEndringstype,
    @Schema(required = true)
    val rapporteringstype: Inntektsrapportering,
    @Schema(required = true)
    val beløp: BigDecimal,
    val periode: ÅrMånedsperiode,
    @Schema(required = true)
    val ident: Personident,
    @Schema(required = false)
    val gjelderBarn: Personident?,
    @Schema(required = true)
    val inntektsposter: Set<InntektspostDtoV2>,
    val inntektsposterSomErEndret: Set<InntektspostEndringDto> = emptySet(),
)

data class InntektspostEndringDto(
    val kode: String,
    val visningsnavn: String,
    val inntektstype: Inntektstype?,
    val beløp: BigDecimal?,
    val endringstype: GrunnlagInntektEndringstype,
)

data class SivilstandAktivGrunnlagDto(
    val grunnlag: Set<SivilstandGrunnlagDto>,
    val innhentetTidspunkt: LocalDateTime,
)

data class SivilstandIkkeAktivGrunnlagDto(
    val sivilstand: List<SivilstandDto> = emptyList(),
    val status: Status,
    val grunnlag: Set<SivilstandGrunnlagDto> = emptySet(),
    val innhentetTidspunkt: LocalDateTime = LocalDateTime.now(),
)

data class HusstandsbarnGrunnlagDto(
    val perioder: Set<HusstandsbarnGrunnlagPeriodeDto>,
    val ident: String? = null,
    val innhentetTidspunkt: LocalDateTime,
) {
    data class HusstandsbarnGrunnlagPeriodeDto(
        @Schema(type = "string", format = "date", example = "2025-01-25")
        @JsonFormat(pattern = "yyyy-MM-dd")
        val datoFom: LocalDate?,
        @Schema(type = "string", format = "date", example = "2025-01-25")
        @JsonFormat(pattern = "yyyy-MM-dd")
        val datoTom: LocalDate?,
        @Schema(required = true)
        val bostatus: Bostatuskode,
    )
}

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

fun Grunnlagsdatatype.tilInntektrapporteringYtelse() =
    when (this) {
        Grunnlagsdatatype.UTVIDET_BARNETRYGD -> Inntektsrapportering.UTVIDET_BARNETRYGD
        Grunnlagsdatatype.SMÅBARNSTILLEGG -> Inntektsrapportering.SMÅBARNSTILLEGG
        Grunnlagsdatatype.BARNETILLEGG -> Inntektsrapportering.BARNETILLEGG
        Grunnlagsdatatype.BARNETILSYN -> Inntektsrapportering.BARNETILSYN
        Grunnlagsdatatype.KONTANTSTØTTE -> Inntektsrapportering.KONTANTSTØTTE
        else -> null
    }
