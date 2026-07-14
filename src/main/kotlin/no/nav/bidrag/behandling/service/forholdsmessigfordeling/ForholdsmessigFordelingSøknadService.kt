package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.tilBehandlingstema
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.Barn
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.FeilregistrerSøknadsBarnRequest
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.toYearMonth
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

/**
 * Håndterer søknadslivssyklus for forholdsmessig fordeling:
 * - Opprettelse av FF-/revurderingssøknader i BBM
 * - Synkronisering av søknadsstatus mot BBM
 * - Sammenknytning og feilregistrering av søknader
 */
class ForholdsmessigFordelingSøknadService(
    private val bbmConsumer: BidragBBMConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val forsendelseService: ForsendelseService,
    private val kravhaverService: ForholdsmessigFordelingKravhaverService,
    private val virkningstidspunktService: VirkningstidspunktService,
) {
    // ═══════════════════════════════════════════════════════════════════
    // region Opprettelse av søknader
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Oppretter roller og revurderingssøknader for en gitt sak.
     * Splitter barn i med/uten innkreving og oppretter separate søknader.
     */
    fun opprettRollerOgRevurderingssøknadForSak(
        behandling: Behandling,
        saksnummer: String,
        løpendeBidragssak: List<SakKravhaver>,
        behandlerEnhet: String,
        stønadstype: Stønadstype? = null,
        søktFomDato: LocalDate,
        erOppdateringAvBehandlingSomErIFF: Boolean,
        opprettForsendelse: Boolean = true,
    ) {
        val sak = sakConsumer.hentSak(saksnummer)
        val bmFødselsnummer = hentNyesteIdent(sak.bidragsmottaker?.fødselsnummer?.verdi)?.verdi

        val barnUtenInnkreving = løpendeBidragssak.filter { !it.løperBidragEtterDato(behandling.finnBeregnTilDato().toYearMonth()) }
        val barnMedInnkreving = løpendeBidragssak.filter { it.løperBidragEtterDato(behandling.finnBeregnTilDato().toYearMonth()) }
        val ffDetaljerBarn =
            ForholdsmessigFordelingSøknadBarn(
                søknadsid = 0, // Settes senere når søknad opprettes
                mottattDato = LocalDate.now(),
                søknadFomDato = søktFomDato,
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstema = stønadstype?.tilBehandlingstema() ?: behandling.behandlingstema,
                behandlingstype = behandling.behandlingstypeForFF,
                enhet = behandlerEnhet,
            )
        val søknadsidUtenInnkreving =
            opprettEllerOppdaterRevurderingssøknadForBarn(
                behandling = behandling,
                saksnummer = saksnummer,
                barn = barnUtenInnkreving,
                behandlerEnhet = behandlerEnhet,
                stønadstype = stønadstype,
                søktFomDato = søktFomDato,
                medInnkreving = false,
                erOppdateringAvBehandlingSomErIFF = erOppdateringAvBehandlingSomErIFF,
                bmFødselsnummer = bmFødselsnummer,
                ffDetaljerBarn = ffDetaljerBarn,
            )

        val søknadsidInnkreving =
            opprettEllerOppdaterRevurderingssøknadForBarn(
                behandling = behandling,
                saksnummer = saksnummer,
                barn = barnMedInnkreving,
                behandlerEnhet = behandlerEnhet,
                stønadstype = stønadstype,
                søktFomDato = søktFomDato,
                medInnkreving = true,
                erOppdateringAvBehandlingSomErIFF = erOppdateringAvBehandlingSomErIFF,
                bmFødselsnummer = bmFødselsnummer,
                ffDetaljerBarn = ffDetaljerBarn,
            )

        val alleSøknader =
            setOfNotNull(
                søknadsidInnkreving?.let {
                    ffDetaljerBarn.copy(søknadsid = it)
                },
                søknadsidUtenInnkreving?.let {
                    ffDetaljerBarn.copy(søknadsid = it)
                },
            ).toMutableSet()

        val ffDetaljer =
            ForholdsmessigFordelingRolle(
                delAvOpprinneligBehandling = false,
                erRevurdering = true,
                tilhørerSak = saksnummer,
                behandlerenhet = sak.eierfogd.verdi,
                bidragsmottaker = bmFødselsnummer,
                søknader = alleSøknader,
            )

        if (bmFødselsnummer != null && behandling.roller.none { it.ident == bmFødselsnummer }) {
            opprettEllerOppdaterRolle(
                behandling,
                Rolletype.BIDRAGSMOTTAKER,
                bmFødselsnummer,
                ffDetaljer = ffDetaljer,
            )
        } else {
            behandling.roller.find { hentNyesteIdent(it.ident)?.verdi == bmFødselsnummer }?.let {
                if (it.forholdsmessigFordeling == null) {
                    it.forholdsmessigFordeling = behandling.tilFFDetaljerBM()
                }
                it.forholdsmessigFordeling!!.søknader.addAll(alleSøknader.toMutableSet())
            }
        }

        behandling.bidragspliktig?.let {
            if (it.forholdsmessigFordeling == null) {
                it.forholdsmessigFordeling = behandling.tilFFDetaljerBP()
            }
            it.forholdsmessigFordeling!!.søknader.addAll(alleSøknader.toMutableSet())
        }
    }

    /**
     * Oppretter eller oppdaterer revurderingssøknad for barn i en gitt sak.
     * @return søknadsid for den opprettede/oppdaterte søknaden, eller null hvis barn-listen er tom
     */
    private fun opprettEllerOppdaterRevurderingssøknadForBarn(
        behandling: Behandling,
        saksnummer: String,
        barn: List<SakKravhaver>,
        behandlerEnhet: String,
        stønadstype: Stønadstype?,
        søktFomDato: LocalDate,
        medInnkreving: Boolean,
        erOppdateringAvBehandlingSomErIFF: Boolean,
        bmFødselsnummer: String?,
        ffDetaljerBarn: ForholdsmessigFordelingSøknadBarn,
        opprettForsendelse: Boolean = true,
    ): Long? {
        if (barn.isEmpty()) {
            return null
        }
        val søknad =
            if (!erOppdateringAvBehandlingSomErIFF) {
                opprettSøknad(
                    behandling.bidragspliktig!!.ident!!,
                    barn,
                    saksnummer,
                    behandling,
                    behandlerEnhet,
                    stønadstype,
                    søktFomDato,
                    medInnkreving,
                    bmFødselsnummer!!,
                    opprettForsendelse,
                )
            } else {
                val søknader =
                    barn.map {
                        leggTilEllerOpprettSøknadForRevurderingsbarn(
                            barnIdent = it.kravhaver,
                            saksnummer = saksnummer,
                            behandling = behandling,
                            stønadstype = stønadstype,
                            søktFomDato = søktFomDato,
                            medInnkreving = medInnkreving,
                        )
                    }
                // Antar at alle barn havner i samme søknad
                søknader.first()
            }

        val ffDetaljer =
            ForholdsmessigFordelingRolle(
                delAvOpprinneligBehandling = false,
                erRevurdering = true,
                tilhørerSak = saksnummer,
                behandlerenhet = behandlerEnhet,
                bidragsmottaker = bmFødselsnummer,
                revurderingsdatoVedOpprettelseAvFF = søktFomDato,
                søknader = mutableSetOf(),
            )
        if (opprettForsendelse) {
            opprettForsendelseForNySøknad(
                saksnummer = saksnummer,
                behandling = behandling,
                bmFødselsnummer = bmFødselsnummer!!,
                søknad = søknad,
//                barn = barn,
            )
        }
        barn.forEach { barnDetaljer ->
            val søknader =
                setOfNotNull(ffDetaljerBarn.copy(søknadsid = søknad.søknadsid))
            val rolle =
                opprettEllerOppdaterRolle(
                    behandling,
                    Rolletype.BARN,
                    barnDetaljer.kravhaver,
                    stønadstype = stønadstype ?: Stønadstype.BIDRAG,
                    innkrevesFraDato = if (medInnkreving) barnDetaljer.løperBidragFra else null,
                    medInnkreving = medInnkreving,
                    opphørsdato = barnDetaljer.løperBidragTil ?: barnDetaljer.opphørsdato,
                    ffDetaljer =
                        ffDetaljer.copy(
                            løperBidragFra = barnDetaljer.løperBidragFra,
                            løperBidragTil = barnDetaljer.løperBidragTil,
                            søknader = søknader.toMutableSet(),
                        ),
                )
            rolle.innkrevingstype = if (medInnkreving) Innkrevingstype.MED_INNKREVING else Innkrevingstype.UTEN_INNKREVING
            if (barnDetaljer.privatAvtale != null) {
                barnDetaljer.privatAvtale.rolle = rolle
                barnDetaljer.privatAvtale.person = null
            }
            // Hvis det var en eksisterende rolle
            if (rolle.id != null) {
                virkningstidspunktService.oppdaterVirkningstidspunkt(
                    rolle.id,
                    søktFomDato.withDayOfMonth(1),
                    behandling,
                    true,
                    rekalkulerOpplysningerVedEndring = false,
                )
            }
        }

        return søknad.søknadsid
    }

    /**
     * Oppretter ny søknad i BBM, eller legger til barn i eksisterende åpen FF-søknad.
     */
    fun opprettSøknad(
        bidragspliktigFnr: String,
        barnUtenSøknader: List<SakKravhaver>,
        saksnummer: String,
        behandling: Behandling,
        behandlerEnhet: String,
        stønadstype: Stønadstype?,
        søktFomDato: LocalDate,
        medInnkreving: Boolean,
        bmFødselsnummer: String,
        opprettForsendelse: Boolean = true,
    ): ForholdsmessigFordelingSøknadBarn {
        val opprettSøknader =
            barnUtenSøknader.map {
                Barn(
                    personident = it.kravhaver,
                )
            }
        val eksisterendeSøknad =
            kravhaverService.hentÅpenSøknadFFForBP(
                bidragspliktigFnr = bidragspliktigFnr,
                behandlingstype = behandling.behandlingstypeForFF,
                medInnkreving = medInnkreving,
                saksnummer = saksnummer,
                søktFomDato = søktFomDato,
                stønadstype = stønadstype,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )
        if (eksisterendeSøknad != null) {
            val søknadBarnIdenter = eksisterendeSøknad.barn.map { eksisterendeSøknad.tilIdentStønadstypeNøkkel(it.personident!!) }
            barnUtenSøknader
                .filter { !søknadBarnIdenter.contains(it.distinctKey) }
                .forEach {
                    bbmConsumer.leggTilBarnISøknad(
                        LeggTilBarnIFFSøknadRequest(
                            personidentBarn = it.kravhaver,
                            søknadsid = eksisterendeSøknad.søknadsid,
                        ),
                    )
                }
            if (eksisterendeSøknad.behandlingsid != behandling.id) {
                bbmConsumer.lagreBehandlingsid(
                    OppdaterBehandlingsidRequest(eksisterendeSøknad.søknadsid, eksisterendeSøknad.behandlingsid, behandling.id!!),
                )
            }
            return eksisterendeSøknad.tilForholdsmessigFordelingSøknad()
        }
        val response =
            bbmConsumer.opprettSøknader(
                OpprettSøknadRequest(
                    saksnummer = saksnummer,
                    behandlingsid = behandling.id,
                    behandlerenhet = behandlerEnhet,
                    behandlingstema = stønadstype?.tilBehandlingstema() ?: Behandlingstema.BIDRAG,
                    søknadFomDato = søktFomDato,
                    barnListe = opprettSøknader,
                    hovedsøknadsid = behandling.soknadsid,
                    innkreving = medInnkreving,
                    behandlingstype = behandling.behandlingstypeForFF,
                ),
            )

        val søknad =
            ForholdsmessigFordelingSøknadBarn(
                søknadsid = response.søknadsid,
                behandlingstype = behandling.behandlingstypeForFF,
                behandlingstema = stønadstype?.tilBehandlingstema() ?: Behandlingstema.BIDRAG,
                søknadFomDato = søktFomDato,
                mottattDato = LocalDate.now(),
                innkreving = medInnkreving,
                enhet = behandlerEnhet,
                saksnummer = saksnummer,
                status = Behandlingstatus.UNDER_BEHANDLING,
                søktAvType = SøktAvType.NAV_BIDRAG,
            )

        if (opprettForsendelse) {
            opprettForsendelseForNySøknad(
                saksnummer,
                behandling,
                bmFødselsnummer,
                søknad,
//                barnUtenSøknader
            )
        }
        return søknad
    }

    /**
     * Legger til barn i eksisterende åpen FF-søknad, eller oppretter ny søknad for revurderingsbarn.
     */
    fun leggTilEllerOpprettSøknadForRevurderingsbarn(
        behandling: Behandling,
        barnIdent: String,
        stønadstype: Stønadstype?,
        saksnummer: String,
        søktFomDato: LocalDate,
        medInnkreving: Boolean,
    ): ForholdsmessigFordelingSøknadBarn {
        val bidragspliktigFnr = behandling.bidragspliktig!!.ident!!

        val åpenFFSøknad =
            kravhaverService.hentÅpenSøknadFFForBP(
                bidragspliktigFnr,
                behandlingstype = behandling.behandlingstypeForFF,
                medInnkreving = medInnkreving,
                saksnummer = saksnummer,
                søktFomDato = søktFomDato,
                stønadstype = stønadstype,
                omgjøringsdetaljer = behandling.omgjøringsdetaljer,
            )

        if (åpenFFSøknad != null) {
            if (åpenFFSøknad.barn.none { it.personident == barnIdent }) {
                bbmConsumer.leggTilBarnISøknad(
                    LeggTilBarnIFFSøknadRequest(
                        åpenFFSøknad.søknadsid,
                        barnIdent,
                    ),
                )
            }
            if (åpenFFSøknad.behandlingsid != behandling.id) {
                bbmConsumer.lagreBehandlingsid(
                    OppdaterBehandlingsidRequest(åpenFFSøknad.søknadsid, åpenFFSøknad.behandlingsid, behandling.id!!),
                )
            }
            return åpenFFSøknad.tilForholdsmessigFordelingSøknad().copy(
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstype = behandling.behandlingstypeForFF,
                behandlingstema = Behandlingstema.BIDRAG,
            )
        } else {
            val behandlingstema =
                if (stønadstype == Stønadstype.BIDRAG18AAR) {
                    Behandlingstema.BIDRAG_18_ÅR
                } else {
                    Behandlingstema.BIDRAG
                }
            val søknad =
                bbmConsumer.opprettSøknader(
                    OpprettSøknadRequest(
                        saksnummer = saksnummer,
                        behandlingsid = behandling.id,
                        refVedtaksid = behandling.omgjøringsdetaljer?.omgjørVedtakId,
                        behandlingstype = behandling.behandlingstypeForFF,
                        behandlerenhet = behandling.behandlerEnhet,
                        hovedsøknadsid = behandling.soknadsid,
                        behandlingstema = behandlingstema,
                        søknadFomDato = søktFomDato,
                        innkreving = medInnkreving,
                        barnListe = listOf(Barn(personident = barnIdent)),
                    ),
                )

            return ForholdsmessigFordelingSøknadBarn(
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstype = behandling.behandlingstypeForFF,
                behandlingstema = behandlingstema,
                mottattDato = LocalDate.now(),
                søknadFomDato = søktFomDato,
                søknadsid = søknad.søknadsid,
                enhet = behandling.behandlerEnhet,
            )
        }
    }

    /**
     * Oppretter forsendelse for varsling av ny søknad i forholdsmessig fordeling.
     */
    fun opprettForsendelseForNySøknad(
        saksnummer: String,
        behandling: Behandling,
        bmFødselsnummer: String,
        søknad: ForholdsmessigFordelingSøknadBarn,
        barn: List<SakKravhaver> = emptyList(),
    ) {
        forsendelseService.slettEllerOpprettForsendelse(
            InitalizeForsendelseRequest(
                saksnummer = saksnummer,
                enhet = behandling.behandlerEnhet,
                roller =
                    barn.map {
                        ForsendelseRolleDto(
                            fødselsnummer = Personident(it.kravhaver),
                            type = Rolletype.BARN,
                        )
                    } +
                        listOf(
                            ForsendelseRolleDto(
                                fødselsnummer = Personident(bmFødselsnummer),
                                type = Rolletype.BIDRAGSMOTTAKER,
                            ),
                            ForsendelseRolleDto(
                                fødselsnummer = Personident(behandling.bidragspliktig!!.ident!!),
                                type = Rolletype.BIDRAGSPLIKTIG,
                            ),
                        ),
                behandlingInfo =
                    BehandlingInfoDto(
                        behandlingId = behandling.id?.toString(),
                        soknadId = søknad.søknadsid?.toString(),
                        soknadFra = søknad.søktAvType,
                        behandlingType = søknad.behandlingstema?.name,
                        soknadType = søknad.behandlingstype?.name,
                        stonadType = søknad.behandlingstema?.tilStønadstype() ?: behandling.stonadstype,
                        engangsBelopType = behandling.engangsbeloptype,
                        vedtakType = søknad.behandlingstype?.tilVedtakstype() ?: behandling.vedtakstype,
                    ),
            ),
        )
    }

    // endregion

    // ═══════════════════════════════════════════════════════════════════
    // region Synkronisering av søknadsstatus
    // ═══════════════════════════════════════════════════════════════════

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
                LOGGER.info { "Knytter sammen søknad $it til hovedsøknad ${behandling.soknadsid} i behandling ${behandling.id}" }
                bbmConsumer.sammeknyttSøknader(behandling.soknadsid!!, it)
            }

        alleSøknadsknytninger.søknader
            .filter { it.søknadsid != behandling.soknadsid }
            .filter { søknad ->
                alleSøknader.none { it == søknad.søknadsid }
            }.forEach {
                LOGGER.info {
                    "Slett søknadsknytning for søknad ${it.søknadsid} fra hovedsøknad ${behandling.soknadsid} i behandling ${behandling.id}"
                }
                bbmConsumer.fjernSammenknytning(it.søknadsid)
            }

        if (alleSøknadsknytninger.søknader.none { it.søknadsid == behandling.soknadsid }) {
            LOGGER.info { "Mangler hovedsøknad i sammenknytningen. Legger til" }
            bbmConsumer.endreSammenknytningSøknad(behandling.soknadsid!!, behandling.soknadsid!!)
        }
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
            LOGGER.info { "Oppdaterer hovedsøknad i behandling ${behandling.id} fra $søknadSomBleSlettet til $hovedsøknadsid" }
            bbmConsumer.fjernSammeknytningHovedsøknad(søknadSomBleSlettet, hovedsøknadsid)
        }
    }

    fun feilregistrerSøknad(
        søknad: ForholdsmessigFordelingSøknadBarn,
        behandling: Behandling,
    ): Boolean =
        try {
            val søknadsid = søknad.søknadsid ?: return false
            bbmConsumer.feilregistrerSøknad(FeilregistrerSøknadRequest(søknadsid))
            bbmConsumer.fjernSammenknytning(søknadsid)
            søknad.status = Behandlingstatus.FEILREGISTRERT
            behandling.roller.forEach { rolle ->
                val søknader = rolle.forholdsmessigFordeling?.søknaderUnderBehandling?.filter { it.søknadsid == søknadsid } ?: emptyList()
                søknader.forEach {
                    it.status = Behandlingstatus.FEILREGISTRERT
                }
            }
            true
        } catch (e: Exception) {
            LOGGER.warn(e) { "Kunne ikke feilregistrere søknad ${søknad.søknadsid} i BBM" }
            false
        }

    fun feilregistrerBarnFraSøknad(
        rolle: Rolle,
        søknadsid: Long,
    ): Long? {
        val personidentBarn = rolle.ident!!
        LOGGER.info { "Feilregistrerer barn $personidentBarn fra søknad $søknadsid" }
        return try {
            bbmConsumer.feilregistrerSøknadsbarn(FeilregistrerSøknadsBarnRequest(søknadsid, personidentBarn))
            rolle.forholdsmessigFordeling!!
                .søknaderUnderBehandling
                .filter { søknadsid == it.søknadsid }
                .forEach {
                    it.status = Behandlingstatus.FEILREGISTRERT
                }
            søknadsid
        } catch (e: Exception) {
            LOGGER.error(e) { "Feil ved feilregistrering av søknad $søknadsid" }
            null
        }
    }

    private fun oppdaterLagredeSoknadsstatuserFraBbm(
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
                                LOGGER.warn(e) { "Kunne ikke hente søknad ${lagretSøknad.søknadsid} fra BBM" }
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

    // endregion
}
