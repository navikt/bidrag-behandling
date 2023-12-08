package no.nav.bidrag.behandling.dto.boforhold

import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto

data class BoforholdResponse(
    val husstandsbarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
    val boforholdsbegrunnelseIVedtakOgNotat: String? = null,
    val boforholdsbegrunnelseKunINotat: String? = null,
)
