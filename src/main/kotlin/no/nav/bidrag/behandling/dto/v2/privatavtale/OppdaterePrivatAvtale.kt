package no.nav.bidrag.behandling.dto.v2.privatavtale

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.felles.OverlappendePeriode
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.tid.Datoperiode
import java.math.BigDecimal
import java.time.LocalDate

data class OppdaterePrivatAvtaleRequest(
    @Schema(description = "Setter avtaledato på privat avtalen. Dersom avtaleDato er null, vil avtaledato fjernes.")
    val avtaleDato: LocalDate? = null,
    @Schema(
        description =
            "Setter om privat avtale periodene skal indeksreguleres eller ikke. " +
                "Dersom skalIndeksreguleres er null, vil ikke indeksregulering endres.",
    )
    val skalIndeksreguleres: Boolean? = null,
    @Schema(
        description = "Oppdater begrunnelse",
    )
    val begrunnelse: String? = null,
    val oppdaterPeriode: OppdaterePrivatAvtalePeriodeDto? = null,
    val slettePeriodeId: Long? = null,
)

data class OppdaterePrivatAvtaleResponsDto(
    @Schema(description = "Privat avtale som ble oppdatert")
    val oppdatertPrivatAvtale: PrivatAvtaleDto? = null,
)

data class OppdaterePrivatAvtalePeriodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val beløp: BigDecimal,
)

data class PrivatAvtaleDto(
    val id: Long,
    val gjelderBarn: PersoninfoDto,
    val avtaleDato: LocalDate?,
    val skalIndeksreguleres: Boolean,
    val begrunnelse: String?,
    val begrunnelseFraOpprinneligVedtak: String? = null,
    val valideringsfeil: PrivatAvtaleValideringsfeilDto?,
    val perioder: List<PrivatAvtalePeriodeDto> = emptyList(),
    val beregnetPrivatAvtale: BeregnetPrivatAvtaleDto? = null,
)

data class PrivatAvtalePeriodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val beløp: BigDecimal,
)

data class PrivatAvtaleValideringsfeilDto(
    val privatAvtaleId: Long,
    @JsonIgnore
    val gjelderPerson: Person,
    val manglerBegrunnelse: Boolean,
    val manglerAvtaledato: Boolean,
    val ingenLøpendePeriode: Boolean,
    val overlappendePerioder: Set<OverlappendePeriode>,
) {
    val harPeriodiseringsfeil
        get() =
            overlappendePerioder.isNotEmpty() || ingenLøpendePeriode
    val gjelderBarn get() = gjelderPerson.ident
    val gjelderBarnNavn get() = gjelderPerson.navn ?: hentPersonVisningsnavn(gjelderPerson.ident)

    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || harPeriodiseringsfeil || manglerAvtaledato
}

data class BeregnetPrivatAvtaleDto(
    val gjelderBarn: PersoninfoDto,
    val perioder: List<BeregnetPrivatAvtalePeriodeDto> = emptyList(),
)

data class BeregnetPrivatAvtalePeriodeDto(
    val periode: Datoperiode,
    val indeksprosent: BigDecimal,
    val beløp: BigDecimal,
)
