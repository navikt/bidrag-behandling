package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BostatusperiodeDto
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.validereSivilstand

fun Set<Bostatusperiode>.tilBostatusperiode() =
    this
        .map {
            BostatusperiodeDto(
                it.id,
                it.datoFom,
                it.datoTom,
                it.bostatus,
                it.kilde,
            )
        }.sortedBy { it.datoFom }
        .toSet()

fun Sivilstand.tilBostatusperiode() = SivilstandDto(this.id, this.datoFom, datoTom = this.datoTom, this.sivilstand, this.kilde)

fun Set<Sivilstand>.tilSivilstandDto() = this.map { it.tilBostatusperiode() }.toSet()

fun Set<Sivilstand>.tilOppdatereBoforholdResponse(behandling: Behandling) =
    OppdatereBoforholdResponse(
        oppdatertSivilstandshistorikk = this.tilSivilstandDto(),
        valideringsfeil =
            BoforholdValideringsfeil(
                sivilstand = this.validereSivilstand(behandling.virkningstidspunktEllerSøktFomDato),
            ),
    )
