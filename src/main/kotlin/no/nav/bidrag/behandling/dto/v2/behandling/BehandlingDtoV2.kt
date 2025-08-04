package no.nav.bidrag.behandling.dto.v2.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.gebyr.GebyrValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDto
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.utgift.MaksGodkjentBeløpDto
import no.nav.bidrag.behandling.dto.v2.validering.UtgiftValideringsfeilDto
import no.nav.bidrag.behandling.transformers.PeriodeDeserialiserer
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Periode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.domene.util.visningsnavnIntern
import no.nav.bidrag.organisasjon.dto.SaksbehandlerDto
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class BehandlingDetaljerDtoV2(
    val id: Long,
    val type: TypeBehandling,
    val innkrevingstype: Innkrevingstype = Innkrevingstype.MED_INNKREVING,
    val vedtakstype: Vedtakstype,
    val opprinneligVedtakstype: Vedtakstype? = null,
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
    val vedtakRefId: Int? = null,
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
    val opprettetAv: SaksbehandlerDto,
)

data class LesemodusVedtak(
    val erAvvist: Boolean,
    val opprettetAvBatch: Boolean,
    val erOrkestrertVedtak: Boolean,
)

data class BehandlingDtoV2(
    val id: Long,
    val type: TypeBehandling,
    val lesemodus: LesemodusVedtak? = null,
    val erBisysVedtak: Boolean,
    val erVedtakUtenBeregning: Boolean = false,
    val grunnlagFraVedtaksid: Int? = null,
    val medInnkreving: Boolean,
    val innkrevingstype: Innkrevingstype = Innkrevingstype.MED_INNKREVING,
    val vedtakstype: Vedtakstype,
    val opprinneligVedtakstype: Vedtakstype? = null,
    val stønadstype: Stønadstype? = null,
    val engangsbeløptype: Engangsbeløptype? = null,
    val erVedtakFattet: Boolean,
    val kanBehandlesINyLøsning: Boolean = true,
    val kanIkkeBehandlesBegrunnelse: String? = null,
    val erKlageEllerOmgjøring: Boolean,
    val opprettetTidspunkt: LocalDateTime,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val søktFomDato: LocalDate,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottattdato: LocalDate,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val klageMottattdato: LocalDate? = null,
    val søktAv: SøktAvType,
    val saksnummer: String,
    val søknadsid: Long? = null,
    val søknadRefId: Long? = null,
    val vedtakRefId: Int? = null,
    val behandlerenhet: String,
    val roller: Set<RolleDto>,
    val virkningstidspunkt: VirkningstidspunktDto,
    val virkningstidspunktV2: List<VirkningstidspunktDtoV2> = emptyList(),
    val inntekter: InntekterDtoV2,
    val boforhold: BoforholdDtoV2,
    val gebyr: GebyrDto? = null,
    val aktiveGrunnlagsdata: AktiveGrunnlagsdata,
    val ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
    val feilOppståttVedSisteGrunnlagsinnhenting: Set<Grunnlagsinnhentingsfeil>? = null,
    @Schema(description = "Utgiftsgrunnlag for særbidrag. Vil alltid være null for forskudd og bidrag")
    val utgift: SærbidragUtgifterDto? = null,
    @Schema(description = "Samværsperioder. Vil alltid være null for forskudd og særbidrag")
    val samvær: List<SamværDto>? = null,
    val privatAvtale: List<PrivatAvtaleDto>? = null,
    var underholdskostnader: Set<UnderholdDto> = emptySet(),
) {
    val vedtakstypeVisningsnavn get() = vedtakstype.visningsnavnIntern(opprinneligVedtakstype)
}

data class GebyrDto(
    val gebyrRoller: List<GebyrRolleDto>,
    val valideringsfeil: List<GebyrValideringsfeilDto>? = null,
)

