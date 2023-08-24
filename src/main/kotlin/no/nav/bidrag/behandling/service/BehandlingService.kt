package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.`404`
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequestExtended
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.tilVedtakType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val forsendelseService: ForsendelseService,
) {
    fun createBehandling(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling).let {
            opprettForsendelseForBehandling(it)
            it
        }
    }

    private fun opprettForsendelseForBehandling(behandling: Behandling){
        forsendelseService.opprettForsendelse(
            InitalizeForsendelseRequest(
                saksnummer = behandling.saksnummer,
                enhet = behandling.behandlerEnhet,
                roller = behandling.tilForsendelseRolleDto(),
                behandlingInfo = BehandlingInfoDto(
                    behandlingId = behandling.id,
                    soknadId = behandling.soknadId,
                    soknadFra = behandling.soknadFra,
                    behandlingType = behandling.behandlingType.name,
                    stonadType = behandling.stonadType,
                    engangsBelopType = behandling.engangsbelopType,
                    vedtakType = behandling.soknadType.tilVedtakType(),
                ),
            ))
    }
    fun oppdaterBehandling(
        behandlingId: Long,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,
        virkningsTidspunktBegrunnelseKunINotat: String? = null,
        boforholdBegrunnelseMedIVedtakNotat: String? = null,
        boforholdBegrunnelseKunINotat: String? = null,
        inntektBegrunnelseMedIVedtakNotat: String? = null,
        inntektBegrunnelseKunINotat: String? = null,
        aarsak: ForskuddAarsakType? = null,
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
            aarsak = aarsak,
            virkningsDato = virkningsDato,
        )
        updatedBehandling.roller = behandling.roller
        updatedBehandling.inntekter = behandling.inntekter
        updatedBehandling.sivilstand = behandling.sivilstand
        updatedBehandling.husstandsBarn = behandling.husstandsBarn
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
        updatedBehandling.husstandsBarn = existingBehandling.husstandsBarn
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
        inntektBegrunnelseMedIVedtakNotat: String?,
        inntektBegrunnelseKunINotat: String?,
    ) {
        val existingBehandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        val updatedBehandling = existingBehandling.copy(
            inntektBegrunnelseMedIVedtakNotat = inntektBegrunnelseMedIVedtakNotat,
            inntektBegrunnelseKunINotat = inntektBegrunnelseKunINotat,
        )

        updatedBehandling.inntekter = inntekter
        updatedBehandling.barnetillegg = barnetillegg
        updatedBehandling.utvidetbarnetrygd = utvidetbarnetrygd

        updatedBehandling.roller = existingBehandling.roller
        updatedBehandling.sivilstand = existingBehandling.sivilstand
        updatedBehandling.husstandsBarn = existingBehandling.husstandsBarn

        behandlingRepository.save(updatedBehandling)
    }

    fun updateVirkningsTidspunkt(
        behandlingId: Long,
        aarsak: ForskuddAarsakType?,
        virkningsDato: Date?,
        virkningsTidspunktBegrunnelseKunINotat: String?,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String?,
    ) {
        behandlingRepository.updateVirkningsTidspunkt(behandlingId, aarsak, virkningsDato, virkningsTidspunktBegrunnelseKunINotat, virkningsTidspunktBegrunnelseMedIVedtakNotat)
    }

    fun updateBoforhold(
        behandlingId: Long,
        husstandsBarn: MutableSet<HusstandsBarn>,
        sivilstand: MutableSet<Sivilstand>,
        boforholdBegrunnelseKunINotat: String?,
        boforholdBegrunnelseMedIVedtakNotat: String?,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }

        val newBehandling = behandling.copy(
            boforholdBegrunnelseKunINotat = boforholdBegrunnelseKunINotat,
            boforholdBegrunnelseMedIVedtakNotat = boforholdBegrunnelseMedIVedtakNotat,
        )

        newBehandling.husstandsBarn = husstandsBarn
        newBehandling.sivilstand = sivilstand

        newBehandling.barnetillegg = behandling.barnetillegg
        newBehandling.utvidetbarnetrygd = behandling.utvidetbarnetrygd
        newBehandling.roller = behandling.roller
        newBehandling.inntekter = behandling.inntekter

        behandlingRepository.save(newBehandling)
    }

    @Transactional
    fun oppdaterVedtakId(behandlingId: Long, vedtakId: Long) {
        behandlingRepository.oppdaterVedtakId(behandlingId, vedtakId)
    }
}
