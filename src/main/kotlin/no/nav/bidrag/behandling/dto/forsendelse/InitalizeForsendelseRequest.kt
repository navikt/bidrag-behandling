package no.nav.bidrag.behandling.dto.forsendelse

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.ident.PersonIdent

data class InitalizeForsendelseRequest(
    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, message = "Saksnummer kan ikke være lengre enn 7 tegn")
    val saksnummer: String,
    val behandlingInfo: BehandlingInfoDto,
    val enhet: String,
    val tema: String? = null,
    val roller: List<ForsendelseRolleDto>,
    val behandlingStatus: BehandlingStatus? = null,
)

data class ForsendelseRolleDto(
    val fødselsnummer: PersonIdent?,
    val type: Rolletype,
)

// TODO: Flytt dette hvis det blir også brukt i Behandling domain objektet
enum class BehandlingStatus {
    OPPRETTET,
    ENDRET,
    FEILREGISTRERT,
}
