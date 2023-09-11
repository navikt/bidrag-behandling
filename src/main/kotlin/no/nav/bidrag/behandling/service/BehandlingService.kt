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
    fun createBehandling(behandling: Behandling): Behandling =
        behandlingRepository.save(behandling)
            .let {
                opprettForsendelseForBehandling(it)
                it
            }

    private fun opprettForsendelseForBehandling(behandling: Behandling) {
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
            ),
        )
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
    ): Behandling = behandlingRepository.save(
        behandlingRepository.findBehandlingById(behandlingId)
            .orElseThrow { `404`(behandlingId) }
            .let {
                it.virkningsTidspunktBegrunnelseMedIVedtakNotat = virkningsTidspunktBegrunnelseMedIVedtakNotat
                it.virkningsTidspunktBegrunnelseKunINotat = virkningsTidspunktBegrunnelseKunINotat
                it.boforholdBegrunnelseMedIVedtakNotat = boforholdBegrunnelseMedIVedtakNotat
                it.boforholdBegrunnelseKunINotat = boforholdBegrunnelseKunINotat
                it.inntektBegrunnelseMedIVedtakNotat = inntektBegrunnelseMedIVedtakNotat
                it.inntektBegrunnelseKunINotat = inntektBegrunnelseKunINotat
                it.aarsak = aarsak
                it.virkningsDato = virkningsDato
                it
            },
    )

    fun hentBehandlingById(behandlingId: Long): Behandling =
        behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }

    fun hentBehandlinger(): List<Behandling> = behandlingRepository.hentBehandlinger()

    fun oppdaterInntekter(
        behandlingId: Long,
        inntekter: MutableSet<Inntekt>,
        barnetillegg: MutableSet<Barnetillegg>,
        utvidetbarnetrygd: MutableSet<Utvidetbarnetrygd>,
        inntektBegrunnelseMedIVedtakNotat: String?,
        inntektBegrunnelseKunINotat: String?,
    ) =
        behandlingRepository.save(
            behandlingRepository.findBehandlingById(behandlingId)
                .orElseThrow { `404`(behandlingId) }
                .let {
                    it.inntektBegrunnelseMedIVedtakNotat = inntektBegrunnelseMedIVedtakNotat
                    it.inntektBegrunnelseKunINotat = inntektBegrunnelseKunINotat

                    it.inntekter.clear()
                    it.inntekter.addAll(inntekter)

                    it.barnetillegg.clear()
                    it.barnetillegg.addAll(barnetillegg)

                    it.utvidetbarnetrygd.clear()
                    it.utvidetbarnetrygd.addAll(utvidetbarnetrygd)

                    it
                },
        )

    fun updateVirkningsTidspunkt(
        behandlingId: Long,
        aarsak: ForskuddAarsakType?,
        virkningsDato: Date?,
        virkningsTidspunktBegrunnelseKunINotat: String?,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String?,
    ) =
        behandlingRepository.updateVirkningsTidspunkt(behandlingId, aarsak, virkningsDato, virkningsTidspunktBegrunnelseKunINotat, virkningsTidspunktBegrunnelseMedIVedtakNotat)

    fun updateBoforhold(
        behandlingId: Long,
        husstandsBarn: MutableSet<HusstandsBarn>,
        sivilstand: MutableSet<Sivilstand>,
        boforholdBegrunnelseKunINotat: String?,
        boforholdBegrunnelseMedIVedtakNotat: String?,
    ) =
        behandlingRepository.save(
            behandlingRepository.findBehandlingById(behandlingId)
                .orElseThrow { `404`(behandlingId) }
                .let {
                    it.boforholdBegrunnelseKunINotat = boforholdBegrunnelseKunINotat
                    it.boforholdBegrunnelseMedIVedtakNotat = boforholdBegrunnelseMedIVedtakNotat

                    it.husstandsBarn.clear()
                    it.husstandsBarn.addAll(husstandsBarn)

                    it.sivilstand.clear()
                    it.sivilstand.addAll(sivilstand)

                    it
                },
        )

    @Transactional
    fun oppdaterVedtakId(behandlingId: Long, vedtakId: Long) = behandlingRepository.oppdaterVedtakId(behandlingId, vedtakId)
}
