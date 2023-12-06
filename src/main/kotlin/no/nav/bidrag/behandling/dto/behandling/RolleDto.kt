package no.nav.bidrag.behandling.dto.behandling

import no.nav.bidrag.domene.enums.rolle.Rolletype
import java.time.LocalDate

data class RolleDto(
    val id: Long,
    val rolletype: Rolletype,
    val ident: String?,
    val navn: String?,
    val fødselsdato: LocalDate?,
)
