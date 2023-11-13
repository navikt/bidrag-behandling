package no.nav.bidrag.behandling.service

import mu.KotlinLogging
import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.consumer.BidragForsendelseConsumer
import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingStatus
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.dto.forsendelse.MottakerDto
import no.nav.bidrag.behandling.dto.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.domene.enums.Engangsbeløptype
import no.nav.bidrag.domene.enums.Rolletype
import no.nav.bidrag.domene.enums.Stønadstype
import no.nav.bidrag.domene.enums.Vedtakstype
import no.nav.bidrag.transport.dokument.BidragEnhet.ENHET_FARSKAP
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class ForsendelseService(
    private val bidragForsendelseConsumer: BidragForsendelseConsumer,
    private val tilgangskontrollConsumer: BidragTilgangskontrollConsumer,
) {
    private val ikkeOpprettVarslingForForskuddMedType =
        listOf(Vedtakstype.FASTSETTELSE, Vedtakstype.ENDRING)

    fun slettEllerOpprettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        if (request.behandlingStatus == BehandlingStatus.FEILREGISTRERT) {
            return slettForsendelse(request)
        }

        return opprettForsendelse(request)
    }

    private fun slettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        return slettVarselbrevUnderOpprettelse(
            request.saksnummer,
            request.behandlingInfo.soknadId,
        ).map { it.toString() }
    }

    private fun opprettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        val opprettRequestTemplate = OpprettForsendelseForespørsel(
            behandlingInfo = request.behandlingInfo
                .copy(
                    barnIBehandling = request.roller
                        .filter { it.type == Rolletype.BARN && !it.fødselsnummer?.verdi.isNullOrEmpty() }
                        .map { it.fødselsnummer!!.verdi },
                ),
            saksnummer = request.saksnummer,
            enhet = request.enhet,
            tema = request.tema
                ?: if (request.enhet == ENHET_FARSKAP && harTilgangTilTemaFar()) "FAR" else "BID",
        )

        val opprettForRoller = opprettForRoller(request.roller, request.behandlingInfo)
        log.info {
            "Oppretter forsendelse ${request.behandlingInfo.typeForsendelse()}brev " +
                "for ${opprettForRoller.size} roller (${opprettForRoller.joinToString(",")}) og behandling ${request.behandlingInfo}"
        }
        val opprettetForsendelser = mutableListOf<String>()
        opprettForRoller.forEach {
            try {
                val response = bidragForsendelseConsumer.opprettForsendelse(
                    opprettRequestTemplate.copy(
                        mottaker = MottakerDto(ident = it.fødselsnummer!!.verdi),
                        gjelderIdent = it.fødselsnummer.verdi,
                    ),
                )
                opprettetForsendelser.add(response.forsendelseId ?: "-1")
                log.info { "Opprettet forsendelse med id ${response.forsendelseId} for rolle $it" }
                SECURE_LOGGER.info("Opprettet forsendelse med id ${response.forsendelseId} for rolle $it, fnr: ${it.fødselsnummer.verdi}")
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved opprettelse av forsendelse for rolle $it. Ignorerer feilen uten å opprette forsendelse" }
            }
        }
        if (request.behandlingInfo.erVedtakFattet()) {
            slettVarselbrevUnderOpprettelse(request.saksnummer, request.behandlingInfo.soknadId)
        }
        return opprettetForsendelser
    }

    private fun slettVarselbrevUnderOpprettelse(saksnummer: String, soknadId: Long): List<Long> {
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

class OpprettForsendelseForRollerListe : MutableList<ForsendelseRolleDto> by mutableListOf() {
    fun leggTil(rolle: ForsendelseRolleDto?) {
        if (rolle?.fødselsnummer == null) return
        val fødselsnummer = rolle.fødselsnummer
        if (fødselsnummer.verdi.isNotEmpty()) this.add(rolle)
    }
}

fun BehandlingInfoDto.typeForsendelse() = if (this.erVedtakFattet()) "vedtak" else "varsel"
fun List<ForsendelseRolleDto>.hentRolle(rolleType: Rolletype): ForsendelseRolleDto? = this.find { it.type == rolleType }

fun List<ForsendelseRolleDto>.hentBarn(): List<ForsendelseRolleDto> = this.filter { it.type == Rolletype.BARN }
