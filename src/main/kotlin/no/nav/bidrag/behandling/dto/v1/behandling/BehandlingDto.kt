package no.nav.bidrag.behandling.dto.v1.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.OpphørsdetaljerRolleDto.EksisterendeOpphørsvedtakDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.validering.AndreVoksneIHusstandenPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandPeriodeseringsfeil
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.util.visningsnavn
import java.time.LocalDate
import java.time.LocalDateTime

data class OppdaterManuellVedtakResponse(
    val erVedtakUtenBeregning: Boolean,
    var underholdskostnader: Set<UnderholdDto> = emptySet(),
)

data class OppdaterManuellVedtakRequest(
    val barnId: Long,
    val vedtaksid: Long?,
)

data class ManuellVedtakResponse(
    val manuelleVedtak: List<ManuellVedtakDto>,
)

data class ManuellVedtakDto(
    val vedtaksid: Long,
    val barnId: Long,
    val fattetTidspunkt: LocalDateTime,
    val virkningsDato: LocalDate,
    val vedtakstype: Vedtakstype,
    @JsonIgnore
    val privatAvtale: Boolean,
    @JsonIgnore
    val begrensetRevurdering: Boolean,
    val resultatSistePeriode: String,
    val manglerGrunnlag: Boolean = false,
) {
    val søknadstype get() =
        when {
            privatAvtale -> "Privat avtale"
            begrensetRevurdering -> "Begrenset revurdering"
            else -> vedtakstype.visningsnavn.intern
        }
}

data class VirkningstidspunktDtoV2(
    val rolle: RolleDto,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningstidspunkt: LocalDate? = null,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligVirkningstidspunkt: LocalDate? = null,
    @Schema(name = "årsak", enumAsRef = true)
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @Schema(enumAsRef = true)
    val avslag: Resultatkode? = null,
    @Schema(description = "Saksbehandlers begrunnelse")
    val begrunnelse: BegrunnelseDto,
    val begrunnelseVurderingAvSkolegang: BegrunnelseDto? = null,
    val harLøpendeBidrag: Boolean = false,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
    val opphørsdato: LocalDate? = null,
    val beregnTilDato: LocalDate? = null,
    val globalOpphørsdato: LocalDate? = null,
    @Schema(description = "Løpende opphørsvedtak detaljer. Er satt hvis det finnes en vedtak hvor bidraget er opphørt")
    val eksisterendeOpphør: EksisterendeOpphørsvedtakDto? = null,
    @Schema(description = "Manuell vedtak valgt for beregning av aldersjustering")
    val grunnlagFraVedtak: Long? = null,
    val erVurderingAvSkolegangPåkrevd: Boolean = false,
    val manuelleVedtak: List<ManuellVedtakDto> = emptyList(),
) {
    @Deprecated("Bruk begrunnelse")
    @Schema(description = "Bruk begrunnelse", deprecated = true)
    val notat: BegrunnelseDto = begrunnelse
}

data class VirkningstidspunktDto(
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningstidspunkt: LocalDate? = null,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligVirkningstidspunkt: LocalDate? = null,
    @Schema(name = "årsak", enumAsRef = true)
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @Schema(enumAsRef = true)
    val avslag: Resultatkode? = null,
    @Schema(description = "Saksbehandlers begrunnelse")
    val begrunnelse: BegrunnelseDto,
    val harLøpendeBidrag: Boolean = false,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
    val opphør: OpphørsdetaljerDto? = null,
) {
    @Deprecated("Bruk begrunnelse")
    @Schema(description = "Bruk begrunnelse", deprecated = true)
    val notat: BegrunnelseDto = begrunnelse
}

data class OpphørsdetaljerDto(
    val opphørsdato: LocalDate? = null,
    val opphørRoller: List<OpphørsdetaljerRolleDto>,
)

data class OpphørsdetaljerRolleDto(
    val rolle: RolleDto,
    val opphørsdato: LocalDate? = null,
    @Schema(description = "Løpende opphørsvedtak detaljer. Er satt hvis det finnes en vedtak hvor bidraget er opphørt")
    val eksisterendeOpphør: EksisterendeOpphørsvedtakDto? = null,
) {
    data class EksisterendeOpphørsvedtakDto(
        val vedtaksid: Int,
        val opphørsdato: LocalDate,
        val vedtaksdato: LocalDate,
    )
}

data class BoforholdValideringsfeil(
    val andreVoksneIHusstanden: AndreVoksneIHusstandenPeriodeseringsfeil? = null,
    val husstandsmedlem: List<BoforholdPeriodeseringsfeil>? = emptyList(),
    val sivilstand: SivilstandPeriodeseringsfeil? = null,
)

data class BegrunnelseDto(
    val innhold: String,
    val gjelder: RolleDto? = null,
) {
    @Deprecated("Bruk innhold")
    @Schema(description = "Bruk innhold", deprecated = true)
    val kunINotat: String = innhold
}
