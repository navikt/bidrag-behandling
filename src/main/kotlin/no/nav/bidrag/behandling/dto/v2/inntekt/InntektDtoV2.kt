package no.nav.bidrag.behandling.dto.v2.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.beregning.felles.InntektPerBarn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class InntektDtoV2(
    val id: Long? = null,
    @Schema(required = true)
    val taMed: Boolean,
    @Schema(required = true)
    val rapporteringstype: Inntektsrapportering,
    @Schema(required = true)
    val beløp: BigDecimal,
    @Schema(type = "string", format = "date", example = "2024-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2024-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligTom: LocalDate?,
    @Schema(required = true)
    val ident: Personident,
    @Schema(required = false)
    val gjelderBarn: Personident?,
    @Schema(required = true)
    val kilde: Kilde = Kilde.MANUELL,
    @Schema(required = true)
    val inntektsposter: Set<InntektspostDtoV2>,
    @Schema(required = true)
    val inntektstyper: Set<Inntektstype> = emptySet(),
    val historisk: Boolean? = false,
) {
    @get:Schema(description = "Avrundet månedsbeløp for barnetillegg")
    val månedsbeløp: BigDecimal?
        get() =
            if (Inntektsrapportering.BARNETILLEGG == rapporteringstype) {
                beløp.divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
            } else {
                null
            }
}

data class InntekterDtoV2(
    val barnetillegg: Set<InntektDtoV2> = emptySet(),
    val utvidetBarnetrygd: Set<InntektDtoV2> = emptySet(),
    val kontantstøtte: Set<InntektDtoV2> = emptySet(),
    val månedsinntekter: Set<InntektDtoV2> = emptySet(),
    val småbarnstillegg: Set<InntektDtoV2> = emptySet(),
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<InntektDtoV2> = emptySet(),
    val beregnetInntekter: List<BeregnetInntekterDto> = emptyList(),
    @Schema(description = "Saksbehandlers begrunnelser", deprecated = false)
    val begrunnelser: Set<BegrunnelseDto> = emptySet(),
    val valideringsfeil: InntektValideringsfeilDto,
) {
    @Deprecated("Bruk begrunnelser for begrunnelse per rolle")
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = true)
    val notat: BegrunnelseDto =
        if (begrunnelser.isNotEmpty()) {
            begrunnelser.find { Rolletype.BIDRAGSMOTTAKER == it.gjelder?.rolletype } ?: begrunnelser.first()
        } else {
            BegrunnelseDto("")
        }
}

data class BeregnetInntekterDto(
    val ident: Personident,
    val rolle: Rolletype,
    val inntekter: List<InntektPerBarn> = emptyList(),
)

data class OppdatereInntektRequest(
    @Schema(description = "Angi periodeinformasjon for inntekt")
    val oppdatereInntektsperiode: OppdaterePeriodeInntekt? = null,
    @Schema(description = "Opprette eller oppdatere manuelt oppgitt inntekt")
    val oppdatereManuellInntekt: OppdatereManuellInntekt? = null,
    @Schema(description = "Oppdatere begrunnelse for inntekt")
    val oppdatereBegrunnelse: OppdatereBegrunnelse? = null,
    @Schema(description = "Deprekert, bruk oppdatereBegrunnelse i stedet")
    val oppdatereNotat: OppdatereBegrunnelse? = null,
    @Schema(description = "Angi id til inntekt som skal slettes")
    val sletteInntekt: Long? = null,
) {
    // TODO: Fjerne når migrering til oppdatereBegrunnelse er fullført
    val henteOppdatereBegrunnelse = oppdatereBegrunnelse ?: oppdatereNotat
}

data class OppdatereInntektResponse(
    @Schema(description = "Inntekt som ble oppdatert")
    val inntekt: InntektDtoV2?,
    val gebyr: GebyrDto? = null,
    val beregnetGebyrErEndret: Boolean = false,
    @Schema(description = "Periodiserte inntekter")
    val beregnetInntekter: List<BeregnetInntekterDto> = emptyList(),
    @Schema(description = "Oppdatert begrunnelse")
    val begrunnelse: String? = null,
    val valideringsfeil: InntektValideringsfeilDto,
) {
    @Deprecated("Erstattes av begrunnelse")
    @Schema(description = "Oppdatert begrunnelse", deprecated = true)
    val notat: String? = begrunnelse
}

@Deprecated("Erstattes av OppdatereInntektRequest")
@Schema(description = "Erstattes av OppdatereInntektRequest", deprecated = true)
data class OppdatereInntekterRequestV2(
    @Schema(description = "Angi periodeinformasjon for inntekter")
    val oppdatereInntektsperioder: Set<OppdaterePeriodeInntekt> = emptySet(),
    @Schema(description = "Opprette eller oppdatere manuelt oppgitte inntekter")
    val oppdatereManuelleInntekter: Set<OppdatereManuellInntekt> = emptySet(),
    @Schema(description = "Angi id til inntekter som skal slettes")
    val sletteInntekter: Set<Long> = emptySet(),
    val notat: OppdatereBegrunnelse? = null,
)

data class OppdaterePeriodeInntekt(
    @Schema(description = "Id til inntekt som skal oppdateres")
    val id: Long,
    @Schema(description = "Anig om inntekten skal inkluderes i beregning")
    val taMedIBeregning: Boolean = false,
    @Schema(description = "Angi periode inntekten skal dekke ved beregnings")
    val angittPeriode: Datoperiode? = null,
)

data class OppdatereManuellInntekt(
    @Schema(
        description = "Inntektens databaseid. Oppgis ikke ved opprettelse av inntekt.",
        required = false,
    )
    val id: Long? = null,
    @Schema(description = "Angir om inntekten skal inkluderes i beregning. Hvis ikke spesifisert inkluderes inntekten.")
    val taMed: Boolean = true,
    @Schema(
        description = "Angir inntektens rapporteringstype.",
        required = true,
        example = "KONTANTSTØTTE",
    )
    val type: Inntektsrapportering,
    @Schema(description = "Inntektens beløp i norske kroner", required = true)
    val beløp: BigDecimal,
    @Schema(type = "String", format = "date", example = "2024-01-01", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate,
    @Schema(type = "String", format = "date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @Schema(
        description = "Ident til personen inntekten gjenlder for.",
        type = "String",
        example = "12345678910",
        required = true,
    )
    val ident: Personident,
    @Schema(
        description =
            "Ident til barnet en ytelse gjelder for. " +
                "sBenyttes kun for ytelser som er koblet til ett spesifikt barn, f.eks kontantstøtte",
        type = "String",
        example = "12345678910",
        required = false,
    )
    val gjelderBarn: Personident? = null,
    @Schema(description = "Spesifisere inntektstype for detaljpost")
    val inntektstype: Inntektstype? = null,
)
