package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validereSivilstand

fun Set<Husstandsbarnperiode>.tilHusstandsbarnperiodeDto() =
    this.map {
        HusstandsbarnperiodeDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.sortedBy { it.datoFom }.toSet()

fun Husstandsbarn.tilHusstandsbarnperiodeDto() =
    HusstandsbarnDtoV2(
        this.id,
        this.kilde,
        !this.ident.isNullOrBlank() && behandling.søknadsbarn.map { it.ident }.contains(this.ident),
        this.perioder.sortedBy { it.datoFom }.toSet().tilHusstandsbarnperiodeDto(),
        this.ident,
        this.navn ?: hentPersonVisningsnavn(this.ident),
        this.fødselsdato,
    )

fun Husstandsbarn.tilOppdatereBoforholdResponse(behandling: Behandling) =
    OppdatereBoforholdResponse(
        oppdatertHusstandsbarn = this.tilHusstandsbarnperiodeDto(),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsbarn =
                    behandling.husstandsbarn.validerBoforhold(behandling.virkningstidspunktEllerSøktFomDato)
                        .filter { it.harFeil },
            ),
    )

fun Sivilstand.tilHusstandsbarnperiodeDto() = SivilstandDto(this.id, this.datoFom, datoTom = this.datoTom, this.sivilstand, this.kilde)

fun Set<Sivilstand>.tilSivilstandDto() = this.map { it.tilHusstandsbarnperiodeDto() }.toSet()

fun Set<Sivilstand>.tilOppdatereBoforholdResponse() =
    OppdatereBoforholdResponse(
        oppdatertSivilstandshistorikk = this.tilSivilstandDto(),
        valideringsfeil =
            BoforholdValideringsfeil(
                sivilstand = this.validereSivilstand(this.first().behandling.virkningstidspunktEllerSøktFomDato),
            ),
    )