data class GebyrRolleDto(
    val inntekt: GebyrInntektDto,
    val beløpGebyrsats: BigDecimal,
    val beregnetIlagtGebyr: Boolean,
    val endeligIlagtGebyr: Boolean,
    val begrunnelse: String? = null,
    val rolle: RolleDto,
) {
    val erManueltOverstyrt get() = beregnetIlagtGebyr != endeligIlagtGebyr

    data class GebyrInntektDto(
        val skattepliktigInntekt: BigDecimal,
        val maksBarnetillegg: BigDecimal? = null,
    ) {
        val totalInntekt get() = skattepliktigInntekt + (maksBarnetillegg ?: BigDecimal.ZERO)
    }
}

data class PersoninfoDto(
    val id: Long? = null,
    val ident: Personident? = null,
    val navn: String? = null,
    val fødselsdato: LocalDate? = null,
    val kilde: Kilde? = null,
    val medIBehandlingen: Boolean? = null,
)

data class SærbidragUtgifterDto(
    val avslag: Resultatkode? = null,
    val kategori: SærbidragKategoriDto,
    val beregning: UtgiftBeregningDto? = null,
    val maksGodkjentBeløp: MaksGodkjentBeløpDto? = null,
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = false)
    val begrunnelse: BegrunnelseDto,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
    val utgifter: List<UtgiftspostDto> = emptyList(),
    val valideringsfeil: UtgiftValideringsfeilDto?,
    val totalBeregning: List<TotalBeregningUtgifterDto> = emptyList(),
) {
    @Deprecated("Erstattes av begrunnelse")
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = true)
    val notat: BegrunnelseDto = begrunnelse
}

data class TotalBeregningUtgifterDto(
    val betaltAvBp: Boolean,
    val utgiftstype: String,
    val totalKravbeløp: BigDecimal,
    val totalGodkjentBeløp: BigDecimal,
) {
    @get:Schema(name = "utgiftstypeVisningsnavn")
    val utgiftstypeVisningsnavn
        get() =
            try {
                Utgiftstype.valueOf(utgiftstype).visningsnavn.intern
            } catch (e: IllegalArgumentException) {
                utgiftstype
            }
}

data class SærbidragKategoriDto(
    val kategori: Særbidragskategori,
    val beskrivelse: String? = null,
)

data class UtgiftBeregningDto(
    @Schema(description = "Beløp som er direkte betalt av BP")
    val beløpDirekteBetaltAvBp: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Summen av godkjente beløp som brukes for beregningen")
    val totalGodkjentBeløp: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Summen av kravbeløp")
    val totalKravbeløp: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Summen av godkjente beløp som brukes for beregningen")
    val totalGodkjentBeløpBp: BigDecimal? = null,
    @Schema(description = "Summen av godkjent beløp for utgifter BP har betalt plus beløp som er direkte betalt av BP")
    val totalBeløpBetaltAvBp: BigDecimal = (totalGodkjentBeløpBp ?: BigDecimal.ZERO) + beløpDirekteBetaltAvBp,
)

data class UtgiftspostDto(
    @Schema(description = "Når utgifter gjelder. Kan være feks dato på kvittering")
    val dato: LocalDate,
    @Schema(
        description = "Type utgift. Kan feks være hva som ble kjøpt for kravbeløp (bugnad, klær, sko, etc)",
        oneOf = [Utgiftstype::class, String::class],
    )
    val type: String,
    @Schema(description = "Beløp som er betalt for utgiften det gjelder")
    val kravbeløp: BigDecimal,
    @Schema(description = "Beløp som er godkjent for beregningen")
    val godkjentBeløp: BigDecimal = kravbeløp,
    @Schema(description = "Begrunnelse for hvorfor godkjent beløp avviker fra kravbeløp. Må settes hvis godkjent beløp er ulik kravbeløp")
    val kommentar: String,
    @Schema(
        description = "Begrunnelse for hvorfor godkjent beløp avviker fra kravbeløp. Må settes hvis godkjent beløp er ulik kravbeløp",
        deprecated = true,
    )
    val begrunnelse: String = kommentar,
    @Schema(description = "Om utgiften er betalt av BP")
    val betaltAvBp: Boolean = false,
    val id: Long,
) {
    @get:Schema(name = "utgiftstypeVisningsnavn")
    val utgiftstypeVisningsnavn
        get() =
            try {
                Utgiftstype.valueOf(type).visningsnavn.intern
            } catch (e: IllegalArgumentException) {
                type
            }
}

