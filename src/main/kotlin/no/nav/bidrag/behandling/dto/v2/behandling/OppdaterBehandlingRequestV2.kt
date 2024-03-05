package no.nav.bidrag.behandling.dto.v2.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import java.math.BigDecimal
import java.time.LocalDate

data class OppdaterBehandlingRequestV2(
    val grunnlagspakkeId: Long? = null,
    val vedtaksid: Long? = null,
    val virkningstidspunkt: OppdaterVirkningstidspunkt? = null,
    val boforhold: OppdaterBoforholdRequest? = null,
    val inntekter: OppdatereInntekterRequestV2? = null,
    val aktivereGrunnlagForPerson: AktivereGrunnlagRequest? = null,
)

data class AktivereGrunnlagRequest(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident,
    @Schema(description = "Grunnlagstyper som skal aktiveres")
    val grunnlagsdatatyper: Set<Grunnlagsdatatype> = emptySet(),
)

data class OppdatereInntekterRequestV2(
    @Schema(description = "Angi periodeinformasjon for inntekter")
    val oppdatereInntektsperioder: Set<OppdaterePeriodeInntekt> = emptySet(),
    @Schema(description = "Opprette eller oppdatere manuelt oppgitte inntekter")
    val oppdatereManuelleInntekter: Set<OppdatereManuellInntekt> = emptySet(),
    @Schema(description = "Angi id til inntekter som skal slettes")
    val sletteInntekter: Set<Long> = emptySet(),
    val notat: OppdaterNotat? = null,
)

data class OppdaterePeriodeInntekt(
    @Schema(description = "Id til inntekt som skal oppdateres")
    val id: Long,
    @Schema(description = "Anig om inntekten skal inkluderes i beregning")
    val taMedIBeregning: Boolean = false,
    @Schema(description = "Angi periode inntekten skal dekke ved beregnings")
    val angittPeriode: Datoperiode,
)

data class OppdatereManuellInntekt(
    @Schema(description = "Inntektens databaseid. Oppgis ikke ved opprettelse av inntekt.", required = false)
    val id: Long? = null,
    @Schema(description = "Angir om inntekten skal inkluderes i beregning. Hvis ikke spesifisert inkluderes inntekten.")
    val taMed: Boolean = true,
    @Schema(description = "Angir inntektens rapporteringstype.", required = true, example = "KONTANTSTØTTE")
    val type: Inntektsrapportering,
    @Schema(description = "Inntektens beløp i norske kroner", required = true)
    val beløp: BigDecimal,
    @Schema(type = "String", format = "date", example = "2024-01-01")
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
