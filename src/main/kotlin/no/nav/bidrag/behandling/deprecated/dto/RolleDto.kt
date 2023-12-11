package no.nav.bidrag.behandling.deprecated.dto

import java.util.Date

data class RolleDto(
    val id: Long,
    val rolleType: RolleTypeDto,
    val ident: String?,
    val navn: String?,
    val fodtDato: Date?,
    val opprettetDato: Date?,
)

enum class RolleTypeDto {
    BARN,
    BIDRAGSMOTTAKER,
    BIDRAGSPLIKTIG,
    FEILREGISTRERT,
    REELMOTTAKER,
}
