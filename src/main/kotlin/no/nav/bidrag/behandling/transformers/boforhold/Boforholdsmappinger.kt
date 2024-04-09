package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.validereBoforhold

fun Set<Husstandsbarnperiode>.toHusstandsBarnPeriodeDto() =
    this.map {
        HusstandsbarnperiodeDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<Husstandsbarn>.toHusstandsBarnDtoV2(behandling: Behandling): Set<HusstandsbarnDtoV2> {
    val identerSøknadsbarn = behandling.søknadsbarn.map { sb -> sb.ident!! }.toSet()

    val søknadsbarn =
        this.filter { !it.ident.isNullOrBlank() && identerSøknadsbarn.contains(it.ident) }.map {
            it.toDto(behandling)
        }.sortedBy { it.fødselsdato }.toSet()

    val barnFraOffentligeKilderSomIkkeErDelAvBehandling =
        this.filter { Kilde.OFFENTLIG == it.kilde }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.toDto(behandling) }
            .sortedBy { it.fødselsdato }.toSet()

    val andreHusstandsbarn =
        this.filter { Kilde.MANUELL == it.kilde }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.toDto(behandling) }
            .sortedBy { it.fødselsdato }.toSet()

    return søknadsbarn + barnFraOffentligeKilderSomIkkeErDelAvBehandling + andreHusstandsbarn
}

fun Husstandsbarn.toDto(behandling: Behandling): HusstandsbarnDtoV2 =
    HusstandsbarnDtoV2(
        this.id,
        this.kilde,
        !this.ident.isNullOrBlank() && behandling.søknadsbarn.map { it.ident }.contains(this.ident),
        this.perioder.toHusstandsBarnPeriodeDto().sortedBy { periode -> periode.datoFom }.toSet(),
        this.ident,
        this.navn ?: hentPersonVisningsnavn(this.ident),
        this.fødselsdato,
    )

fun Husstandsbarn.tilOppdatereBoforholdResponse(behandling: Behandling) =
    OppdatereBoforholdResponse(
        oppdatertHusstandsbarn = this.toDto(behandling),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsbarn =
                    this.validereBoforhold(behandling.virkningstidspunktEllerSøktFomDato, mutableListOf())
                        .filter { it.harFeil },
            ),
    )
