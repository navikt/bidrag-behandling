package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.consumer.BidragForsendelseConsumer
import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForsendelseBestilling
import no.nav.bidrag.behandling.database.datamodell.json.finnForGjelderOgMottaker
import no.nav.bidrag.behandling.database.datamodell.opprettUnikReferanse
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingStatus
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.dto.v1.forsendelse.erBehandlingType
import no.nav.bidrag.behandling.dto.v1.forsendelse.erGebyr
import no.nav.bidrag.behandling.dto.v1.forsendelse.erVedtakFattet
import no.nav.bidrag.commons.service.forsendelse.FellesForsendelseBestilling
import no.nav.bidrag.commons.service.forsendelse.FellesForsendelseMapper
import no.nav.bidrag.commons.service.forsendelse.FellesForsendelseService
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Språk
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.dokument.BidragEnhet.ENHET_FARSKAP
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.dokument.forsendelse.JournalTema
import no.nav.bidrag.transport.dokument.forsendelse.MottakerTo
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.transport.dokument.numeric
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class ForsendelseService(
    private val bidragForsendelseConsumer: BidragForsendelseConsumer,
    private val tilgangskontrollConsumer: BidragTilgangskontrollConsumer,
    private val fellesForsendelseService: FellesForsendelseService,
    private val forsendelseMapper: FellesForsendelseMapper,
) {
    private val ikkeOpprettVarslingForForskuddMedType =
        listOf(Vedtakstype.FASTSETTELSE, Vedtakstype.ENDRING)

    fun slettEllerOpprettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        if (request.behandlingStatus == BehandlingStatus.FEILREGISTRERT) {
            return slettForsendelse(request)
        }

        return opprettForsendelse(request)
    }

    private fun slettForsendelse(request: InitalizeForsendelseRequest): List<String> =
        slettVarselbrevUnderOpprettelse(
            request.saksnummer,
            request.behandlingInfo.soknadId!!.toLong(),
        ).map { it.toString() }

    @Transactional
    fun opprettForsendelseForAldersjustering(behandling: Behandling) {
        val bestillinger = behandling.forsendelseBestillinger
        val søknadsbarn = behandling.søknadsbarn.first()
        val bestillingBp =
            bestillinger.finnForGjelderOgMottaker(
                behandling.bidragspliktig!!.ident,
                behandling.bidragspliktig!!.ident,
                Rolletype.BIDRAGSPLIKTIG,
            )
                ?: run {
                    val bestilling =
                        ForsendelseBestilling(
                            gjelder = behandling.bidragspliktig!!.ident!!,
                            mottaker = behandling.bidragspliktig!!.ident!!,
                            rolletype = Rolletype.BIDRAGSPLIKTIG,
                            språkkode = Språk.NB,
                            dokumentmal = FellesForsendelseMapper.finnDokumentmalAldersjustering(behandling.stonadstype!!)!!,
                        )
                    bestillinger.bestillinger.add(bestilling)
                    bestilling
                }

        val mottakerInfo = forsendelseMapper.finnMottakerForKravhaver(søknadsbarn.ident!!, behandling.saksnummer)
        val bestillingBm =
            bestillinger.finnForGjelderOgMottaker(mottakerInfo.gjelder, mottakerInfo.mottakerIdent, mottakerInfo.mottakerRolle)
                ?: run {
                    val bestilling =
                        bestillingBp.copy(
                            gjelder = mottakerInfo.gjelder,
                            mottaker = mottakerInfo.mottakerIdent,
                            rolletype = mottakerInfo.mottakerRolle,
                            feilBegrunnelse = mottakerInfo.feilBegrunnelse,
                        )
                    bestillinger.bestillinger.add(bestilling)
                    bestilling
                }

        bestillingBm.feilBegrunnelse = mottakerInfo.feilBegrunnelse

        if (mottakerInfo.feilBegrunnelse.isNullOrEmpty()) {
            bestillingBm.opprett(behandling, søknadsbarn)
        }

        bestillingBp.opprett(behandling, søknadsbarn)
    }

    fun distribuerForsendelse(forsendelseBestilling: ForsendelseBestilling) {
        try {
            if (forsendelseBestilling.distribuertTidspunkt != null) {
                secureLogger.info { "Forsendelsebestilling $forsendelseBestilling er allerede distribuert" }
                return
            }
            val forsendelseId =
                forsendelseBestilling.forsendelseId ?: run {
                    secureLogger.error { "Forsendelsebestilling $forsendelseBestilling har ingen forsendelseId" }
                    return
                }
            val response = bidragForsendelseConsumer.distribuerForsendelse(forsendelseId)
            forsendelseBestilling.distribuertTidspunkt = LocalDateTime.now()
            forsendelseBestilling.journalpostId = response.journalpostId.numeric
            secureLogger.info {
                "Distribuerte forsendelse $forsendelseId med journalpostId ${response.journalpostId} og bestillingsid ${response.bestillingsId}"
            }
        } catch (e: Exception) {
            secureLogger.error(e) { "Feil ved distribusjon av forsendelse: ${forsendelseBestilling.forsendelseId}" }
            forsendelseBestilling.feilBegrunnelse = "Feil ved distribusjon av forsendelse: ${e.message}"
        }
    }

    private fun ForsendelseBestilling.opprett(
        behandling: Behandling,
        søknadsbarn: Rolle,
    ) {
        try {
            if (forsendelseId != null) return
            feilBegrunnelse = null
            val forsendelseId = fellesForsendelseService.opprettForsendelse(tilFellesForsendelseBestilling(behandling, søknadsbarn), true)
            this.forsendelseId = forsendelseId
            this.forsendelseOpprettetTidspunkt = LocalDateTime.now()
        } catch (e: Exception) {
            secureLogger.error(e) { "Feil ved opprettelse av forsendelse for bestilling $this" }
            this.feilBegrunnelse = "Feil ved opprettelse av forsendelse: ${e.message}"
        }
    }

    private fun opprettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        val opprettRequestTemplate =
            OpprettForsendelseForespørsel(
                behandlingInfo =
                    request.behandlingInfo
                        .copy(
                            erVedtakIkkeTilbakekreving = false,
                            barnIBehandling =
                                request.roller
                                    .filter { it.type == Rolletype.BARN && !it.fødselsnummer?.verdi.isNullOrEmpty() }
                                    .map { it.fødselsnummer!!.verdi },
                        ),
                saksnummer = request.saksnummer,
                enhet = request.enhet!!,
                gjelderIdent = "", // Placeholder: Settes i neste steg
                opprettTittel = true,
                språk = Språk.NB.name,
                tema =
                    request.tema
                        ?: if (request.enhet == ENHET_FARSKAP && harTilgangTilTemaFar()) JournalTema.FAR else JournalTema.BID,
            )

        val opprettForRoller = opprettForRoller(request.roller, request.behandlingInfo)
        secureLogger.debug {
            "Oppretter forsendelse ${request.behandlingInfo.typeForsendelse()}brev " +
                "for ${opprettForRoller.size} roller (${opprettForRoller.joinToString(",")}) og behandling ${request.behandlingInfo}"
        }
        val opprettetForsendelser = mutableListOf<String>()
        opprettForRoller.forEach {
            try {
                val forsendelseId =
                    bidragForsendelseConsumer
                        .opprettForsendelse(
                            opprettRequestTemplate.copy(
                                mottaker = MottakerTo(ident = it.fødselsnummer!!.verdi),
                                gjelderIdent = it.fødselsnummer.verdi,
                            ),
                        ).forsendelseId
                opprettetForsendelser.add(forsendelseId.toString())
                secureLogger.debug { "Opprettet forsendelse med id $forsendelseId for rolle $it, fnr: ${it.fødselsnummer.verdi}" }
            } catch (e: Exception) {
                log.error(
                    e,
                ) { "Det skjedde en feil ved opprettelse av forsendelse for rolle $it. Ignorerer feilen uten å opprette forsendelse" }
            }
        }
        if (request.behandlingInfo.erVedtakFattet()) {
            slettVarselbrevUnderOpprettelse(request.saksnummer, request.behandlingInfo.soknadId!!.toLong())
        }
        return opprettetForsendelser
    }

    private fun slettVarselbrevUnderOpprettelse(
        saksnummer: String,
        soknadId: Long,
    ): List<Long> {
        val forsendelser = bidragForsendelseConsumer.hentForsendelserISak(saksnummer)
        return forsendelser
            .filter { it.forsendelseType == ForsendelseTypeTo.UTGÅENDE }
            .filter { it.status == ForsendelseStatusTo.UNDER_OPPRETTELSE && it.behandlingInfo?.soknadId?.isNotEmpty() == true }
            .filter { it.behandlingInfo?.soknadId == soknadId.toString() && !it.behandlingInfo.erFattet }
            .map {
                bidragForsendelseConsumer.slettForsendelse(it.forsendelseId)
                log.info { "Slettet forsendelse ${it.forsendelseId} for varselbrev som var under opprettelse" }
                it.forsendelseId
            }
    }

    private fun harTilgangTilTemaFar() = tilgangskontrollConsumer.sjekkTilgangTema(tema = "FAR")

    private fun skalOppretteForsendelseForSoknad(behandlingInfo: BehandlingInfoDto): Boolean {
        val erFattet = behandlingInfo.erFattetBeregnet != null
        if (erFattet) return true
        return !(
            behandlingInfo.stonadType == Stønadstype.FORSKUDD &&
                ikkeOpprettVarslingForForskuddMedType.contains(behandlingInfo.vedtakType)
        )
    }

    private fun opprettForRoller(
        behandlingRoller: List<ForsendelseRolleDto>,
        behandlingInfoDto: BehandlingInfoDto,
    ): OpprettForsendelseForRollerListe {
        val roller = OpprettForsendelseForRollerListe()
        if (!skalOppretteForsendelseForSoknad(behandlingInfoDto)) return roller

        if (behandlingInfoDto.erGebyr()) {
            if (behandlingInfoDto.erBehandlingType(Engangsbeløptype.GEBYR_MOTTAKER)) {
                roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSMOTTAKER))
            } else {
                roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSPLIKTIG))
            }
            return roller
        }

        roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSMOTTAKER))

        if (!behandlingInfoDto.erBehandlingType(Stønadstype.FORSKUDD)) {
            roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSPLIKTIG))
        }

        if (behandlingInfoDto.erBehandlingType(Stønadstype.BIDRAG18AAR)) {
            behandlingRoller.hentBarn().forEach { roller.leggTil(it) }
        }

        return roller
    }
}

