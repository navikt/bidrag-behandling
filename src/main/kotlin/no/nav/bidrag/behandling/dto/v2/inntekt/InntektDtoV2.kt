package no.nav.bidrag.behandling.dto.v2.inntekt

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
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
    val datoFom: LocalDate,
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
    val kilde: Kilde,
    @Schema(required = true)
    val inntektsposter: Set<InntektspostDtoV2>,
    @Schema(required = true)
    val inntektstyper: Set<Inntektstype>,
)

data class InntekterDtoV2(
    val barnetillegg: Set<InntektDtoV2> = emptySet(),
    val barnetilsyn: Set<InntektDtoV2> = emptySet(),
    val kontantstøtte: Set<InntektDtoV2> = emptySet(),
    val månedsinntekter: Set<InntektDtoV2> = emptySet(),
    val småbarnstillegg: Set<InntektDtoV2> = emptySet(),
<<<<<<< HEAD
    val årsinntekter: Set<InntektDtoV2> = emptySet(),
=======
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<InntektDtoV2> = emptySet(),
    val inntekter: Set<InntektDtoV2> = emptySet(),
>>>>>>> main
    val notat: BehandlingNotatDto,
)
