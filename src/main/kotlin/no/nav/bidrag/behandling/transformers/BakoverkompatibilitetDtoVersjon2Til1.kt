package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.husstandsmedlem.BostatusperiodeDto
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsmedlemDtoV2

/**
 * Inneholder omgjøringer som kreves for å støtte bakoverkompatibilitet. Fila skal kunne slettes når migrering til API V2 er fullført.
 */

@Deprecated("Utgår når manuell oppdatering av sivilstand kun gjøres via endepunktet for oppdatering av boforhold (boforhold v2) ")
fun Set<SivilstandDto>.toSivilstandDomain(behandling: Behandling) =
    this.map {
        Sivilstand(behandling, it.datoFom, it.datoTom, it.sivilstand, it.kilde, it.id)
    }.toMutableSet()

@Deprecated("Utgår når manuell oppdatering av husstandsmedlem kun gjøres via endepunktet for oppdatering av boforhold (boforhold v2) ")
fun Set<HusstandsmedlemDtoV2>.toDomain(behandling: Behandling) =
    this.map {
        val barn =
            Husstandsmedlem(
                behandling,
                it.kilde,
                it.id,
                it.ident,
                it.navn,
                it.fødselsdato,
            )
        barn.perioder = it.perioder.toDomain(barn).toMutableSet()
        barn
    }.toMutableSet()

@Deprecated("Utgår når manuell oppdatering av husstandsmedlem kun gjøres via endepunktet for oppdatering av boforhold (boforhold v2) ")
fun Set<BostatusperiodeDto>.toDomain(husstandsmedlem: Husstandsmedlem) =
    this.map {
        Bostatusperiode(
            husstandsmedlem,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.toSet()
