package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import java.time.LocalDate

private val SYNC_LOGGER = KotlinLogging.logger {}

class ForholdsmessigFordelingSøknadSyncService(
    private val bbmConsumer: BidragBBMConsumer,
    private val kravhaverService: ForholdsmessigFordelingKravhaverService,
) {
    fun oppdaterSøknadStatuserForAlleRoller(behandling: Behandling) {
        if (!behandling.erIForholdsmessigFordeling) return
        val alleSøknaderRelevantForBehandling =
            kravhaverService.hentÅpneSøknader(
                behandling.bidragspliktig!!.ident!!,
                behandling.behandlingstypeForFF,
                behandling.omgjøringsdetaljer,
            )

        behandling.roller.forEach { rolle ->
            val ffDetaljer = rolle.forholdsmessigFordeling ?: return@forEach
            rolle.forholdsmessigFordeling =
                ffDetaljer.copy(
                    søknader =
                        oppdaterLagredeSoknadsstatuserFraBbm(
                            ffDetaljer.søknader,
                            alleSøknaderRelevantForBehandling.filter { it.saksnummer == rolle.saksnummer },
                            rolle,
                        ),
                )
        }
    }

    fun slettDuplikatForholdsmessigFordelingSøknader(behandling: Behandling) {
        behandling.søknadsbarn.forEach { rolle ->
            val ffDetaljer = rolle.forholdsmessigFordeling ?: return@forEach
            val åpneFFSøknader =
                ffDetaljer.søknaderUnderBehandling
                    .filter { it.behandlingstype?.erForholdsmessigFordeling == true }
                    .filter { it.søknadsid != null }

            if (åpneFFSøknader.size <= 1) return@forEach

            val søknadSomSkalBeholdes =
                åpneFFSøknader.minWithOrNull(
                    compareBy(
                        { it.søknadFomDato ?: LocalDate.MAX },
                        { it.mottattDato },
                    ),
                ) ?: return@forEach

            åpneFFSøknader
                .filter { it.søknadsid != søknadSomSkalBeholdes.søknadsid }
                .forEach { duplikatSøknad ->
                    feilregistrerSøknad(duplikatSøknad, behandling)
                }
        }
    }

    fun knyttSammenManglendeSøknadsknytningerIBehandling(behandling: Behandling) {
        if (!behandling.erIForholdsmessigFordeling) return
        val alleSøknadsknytninger = bbmConsumer.finnSammenknytningerHovedsøknad(behandling.soknadsid!!)

        val alleSøknader =
            behandling.søknadsbarn
                .mapNotNull {
                    it.forholdsmessigFordeling?.søknaderUnderBehandling?.mapNotNull { søknad -> søknad.søknadsid }
                }.flatten()
                .distinct()

        alleSøknader
            .filter { søknadsid ->
                alleSøknadsknytninger.søknader.none { it.søknadsid == søknadsid }
            }.forEach {
                SYNC_LOGGER.info { "Knytter sammen søknad $it til hovedsøknad ${behandling.soknadsid} i behandling ${behandling.id}" }
                bbmConsumer.sammeknyttSøknader(behandling.soknadsid!!, it)
            }

        alleSøknadsknytninger.søknader
            .filter { it.søknadsid != behandling.soknadsid }
            .filter { søknad ->
                alleSøknader.none { it == søknad.søknadsid }
            }.forEach {
                SYNC_LOGGER.info {
                    "Slett søknadsknytning for søknad ${it.søknadsid} fra hovedsøknad ${behandling.soknadsid} i behandling ${behandling.id}"
                }
                bbmConsumer.fjernSammeknytning(it.søknadsid)
            }

        if (alleSøknadsknytninger.søknader.none { it.søknadsid == behandling.soknadsid }) {
            SYNC_LOGGER.info { "Mangler hovedsøknad i sammenknytningen. Legger til" }
            bbmConsumer.endreSammenknytningSøknad(behandling.soknadsid!!, behandling.soknadsid!!)
        }
    }

    fun feilregistrerAndreSøknader(
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        søknadSomSkalBeholdes: ForholdsmessigFordelingSøknadBarn,
        behandling: Behandling,
    ) {
        lagretSøknader
            .filter { it.søknadsid != søknadSomSkalBeholdes.søknadsid }
            .filter { it.innkreving == søknadSomSkalBeholdes.innkreving }
            .forEach { søknad ->
                feilregistrerSøknad(søknad, behandling)
            }
    }

    fun feilregistrerSøknad(
        søknad: ForholdsmessigFordelingSøknadBarn,
        behandling: Behandling,
    ): Boolean =
        try {
            val søknadsid = søknad.søknadsid ?: return false
            bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknadsid))
            bbmConsumer.fjernSammeknytning(søknadsid)
            søknad.status = Behandlingstatus.FEILREGISTRERT
            behandling.roller.forEach { rolle ->
                val søknader = rolle.forholdsmessigFordeling?.søknaderUnderBehandling?.filter { it.søknadsid == søknadsid } ?: emptyList()
                søknader.forEach {
                    it.status = Behandlingstatus.FEILREGISTRERT
                }
            }
            true
        } catch (e: Exception) {
            SYNC_LOGGER.warn(e) { "Kunne ikke feilregistrere søknad ${søknad.søknadsid} i BBM" }
            false
        }

    fun finnApneSoknaderForBarn(
        alleSøknaderRelevantForBehandling: List<HentSøknad>,
        rolle: Rolle,
    ) = alleSøknaderRelevantForBehandling
        .filter { it.behandlingstema.tilStønadstype() == rolle.stønadstype }
        .filter { søknad ->
            søknad.partISøknadListe
                .filter { it.rolletype == Rolletype.BARN }
                .filter { it.behandlingstatus?.erFeilregistrert != true }
                .any { it.personident == rolle.ident }
        }

    fun oppdaterLagredeSoknadsstatuserFraBbm(
        lagretSøknader: MutableSet<ForholdsmessigFordelingSøknadBarn>,
        alleSøknaderRelevantForBehandling: List<HentSøknad>,
        rolle: Rolle,
    ): MutableSet<ForholdsmessigFordelingSøknadBarn> {
        val eksisterendeSøknaderOppdatert =
            lagretSøknader
                .mapNotNull { lagretSøknad ->
                    val søknad =
                        alleSøknaderRelevantForBehandling.find { it.søknadsid == lagretSøknad.søknadsid }?.takeIf {
                            (!rolle.erBarn || rolle.stønadstype == null || it.behandlingstema.tilStønadstype() == rolle.stønadstype)
                        }
                    var oppslagMotBbmFeilet = false

                    val partISøknad =
                        if (søknad == null) {
                            try {
                                val søknadFraBbm =
                                    bbmConsumer
                                        .hentSøknad(lagretSøknad.søknadsid!!)
                                        ?.søknad
                                        ?.takeIf {
                                            (
                                                !rolle.erBarn || rolle.stønadstype == null ||
                                                    it.behandlingstema.tilStønadstype() == rolle.stønadstype
                                            )
                                        }
                                søknadFraBbm?.partISøknadListe?.find { it.personident == rolle.ident }
                            } catch (e: Exception) {
                                oppslagMotBbmFeilet = true
                                SYNC_LOGGER.warn(e) { "Kunne ikke hente søknad ${lagretSøknad.søknadsid} fra BBM" }
                                null
                            }
                        } else {
                            søknad.partISøknadListe.find { it.personident == rolle.ident }
                        }

                    if (partISøknad == null && !oppslagMotBbmFeilet) {
                        return@mapNotNull null
                    }

                    if (partISøknad != null) {
                        lagretSøknad.status = partISøknad.behandlingstatus ?: lagretSøknad.status
                    } else if (!oppslagMotBbmFeilet) {
                        lagretSøknad.status = Behandlingstatus.FEILREGISTRERT
                    }
                    lagretSøknad.søknadFomDato = søknad?.søknadFomDato ?: lagretSøknad.søknadFomDato
                    lagretSøknad.behandlingstema = søknad?.behandlingstema ?: lagretSøknad.behandlingstema
                    lagretSøknad.søktAvType = søknad?.søktAvType ?: lagretSøknad.søktAvType
                    lagretSøknad.behandlingstype = søknad?.behandlingstype ?: lagretSøknad.behandlingstype
                    lagretSøknad.mottattDato = søknad?.søknadMottattDato ?: lagretSøknad.mottattDato
                    lagretSøknad.omgjørSøknadsid = søknad?.refSøknadsid ?: lagretSøknad.omgjørSøknadsid
                    lagretSøknad.omgjørVedtaksid = søknad?.refVedtaksid ?: lagretSøknad.omgjørVedtaksid
                    lagretSøknad.innkreving = søknad?.innkreving ?: lagretSøknad.innkreving
                    lagretSøknad
                }.toMutableSet()

        val eksisterendeSøknader = eksisterendeSøknaderOppdatert.map { it.søknadsid }
        val nyeSøknader =
            alleSøknaderRelevantForBehandling
                .asSequence()
                .filter { !eksisterendeSøknader.contains(it.søknadsid) }
                .filter { !rolle.erBarn || it.behandlingstema.tilStønadstype() == rolle.stønadstype }
                .filter { it.parterForRolle(rolle.rolletype).any { it.personident == rolle.ident } }
                .map {
                    val partBarn = it.parterForRolle(rolle.rolletype).find { it.personident == rolle.ident }
                    val status =
                        if (rolle.rolletype != Rolletype.BARN) {
                            val statuser = it.barn.map { barn -> barn.behandlingstatus }.distinct()
                            when {
                                statuser.size == 1 -> statuser.first()

                                it.barn.any { barn ->
                                    barn.behandlingstatus == Behandlingstatus.VEDTAK_FATTET
                                } -> Behandlingstatus.VEDTAK_FATTET

                                else -> Behandlingstatus.UNDER_BEHANDLING
                            }
                        } else {
                            partBarn?.behandlingstatus ?: Behandlingstatus.UNDER_BEHANDLING
                        }
                    it.tilForholdsmessigFordelingSøknad().copy(
                        status = status,
                    )
                }.toMutableSet()

        return (eksisterendeSøknaderOppdatert + nyeSøknader).toMutableSet()
    }

    fun endreHovedsøknadIFFEtterHovedsøknadBleSlettet(
        behandling: Behandling,
        søknadSomBleSlettet: Long,
    ) {
        if (!behandling.erIForholdsmessigFordeling ||
            behandling.forholdsmessigFordeling == null || !behandling.forholdsmessigFordeling!!.erHovedbehandling
        ) {
            return
        }

        if (behandling.soknadsid == søknadSomBleSlettet) {
            val søknadsbarnIkkeRevurdering =
                behandling.søknadsbarn
                    .filter { !it.erRevurderingsbarn && it.forholdsmessigFordeling?.søknadsid != null }
            val søknadsidMedFlestBarn =
                søknadsbarnIkkeRevurdering
                    .groupBy { it.forholdsmessigFordeling!!.søknadsid!! }
                    .maxByOrNull { (_, barn) -> barn.size }
                    ?.key
            behandling.soknadsid = søknadsidMedFlestBarn ?: behandling.soknadsid
            val hovedsøknadsid = behandling.soknadsid!!
            SYNC_LOGGER.info { "Oppdaterer hovedsøknad i behandling ${behandling.id} fra $søknadSomBleSlettet til $hovedsøknadsid" }
            bbmConsumer.fjernSammeknytningHovedsøknad(søknadSomBleSlettet, hovedsøknadsid)
        }
    }
}
