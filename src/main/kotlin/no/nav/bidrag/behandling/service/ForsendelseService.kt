package no.nav.bidrag.behandling.service

import mu.KotlinLogging
import no.nav.bidrag.behandling.SECURE_LOGGER
import no.nav.bidrag.behandling.consumer.BidragForsendelseConsumer
import no.nav.bidrag.behandling.consumer.BidragTIlgangskontrollConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.dto.forsendelse.MottakerDto
import no.nav.bidrag.behandling.dto.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.transport.dokument.BidragEnhet.ENHET_FARSKAP
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class ForsendelseService(
    private val bidragForsendelseConsumer: BidragForsendelseConsumer,
    private val tIlgangskontrollConsumer: BidragTIlgangskontrollConsumer,
) {
    private val ikkeOpprettVarslingForForskuddMedType =
        listOf(VedtakType.FASTSETTELSE, VedtakType.ENDRING)

    fun opprettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        val opprettRequestTemplate = OpprettForsendelseForespørsel(
            behandlingInfo = request.behandlingInfo,
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
        opprettForRoller.forEach {
            try {
                val response = bidragForsendelseConsumer.opprettForsendelse(
                    opprettRequestTemplate.copy(
                        mottaker = MottakerDto(ident = it.fødselsnummer.verdi),
                        gjelderIdent = it.fødselsnummer.verdi,
                    ),
                )
                log.info { "Opprettet forsendelse med id ${response.forsendelseId} for rolle $it" }
                SECURE_LOGGER.info("Opprettet forsendelse med id ${response.forsendelseId} for rolle $it, fnr: ${it.fødselsnummer.verdi}")
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved opprettelse av forsendelse for rolle $it. Ignorerer feilen uten å opprette forsendelse" }
            }
        }
        if (request.behandlingInfo.erVedtakFattet()) {
            slettVarselbrevUnderOpprettelse(request.saksnummer, request.behandlingInfo.soknadId)
        }
        return opprettForRoller.listeMedFødselsnummere()
    }

    fun slettVarselbrevUnderOpprettelse(saksnummer: String, soknadId: Long) {

        val forsendelser = bidragForsendelseConsumer.hentForsendelserISak(saksnummer)
        forsendelser
            .filter { it.forsendelseType == ForsendelseTypeTo.UTGÅENDE }
            .filter { it.status == ForsendelseStatusTo.UNDER_OPPRETTELSE && it.behandlingInfo?.soknadId?.isNotEmpty() == true }
            .filter { it.behandlingInfo?.soknadId == soknadId.toString() && !it.behandlingInfo.erFattet }
            .forEach {
                bidragForsendelseConsumer.slettForsendelse(it.forsendelseId)
                log.info { "Slettet forsendelse ${it.forsendelseId} for varselbrev som var under opprettelse" }
            }
    }

    private fun harTilgangTilTemaFar() = tIlgangskontrollConsumer.sjekkTilgangTema(tema = "FAR")
    private fun skalOppretteForsendelseForSoknad(behandlingInfo: BehandlingInfoDto): Boolean {
        val erFattet = behandlingInfo.erFattetBeregnet != null
        if (erFattet) return true
        return !(
                behandlingInfo.stonadType == StonadType.FORSKUDD &&
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
            if (behandlingInfoDto.erBehandlingType(EngangsbelopType.GEBYR_MOTTAKER)) {
                roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSMOTTAKER))
            } else {
                roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSPLIKTIG))
            }
            return roller
        }

        roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSMOTTAKER))

        if (!behandlingInfoDto.erBehandlingType(StonadType.FORSKUDD)) {
            roller.leggTil(behandlingRoller.hentRolle(Rolletype.BIDRAGSPLIKTIG))
        }

        if (behandlingInfoDto.erBehandlingType(StonadType.BIDRAG18AAR)) {
            behandlingRoller.hentBarn().forEach { roller.leggTil(it) }
        }

        return roller
    }
}

class OpprettForsendelseForRollerListe : MutableList<ForsendelseRolleDto> by mutableListOf() {
    fun leggTil(rolle: ForsendelseRolleDto?) {
        if (rolle == null) return
        val fødselsnummer = rolle.fødselsnummer
        if (fødselsnummer.verdi.isNotEmpty()) this.add(rolle)
    }

    fun listeMedFødselsnummere() = this.map { it.fødselsnummer.verdi }
}

fun BehandlingInfoDto.typeForsendelse() = if (this.erVedtakFattet()) "vedtak" else "varsel"
fun List<ForsendelseRolleDto>.hentRolle(rolleType: Rolletype): ForsendelseRolleDto? =
    this.find { it.type == rolleType }

fun List<ForsendelseRolleDto>.hentBarn(): List<ForsendelseRolleDto> =
    this.filter { it.type == Rolletype.BARN }
