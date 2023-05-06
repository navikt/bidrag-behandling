package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.`404`
import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequestExtended
import no.nav.bidrag.behandling.dto.behandlingbarn.BehandlingBarnDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.transformers.toDate
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toInntektDomain
import org.springframework.stereotype.Service
import java.util.Date

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
) {
    fun createBehandling(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling)
    }

    fun oppdaterBehandlingBarn(behandlingId: Long, behandlingBarn: Set<BehandlingBarnDto>): Behandling {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val nyBehandling = behandling.copy()
        nyBehandling.behandlingBarn = behandlingBarn.toDomain(behandling)
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
        updatedBehandling.inntekter = behandling.inntekter
        updatedBehandling.sivilstand = behandling.sivilstand

        if (behandlingBarn != null) {
            val updatedBehandlingBarn = behandlingBarn.map {
                BehandlingBarn(
                    behandling,
                    it.medISaken,
                    it.id,
                    it.ident,
                    it.navn,
                    it.foedselsDato?.toDate(),
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
        updatedBehandling.inntekter = existingBehandling.inntekter
        updatedBehandling.sivilstand = existingBehandling.sivilstand
        updatedBehandling.behandlingBarn = existingBehandling.behandlingBarn

        return behandlingRepository.save(updatedBehandling)
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        return behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
    }

    fun hentBehandlinger(): List<Behandling> {
        return behandlingRepository.hentBehandlinger()
    }

    fun oppdaterInntekter(behandlingId: Long, inntekter: Set<InntektDto>): Set<Inntekt> {
        val existingBehandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val updatedBehandling = existingBehandling.copy()

        updatedBehandling.inntekter = inntekter.toInntektDomain(existingBehandling)

        updatedBehandling.roller = existingBehandling.roller
        updatedBehandling.sivilstand = existingBehandling.sivilstand
        updatedBehandling.behandlingBarn = existingBehandling.behandlingBarn

        behandlingRepository.save(updatedBehandling)
        return behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }.inntekter
    }
}
