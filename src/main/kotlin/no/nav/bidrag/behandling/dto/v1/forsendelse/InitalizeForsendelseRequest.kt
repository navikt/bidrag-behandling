package no.nav.bidrag.behandling.dto.v1.forsendelse

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident

data class InitalizeForsendelseRequest(
    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, message = "Saksnummer kan ikke være lengre enn 7 tegn")
    val saksnummer: String,
    val behandlingInfo: BehandlingInfoDto,
    val enhet: String? = null,
    val tema: String? = null,
    val roller: List<ForsendelseRolleDto>,
    val behandlingStatus: BehandlingStatus? = null,
)

data class ForsendelseRolleDto(
    val fødselsnummer: Personident?,
    val type: Rolletype,
)

// TODO: Flytt dette hvis det blir også brukt i Behandling domain objektet
enum class BehandlingStatus {
    OPPRETTET,
    ENDRET,
    FEILREGISTRERT,
}
