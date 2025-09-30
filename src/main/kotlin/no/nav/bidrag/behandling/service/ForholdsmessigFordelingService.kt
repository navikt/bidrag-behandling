package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssak
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ForholdsmessigFordelingService(
    private val sakConsumer: BidragSakConsumer,
    private val behandlingRepository: BehandlingRepository,
    private val beløpshistorikkConsumer: BidragBeløpshistorikkConsumer,
    private val grunnlagService: GrunnlagService,
    private val beregningService: BeregningService,
    private val bbmConsumer: BidragBBMConsumer,
) {
    @Transactional
    fun opprettForholdsmessigFordeling(behandlingId: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        overførÅpneBehandlingTilHovedbehandling(behandling)
        val bidraggsakerBP = hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!))
        bidraggsakerBP.forEach {
            opprettRollerForSak(behandling, it)
        }
        behandling.forholdsmessigFordeling =
            ForholdsmessigFordeling(
                erHovedbehandling = true,
            )
        behandlingRepository.save(behandling)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
    }

    fun overførÅpneBisysSøknaderTilBehandling(behandling: Behandling) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        bbmConsumer.hentÅpneSøknaderForBp(bidragspliktigFnr)
        // TODO: Kall BBM for å hente alle åpne søknader
        // TODO: Opprett roller i behandling for barn og bidragsmottaker
        // TODO: Lagre behandlingid
    }

    @Transactional
    fun overførÅpneBehandlingTilHovedbehandling(behandling: Behandling) {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr)
        åpneBehandlinger.forEach {
            it.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    behandlesAvBehandling = behandling.id,
                )
            it.bidragsmottaker?.let { rolle ->
                behandling.roller.add(
                    rolle.kopierRolle(behandling),
                )
            }
            it.søknadsbarn.forEach { rolle ->
                if (behandling.søknadsbarn.none { barn -> barn.ident == rolle.ident }) {
                    behandling.roller.add(
                        rolle.kopierRolle(behandling),
                    )
                }
            }
            it.grunnlag
                .filter { it.rolle.ident != bidragspliktigFnr }
                .forEach {
                    behandling.grunnlag.add(
                        it.kopierGrunnlag(behandling),
                    )
                }
        }
    }

    fun opprettRollerForSak(
        behandling: Behandling,
        løpendeBidragssak: LøpendeBidragssak,
    ) {
        val sak = sakConsumer.hentSak(løpendeBidragssak.sak.verdi)

        if (behandling.roller.none { it.ident == løpendeBidragssak.kravhaver.verdi }) {
            val rolleBarn = løpendeBidragssak.opprettRolle(behandling, Rolletype.BARN, løpendeBidragssak.kravhaver.verdi)
            behandling.roller.add(rolleBarn)
        }

        val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
        if (bmFødselsnummer != null && behandling.roller.none { it.ident == bmFødselsnummer }) {
            val rolleBM = løpendeBidragssak.opprettRolle(behandling, Rolletype.BIDRAGSMOTTAKER, bmFødselsnummer)
            behandling.roller.add(rolleBM)
        }
        // TODO: Kall BBM for å opprette søknad
    }

    private fun hentSisteLøpendeStønader(bpIdent: Personident): List<LøpendeBidragssak> =
        beløpshistorikkConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bpIdent)).bidragssakerListe

    private fun LøpendeBidragssak.opprettRolle(
        behandling: Behandling,
        rolletype: Rolletype,
        fødselsnummer: String,
    ): Rolle =
        Rolle(
            behandling = behandling,
            rolletype = rolletype,
            ident = fødselsnummer,
            fødselsdato = hentPersonFødselsdato(fødselsnummer)!!,
            forholdsmessigFordeling =
                ForholdsmessigFordelingRolle(
                    delAvOpprinneligBehandling = false,
                    tilhørerSak = sak.verdi,
                ),
        )

    private fun Grunnlag.kopierGrunnlag(hovedbehandling: Behandling): Grunnlag =
        Grunnlag(
            behandling = hovedbehandling,
            rolle = hovedbehandling.roller.find { it.ident == rolle.ident }!!,
            erBearbeidet = erBearbeidet,
            grunnlagFraVedtakSomSkalOmgjøres = grunnlagFraVedtakSomSkalOmgjøres,
            type = type,
            data = data,
            gjelder = gjelder,
            aktiv = aktiv,
            innhentet = innhentet,
        )

    private fun Rolle.kopierRolle(hovedbehandling: Behandling) =
        Rolle(
            behandling = hovedbehandling,
            rolletype = rolletype,
            ident = ident,
            årsak = årsak,
            avslag = avslag,
            virkningstidspunkt = virkningstidspunkt,
            grunnlagFraVedtakListe = grunnlagFraVedtakListe,
            opphørsdato = opphørsdato,
            opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
            beregnTil = beregnTil,
            fødselsdato = fødselsdato,
            forholdsmessigFordeling =
                ForholdsmessigFordelingRolle(
                    delAvOpprinneligBehandling = true,
                    overførtFraBehandling = behandling.id,
                    tilhørerSak = behandling.saksnummer,
                ),
        )
}
