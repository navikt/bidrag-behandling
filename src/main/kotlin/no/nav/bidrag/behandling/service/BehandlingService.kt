package no.nav.bidrag.behandling.service

import mu.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.RolleRepository
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.tilVedtakType
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.domene.enums.Rolletype
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

private val log = KotlinLogging.logger {}

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val rolleRepository: RolleRepository,
    private val forsendelseService: ForsendelseService,
) {
    fun createBehandling(behandling: Behandling): Behandling = behandlingRepository.save(behandling)
        .let {
            opprettForsendelseForBehandling(it)
            it
        }

    private fun opprettForsendelseForBehandling(behandling: Behandling) {
        forsendelseService.slettEllerOpprettForsendelse(
            InitalizeForsendelseRequest(
                saksnummer = behandling.saksnummer,
                enhet = behandling.behandlerEnhet,
                roller = behandling.tilForsendelseRolleDto(),
                behandlingInfo =
                BehandlingInfoDto(
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

    fun deleteBehandlingById(behandlingId: Long) = behandlingRepository.deleteById(behandlingId)

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
            .orElseThrow { behandlingNotFoundException(behandlingId) }
            .let {
                it.virkningsTidspunktBegrunnelseMedIVedtakNotat =
                    virkningsTidspunktBegrunnelseMedIVedtakNotat
                it.virkningsTidspunktBegrunnelseKunINotat =
                    virkningsTidspunktBegrunnelseKunINotat
                it.boforholdBegrunnelseMedIVedtakNotat = boforholdBegrunnelseMedIVedtakNotat
                it.boforholdBegrunnelseKunINotat = boforholdBegrunnelseKunINotat
                it.inntektBegrunnelseMedIVedtakNotat = inntektBegrunnelseMedIVedtakNotat
                it.inntektBegrunnelseKunINotat = inntektBegrunnelseKunINotat
                it.aarsak = aarsak
                it.virkningsDato = virkningsDato
                it
            },
    )

    fun hentBehandlingById(behandlingId: Long): Behandling = behandlingRepository.findBehandlingById(behandlingId)
        .orElseThrow { behandlingNotFoundException(behandlingId) }

    fun hentBehandlinger(): List<Behandling> = behandlingRepository.hentBehandlinger()

    @Transactional
    fun oppdaterInntekter(
        behandlingId: Long,
        inntekter: MutableSet<Inntekt>,
        barnetillegg: MutableSet<Barnetillegg>,
        utvidetbarnetrygd: MutableSet<Utvidetbarnetrygd>,
        inntektBegrunnelseMedIVedtakNotat: String?,
        inntektBegrunnelseKunINotat: String?,
    ) {
        var behandling = behandlingRepository.findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }

        behandling.inntektBegrunnelseMedIVedtakNotat = inntektBegrunnelseMedIVedtakNotat
        behandling.inntektBegrunnelseKunINotat = inntektBegrunnelseKunINotat

        if (behandling.inntekter != inntekter) {
            log.info("Oppdaterer inntekter for behandlingsid $behandlingId")
            behandling.inntekter.clear()
            behandling.inntekter.addAll(inntekter)
        }

        if (behandling.barnetillegg != barnetillegg) {
            log.info("Oppdaterer barnetillegg for behandlingsid $behandlingId")
            behandling.barnetillegg.clear()
            behandling.barnetillegg.addAll(barnetillegg)
        }

        if (behandling.utvidetbarnetrygd != utvidetbarnetrygd) {
            log.info("Oppdaterer utvidet barnetrygd for behandlingsid $behandlingId")
            behandling.utvidetbarnetrygd.clear()
            behandling.utvidetbarnetrygd.addAll(utvidetbarnetrygd)
        }
    }

    fun updateVirkningsTidspunkt(
        behandlingId: Long,
        aarsak: ForskuddAarsakType?,
        virkningsDato: Date?,
        virkningsTidspunktBegrunnelseKunINotat: String?,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String?,
    ) = behandlingRepository.updateVirkningsTidspunkt(
        behandlingId,
        aarsak,
        virkningsDato,
        virkningsTidspunktBegrunnelseKunINotat,
        virkningsTidspunktBegrunnelseMedIVedtakNotat,
    )

    fun updateBoforhold(
        behandlingId: Long,
        husstandsBarn: MutableSet<HusstandsBarn>,
        sivilstand: MutableSet<Sivilstand>,
        boforholdBegrunnelseKunINotat: String?,
        boforholdBegrunnelseMedIVedtakNotat: String?,
    ) = behandlingRepository.save(
        behandlingRepository.findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }
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

    @Transactional
    fun syncRoller(behandlingId: Long, roller: List<CreateRolleDto>) {
        val existingRoller = rolleRepository.findRollerByBehandlingId(behandlingId)

        val behandling = behandlingRepository.findById(behandlingId).get()

        val rollerSomLeggesTil =
            roller.filter { r ->
                // not deleted and behandling.roller doesn't contain it yet
                !r.erSlettet && !existingRoller.any { br -> br.ident == r.ident }
            }

        behandling.roller.addAll(rollerSomLeggesTil.map { it.toRolle(behandling) })

        val identsSomSkalSlettes = roller.filter { r -> r.erSlettet }.map { it.ident }
        behandling.roller.removeIf { r -> identsSomSkalSlettes.contains(r.ident) }

        behandlingRepository.save(behandling)

        if (behandling.roller.none { r -> r.rolleType == Rolletype.BARN }) {
            behandlingRepository.delete(behandling)
        }
    }

    fun updateBehandling(behandlingId: Long, grunnlagspakkeId: Long?) {
        hentBehandlingById(behandlingId).let {
            it.grunnlagspakkeId = grunnlagspakkeId
            behandlingRepository.save(it)
        }
    }
}
