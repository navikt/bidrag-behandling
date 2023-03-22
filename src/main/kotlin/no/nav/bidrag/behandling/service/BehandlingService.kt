package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import org.springframework.stereotype.Service

@Service
class BehandlingService(
    val behandlingRepository: BehandlingRepository,
) {
    fun createBehandling(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling)
    }

    fun oppdaterBehandling(behandlingId: Long, begrunnelseKunINotat: String?, begrunnelseMedIVedtakNotat: String?): Behandling {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val updatedBehandling = behandling.copy(
            begrunnelseKunINotat = begrunnelseKunINotat,
            begrunnelseMedIVedtakNotat = begrunnelseMedIVedtakNotat,
        )
        updatedBehandling.roller = behandling.roller
        return behandlingRepository.save(updatedBehandling)
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        return behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
    }

    fun hentBehandlinger(): List<Behandling> {
        return behandlingRepository.hentBehandlinger()
    }
}
