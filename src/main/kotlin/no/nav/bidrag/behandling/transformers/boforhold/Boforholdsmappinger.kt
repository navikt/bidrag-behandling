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
import no.nav.bidrag.behandling.transformers.validerSivilstand
import no.nav.bidrag.behandling.transformers.validereBoforhold
import no.nav.bidrag.boforhold.dto.Kilde
import java.time.LocalDate

fun Set<Husstandsbarnperiode>.tilDto() =
    this.map {
        HusstandsbarnperiodeDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<Husstandsbarn>.tilHusstandsBarnDtoV2(behandling: Behandling): Set<HusstandsbarnDtoV2> {
    val identerSøknadsbarn = behandling.søknadsbarn.map { sb -> sb.ident!! }.toSet()

    val søknadsbarn =
        this.filter { !it.ident.isNullOrBlank() && identerSøknadsbarn.contains(it.ident) }.map {
            it.tilDto(behandling)
        }.sortedBy { it.fødselsdato }.toSet()

    val barnFraOffentligeKilderSomIkkeErDelAvBehandling =
        this.filter { Kilde.OFFENTLIG == it.kilde }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.tilDto(behandling) }
            .sortedBy { it.fødselsdato }.toSet()

    val andreHusstandsbarn =
        this.filter { Kilde.MANUELL == it.kilde }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.tilDto(behandling) }
            .sortedBy { it.fødselsdato }.toSet()

    return søknadsbarn + barnFraOffentligeKilderSomIkkeErDelAvBehandling + andreHusstandsbarn
}

fun Husstandsbarn.tilDto(behandling: Behandling) =
    HusstandsbarnDtoV2(
        this.id,
        this.kilde,
        !this.ident.isNullOrBlank() && behandling.søknadsbarn.map { it.ident }.contains(this.ident),
        this.perioder.tilDto().sortedBy { periode -> periode.datoFom }.toSet(),
        this.ident,
        this.navn,
        this.fødselsdato,
    )

fun Husstandsbarn.tilOppdatereBoforholdResponse(behandling: Behandling) =
    OppdatereBoforholdResponse(
        oppdatertHusstandsbarn = this.tilDto(behandling),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsbarn =
                    this.validereBoforhold(behandling.virkningstidspunktEllerSøktFomDato, mutableListOf())
                        .filter { it.harFeil },
            ),
    )

fun Husstandsbarnperiode.tilOppdatereBoforholdResponse(behandling: Behandling) =
    OppdatereBoforholdResponse(
        oppdatertHusstandsbarn = this.husstandsbarn.tilDto(behandling),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsbarn =
                    this.husstandsbarn.validereBoforhold(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        mutableListOf(),
                    ).filter { it.harFeil },
            ),
    )

fun Sivilstand.tilDto() = SivilstandDto(this.id, this.datoFom, datoTom = this.datoTom, this.sivilstand, this.kilde)

fun Sivilstand.tilOppdatereBoforholdResponse(virkningstidspunkt: LocalDate) =
    OppdatereBoforholdResponse(
        oppdatertSivilstand = this.tilDto(),
        valideringsfeil =
            BoforholdValideringsfeil(
                sivilstand = setOf(this).validerSivilstand(virkningstidspunkt),
            ),
    )
