package no.nav.bidrag.behandling.controller.v2

import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.transformers.vedtak.TreeChild
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

@BehandlingRestControllerV2
class VedtakGraphController(
    private val vedtakService: VedtakService,
) {
    @Suppress("unused")
    @PostMapping("/vedtak/mermaid/{behandlingId}")
    fun vedtakTilMermaid(
        @PathVariable behandlingId: Long,
    ): ResponseEntity<String> {
        val mermaid = vedtakService.vedtakTilMermaid(behandlingId)
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(mermaid)
    }

    @Suppress("unused")
    @PostMapping("/vedtak/graph/{behandlingId}")
    fun vedtakTilTre(
        @PathVariable behandlingId: Long,
    ): ResponseEntity<TreeChild> {
        val mermaid = vedtakService.vedtakTilTreeMap(behandlingId)
        return ResponseEntity.ok(mermaid)
    }
}
