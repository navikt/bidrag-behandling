package no.nav.bidrag.behandling.dto.v2.privatavtale

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.domene.tid.Datoperiode
import java.math.BigDecimal
import java.time.LocalDate

data class OppdaterePrivatAvtaleSkalIndeksreguleresRequest(
    val skalIndeksreguleres: Boolean,
)

data class OppdaterePrivatAvtaleBegrunnelseRequest(
    val begrunnelse: String,
)

data class OppdaterePrivatAvtaleAvtaleDatoRequest(
    val avtaleDato: LocalDate? = null,
)

data class OppdaterePrivatAvtaleResponsDto(
    @Schema(description = "Privat avtale som ble oppdatert")
    val oppdatertPrivatAvtale: PrivatAvtaleDto? = null,
)

data class OppdaterePrivatAvtalePeriodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
)

data class PrivatAvtaleDto(
    val id: Long,
    val gjelderBarn: String,
    val skalIndeksreguleres: Boolean,
    val begrunnelse: BegrunnelseDto?,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
    val valideringsfeil: PrivatAvtaleValideringsfeilDto?,
    val perioder: List<PrivatAvtalePeriodeDto> = emptyList(),
    val beregnetPrivatAvtale: BeregnetPrivatAvtaleDto? = null,
)

data class PrivatAvtalePeriodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
)

data class PrivatAvtaleValideringsfeilDto(
    val privatAvtaleId: Long,
    @JsonIgnore
    val gjelderRolle: Rolle,
    val manglerBegrunnelse: Boolean,
    val ugyldigSluttperiode: Boolean,
    val overlappendePerioder: Set<OverlappendePrivatAvtalePeriode>,
    val hullIPerioder: List<Datoperiode> = emptyList(),
) {
    val harPeriodiseringsfeil
        get() =
            overlappendePerioder.isNotEmpty() ||
                hullIPerioder.isNotEmpty() ||
                ugyldigSluttperiode
    val gjelderBarn get() = gjelderRolle.ident
    val gjelderBarnNavn get() = gjelderRolle.navn

    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || harPeriodiseringsfeil
}

data class OverlappendePrivatAvtalePeriode(
    val periode: Datoperiode,
    @Schema(description = "Teknisk id på inntekter som overlapper")
    val idListe: MutableSet<Long>,
)

data class BeregnetPrivatAvtaleDto(
    val perioder: List<BeregnetPrivatAvtalePeriodeDto> = emptyList(),
)

data class BeregnetPrivatAvtalePeriodeDto(
    val periode: Datoperiode,
    val indeksprosent: BigDecimal,
    val beløp: BigDecimal,
)
