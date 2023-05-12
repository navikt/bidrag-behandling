package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.`404`
import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequestExtended
import org.springframework.stereotype.Service
import java.util.Date

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
) {
    fun createBehandling(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling)
    }

    fun oppdaterBehandling(
        behandlingId: Long,
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
        updatedBehandling.behandlingBarn = behandling.behandlingBarn
        updatedBehandling.barnetillegg = behandling.barnetillegg
        updatedBehandling.utvidetbarnetrygd = behandling.utvidetbarnetrygd

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
        updatedBehandling.barnetillegg = existingBehandling.barnetillegg
        updatedBehandling.utvidetbarnetrygd = existingBehandling.utvidetbarnetrygd

        return behandlingRepository.save(updatedBehandling)
    }

    fun hentBehandlingById(behandlingId: Long): Behandling {
        return behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
    }

    fun hentBehandlinger(): List<Behandling> {
        return behandlingRepository.hentBehandlinger()
    }

    fun oppdaterInntekter(
        behandlingId: Long,
        inntekter: MutableSet<Inntekt>,
        barnetillegg: MutableSet<Barnetillegg>,
        utvidetbarnetrygd: MutableSet<Utvidetbarnetrygd>,
    ) {
        val existingBehandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val updatedBehandling = existingBehandling.copy()

        updatedBehandling.inntekter = inntekter
        updatedBehandling.barnetillegg = barnetillegg
        updatedBehandling.utvidetbarnetrygd = utvidetbarnetrygd

        updatedBehandling.roller = existingBehandling.roller
        updatedBehandling.sivilstand = existingBehandling.sivilstand
        updatedBehandling.behandlingBarn = existingBehandling.behandlingBarn

        behandlingRepository.save(updatedBehandling)
    }

    fun updateVirkningsTidspunkt(
        behandlingId: Long,
        aarsak: ForskuddBeregningKodeAarsakType?,
        avslag: AvslagType?,
        virkningsDato: Date?,
        virkningsTidspunktBegrunnelseKunINotat: String?,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String?,
    ) {
        behandlingRepository.updateVirkningsTidspunkt(behandlingId, aarsak, avslag, virkningsDato, virkningsTidspunktBegrunnelseKunINotat, virkningsTidspunktBegrunnelseMedIVedtakNotat)
    }

    fun updateBoforhold(
        behandlingId: Long,
        behandlingBarn: MutableSet<BehandlingBarn>,
        sivilstand: MutableSet<Sivilstand>,
        boforholdBegrunnelseKunINotat: String?,
        boforholdBegrunnelseMedIVedtakNotat: String?,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }

        val newBehandling = behandling.copy(
            boforholdBegrunnelseKunINotat = boforholdBegrunnelseKunINotat,
            boforholdBegrunnelseMedIVedtakNotat = boforholdBegrunnelseMedIVedtakNotat,
        )

        newBehandling.behandlingBarn = behandlingBarn
        newBehandling.sivilstand = sivilstand

        newBehandling.barnetillegg = behandling.barnetillegg
        newBehandling.utvidetbarnetrygd = behandling.utvidetbarnetrygd
        newBehandling.roller = behandling.roller
        newBehandling.inntekter = behandling.inntekter

        behandlingRepository.save(newBehandling)
    }
}
