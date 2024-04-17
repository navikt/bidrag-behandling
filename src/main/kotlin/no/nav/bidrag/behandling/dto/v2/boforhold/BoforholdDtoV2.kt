package no.nav.bidrag.behandling.dto.v2.boforhold

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.boforhold.dto.Kilde
import java.time.LocalDate

data class BoforholdDtoV2(
    val husstandsbarn: Set<HusstandsbarnDtoV2>,
    val sivilstand: Set<SivilstandDto>,
    val notat: BehandlingNotatDto,
    val valideringsfeil: BoforholdValideringsfeil,
)

data class HusstandsbarnDtoV2(
    val id: Long?,
    @Schema(required = true)
    val kilde: Kilde,
    @Schema(required = true)
    val medIBehandling: Boolean,
    val perioder: Set<HusstandsbarnperiodeDto>,
    val ident: String? = null,
    val navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val fødselsdato: LocalDate,
)
