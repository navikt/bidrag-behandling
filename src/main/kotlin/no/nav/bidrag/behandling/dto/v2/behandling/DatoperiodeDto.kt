package no.nav.bidrag.behandling.dto.v2.behandling

import java.time.LocalDate

data class DatoperiodeDto(
    var fom: LocalDate,
    var tom: LocalDate? = null,
)
