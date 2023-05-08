package no.nav.bidrag.behandling.dto.sivilstand

import no.nav.bidrag.behandling.dto.behandling.SivilstandDto

data class UpdateBehandlingSivilstandResponse(
    val sivilstand: Set<SivilstandDto>,
)
