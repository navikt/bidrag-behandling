package no.nav.bidrag.behandling.dto.v2.privatavtale

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.ManuellVedtakDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.felles.OverlappendePeriode
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.samhandler.Valutakode
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import java.math.BigDecimal
import java.time.LocalDate

data class OppdaterePrivatAvtaleBegrunnelseRequest(
    val privatavtaleid: Long? = null,
    val barnIdent: String? = null,
    val begrunnelse: String? = null,
)

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
    val avtaleType: PrivatAvtaleType? = null,
    val gjelderUtland: Boolean? = false,
    val oppdaterPeriode: OppdaterePrivatAvtalePeriodeDto? = null,
    val slettePeriodeId: Long? = null,
)

data class OppdaterePrivatAvtaleResponsDto(
    @Schema(description = "Privat avtale som ble oppdatert", deprecated = true)
    val oppdatertPrivatAvtale: PrivatAvtaleBarnDto? = null,
    val privatAvtale: PrivatAvtaleDtoV3,
    @Schema(deprecated = true)
    val begrunnelseAndreBarn: String? = null,
    @Schema(deprecated = true)
    val mangleBegrunnelseAndreBarn: Boolean = false,
)

data class OppdaterePrivatAvtalePeriodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val beløp: BigDecimal,
    val samværsklasse: Samværsklasse? = null,
    val valuta: Valutakode? = null,
)

data class PrivatAvtaleDtoV3(
    val søknadsbarn: List<PrivatAvtaleBarnInfoDto> = emptyList(),
    val andreBarn: PrivatAvtaleAndreBarnDetaljerDtoV2? = null,
)

data class PrivatAvtaleDto(
    val barn: List<PrivatAvtaleBarnDto> = emptyList(),
    val andreBarn: PrivatAvtaleAndreBarnDto? = null,
)

data class PrivatAvtaleAndreBarnDetaljerDtoV2(
    val manglerBegrunnelse: Boolean = false,
    val begrunnelse: String? = null,
    val begrunnelseFraOpprinneligVedtak: String? = null,
    val barn: List<PrivatAvtaleAndreBarnDtoV2> = emptyList(),
)

data class PrivatAvtaleAndreBarnDtoV2(
    val gjelderBarn: PersoninfoDto,
    val privatAvtale: PrivatAvtaleBarnDtoV2? = null,
    val enhet: String? = null,
    val saksnummer: String? = null,
)

data class PrivatAvtaleAndreBarnDto(
    val manglerBegrunnelse: Boolean = false,
    val begrunnelse: String? = null,
    val begrunnelseFraOpprinneligVedtak: String? = null,
    val barn: List<PrivatAvtaleBarnDto> = emptyList(),
)

data class PrivatAvtaleBarnInfoDto(
    val gjelderBarn: PersoninfoDto,
    val perioderLøperBidrag: List<ÅrMånedsperiode> = emptyList(),
    val begrunnelse: String?,
    val erSøknadsbarn: Boolean = true,
    val begrunnelseFraOpprinneligVedtak: String? = null,
    val privatAvtale: PrivatAvtaleBarnDtoV2? = null,
)

data class PrivatAvtaleBarnDtoV2(
    val id: Long,
    val avtaleDato: LocalDate?,
    val avtaleType: PrivatAvtaleType?,
    val gjelderUtland: Boolean = false,
    val skalIndeksreguleres: Boolean = false,
    val erSøknadsbarn: Boolean = true,
    val valideringsfeil: PrivatAvtaleValideringsfeilDto?,
    val perioder: List<PrivatAvtalePeriodeDto> = emptyList(),
    val beregnetPrivatAvtale: BeregnetPrivatAvtaleDto? = null,
    val manuelleVedtakUtenInnkreving: List<ManuellVedtakDto>?,
)

data class PrivatAvtaleBarnDto(
    val id: Long,
    val gjelderBarn: PersoninfoDto,
    val perioderLøperBidrag: List<ÅrMånedsperiode> = emptyList(),
    val avtaleDato: LocalDate?,
    val avtaleType: PrivatAvtaleType?,
    val skalIndeksreguleres: Boolean = false,
    val begrunnelse: String?,
    val erSøknadsbarn: Boolean = true,
    val begrunnelseFraOpprinneligVedtak: String? = null,
    val valideringsfeil: PrivatAvtaleValideringsfeilDto?,
    val perioder: List<PrivatAvtalePeriodeDto> = emptyList(),
    val beregnetPrivatAvtale: BeregnetPrivatAvtaleDto? = null,
    val manuelleVedtakUtenInnkreving: List<ManuellVedtakDto>?,
)

data class PrivatAvtalePeriodeDto(
    val id: Long? = null,
    val periode: DatoperiodeDto,
    val beløp: BigDecimal,
    val samværsklasse: Samværsklasse? = null,
    val valuta: Valutakode? = null,
)

data class PrivatAvtaleValideringsfeilDto(
    val privatAvtaleId: Long,
    @JsonIgnore
    val gjelderPerson: RolleDto,
    val perioderOverlapperMedLøpendeBidrag: Set<Datoperiode>,
    val manglerBegrunnelse: Boolean = false,
    val manglerAvtaledato: Boolean = false,
    val manglerAvtaletype: Boolean,
    val måVelgeVedtakHvisAvtaletypeErVedtakFraNav: Boolean,
    val ingenLøpendePeriode: Boolean,
    val overlappendePerioder: Set<OverlappendePeriode>,
) {
    val harPeriodiseringsfeil
        get() =
            overlappendePerioder.isNotEmpty() || ingenLøpendePeriode || perioderOverlapperMedLøpendeBidrag.isNotEmpty()

    val gjelderBarn get() = gjelderPerson.ident
    val gjelderBarnNavn get() = gjelderPerson.navn ?: hentPersonVisningsnavn(gjelderPerson.ident)

    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || harPeriodiseringsfeil || manglerAvtaledato || måVelgeVedtakHvisAvtaletypeErVedtakFraNav
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
