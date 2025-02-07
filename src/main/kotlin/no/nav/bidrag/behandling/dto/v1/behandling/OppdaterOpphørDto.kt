@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.behandling

import java.time.LocalDate

data class OppdaterOpphørsdatoRequestDto(
    val opphørsdato: LocalDate? = null,
)
