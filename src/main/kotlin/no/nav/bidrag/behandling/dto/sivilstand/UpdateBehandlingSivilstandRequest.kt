package no.nav.bidrag.behandling.dto.sivilstand

import no.nav.bidrag.behandling.dto.behandling.SivilstandDto

data class UpdateBehandlingSivilstandRequest(
    val sivilstand: Set<SivilstandDto>,
)
