package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.BehandlingBarnDto
import no.nav.bidrag.behandling.dto.UpdateBehandlingRequestExtended
import org.springframework.stereotype.Service
import java.util.Date

@Service
class BehandlingService(
    val behandlingRepository: BehandlingRepository,
) {
    fun createBehandling(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling)
    }

    fun oppdaterBehandlingBarn(behandlingId: Long, behandlingBarn: Set<BehandlingBarnDto>): Behandling {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val nyBehandling = behandling.copy()
        nyBehandling.behandlingBarn = behandlingBarn.map {
            BehandlingBarn(
                behandling,
                it.medISaken,
                it.fraDato,
                it.tilDato,
                it.boStatus,
                it.kilde,
                it.id,
                it.ident,
                it.navn,
                it.foedselsDato,
            )
        }.toMutableSet()

        return behandlingRepository.save(nyBehandling)
    }

    fun oppdaterBehandling(
        behandlingId: Long,
        behandlingBarn: Set<BehandlingBarnDto>?,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,
        virkningsTidspunktBegrunnelseKunINotat: String? = null,
        boforholdBegrunnelseMedIVedtakNotat: String? = null,
        boforholdBegrunnelseKunINotat: String? = null,
        inntektBegrunnelseMedIVedtakNotat: String? = null,
        inntektBegrunnelseKunINotat: String? = null,
        avslag: AvslagType? = null,
        aarsak: ForskuddBeregningKodeAarsakType? = null,
        virkningsDato: Date? = null,
    ): Behandling {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val updatedBehandling = behandling.copy(
            virkningsTidspunktBegrunnelseMedIVedtakNotat = virkningsTidspunktBegrunnelseMedIVedtakNotat,
            virkningsTidspunktBegrunnelseKunINotat = virkningsTidspunktBegrunnelseKunINotat,
            boforholdBegrunnelseMedIVedtakNotat = boforholdBegrunnelseMedIVedtakNotat,
            boforholdBegrunnelseKunINotat = boforholdBegrunnelseKunINotat,
            inntektBegrunnelseMedIVedtakNotat = inntektBegrunnelseMedIVedtakNotat,
            inntektBegrunnelseKunINotat = inntektBegrunnelseKunINotat,
            avslag = avslag,
            aarsak = aarsak,
            virkningsDato = virkningsDato,
        )
        updatedBehandling.roller = behandling.roller

        if (behandlingBarn != null) {
            val updatedBehandlingBarn = behandlingBarn.map {
                BehandlingBarn(
                    behandling,
                    it.medISaken,
                    it.fraDato,
                    it.tilDato,
                    it.boStatus,
                    it.kilde,
                    it.id,
                    it.ident,
                    it.navn,
                    it.foedselsDato,
                )
            }.toMutableSet()

            updatedBehandling.behandlingBarn = updatedBehandlingBarn
        } else {
            updatedBehandling.behandlingBarn = behandling.behandlingBarn
        }
        return behandlingRepository.save(updatedBehandling)
    }

    fun oppdaterBehandlingExtended(
        behandlingId: Long,
        behandlingRequest: UpdateBehandlingRequestExtended,
    ): Behandling {
        val existingBehandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val updatedBehandling = existingBehandling.copy(
            soknadFra = behandlingRequest.soknadFraType,
            soknadType = behandlingRequest.soknadType,
            mottatDato = behandlingRequest.mottatDato,
            datoFom = behandlingRequest.datoFom,
        )
        updatedBehandling.roller = existingBehandling.roller
        return behandlingRepository.save(updatedBehandling)
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        return behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
    }

    fun hentBehandlinger(): List<Behandling> {
        return behandlingRepository.hentBehandlinger()
    }
}
