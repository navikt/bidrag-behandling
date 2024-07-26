package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.service.NotatOpplysningerService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.transport.notat.NotatDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

@BehandlingRestControllerV1
class NotatOpplysningerController(
    private val notatOpplysningerService: NotatOpplysningerService,
    private val vedtakService: VedtakService,
) {
    @GetMapping("/notat/{behandlingId}")
    fun hentNotatOpplysninger(
        @PathVariable behandlingId: Long,
    ): NotatDto = notatOpplysningerService.hentNotatOpplysninger(behandlingId)

    @GetMapping("/notat/vedtak/{vedtaksid}")
    fun hentNotatOpplysningerForVedtak(
        @PathVariable vedtaksid: Long,
    ): NotatDto {
        val behandling =
            vedtakService.konverterVedtakTilBehandlingForLesemodus(vedtaksid)
                ?: throw RuntimeException("Fant ikke vedtak for vedtakid $vedtaksid")
        return notatOpplysningerService.hentNotatOpplysningerForBehandling(behandling)
    }

    @PostMapping("/notat/{behandlingId}")
    fun opprettNotat(
        @PathVariable behandlingId: Long,
    ) {
        notatOpplysningerService.opprettNotat(behandlingId)
    }
}