fun ForsendelseBestilling.tilFellesForsendelseBestilling(
    behandling: Behandling,
    søknadsbarn: Rolle,
): FellesForsendelseBestilling =
    FellesForsendelseBestilling(
        unikReferanse = behandling.opprettUnikReferanse("${rolletype!!.name}_${søknadsbarn.ident}"),
        gjelder = gjelder!!,
        mottaker = mottaker!!,
        saksnummer = behandling.saksnummer,
        språkkode = this.språkkode ?: Språk.NB,
        dokumentmal = this.dokumentmal!!,
        behandlingInfoDto =
            FellesForsendelseMapper.byggBehandlingInfoDtoForAldersjustering(
                behandling.vedtaksid.toString(),
                behandling.stonadstype!!,
                listOf(søknadsbarn.ident!!),
            ),
    )

class OpprettForsendelseForRollerListe : MutableList<ForsendelseRolleDto> by mutableListOf() {
    fun leggTil(rolle: ForsendelseRolleDto?) {
        if (rolle?.fødselsnummer == null) return
        val fødselsnummer = rolle.fødselsnummer
        if (fødselsnummer.verdi.isNotEmpty()) this.add(rolle)
    }
}

fun BehandlingInfoDto.typeForsendelse() = if (this.erVedtakFattet()) "vedtak" else "varsel"

fun List<ForsendelseRolleDto>.hentRolle(rolleType: Rolletype): ForsendelseRolleDto? =
    this.find {
        it.type == rolleType
    }

fun List<ForsendelseRolleDto>.hentBarn(): List<ForsendelseRolleDto> =
    this.filter {
        it.type == Rolletype.BARN
    }
