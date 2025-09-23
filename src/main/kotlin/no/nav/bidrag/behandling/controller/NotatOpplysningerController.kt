package no.nav.bidrag.behandling.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.NotatOpplysningerService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.transport.dokumentmaler.notat.VedtakNotatDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.client.HttpClientErrorException

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV1
class NotatOpplysningerController(
    private val notatOpplysningerService: NotatOpplysningerService,
    private val vedtakService: VedtakService,
    private val behandlingService: BehandlingService,
) {
    @GetMapping("/notat/{behandlingId}")
    fun hentNotatOpplysninger(
        @PathVariable behandlingId: Long,
    ): VedtakNotatDto = notatOpplysningerService.hentNotatOpplysninger(behandlingId)

    @GetMapping("/notat/vedtak/{vedtaksid}")
    fun hentNotatOpplysningerForVedtak(
        @PathVariable vedtaksid: Int,
    ): VedtakNotatDto {
        val behandling =
            vedtakService.konverterVedtakTilBehandlingForLesemodus(vedtaksid)
                ?: throw RuntimeException("Fant ikke vedtak for vedtakid $vedtaksid")
        return notatOpplysningerService.hentNotatOpplysningerForBehandling(behandling)
    }

    @PostMapping("/notat/{behandlingId}")
    fun opprettNotat(
        @PathVariable behandlingId: Long,
    ): ResponseEntity<String> {
        log.info { "Opprettet notat for behandling $behandlingId manuelt." }
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        if (!behandling.erVedtakFattet) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Det er ikke fattet vedtak for behandling $behandlingId. Kan ikke opprette notat for behandling vedtak ikke er fattet",
            )
        }
        return ResponseEntity.ok(
            behandling.notatJournalpostId ?: notatOpplysningerService.opprettNotat(behandlingId, true),
        )
    }
}
