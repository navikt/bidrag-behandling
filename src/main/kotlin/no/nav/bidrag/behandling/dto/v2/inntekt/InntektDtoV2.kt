package no.nav.bidrag.behandling.dto.v2.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDto
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDtoV3
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilV2Dto
import no.nav.bidrag.domene.enums.diverse.InntektBeløpstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.rolle.Rolle
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class InntektDtoV2(
    val id: Long? = null,
    @get:Schema(required = true)
    val taMed: Boolean,
    @get:Schema(required = true)
    val rapporteringstype: Inntektsrapportering,
    @get:Schema(required = true)
    val beløp: BigDecimal,
    @get:Schema(type = "string", format = "date", example = "2024-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @get:Schema(type = "string", format = "date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @get:Schema(type = "string", format = "date", example = "2024-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligFom: LocalDate?,
    @get:Schema(type = "string", format = "date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligTom: LocalDate?,
    @get:Schema(required = false, deprecated = true)
    @Deprecated("Bruk gjelderRolleId")
    val ident: Personident?,
    @get:Schema(required = true)
    val gjelderRolleId: Long?,
    @get:Schema(required = false, deprecated = true)
    @Deprecated("Bruk gjelderBarnId")
    val gjelderBarn: Personident?,
    val gjelderBarnId: Long?,
    @get:Schema(required = true)
    val kilde: Kilde = Kilde.MANUELL,
    @get:Schema(required = true)
    val inntektsposter: Set<InntektspostDtoV2>,
    @get:Schema(required = true)
    val inntektstyper: Set<Inntektstype> = emptySet(),
    val historisk: Boolean? = false,
) {
    fun gjelderRolle(rolle: no.nav.bidrag.behandling.database.datamodell.Rolle) =
        if (gjelderRolleId !=
            null
        ) {
            rolle.id == gjelderRolleId
        } else {
            ident?.verdi == rolle.ident
        }

    val skatteprosent get() =
        if (rapporteringstype == Inntektsrapportering.BARNETILLEGG) {
            inntektsposter.firstOrNull()?.skatteprosent
        } else {
            null
        }
    val beløpstype get() =
        if (rapporteringstype == Inntektsrapportering.BARNETILLEGG) {
            if (inntektsposter.firstOrNull()?.beløpstype == null ||
                inntektsposter.firstOrNull()?.beløpstype == InntektBeløpstype.ÅRSBELØP
            ) {
                InntektBeløpstype.MÅNEDSBELØP
            } else {
                inntektsposter.firstOrNull()?.beløpstype
            }
        } else {
            InntektBeløpstype.ÅRSBELØP
        }

    @get:Schema(description = "Avrundet månedsbeløp for barnetillegg")
    val beløpMånedDagsats: BigDecimal?
        get() =
            when (beløpstype) {
                InntektBeløpstype.MÅNEDSBELØP -> månedsbeløp
                InntektBeløpstype.DAGSATS -> dagsats
                else -> null
            }

    @get:Schema(description = "Avrundet månedsbeløp for barnetillegg")
    val månedsbeløp: BigDecimal?
        get() =
            run {
                val beløpstype = inntektsposter.firstOrNull()?.beløpstype
                if (Inntektsrapportering.BARNETILLEGG == rapporteringstype &&
                    beløpstype == InntektBeløpstype.MÅNEDSBELØP
                ) {
                    inntektsposter.first().beløp
                } else if (Inntektsrapportering.BARNETILLEGG == rapporteringstype &&
                    (beløpstype == null || beløpstype == InntektBeløpstype.ÅRSBELØP)
                ) {
                    beløp.divide(BigDecimal(12), 2, RoundingMode.HALF_UP)
                } else {
                    null
                }
            }

    @get:Schema(description = "Avrundet dagsats for barnetillegg")
    val dagsats: BigDecimal?
        get() =
            if (Inntektsrapportering.BARNETILLEGG == rapporteringstype &&
                inntektsposter.firstOrNull()?.beløpstype == InntektBeløpstype.DAGSATS
            ) {
                inntektsposter.first().beløp
            } else {
                null
            }
}

data class InntekterDtoRolle(
    val gjelder: RolleDto,
    val inntekter: InntekterDtoV3,
)

data class InntektBarn(
    val gjelderBarn: RolleDto,
    val inntekter: Set<InntektDtoV2> = emptySet(),
)

data class InntekterDtoV3(
    val barnetillegg: Collection<InntektBarn> = emptySet(),
    val utvidetBarnetrygd: Set<InntektDtoV2> = emptySet(),
    val kontantstøtte: Collection<InntektBarn> = emptySet(),
    val månedsinntekter: Set<InntektDtoV2> = emptySet(),
    val småbarnstillegg: Set<InntektDtoV2> = emptySet(),
    @get:JsonProperty("årsinntekter")
    @get:Schema(name = "årsinntekter")
    val årsinntekter: Set<InntektDtoV2> = emptySet(),
    val beregnetInntekt: BeregnetInntekterDto,
    @get:Schema(description = "Saksbehandlers begrunnelser", deprecated = false)
    val begrunnelse: BegrunnelseDto? = null,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
    val valideringsfeil: InntektValideringsfeilV2Dto,
)

data class InntekterDtoV2(
    val barnetillegg: Set<InntektDtoV2> = emptySet(),
    val utvidetBarnetrygd: Set<InntektDtoV2> = emptySet(),
    val kontantstøtte: Set<InntektDtoV2> = emptySet(),
    val månedsinntekter: Set<InntektDtoV2> = emptySet(),
    val småbarnstillegg: Set<InntektDtoV2> = emptySet(),
    @get:Schema(name = "årsinntekter")
    @get:JsonProperty("årsinntekter")
    val årsinntekter: Set<InntektDtoV2> = emptySet(),
    val beregnetInntekter: List<BeregnetInntekterDto> = emptyList(),
    @get:Schema(description = "Saksbehandlers begrunnelser", deprecated = false)
    val begrunnelser: Set<BegrunnelseDto> = emptySet(),
    val begrunnelserFraOpprinneligVedtak: Set<BegrunnelseDto> = emptySet(),
    val valideringsfeil: InntektValideringsfeilDto = InntektValideringsfeilDto(),
) {
    @Deprecated("Bruk begrunnelser for begrunnelse per rolle")
    @get:Schema(description = "Saksbehandlers begrunnelse", deprecated = true)
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
    val inntekter: List<InntektPerBarnDto> = emptyList(),
)

data class InntektPerBarnDto(
    @get:Schema(description = "Referanse til barn", deprecated = true) val inntektGjelderBarnIdent: Personident? = null,
    @get:Schema(description = "Referanse til barn") val inntektGjelderBarn: RolleDto? = null,
    @get:Schema(description = "Liste over summerte inntektsperioder") var summertInntektListe: List<DelberegningSumInntekt> = emptyList(),
)

data class OppdatereInntektBegrunnelseRespons(
    @get:Schema(description = "Oppdatere begrunnelse for inntekt")
    val oppdatertBegrunnelse: OppdatereBegrunnelse? = null,
)

data class OppdatereInntektBegrunnelseRequest(
    @get:Schema(description = "Oppdatere begrunnelse for inntekt")
    val oppdatereBegrunnelse: OppdatereBegrunnelse,
)

data class OppdatereInntektRequest(
    @get:Schema(description = "Angi periodeinformasjon for inntekt")
    val oppdatereInntektsperiode: OppdaterePeriodeInntekt? = null,
    val oppdaterInnteksperiodeSkatteprosent: List<OppdatereSkatteprosentInntekt> = emptyList(),
    @get:Schema(description = "Opprette eller oppdatere manuelt oppgitt inntekt")
    val oppdatereManuellInntekt: OppdatereManuellInntekt? = null,
    @get:Schema(description = "Oppdatere begrunnelse for inntekt")
    val oppdatereBegrunnelse: OppdatereBegrunnelse? = null,
    @get:Schema(description = "Deprekert, bruk oppdatereBegrunnelse i stedet")
    val oppdatereNotat: OppdatereBegrunnelse? = null,
    @get:Schema(description = "Angi id til inntekt som skal slettes")
    val sletteInntekt: Long? = null,
) {
    // TODO: Fjerne når migrering til oppdatereBegrunnelse er fullført
    val henteOppdatereBegrunnelse = oppdatereBegrunnelse ?: oppdatereNotat
}

data class OppdatereInntektResponse(
    val inntekter: InntekterDtoV2,
    val inntekterV2: List<InntekterDtoRolle>,
    @get:Schema(deprecated = true)
    val gebyr: GebyrDto? = null,
    val gebyrV2: GebyrDtoV2? = null,
    val gebyrV3: GebyrDtoV3? = null,
    val beregnetGebyrErEndret: Boolean = false,
    @get:Schema(description = "Periodiserte inntekter")
    val beregnetInntekter: List<BeregnetInntekterDto> = emptyList(),
    @get:Schema(description = "Oppdatert begrunnelse")
    val begrunnelse: String? = null,
    val valideringsfeil: InntektValideringsfeilDto,
) {
    @Deprecated("Erstattes av begrunnelse")
    @get:Schema(description = "Oppdatert begrunnelse", deprecated = true)
    val notat: String? = begrunnelse
}

@Deprecated("Erstattes av OppdatereInntektRequest")
@Schema(description = "Erstattes av OppdatereInntektRequest", deprecated = true)
data class OppdatereInntekterRequestV2(
    @get:Schema(description = "Angi periodeinformasjon for inntekter")
    val oppdatereInntektsperioder: Set<OppdaterePeriodeInntekt> = emptySet(),
    @get:Schema(description = "Opprette eller oppdatere manuelt oppgitte inntekter")
    val oppdatereManuelleInntekter: Set<OppdatereManuellInntekt> = emptySet(),
    @get:Schema(description = "Angi id til inntekter som skal slettes")
    val sletteInntekter: Set<Long> = emptySet(),
    val notat: OppdatereBegrunnelse? = null,
)

data class OppdatereSkatteprosentInntekt(
    @get:Schema(description = "Id til inntekt som skal oppdateres")
    val id: Long,
    val skatteprosent: BigDecimal? = null,
)

data class OppdaterePeriodeInntekt(
    @get:Schema(description = "Id til inntekt som skal oppdateres")
    val id: Long,
    @get:Schema(description = "Anig om inntekten skal inkluderes i beregning")
    val taMedIBeregning: Boolean = false,
    @get:Schema(description = "Angi periode inntekten skal dekke ved beregnings")
    val angittPeriode: Datoperiode? = null,
    val skatteprosent: BigDecimal? = null,
)

data class OppdatereManuellInntekt(
    @get:Schema(
        description = "Inntektens databaseid. Oppgis ikke ved opprettelse av inntekt.",
        required = false,
    )
    val id: Long? = null,
    @get:Schema(description = "Angir om inntekten skal inkluderes i beregning. Hvis ikke spesifisert inkluderes inntekten.")
    val taMed: Boolean = true,
    @get:Schema(
        description = "Angir inntektens rapporteringstype.",
        required = true,
        example = "KONTANTSTØTTE",
    )
    val type: Inntektsrapportering,
    @get:Schema(description = "Inntektens beløp i norske kroner", required = true)
    val beløp: BigDecimal,
    val beløpstype: InntektBeløpstype = InntektBeløpstype.ÅRSBELØP,
    val skatteprosent: BigDecimal? = null,
    @get:Schema(type = "String", format = "date", example = "2024-01-01", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate,
    @get:Schema(type = "String", format = "date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @get:Schema(
        description = "Ident til personen inntekten gjenlder for.",
        type = "String",
        example = "12345678910",
        required = false,
        deprecated = true,
    )
    @Deprecated("Bruk gjelderId")
    val ident: Personident?,
    @get:Schema(
        description = "Id til rollen til personen inntekten gjenlder for.",
        type = "String",
        example = "12345678910",
        required = true,
        deprecated = true,
    )
    val gjelderId: Long? = null,
    @get:Schema(
        description =
            "Ident til barnet en ytelse gjelder for. " +
                "sBenyttes kun for ytelser som er koblet til ett spesifikt barn, f.eks kontantstøtte",
        type = "String",
        example = "12345678910",
        required = false,
        deprecated = true,
    )
    @Deprecated("Bruk gjelderBarnId")
    val gjelderBarn: Personident? = null,
    @get:Schema(
        description =
            "Id til rollen til barnet en ytelse gjelder for. " +
                "sBenyttes kun for ytelser som er koblet til ett spesifikt barn, f.eks kontantstøtte",
        type = "String",
        example = "12345678910",
        required = false,
        deprecated = true,
    )
    val gjelderBarnId: Long? = null,
    @get:Schema(description = "Spesifisere inntektstype for detaljpost")
    val inntektstype: Inntektstype? = null,
)
