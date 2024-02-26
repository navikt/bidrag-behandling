package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.dto.v1.notat.NotatDto
import no.nav.bidrag.behandling.service.NotatOpplysningerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@BehandlingRestControllerV1
class NotatOpplysningerController(
    private val notatOpplysningerService: NotatOpplysningerService,
) {
    @GetMapping("/notat/{behandlingId}")
    fun hentNotatOpplysninger(
        @PathVariable behandlingId: Long,
    ): NotatDto {
        return notatOpplysningerService.hentNotatOpplysninger(behandlingId)
    }
}