package no.nav.bidrag.behandling.deprecated.modell

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto

@Schema(enumAsRef = true)
enum class SoknadType {
    INDEKSREGULERING,
    ALDERSJUSTERING,
    OPPHØR,
    ALDERSOPPHØR,
    REVURDERING,
    FASTSETTELSE,
    INNKREVING,
    KLAGE,
    ENDRING,
    ENDRING_MOTTAKER,
}

data class BoforholdResponse(
    val husstandsBarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
    val boforholdBegrunnelseMedIVedtakNotat: String? = null,
    val boforholdBegrunnelseKunINotat: String? = null,
)
