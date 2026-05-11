package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.datamodell.tilBehandlingstype
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.service.ForsendelseService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.commons.service.forsendelse.bidragsmottaker
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.tilBehandlingstema
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.Barn
import no.nav.bidrag.transport.behandling.beregning.felles.LeggTilBarnIFFSøknadRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OppdaterBehandlingsidRequest
import no.nav.bidrag.transport.behandling.beregning.felles.OpprettSøknadRequest
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.felles.toYearMonth
import java.time.LocalDate

private val SØKNAD_OPPRETT_LOGGER = KotlinLogging.logger {}

/**
 * Service for opprettelse og oppdatering av FF-søknader og revurderingssøknader.
 * Håndterer søknadlivssyklus i BBM for forholdsmessig fordeling.
 */
class ForholdsmessigFordelingSøknadOpprettService(
    private val bbmConsumer: BidragBBMConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val forsendelseService: ForsendelseService,
    private val kravhaverService: ForholdsmessigFordelingKravhaverService,
    private val virkningstidspunktService: VirkningstidspunktService,
) {
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
    ) {
        val sak = sakConsumer.hentSak(saksnummer)
        val bmFødselsnummer = hentNyesteIdent(sak.bidragsmottaker?.fødselsnummer?.verdi)?.verdi

        val barnUtenInnkreving = løpendeBidragssak.filter { !it.løperBidragEtterDato(søktFomDato.toYearMonth()) }
        val barnMedInnkreving = løpendeBidragssak.filter { it.løperBidragEtterDato(søktFomDato.toYearMonth()) }
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
    ): Long? {
        if (barn.isEmpty()) {
            return null
        }
        val søknadsid =
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
                søknader.first().søknadsid
            }

        val ffDetaljer =
            ForholdsmessigFordelingRolle(
                delAvOpprinneligBehandling = false,
                erRevurdering = true,
                tilhørerSak = saksnummer,
                behandlerenhet = behandlerEnhet,
                bidragsmottaker = bmFødselsnummer,
                søknader = mutableSetOf(),
            )
        opprettForsendelseForNySøknad(
            saksnummer = saksnummer,
            behandling = behandling,
            bmFødselsnummer = bmFødselsnummer!!,
            søknadsid = søknadsid.toString(),
            barn = barn,
        )

        barn.forEach { barnDetaljer ->
            val søknader =
                setOfNotNull(ffDetaljerBarn.copy(søknadsid = søknadsid))
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

        return søknadsid
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
    ): Long {
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
            return eksisterendeSøknad.søknadsid
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

        val søknadsid = response.søknadsid

        opprettForsendelseForNySøknad(saksnummer, behandling, bmFødselsnummer, søknadsid.toString(), barnUtenSøknader)
        return søknadsid
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
            val søknad =
                bbmConsumer.opprettSøknader(
                    OpprettSøknadRequest(
                        saksnummer = saksnummer,
                        behandlingsid = behandling.id,
                        refVedtaksid = behandling.omgjøringsdetaljer?.omgjørVedtakId,
                        behandlingstype = behandling.behandlingstypeForFF,
                        behandlerenhet = behandling.behandlerEnhet,
                        hovedsøknadsid = behandling.soknadsid,
                        behandlingstema =
                            if (stønadstype == Stønadstype.BIDRAG18AAR) {
                                Behandlingstema.BIDRAG_18_ÅR
                            } else {
                                Behandlingstema.BIDRAG
                            },
                        søknadFomDato = søktFomDato,
                        innkreving = medInnkreving,
                        barnListe = listOf(Barn(personident = barnIdent)),
                    ),
                )

            return ForholdsmessigFordelingSøknadBarn(
                søktAvType = SøktAvType.NAV_BIDRAG,
                behandlingstype = behandling.behandlingstypeForFF,
                behandlingstema = Behandlingstema.BIDRAG,
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
        søknadsid: String,
        barn: List<SakKravhaver>,
    ) {
        forsendelseService.slettEllerOpprettForsendelse(
            InitalizeForsendelseRequest(
                saksnummer = saksnummer,
                enhet = behandling.behandlerEnhet,
                roller =
                    listOf(
                        ForsendelseRolleDto(
                            fødselsnummer = Personident(bmFødselsnummer),
                            type = Rolletype.BIDRAGSMOTTAKER,
                        ),
                        ForsendelseRolleDto(
                            fødselsnummer = Personident(behandling.bidragspliktig!!.ident!!),
                            type = Rolletype.BIDRAGSPLIKTIG,
                        ),
                    ) +
                        barn.map {
                            ForsendelseRolleDto(
                                fødselsnummer = Personident(it.kravhaver),
                                type = Rolletype.BARN,
                            )
                        },
                behandlingInfo =
                    BehandlingInfoDto(
                        behandlingId = behandling.id?.toString(),
                        soknadId = søknadsid,
                        soknadFra = SøktAvType.NAV_BIDRAG,
                        behandlingType = behandling.tilBehandlingstype(),
                        stonadType = behandling.stonadstype,
                        engangsBelopType = behandling.engangsbeloptype,
                        vedtakType = behandling.vedtakstype,
                    ),
            ),
        )
    }
}
