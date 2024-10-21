package no.nav.bidrag.behandling.dto.v2.behandling

import java.time.LocalDate

data class DatoperiodeDto(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)
