package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.model.CreateBehandlingRequest
import org.springframework.stereotype.Service

@Service
class BehandlingService(
    val behandlingRepository: BehandlingRepository,
) {
    fun createBehandling(createBehandlingRequest: CreateBehandlingRequest): Behandling {
        return behandlingRepository.save(
            Behandling(
                null,
                createBehandlingRequest.behandlingType,
                createBehandlingRequest.soknadType,
                createBehandlingRequest.rolle,
                createBehandlingRequest.datoFom,
                createBehandlingRequest.datoTom,
                createBehandlingRequest.saksnummer,
                createBehandlingRequest.behandlerEnhet,
            ),
        )
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        return behandlingRepository.findBehandlingById(behandlingId)
    }

    fun hentBehandlinger(): List<Behandling> {
        return behandlingRepository.hentBehandlinger()
    }
}
