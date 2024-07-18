package no.nav.bidrag.behandling.dto.v1.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.validering.AndreVoksneIHusstandenPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandPeriodeseringsfeil
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import java.time.LocalDate

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
    val notat: BehandlingNotatDto,
)

data class BoforholdValideringsfeil(
    val andreVoksneIHusstanden: AndreVoksneIHusstandenPeriodeseringsfeil? = null,
    val husstandsmedlem: List<BoforholdPeriodeseringsfeil>? = emptyList(),
    val sivilstand: SivilstandPeriodeseringsfeil? = null,
)

data class BehandlingNotatDto(
    val kunINotat: String? = null,
    val medIVedtaket: String? = null,
)
