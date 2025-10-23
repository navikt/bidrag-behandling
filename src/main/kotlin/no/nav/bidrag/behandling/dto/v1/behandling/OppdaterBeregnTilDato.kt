@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.behandling

import no.nav.bidrag.domene.enums.vedtak.BeregnTil

data class OppdaterBeregnTilDatoRequestDto(
    val idRolle: Long?,
    val beregnTil: BeregnTil? = null,
)