data class AktiveGrunnlagsdata(
    val arbeidsforhold: Set<ArbeidsforholdGrunnlagDto> = emptySet(),
    val husstandsmedlemBM: Set<HusstandsmedlemGrunnlagDto> = emptySet(),
    val husstandsmedlem: Set<HusstandsmedlemGrunnlagDto> = emptySet(),
    val andreVoksneIHusstanden: AndreVoksneIHusstandenGrunnlagDto? = null,
    val sivilstand: SivilstandAktivGrunnlagDto? = null,
    val stønadTilBarnetilsyn: StønadTilBarnetilsynAktiveGrunnlagDto? = null,
) {
    @Deprecated("Erstattes av husstandsmedlem")
    @Schema(description = "Erstattes av husstandsmedlem", deprecated = true)
    val husstandsbarn = husstandsmedlem
}

data class IkkeAktiveGrunnlagsdata(
    val inntekter: IkkeAktiveInntekter = IkkeAktiveInntekter(),
    val husstandsmedlemBM: Set<HusstandsmedlemGrunnlagDto> = emptySet(),
    val husstandsmedlem: Set<HusstandsmedlemGrunnlagDto> = emptySet(),
    val arbeidsforhold: Set<ArbeidsforholdGrunnlagDto> = emptySet(),
    val andreVoksneIHusstanden: AndreVoksneIHusstandenGrunnlagDto? = null,
    val sivilstand: SivilstandIkkeAktivGrunnlagDto? = null,
    val stønadTilBarnetilsyn: StønadTilBarnetilsynIkkeAktiveGrunnlagDto? = null,
) {
    @Deprecated("Erstattes av husstandsmedlem")
    @Schema(description = "Erstattes av husstandsmedlem", deprecated = true)
    val husstandsbarn = husstandsmedlem
}

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
    val rolle: RolleDto,
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

data class StønadTilBarnetilsynAktiveGrunnlagDto(
    val grunnlag: Map<Personident, Set<BarnetilsynGrunnlagDto>> = emptyMap(),
    val innhentetTidspunkt: LocalDateTime = LocalDateTime.now(),
)

