package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.behandling.service.NotatOpplysningerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@BehandlingRestController
class NotatOpplysningerController(
    private val notatOpplysningerService: NotatOpplysningerService,
) {
    @GetMapping("/notat/{behandlingId}")
    fun hentNotatOpplysninger(
        @PathVariable behandlingId: Long,
    ): no.nav.bidrag.behandling.dto.v1.notat.NotatDto {
        return notatOpplysningerService.hentNotatOpplysninger(behandlingId)
    }
}
