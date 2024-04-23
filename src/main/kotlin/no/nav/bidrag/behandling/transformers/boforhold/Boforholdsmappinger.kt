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
import no.nav.bidrag.behandling.transformers.validerSivilstand
import no.nav.bidrag.behandling.transformers.validereBoforhold
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
    }.sortedBy { it.datoFom }.toSet()

fun Husstandsbarn.tilDto() =
    HusstandsbarnDtoV2(
        this.id,
        this.kilde,
        !this.ident.isNullOrBlank() && behandling.søknadsbarn.map { it.ident }.contains(this.ident),
        this.perioder.tilDto(),
        this.ident,
        this.navn ?: hentPersonVisningsnavn(this.ident),
        this.fødselsdato,
    )

fun Husstandsbarn.tilOppdatereBoforholdResponse(
    behandling: Behandling,
    validerePerioder: Boolean = true,
) = OppdatereBoforholdResponse(
    oppdatertHusstandsbarn = this.tilDto(),
    valideringsfeil =
        BoforholdValideringsfeil(
            husstandsbarn =
                this.validereBoforhold(behandling.virkningstidspunktEllerSøktFomDato, mutableListOf(), validerePerioder)
                    .filter { it.harFeil },
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