data class StønadTilBarnetilsynIkkeAktiveGrunnlagDto(
    val stønadTilBarnetilsyn: Map<Personident, Set<StønadTilBarnetilsynDto>> = emptyMap(),
    val grunnlag: Map<Personident, Set<BarnetilsynGrunnlagDto>> = emptyMap(),
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

data class AndreVoksneIHusstandenGrunnlagDto(
    val perioder: Set<PeriodeAndreVoksneIHusstanden>,
    val innhentet: LocalDateTime,
)

data class PeriodeAndreVoksneIHusstanden(
    val periode: ÅrMånedsperiode,
    val status: Bostatuskode,
    @Schema(description = "Total antall husstandsmedlemmer som bor hos BP for gjeldende periode")
    val totalAntallHusstandsmedlemmer: Int,
    @Schema(
        description =
            "Detaljer om husstandsmedlemmer som bor hos BP for gjeldende periode. " +
                "Antall hustandsmedlemmer er begrenset til maks 10 personer",
    )
    val husstandsmedlemmer: List<AndreVoksneIHusstandenDetaljerDto> = emptyList(),
)

data class AndreVoksneIHusstandenDetaljerDto(
    val navn: String,
    val fødselsdato: LocalDate?,
    val harRelasjonTilBp: Boolean,
    @Schema(description = "Relasjon til BP. Brukes for debugging", deprecated = true)
    val relasjon: Familierelasjon,
    val erBeskyttet: Boolean = false,
)

@Schema(enumAsRef = true, name = "OpplysningerType")
enum class Grunnlagsdatatype(
    val behandlingstypeMotRolletyper: Map<TypeBehandling, Set<Rolletype>> = emptyMap(),
    val erGjeldende: Boolean = true,
) {
    ARBEIDSFORHOLD(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
        ),
    ),
    BARNETILLEGG(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
        ),
    ),
    BARNETILSYN(mapOf(TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER))),
    ANDRE_BARN(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    BOFORHOLD(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSPLIKTIG),
        ),
    ),
    BOFORHOLD_BM_SØKNADSBARN(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.FORSKUDD to setOf(),
            TypeBehandling.SÆRBIDRAG to setOf(),
        ),
    ),
    BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSPLIKTIG),
        ),
    ),
    KONTANTSTØTTE(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    SIVILSTAND(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    UTVIDET_BARNETRYGD(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    SMÅBARNSTILLEGG(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    SKATTEPLIKTIGE_INNTEKTER(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
        ),
    ),
    SUMMERTE_MÅNEDSINNTEKTER(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
        ),
    ),
    TILLEGGSSTØNAD(mapOf(TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER))),
    MANUELLE_VEDTAK(mapOf(), erGjeldende = false),
    BELØPSHISTORIKK_BIDRAG(mapOf(), erGjeldende = false),
    BELØPSHISTORIKK_FORSKUDD(mapOf(), erGjeldende = false),
    BELØPSHISTORIKK_BIDRAG_18_ÅR(mapOf(), erGjeldende = false),

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER")
    @Schema(deprecated = true)
    AINNTEKT(erGjeldende = false),

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER")
    @Schema(deprecated = true)
    SKATTEGRUNNLAG(erGjeldende = false),

    @Deprecated("Erstattes av BOFORHOLD i kombiansjon med erBearbeidet = true")
    @Schema(deprecated = true)
    BOFORHOLD_BEARBEIDET(erGjeldende = false),

    @Deprecated("Erstattes av BOFORHOLD i kombinasjon med erBearbeidet = false")
    @Schema(description = "Erstattes av BOFORHOLD i kombinasjon med erBearbeidet = false", deprecated = true)
    HUSSTANDSMEDLEMMER(erGjeldende = false),

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER i kombinasjon med erBearbeidet = true")
    @Schema(deprecated = true)
    INNTEKT_BEARBEIDET(erGjeldende = false),

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER i kombinasjon med erBearbeidet = false")
    @Schema(deprecated = true)
    INNTEKTSOPPLYSNINGER(erGjeldende = false),

    @Deprecated("Erstattes av SKATTEPLIKTIGE_INNTEKTER i kombinasjon med erBearbeidet = true")
    @Schema(deprecated = true)
    SUMMERTE_ÅRSINNTEKTER(erGjeldende = false),

    ;

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun grunnlagsdatatypeobjekter(
            behandlingstype: TypeBehandling,
            rolletype: Rolletype? = null,
        ): Set<Grunnlagsdatatype> =
            when (rolletype != null) {
                true ->
                    entries
                        .filter { it.behandlingstypeMotRolletyper.keys.contains(behandlingstype) }
                        .filter { it.behandlingstypeMotRolletyper[behandlingstype]?.any { it == rolletype } == true }
                        .toSet()

                false -> entries.filter { it.behandlingstypeMotRolletyper.keys.contains(behandlingstype) }.toSet()
            }

        @OptIn(ExperimentalStdlibApi::class)
        fun gjeldende() = Grunnlagsdatatype.entries.filter { it.erGjeldende }
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

fun Grunnlagsdatatype.innhentesForRolle2(behandling: Behandling) = this.behandlingstypeMotRolletyper[behandling.tilType()]

fun Grunnlagsdatatype.innhentesForRolle(behandling: Behandling) =
    when (this) {
        Grunnlagsdatatype.BARNETILSYN,
        Grunnlagsdatatype.BOFORHOLD,
        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
        Grunnlagsdatatype.ANDRE_BARN,
        -> {
            val t = this.behandlingstypeMotRolletyper[behandling.tilType()]
            t?.let {
                when (it.first()) {
                    Rolletype.BIDRAGSMOTTAKER -> behandling.bidragsmottaker
                    Rolletype.BIDRAGSPLIKTIG -> behandling.bidragspliktig
                    else -> null
                }
            }
        }
        Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN ->
            when (behandling.tilType()) {
                TypeBehandling.BIDRAG -> behandling.bidragsmottaker
                else -> null
            }

        else -> null
    }
