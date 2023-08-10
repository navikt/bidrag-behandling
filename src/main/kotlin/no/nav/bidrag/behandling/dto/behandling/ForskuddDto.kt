package no.nav.bidrag.behandling.dto.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Beregnet forskudd")
data class ForskuddDto(
    @Schema(description = "Periodisert liste over resultat av forskuddsberegning") var beregnetForskuddPeriodeListe: List<ResultatPeriode> = emptyList(),
)

@Schema(description = "Resultatet av en beregning for en gitt periode")
data class ResultatPeriode(
    @Schema(description = "Beregnet resultat periode") var periode: Periode = Periode(),
    @Schema(description = "Beregnet resultat innhold") var resultat: ResultatBeregning = ResultatBeregning(),
    @Schema(description = "Beregnet grunnlag innhold") var grunnlagReferanseListe: List<String> = emptyList(),
    @Schema(description = "Sivilstand") var sivilstandType: SivilstandType? = null,
)

@Schema(description = "Periode (fra-til dato")
data class Periode(
    @Schema(description = "Fra-og-med-dato") var datoFom: LocalDate? = null,
    @Schema(description = "Til-dato") var datoTil: LocalDate? = null,
)

@Schema(description = "Resultatet av en beregning")
data class ResultatBeregning(
    @Schema(description = "Resultat beløp") var belop: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Resultat kode") var kode: String = "",
    @Schema(description = "Resultat regel") var regel: String = "",
)
