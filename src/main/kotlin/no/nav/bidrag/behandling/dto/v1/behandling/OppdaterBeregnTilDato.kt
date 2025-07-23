@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.behandling

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class OppdaterBeregnTilDatoRequestDto(
    val idRolle: Long,
    val beregnTil: BeregnTil? = null,
)

@Schema(enumAsRef = true)
enum class BeregnTil {
    OPPRINNELIG_VEDTAKSTIDSPUNKT,
    INNEVÆRENDE_MÅNED,
}
