package no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.time.LocalDate
import java.time.YearMonth

data class ForholdmessigFordelingDetaljerDto(
    val barn: List<ForholdsmessigFordelingBarnDto>,
)

data class SjekkForholdmessigFordelingResponse(
    val skalBehandlesAvEnhet: String,
    val kanOppretteForholdsmessigFordeling: Boolean,
    val måOppretteForholdsmessigFordeling: Boolean = false,
    val barn: List<ForholdsmessigFordelingBarnDto> = emptyList(),
)

data class ForholdsmessigFordelingBarnDto(
    val ident: String,
    val bidragsmottaker: RolleDto?,
    val navn: String,
    val fødselsdato: LocalDate?,
    val saksnr: String,
    val enhet: String,
    val erRevurdering: Boolean,
    val harLøpendeBidrag: Boolean,
    val innkrevesFraDato: YearMonth?,
    val sammeSakSomBehandling: Boolean,
    @Schema(name = "åpenBehandling")
    val åpenBehandling: ForholdsmessigFordelingÅpenBehandlingDto?,
)

data class ForholdsmessigFordelingÅpenBehandlingDto(
    val søktFraDato: LocalDate?,
    val mottattDato: LocalDate?,
    val stønadstype: Stønadstype,
    val medInnkreving: Boolean,
    val behandlerEnhet: String,
    val behandlingId: Long?,
    val søknadsid: Long?,
)
