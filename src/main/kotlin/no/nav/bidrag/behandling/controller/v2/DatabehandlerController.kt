package no.nav.bidrag.behandling.controller.v2

import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@BehandlingRestControllerV2
@Deprecated("Midlertidlig kontroller for proessering av grunnlagdata")
class DatabehandlerController(private val behandlingService: BehandlingService) {
    @Suppress("unused")
    @PostMapping("/databehandler/sivilstand/{behandlingId}")
    fun konverterSivilstand(
        @PathVariable behandlingId: Long,
        @RequestBody request: List<SivilstandGrunnlagDto>,
    ): ResponseEntity<SivilstandBeregnet> {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return SivilstandApi.beregn(
            behandling.virkningstidspunkt ?: behandling.søktFomDato,
            request,
        )
            .let { ResponseEntity.ok(it) }
    }
}
