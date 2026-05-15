package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.transformers.harLøpendeBidragFørOpphørEllerLøpende
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import java.time.LocalDate

private val BARN_STATUS_LOGGER = KotlinLogging.logger {}

private typealias LeggTilEllerSlettBarnFraFF = (OppdaterBarnFraFFRequest) -> Unit
private typealias FeilregistrerBarnFraFF = (Rolle) -> Unit
private typealias SlettRevurderingsbarn = (Behandling, Rolle) -> Unit
private typealias OpprettEllerOppdaterFF = (Long, Pair<String, Stønadstype?>?) -> Unit
private typealias LeggTilEllerOpprettRevurderingssøknad = (
    behandling: Behandling,
    barnIdent: String,
    stønadstype: Stønadstype?,
    saksnummer: String,
    søktFomDato: LocalDate,
    medInnkreving: Boolean,
) -> ForholdsmessigFordelingSøknadBarn

class ForholdsmessigFordelingBarnStatusService(
    private val søknadSyncService: ForholdsmessigFordelingSøknadSyncService,
) {
    fun sjekkOgOppdaterStatusPåRevurderingOgSøknadsbarn(
        åpneSøknaderIkkeFF: List<HentSøknad>,
        rolle: Rolle,
        behandling: Behandling,
        åpneSøknaderFF: List<HentSøknad>,
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        leggTilEllerSlettBarnFraBehandlingSomErIFF: LeggTilEllerSlettBarnFraFF,
        feilregistrerBarnFraFFSøknad: FeilregistrerBarnFraFF,
        slettRevurderingsbarn: SlettRevurderingsbarn,
        opprettEllerOppdaterForholdsmessigFordeling: OpprettEllerOppdaterFF,
        leggTilEllerOpprettSøknadForRevurderingsbarn: LeggTilEllerOpprettRevurderingssøknad,
    ) {
        if (behandling.erKlageEllerOmgjøring) {
            if (rolle.erRevurderingsbarn && åpneSøknaderFF.isEmpty()) {
                BARN_STATUS_LOGGER.info {
                    "Barn ${rolle.ident} i ${behandling.id} er markert som revurderingsbarn men har ingen åpne FF søknader." +
                        "Oppretter eller legger til i eksisterende FF søknad"
                }
                håndterBarnSomSkalVæreRevurderingsbarn(
                    behandling = behandling,
                    rolle = rolle,
                    lagretSøknader = lagretSøknader,
                    opprettEllerOppdaterForholdsmessigFordeling = opprettEllerOppdaterForholdsmessigFordeling,
                    leggTilEllerOpprettSøknadForRevurderingsbarn = leggTilEllerOpprettSøknadForRevurderingsbarn,
                )
            }
            return
        }

        if (åpneSøknaderIkkeFF.isNotEmpty() && rolle.erRevurderingsbarn) {
            håndterBarnSomSkalVæreSøknadsbarn(
                behandling = behandling,
                rolle = rolle,
                førsteSøknad = åpneSøknaderIkkeFF.first(),
                leggTilEllerSlettBarnFraBehandlingSomErIFF = leggTilEllerSlettBarnFraBehandlingSomErIFF,
            )
        } else if (åpneSøknaderFF.isNotEmpty() && !rolle.erRevurderingsbarn) {
            feilregistrerBarnFraFFSøknad(rolle)
        } else if (!rolle.erRevurderingsbarn && åpneSøknaderIkkeFF.isEmpty()) {
            BARN_STATUS_LOGGER.info {
                "Barn ${rolle.ident} i ${behandling.id} er ikke markert som revurderingsbarn men har ingen åpne søknader." +
                    "Oppretter eller legger til i eksisterende FF søknad og endrer barnet til revurderingsbarn"
            }
            håndterBarnSomSkalVæreRevurderingsbarn(
                behandling = behandling,
                rolle = rolle,
                lagretSøknader = lagretSøknader,
                opprettEllerOppdaterForholdsmessigFordeling = opprettEllerOppdaterForholdsmessigFordeling,
                leggTilEllerOpprettSøknadForRevurderingsbarn = leggTilEllerOpprettSøknadForRevurderingsbarn,
            )
        } else if (
            rolle.erRevurderingsbarn &&
            åpneSøknaderFF.isEmpty() &&
            åpneSøknaderIkkeFF.isEmpty() &&
            rolle.harLøpendeBidragFørOpphørEllerLøpende()
        ) {
            BARN_STATUS_LOGGER.info {
                "Barn ${rolle.ident} i ${behandling.id} er markert som revurderingsbarn men har ingen åpne FF søknader." +
                    "Oppretter eller legger til i eksisterende FF søknad"
            }
            håndterBarnSomSkalVæreRevurderingsbarn(
                behandling = behandling,
                rolle = rolle,
                lagretSøknader = lagretSøknader,
                opprettEllerOppdaterForholdsmessigFordeling = opprettEllerOppdaterForholdsmessigFordeling,
                leggTilEllerOpprettSøknadForRevurderingsbarn = leggTilEllerOpprettSøknadForRevurderingsbarn,
            )
        } else if (rolle.erRevurderingsbarn && !rolle.harLøpendeBidragFørOpphørEllerLøpende()) {
            slettRevurderingsbarn(behandling, rolle)
        }
    }

    private fun håndterBarnSomSkalVæreSøknadsbarn(
        behandling: Behandling,
        rolle: Rolle,
        førsteSøknad: HentSøknad,
        leggTilEllerSlettBarnFraBehandlingSomErIFF: LeggTilEllerSlettBarnFraFF,
    ) {
        leggTilEllerSlettBarnFraBehandlingSomErIFF(
            OppdaterBarnFraFFRequest(
                rollerSomSkalLeggesTilDto = listOf(rolle.tilOpprettRolleDto()),
                behandling = behandling,
                medInnkreving = førsteSøknad.innkreving,
                søktFraDato = førsteSøknad.søknadFomDato,
                stønadstype = førsteSøknad.behandlingstema.tilStønadstype(),
                behandlerenhet = førsteSøknad.behandlerenhet!!,
                søknadsid = førsteSøknad.søknadsid,
                saksnummer = førsteSøknad.saksnummer,
            ),
        )
    }

    private fun håndterBarnSomSkalVæreRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        opprettEllerOppdaterForholdsmessigFordeling: OpprettEllerOppdaterFF,
        leggTilEllerOpprettSøknadForRevurderingsbarn: LeggTilEllerOpprettRevurderingssøknad,
    ) {
        val sisteFfSøknad = finnSisteFFSøknad(lagretSøknader)
        val aktivFfSøknad = finnAktivFFSøknad(lagretSøknader)

        val søknadSomSkalBeholdes =
            when {
                aktivFfSøknad != null -> {
                    rolle.forholdsmessigFordeling?.erRevurdering = true
                    aktivFfSøknad
                }

                sisteFfSøknad == null -> {
                    opprettEllerOppdaterForholdsmessigFordeling(
                        behandling.id!!,
                        Pair(rolle.ident!!, rolle.stønadstype),
                    )
                    return
                }

                else -> {
                    opprettEllerGjenopprettFfSøknadForRevurderingsbarn(
                        behandling = behandling,
                        rolle = rolle,
                        lagretSøknader = lagretSøknader,
                        referanseSøknad = sisteFfSøknad,
                        leggTilEllerOpprettSøknadForRevurderingsbarn = leggTilEllerOpprettSøknadForRevurderingsbarn,
                    )
                }
            }

        søknadSyncService.feilregistrerAndreSøknader(lagretSøknader, søknadSomSkalBeholdes, behandling)
    }

    private fun opprettEllerGjenopprettFfSøknadForRevurderingsbarn(
        behandling: Behandling,
        rolle: Rolle,
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        referanseSøknad: ForholdsmessigFordelingSøknadBarn,
        leggTilEllerOpprettSøknadForRevurderingsbarn: LeggTilEllerOpprettRevurderingssøknad,
    ): ForholdsmessigFordelingSøknadBarn {
        val nyEllerEksisterendeSøknad =
            leggTilEllerOpprettSøknadForRevurderingsbarn(
                behandling,
                rolle.ident!!,
                rolle.stønadstype,
                referanseSøknad.saksnummer ?: rolle.forholdsmessigFordeling!!.tilhørerSak,
                referanseSøknad.søknadFomDato ?: LocalDate.now().plusMonths(1).withDayOfMonth(1),
                rolle.innkrevingstype == Innkrevingstype.MED_INNKREVING,
            )

        val eksisterendeSøknad = lagretSøknader.find { it.søknadsid == nyEllerEksisterendeSøknad.søknadsid }
        val søknadSomSkalBeholdes =
            if (eksisterendeSøknad != null) {
                eksisterendeSøknad.status = Behandlingstatus.UNDER_BEHANDLING
                eksisterendeSøknad
            } else {
                lagretSøknader.add(nyEllerEksisterendeSøknad)
                nyEllerEksisterendeSøknad
            }
        rolle.forholdsmessigFordeling?.erRevurdering = true
        return søknadSomSkalBeholdes
    }

    private fun finnAktivFFSøknad(lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>) =
        lagretSøknader
            .filter { it.behandlingstype?.erForholdsmessigFordeling == true }
            .filter { it.status == Behandlingstatus.UNDER_BEHANDLING || it.status == null }
            .maxByOrNull { it.søknadsid ?: Long.MIN_VALUE }

    private fun finnSisteFFSøknad(lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>) =
        lagretSøknader
            .filter { it.behandlingstype?.erForholdsmessigFordeling == true }
            .maxByOrNull { it.søknadsid ?: Long.MIN_VALUE }
}
