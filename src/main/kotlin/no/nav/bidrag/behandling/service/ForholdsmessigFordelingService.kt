package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingBarnDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingÅpenBehandlingDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.SjekkForholdmessigFordelingResponse
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
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

        opprettSamværForBarn(behandling)
        behandlingRepository.save(behandling)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
    }

    private fun opprettSamværForBarn(behandling: Behandling) {
        behandling.søknadsbarn.forEach {
            if (behandling.samvær.none { s -> s.rolle.ident == it.ident }) {
                behandling.samvær.add(
                    Samvær(
                        rolle = it,
                        behandling = behandling,
                    ),
                )
            }
        }
        behandling.søknadsbarn.forEach {
            if (behandling.underholdskostnader.none { s -> s.rolle?.ident == it.ident }) {
                behandling.underholdskostnader.add(
                    Underholdskostnad(
                        rolle = it,
                        behandling = behandling,
                    ),
                )
            }
        }
    }

    @Transactional
    fun skalLeggeTilBarnFraAndreSøknaderEllerBehandlinger(behandlingId: Long): Boolean {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        if (behandling.forholdsmessigFordeling == null || !behandling.forholdsmessigFordeling!!.erHovedbehandling) return false

        return harLøpendeBidragForBarnIkkeIBehandling(behandling)
    }

    private fun harLøpendeBidragForBarnIkkeIBehandling(behandling: Behandling): Boolean {
        val bidraggsakerBP = hentSisteLøpendeStønader(Personident(behandling.bidragspliktig!!.ident!!))
        return bidraggsakerBP.any { lb ->
            val sak = sakConsumer.hentSak(lb.sak.verdi)
            val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
            behandling.roller.none { it.ident == lb.kravhaver.verdi } || behandling.roller.none { it.ident == bmFødselsnummer }
        }
    }

    @Transactional
    fun sjekkSkalOppretteForholdsmessigFordeling(behandlingId: Long): SjekkForholdmessigFordelingResponse {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()

        if (behandling.forholdsmessigFordeling != null) {
            return SjekkForholdmessigFordelingResponse(false)
        }
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!

        val bidraggsakerBP = hentSisteLøpendeStønader(Personident(bidragspliktigFnr))
        val bpHarLøpendeBidragForAndreBarn = harLøpendeBidragForBarnIkkeIBehandling(behandling)
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
        return SjekkForholdmessigFordelingResponse(
            bpHarLøpendeBidragForAndreBarn,
            false,
            bidraggsakerBP
                .map { lb ->
                    val sak = sakConsumer.hentSak(lb.sak.verdi)
                    val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
                    val åpenBehandling = åpneBehandlinger.find { it.saksnummer == sak.saksnummer.verdi }
                    // TODO: Sjekk for søknader fra bidrag-bbm
                    val barnFødselsnummer = lb.kravhaver.verdi
                    ForholdsmessigFordelingBarnDto(
                        ident = lb.kravhaver.verdi,
                        navn = hentPersonVisningsnavn(barnFødselsnummer) ?: "Ukjent",
                        fødselsdato = hentPersonFødselsdato(barnFødselsnummer),
                        saksnr = lb.sak.verdi,
                        sammeSakSomBehandling = behandling.saksnummer == lb.sak.verdi,
                        åpenBehandling =
                            if (åpenBehandling != null) {
                                ForholdsmessigFordelingÅpenBehandlingDto(
                                    søktFraDato = åpenBehandling.søktFomDato,
                                    mottattDato = åpenBehandling.mottattdato,
                                    stønadstype = åpenBehandling.stonadstype!!,
                                    behandlerEnhet = åpenBehandling.behandlerEnhet,
                                )
                            } else {
                                null
                            },
                        bidragsmottaker =
                            RolleDto(
                                id = -1,
                                ident = bmFødselsnummer,
                                rolletype = Rolletype.BIDRAGSMOTTAKER,
                                navn = hentPersonVisningsnavn(bmFødselsnummer) ?: "Ukjent",
                                fødselsdato = hentPersonFødselsdato(bmFødselsnummer),
                                delAvOpprinneligBehandling = false,
                            ),
                    )
                },
        )
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
        val åpneBehandlinger = behandlingRepository.finnÅpneBidragsbehandlingerForBp(bidragspliktigFnr, behandling.id!!)
        åpneBehandlinger.forEach {
            it.forholdsmessigFordeling =
                ForholdsmessigFordeling(
                    behandlesAvBehandling = behandling.id,
                )
            it.bidragsmottaker?.let { rolle ->
                if (behandling.roller.none { it.ident == rolle.ident }) {
                    behandling.roller.add(
                        rolle.kopierRolle(behandling),
                    )
                }
            }
            it.søknadsbarn.forEach { rolle ->
                if (behandling.søknadsbarn.none { barn -> barn.ident == rolle.ident }) {
                    behandling.roller.add(
                        rolle.kopierRolle(behandling),
                    )
                }
            }
            it.grunnlag
                .filter { it.rolle.ident != bidragspliktigFnr && behandling.roller.any { r -> r.ident == it.rolle.ident } }
                .forEach {
                    behandling.grunnlag.add(
                        it.kopierGrunnlag(behandling),
                    )
                }

            behandlingRepository.save(it)
        }
    }

    fun opprettRollerForSak(
        behandling: Behandling,
        løpendeBidragssak: LøpendeBidragssak,
    ) {
        val sak = sakConsumer.hentSak(løpendeBidragssak.sak.verdi)

        if (behandling.roller.none { it.ident == løpendeBidragssak.kravhaver.verdi }) {
            val rolleBarn = løpendeBidragssak.opprettRolle(behandling, Rolletype.BARN, løpendeBidragssak.kravhaver.verdi, sak.eierfogd)
            behandling.roller.add(rolleBarn)
        }

        val bmFødselsnummer = sak.bidragsmottaker?.fødselsnummer?.verdi
        if (bmFødselsnummer != null && behandling.roller.none { it.ident == bmFødselsnummer }) {
            val rolleBM = løpendeBidragssak.opprettRolle(behandling, Rolletype.BIDRAGSMOTTAKER, bmFødselsnummer, sak.eierfogd)
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
        eierfogd: Enhetsnummer,
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
                    sakBehandlerEnhet = eierfogd,
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
            manueltOverstyrtGebyr = manueltOverstyrtGebyr,
            harGebyrsøknad = harGebyrsøknad,
            opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
            beregnTil = beregnTil,
            fødselsdato = fødselsdato,
            forholdsmessigFordeling =
                ForholdsmessigFordelingRolle(
                    delAvOpprinneligBehandling = true,
                    overførtFraBehandling = behandling.id,
                    tilhørerSak = behandling.saksnummer,
                    sakBehandlerEnhet = Enhetsnummer(behandling.behandlerEnhet),
                ),
        )
}
