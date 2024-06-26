package no.nav.bidrag.behandling.dto.v2.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.behandling.transformers.PeriodeDeserialiserer
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.SærbidragKategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Periode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class BehandlingDetaljerDtoV2(
    val id: Long,
    val type: TypeBehandling,
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
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningstidspunkt: LocalDate? = null,
    @Schema(name = "årsak", enumAsRef = true)
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @Schema(enumAsRef = true)
    val avslag: Resultatkode? = null,
    val kategori: SærbidragKategoriDto? = null,
)

data class BehandlingDtoV2(
    val id: Long,
    val type: TypeBehandling,
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
    val virkningstidspunkt: VirkningstidspunktDto,
    val inntekter: InntekterDtoV2,
    val boforhold: BoforholdDtoV2,
    val aktiveGrunnlagsdata: AktiveGrunnlagsdata,
    val ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
    val feilOppståttVedSisteGrunnlagsinnhenting: Set<Grunnlagsinnhentingsfeil>? = null,
    @Schema(description = "Utgiftsgrunnlag for særbidrag. Vil alltid være null for forskudd og bidrag")
    val utgift: SærbidragUtgifterDto? = null,
)

data class SærbidragUtgifterDto(
    val avslag: Resultatkode? = null,
    val kategori: SærbidragKategoriDto,
    val beregning: UtgiftBeregningDto? = null,
    val notat: BehandlingNotatDto,
    val utgifter: List<UtgiftspostDto> = emptyList(),
)

data class SærbidragKategoriDto(
    val kategori: SærbidragKategori,
    val beskrivelse: String? = null,
)

data class UtgiftBeregningDto(
    @Schema(description = "Beløp som er direkte betalt av BP")
    val beløpDirekteBetaltAvBp: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Summen av godkjent beløp for utgifter BP har betalt og beløp som er direkte betalt av BP")
    val totalBeløpBetaltAvBp: BigDecimal? = null,
    @Schema(description = "Summen av godkjente beløp som brukes for beregningen")
    val totalGodkjentBeløp: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Summen av godkjente beløp som brukes for beregningen")
    val totalGodkjentBeløpBp: BigDecimal? = null,
)

data class UtgiftspostDto(
    @Schema(description = "Når utgifter gjelder. Kan være feks dato på kvittering")
    val dato: LocalDate,
    @Schema(description = "Type utgift. Kan feks være hva som ble kjøpt for kravbeløp (bugnad, klær, sko, etc)")
    val type: Utgiftstype,
    @Schema(description = "Beløp som er betalt for utgiften det gjelder")
    val kravbeløp: BigDecimal,
    @Schema(description = "Beløp som er godkjent for beregningen")
    val godkjentBeløp: BigDecimal = kravbeløp,
    @Schema(description = "Begrunnelse for hvorfor godkjent beløp avviker fra kravbeløp. Må settes hvis godkjent beløp er ulik kravbeløp")
    val begrunnelse: String,
    @Schema(description = "Om utgiften er betalt av BP")
    val betaltAvBp: Boolean = false,
    val id: Long,
)

data class AktiveGrunnlagsdata(
    val arbeidsforhold: Set<ArbeidsforholdGrunnlagDto>,
    val husstandsmedlem: Set<HusstandsmedlemGrunnlagDto>,
    val sivilstand: SivilstandAktivGrunnlagDto?,
) {
    @Deprecated("Erstattes av husstandsmedlem")
    val husstandsbarn = husstandsmedlem
}

data class IkkeAktiveGrunnlagsdata(
    val inntekter: IkkeAktiveInntekter = IkkeAktiveInntekter(),
    @Deprecated("Erstattes av husstandsmedlem")
    val husstandsbarn: Set<HusstandsmedlemGrunnlagDto> = emptySet(),
    val husstandsmedlem: Set<HusstandsmedlemGrunnlagDto> = emptySet(),
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
            barnetillegg.isEmpty() &&
                utvidetBarnetrygd.isEmpty() &&
                kontantstøtte.isEmpty() &&
                småbarnstillegg.isEmpty() &&
                årsinntekter.isEmpty()
}

data class Grunnlagsinnhentingsfeil(
    val rolleid: Long,
    val grunnlagsdatatype: Grunnlagsdatatype,
    val feilmelding: String,
    @JsonDeserialize(using = PeriodeDeserialiserer::class)
    val periode: Periode<LocalDate>? = null,
)

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
    val grunnlag: Set<SivilstandGrunnlagDto> = emptySet(),
    val innhentetTidspunkt: LocalDateTime = LocalDateTime.now(),
)

data class HusstandsmedlemGrunnlagDto(
    val perioder: Set<BostatusperiodeGrunnlagDto>,
    val ident: String? = null,
    val innhentetTidspunkt: LocalDateTime,
) {
    data class BostatusperiodeGrunnlagDto(
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
enum class Grunnlagsdatatype(
    val behandlinstypeMotRolletyper: Map<TypeBehandling, Set<Rolletype>> = emptyMap(),
) {
    ARBEIDSFORHOLD(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    BARNETILLEGG(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    BARNETILSYN(emptyMap()),
    BOFORHOLD(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                ),
        ),
    ),
    KONTANTSTØTTE(mapOf(TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER))),
    SIVILSTAND(mapOf(TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER))),
    UTVIDET_BARNETRYGD(mapOf(TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER))),
    SMÅBARNSTILLEGG(mapOf(TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER))),
    SKATTEPLIKTIGE_INNTEKTER(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    SUMMERTE_MÅNEDSINNTEKTER(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
        ),
    ),

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER")
    AINNTEKT(),

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

    ;

    companion object {
        fun grunnlagsdatatypeobjekter(
            behandlingstype: TypeBehandling,
            rolletype: Rolletype? = null,
        ): Set<Grunnlagsdatatype> =
            when (rolletype != null) {
                true ->
                    entries
                        .filter { it.behandlinstypeMotRolletyper.keys.contains(behandlingstype) }
                        .filter { it.behandlinstypeMotRolletyper.values.any { roller -> roller.contains(rolletype) } }
                        .toSet()

                false -> entries.filter { it.behandlinstypeMotRolletyper.keys.contains(behandlingstype) }.toSet()
            }
    }
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
