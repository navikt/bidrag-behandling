package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.dto.BehandlingBarnDto

fun Set<BehandlingBarn>.toDto() = this.map {
    BehandlingBarnDto(it.id!!, it.medISaken, it.fraDato, it.tilDato, it.boStatus, it.kilde, it.ident, it.navn, it.foedselsDato)
}.toSet()
