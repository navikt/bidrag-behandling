package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.OpprettFFRequest
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.behandling.service.ForholdsmessigFordelingService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
@RequestMapping("/api/v2/behandling/forholdsmessigfordeling")
class ForholdsmessigFordelingController(
    private val forholdsmessigFordelingService: ForholdsmessigFordelingService,
) {
    @PostMapping("/{behandlingsid}")
    fun opprettForholdsmessigFordeling(
        @PathVariable behandlingsid: Long,
        @RequestBody request: OpprettFFRequest,
    ) {
        forholdsmessigFordelingService.opprettEllerOppdaterForholdsmessigFordeling(behandlingsid, request = request)
    }

    @PostMapping("/nyeopplysninger/{behandlingsid}")
    fun skalLeggeTilBarnFraAndreSøknaderEllerBehandlinger(
        @PathVariable behandlingsid: Long,
    ): Boolean = forholdsmessigFordelingService.skalLeggeTilBarnFraAndreSøknaderEllerBehandlinger(behandlingsid)

    @PostMapping("/sjekk/{behandlingsid}")
    fun kanOppretteForholdsmessigFordeling(
        @PathVariable behandlingsid: Long,
    ): SjekkForholdmessigFordelingResponse = forholdsmessigFordelingService.sjekkSkalOppretteForholdsmessigFordeling(behandlingsid)
}
