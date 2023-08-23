package no.nav.bidrag.behandling.dto.forsendelse

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.nav.bidrag.transport.sak.RolleDto

data class InitalizeForsendelseRequest(
    @field:NotBlank(message = "Saksnummer kan ikke være blank")
    @field:Size(max = 7, message = "Saks nummer kan ikke være lengre enn 7 tegn")
    val saksnummer: String,
    val behandlingInfo: BehandlingInfoDto,
    val enhet: String,
    val tema: String? = null,
    val roller: List<RolleDto>
)