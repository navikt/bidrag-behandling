package no.nav.bidrag.behandling.service

import mu.KotlinLogging
import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.RolleRepository
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.transformers.tilBehandlingDto
import no.nav.bidrag.behandling.transformers.tilForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.toBarnetilleggDomain
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toInntektDomain
import no.nav.bidrag.behandling.transformers.toRolle
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import no.nav.bidrag.behandling.transformers.toUtvidetBarnetrygdDomain
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Service
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val rolleRepository: RolleRepository,
    private val forsendelseService: ForsendelseService,
    private val opplysningerService: OpplysningerService,
) {
    fun opprettBehandling(behandling: Behandling): Behandling =
        behandlingRepository.save(behandling)
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
                        soknadId = behandling.soknadsid,
                        soknadFra = behandling.soknadFra,
                        behandlingType = behandling.tilBehandlingstype(),
                        stonadType = behandling.stonadstype,
                        engangsBelopType = behandling.engangsbeloptype,
                        vedtakType = behandling.vedtakstype,
                    ),
            ),
        )
    }

    fun deleteBehandlingById(behandlingId: Long) = behandlingRepository.deleteById(behandlingId)

    @Transactional
    fun oppdaterBehandlingV1(
        behandlingsid: Long,
        oppdaterBehandling: OppdaterBehandlingRequest,
    ): BehandlingDto =
        behandlingRepository.save(
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }
                .let {
                    log.info { "Oppdaterer behandling $behandlingsid" }
                    SECURE_LOGGER.info("Oppdaterer behandling $behandlingsid for forespørsel $oppdaterBehandling")
                    it.grunnlagspakkeid = oppdaterBehandling.grunnlagspakkeId ?: it.grunnlagspakkeid
                    it.vedtaksid = oppdaterBehandling.vedtaksid ?: it.vedtaksid
                    oppdaterBehandling.virkningstidspunkt?.let { vt ->
                        log.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid" }
                        it.aarsak = vt.årsak
                        it.virkningsdato = vt.virkningsdato
                        it.virkningstidspunktbegrunnelseKunINotat =
                            vt.notat?.kunINotat ?: it.virkningstidspunktbegrunnelseKunINotat
                        it.virkningstidspunktsbegrunnelseIVedtakOgNotat = vt.notat?.medIVedtaket
                            ?: it.virkningstidspunktsbegrunnelseIVedtakOgNotat
                    }
                    oppdaterBehandling.inntekter?.let { inntekter ->
                        log.info { "Oppdaterer inntekter for behandling $behandlingsid" }
                        inntekter.inntekter?.run {
                            it.inntekter.clear()
                            it.inntekter.addAll(inntekter.inntekter.toInntektDomain(it))
                        }
                        inntekter.barnetillegg?.run {
                            it.barnetillegg.clear()
                            it.barnetillegg.addAll(inntekter.barnetillegg.toBarnetilleggDomain(it))
                        }
                        inntekter.utvidetbarnetrygd?.run {
                            it.utvidetBarnetrygd.clear()
                            it.utvidetBarnetrygd.addAll(
                                inntekter.utvidetbarnetrygd.toUtvidetBarnetrygdDomain(
                                    it,
                                ),
                            )
                        }
                        it.inntektsbegrunnelseKunINotat =
                            inntekter.notat?.kunINotat ?: it.inntektsbegrunnelseKunINotat
                        it.inntektsbegrunnelseIVedtakOgNotat =
                            inntekter.notat?.medIVedtaket ?: it.inntektsbegrunnelseIVedtakOgNotat
                    }
                    oppdaterBehandling.boforhold?.let { bf ->
                        log.info { "Oppdaterer informasjon om boforhold for behandling $behandlingsid" }
                        bf.sivilstand?.run {
                            it.sivilstand.clear()
                            it.sivilstand.addAll(bf.sivilstand.toSivilstandDomain(it))
                        }
                        bf.husstandsbarn?.run {
                            it.husstandsbarn.clear()
                            it.husstandsbarn.addAll(bf.husstandsbarn.toDomain(it))
                        }
                        it.boforholdsbegrunnelseKunINotat =
                            bf.notat?.kunINotat ?: it.boforholdsbegrunnelseKunINotat
                        it.boforholdsbegrunnelseIVedtakOgNotat =
                            bf.notat?.medIVedtaket ?: it.boforholdsbegrunnelseIVedtakOgNotat
                    }
                    it
                },
        ).tilBehandlingDto(opplysningerService.hentAlleSistAktiv(behandlingsid))

    fun oppdaterBehandling(
        behandlingsid: Long,
        virkningstidspunktBegrunnelseMedIVedtakNotat: String? = null,
        virkningstidspunktBegrunnelseKunINotat: String? = null,
        boforholdsbegrunnelseMedIVedtakOgNotat: String? = null,
        boforholdsbegrunnelseKunINotat: String? = null,
        inntektsbegrunnelseMedIVedtakNotat: String? = null,
        inntektsbegrunnelseKunINotat: String? = null,
        årsak: ForskuddAarsakType? = null,
        virkningsdato: LocalDate? = null,
    ): Behandling =
        behandlingRepository.save(
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }
                .let {
                    it.virkningstidspunktsbegrunnelseIVedtakOgNotat =
                        virkningstidspunktBegrunnelseMedIVedtakNotat
                    it.virkningstidspunktbegrunnelseKunINotat =
                        virkningstidspunktBegrunnelseKunINotat
                    it.boforholdsbegrunnelseIVedtakOgNotat = boforholdsbegrunnelseMedIVedtakOgNotat
                    it.boforholdsbegrunnelseKunINotat = boforholdsbegrunnelseKunINotat
                    it.inntektsbegrunnelseIVedtakOgNotat = inntektsbegrunnelseMedIVedtakNotat
                    it.inntektsbegrunnelseKunINotat = inntektsbegrunnelseKunINotat
                    it.aarsak = årsak
                    it.virkningsdato = virkningsdato
                    it
                },
        )

    fun hentBehandlingById(behandlingId: Long): Behandling =
        behandlingRepository.findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }

    @Transactional
    fun oppdaterInntekter(
        behandlingsid: Long,
        nyeInntekter: Set<InntektDto>,
        nyeBarnetillegg: Set<BarnetilleggDto>,
        nyUtvidetBarnetrygd: Set<UtvidetBarnetrygdDto>,
        inntektsbegrunnelseMedIVedtakOgNotat: String?,
        inntektsbegrunnelseKunINotat: String?,
    ) {
        var behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        behandling.inntektsbegrunnelseIVedtakOgNotat = inntektsbegrunnelseMedIVedtakOgNotat
        behandling.inntektsbegrunnelseKunINotat = inntektsbegrunnelseKunINotat

        var inntektOppdatert = false

        val inntekter = nyeInntekter.toInntektDomain(behandling)
        val barnetillegg = nyeBarnetillegg.toBarnetilleggDomain(behandling)
        val nyUtvidetbarnetrygd = nyUtvidetBarnetrygd.toUtvidetBarnetrygdDomain(behandling)

        if (behandling.inntekter != inntekter) {
            log.info("Oppdaterer inntekter for behandlingsid $behandlingsid")
            behandling.inntekter.clear()
            behandling.inntekter.addAll(inntekter)
            inntektOppdatert = true
        }

        if (behandling.barnetillegg != barnetillegg) {
            log.info("Oppdaterer barnetillegg for behandlingsid $behandlingsid")
            behandling.barnetillegg.clear()
            behandling.barnetillegg.addAll(barnetillegg)
            inntektOppdatert = true
        }

        if (behandling.utvidetBarnetrygd != nyUtvidetbarnetrygd) {
            log.info("Oppdaterer utvidet barnetrygd for behandlingsid $behandlingsid")
            behandling.utvidetBarnetrygd.clear()
            behandling.utvidetBarnetrygd.addAll(nyUtvidetbarnetrygd)
            inntektOppdatert = true
        }

        if (inntektOppdatert == true) {
            behandlingRepository.save(behandling)
        }
    }

    fun oppdatereVirkningstidspunkt(
        behandlingsid: Long,
        årsak: ForskuddAarsakType?,
        virkningsdato: LocalDate?,
        virkningstidspunktbegrunnelseKunINotat: String?,
        virkningstidspunktbegrunnelseMedIVedtakOgNotat: String?,
    ) = behandlingRepository.updateVirkningstidspunkt(
        behandlingsid,
        årsak,
        virkningsdato,
        virkningstidspunktbegrunnelseKunINotat,
        virkningstidspunktbegrunnelseMedIVedtakOgNotat,
    )

    @Transactional
    fun updateBoforhold(
        behandlingsid: Long,
        husstandsbarn: Set<HusstandsbarnDto>,
        sivilstand: Set<SivilstandDto>,
        boforholdsbegrunnelseKunINotat: String?,
        boforholdsbegrunnelseMedIVedtakOgNotat: String?,
    ) {
        var behandling = hentBehandlingById(behandlingsid)
        behandling.boforholdsbegrunnelseKunINotat = boforholdsbegrunnelseKunINotat
        behandling.boforholdsbegrunnelseIVedtakOgNotat = boforholdsbegrunnelseMedIVedtakOgNotat

        behandling.husstandsbarn.clear()
        behandling.husstandsbarn.addAll(husstandsbarn.toDomain(behandling))

        behandling.sivilstand.clear()
        behandling.sivilstand.addAll(sivilstand.toSivilstandDomain(behandling))

        behandlingRepository.save(behandling)
    }

    @Transactional
    fun oppdaterVedtakId(
        behandlingId: Long,
        vedtakId: Long,
    ) = behandlingRepository.oppdaterVedtakId(behandlingId, vedtakId)

    @Transactional
    fun syncRoller(
        behandlingId: Long,
        roller: List<OpprettRolleDto>,
    ) {
        val existingRoller = rolleRepository.findRollerByBehandlingId(behandlingId)

        val behandling = behandlingRepository.findById(behandlingId).get()

        val rollerSomLeggesTil =
            roller.filter { r ->
                // not deleted and behandling.roller doesn't contain it yet
                !r.erSlettet && !existingRoller.any { br -> br.ident == r.ident?.verdi }
            }

        behandling.roller.addAll(rollerSomLeggesTil.map { it.toRolle(behandling) })

        val identsSomSkalSlettes = roller.filter { r -> r.erSlettet }.map { it.ident?.verdi }
        behandling.roller.removeIf { r -> identsSomSkalSlettes.contains(r.ident) }

        behandlingRepository.save(behandling)

        if (behandling.roller.none { r -> r.rolletype == Rolletype.BARN }) {
            behandlingRepository.delete(behandling)
        }
    }

    fun oppdaterGrunnlagspakkeid(
        behandlingId: Long,
        grunnlagspakkeId: Long?,
    ) {
        hentBehandlingById(behandlingId).let {
            it.grunnlagspakkeid = grunnlagspakkeId
            behandlingRepository.save(it)
        }
    }

    fun oppfriskeGrunnlagsdata(grunlagspakkeid: Long) {
        val grunnlagspakke = bidragGrunnlagConsumer.henteGrunnlagspakke(grunlagspakkeid)
    }
}
