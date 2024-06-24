package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.husstandsmedlem.BostatusperiodeDto
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsmedlemDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validereSivilstand

fun Set<Bostatusperiode>.tilBostatusperiode() =
    this.map {
        BostatusperiodeDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.sortedBy { it.datoFom }.toSet()

fun Husstandsmedlem.tilBostatusperiode() =
    HusstandsmedlemDtoV2(
        this.id,
        this.kilde,
        !this.ident.isNullOrBlank() && behandling.søknadsbarn.map { it.ident }.contains(this.ident),
        this.perioder.sortedBy { it.datoFom }.toSet().tilBostatusperiode(),
        this.ident,
        this.navn ?: hentPersonVisningsnavn(this.ident),
        this.fødselsdato,
    )

fun Husstandsmedlem.tilOppdatereBoforholdResponse(behandling: Behandling) =
    OppdatereBoforholdResponse(
        oppdatertHusstandsmedlem = this.tilBostatusperiode(),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsmedlem =
                    behandling.husstandsmedlem.validerBoforhold(behandling.virkningstidspunktEllerSøktFomDato)
                        .filter { it.harFeil },
            ),
    )

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
