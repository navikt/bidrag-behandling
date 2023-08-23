package no.nav.bidrag.behandling.service

import mu.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragForsendelseConsumer
import no.nav.bidrag.behandling.consumer.BidragTIlgangskontrollConsumer
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.dto.forsendelse.MottakerDto
import no.nav.bidrag.behandling.dto.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.domain.ident.PersonIdent
import no.nav.bidrag.transport.dokument.BidragEnhet.ENHET_FARSKAP
import no.nav.bidrag.transport.sak.RolleDto
import org.springframework.stereotype.Service
import java.util.*

private val log = KotlinLogging.logger {}

@Service
class ForsendelseService(
    private val bidragForsendelseConsumer: BidragForsendelseConsumer,
    private val tIlgangskontrollConsumer: BidragTIlgangskontrollConsumer,
) {
    private val ikkeOpprettVarslingForForskuddMedType = listOf(VedtakType.FASTSETTELSE, VedtakType.ENDRING)
    fun opprettForsendelse(request: InitalizeForsendelseRequest): List<String> {
        val opprettRequestTemplate = OpprettForsendelseForespørsel(
            behandlingInfo = request.behandlingInfo,
            saksnummer = request.saksnummer,
            enhet = request.enhet,
            tema = request.tema
                ?: if (request.enhet == ENHET_FARSKAP && harTilgangTilTemaFar()) "FAR" else "BID",
        )

        val opprettForRoller = opprettForRoller(request.roller, request.behandlingInfo)
        log.info { "Oppretter forsendelse for ${opprettForRoller.size} roller for behandling ${request.behandlingInfo}" }
        opprettForRoller.forEach {
            try {
                val response = bidragForsendelseConsumer.opprettForsendelse(
                    opprettRequestTemplate.copy(
                        mottaker = MottakerDto(ident = it.fødselsnummer.verdi),
                        gjelderIdent = it.fødselsnummer.verdi,
                    ),
                )
                log.info { "Opprettet forsendelse med id ${response.forsendelseId} for behandling ${request.behandlingInfo} og rolle $it" }
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved opprettelse av forsendelse for behandling ${request.behandlingInfo}. Ignorerer feilen uten å opprette forsendelse" }
            }
        }
        return opprettForRoller.listeMedFødselsnummere()
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
        roller.leggTil(behandlingRoller.hentRolle(Rolletype.BM))

        if (!behandlingInfoDto.erBehandlingType(StonadType.FORSKUDD)) {
            roller.leggTil(behandlingRoller.hentRolle(Rolletype.BP))
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

fun List<ForsendelseRolleDto>.hentRolle(rolleType: Rolletype): ForsendelseRolleDto? = this.find { it.type == rolleType }
fun List<ForsendelseRolleDto>.hentBarn(): List<ForsendelseRolleDto> = this.filter { it.type == Rolletype.BA }
