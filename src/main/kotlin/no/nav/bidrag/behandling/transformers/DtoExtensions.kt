package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.BehandlingBarnDto
import no.nav.bidrag.behandling.dto.BehandlingBarnPeriodeDto
import no.nav.bidrag.behandling.dto.SivilstandDto

fun Set<Sivilstand>.toSivilstandDto() = this.map {
    SivilstandDto(it.id, it.gyldigFraOgMed, it.bekreftelsesdato, it.sivilstandType)
}.toSet()

fun Set<BehandlingBarnPeriode>.toBehandlingBarnPeriodeDto() = this.map {
    BehandlingBarnPeriodeDto(it.id, it.fraDato, it.tilDato, it.boStatus, it.kilde)
}.toSet()

fun Set<BehandlingBarnPeriodeDto>.toDomain(behandlingBarn: BehandlingBarn) = this.map {
    BehandlingBarnPeriode(behandlingBarn, it.fraDato, it.tilDato, it.boStatus, it.kilde)
}.toSet()

fun Set<BehandlingBarn>.toBehandlingBarnDto() = this.map {
    BehandlingBarnDto(it.id!!, it.medISaken, it.perioder.toBehandlingBarnPeriodeDto(), it.ident, it.navn, it.foedselsDato)
}.toSet()

fun Set<BehandlingBarnDto>.toDomain(behandling: Behandling) = this.map {
    val barn = BehandlingBarn(
        behandling,
        it.medISaken,
        it.id,
        it.ident,
        it.navn,
        it.foedselsDato,
    )
    barn.perioder = it.perioder.toDomain(barn).toMutableSet()
    barn
}.toMutableSet()
