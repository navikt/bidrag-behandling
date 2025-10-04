package no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling

import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.time.LocalDate

data class ForholdmessigFordelingDetaljerDto(
    val barn: List<ForholdsmessigFordelingBarnDto>,
)

data class SjekkForholdmessigFordelingResponse(
    val kanOppretteForholdsmessigFordeling: Boolean,
    val måOppretteForholdsmessigFordeling: Boolean = false,
    val barn: List<ForholdsmessigFordelingBarnDto> = emptyList(),
)

data class ForholdsmessigFordelingBarnDto(
    val ident: String,
    val bidragsmottaker: RolleDto,
    val navn: String,
    val fødselsdato: LocalDate?,
    val saksnr: String,
    val sammeSakSomBehandling: Boolean,
    val åpenBehandling: ForholdsmessigFordelingÅpenBehandlingDto?,
)

data class ForholdsmessigFordelingÅpenBehandlingDto(
    val søktFraDato: LocalDate,
    val mottattDato: LocalDate,
    val stønadstype: Stønadstype,
    val behandlerEnhet: String,
)
