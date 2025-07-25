package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.finnBostatusperiode
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.database.datamodell.henteEllerOpprettBpHusstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.henteLagretSivilstandshistorikk
import no.nav.bidrag.behandling.database.datamodell.henteNyesteGrunnlag
import no.nav.bidrag.behandling.database.datamodell.henteSisteSivilstand
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.lagreSivilstandshistorikk
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.HusstandsmedlemRepository
import no.nav.bidrag.behandling.database.repository.SivilstandRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereAndreVoksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeiletException
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.boforhold.filtrerUtUrelevantePerioder
import no.nav.bidrag.behandling.transformers.boforhold.overskriveAndreVoksneIHusstandMedBearbeidaPerioder
import no.nav.bidrag.behandling.transformers.boforhold.overskriveMedBearbeidaBostatusperioder
import no.nav.bidrag.behandling.transformers.boforhold.overskriveMedBearbeidaPerioder
import no.nav.bidrag.behandling.transformers.boforhold.overskriveMedBearbeidaSivilstandshistorikk
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdVoksneRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatus
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusperiode
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsmedlem
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.boforhold.tilOppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.boforhold.tilPerioder
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandDto
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSvilstandRequest
import no.nav.bidrag.behandling.transformers.tilTypeBoforhold
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validere
import no.nav.bidrag.behandling.transformers.validereSivilstand
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequestV3
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.diverse.TypeEndring
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

@Service
class BoforholdService(
    private val behandlingRepository: BehandlingRepository,
    private val husstandsmedlemRepository: HusstandsmedlemRepository,
    private val notatService: NotatService,
    private val sivilstandRepository: SivilstandRepository,
    private val dtomapper: Dtomapper,
) {
    @Transactional
    fun oppdatereNotat(
        behandlingsid: Long,
        request: OppdatereBegrunnelse,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        notatService.oppdatereNotat(
            behandling = behandling,
            notattype = Notattype.BOFORHOLD,
            notattekst = request.henteNyttNotat() ?: "",
            rolle = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)!!,
        )

        return OppdatereBoforholdResponse(
            begrunnelse = NotatService.henteNotatinnhold(behandling, Notattype.BOFORHOLD),
            valideringsfeil =
                BoforholdValideringsfeil(
                    husstandsmedlem =
                        behandling.husstandsmedlem
                            .validerBoforhold(behandling.virkningstidspunktEllerSøktFomDato)
                            .filter { it.harFeil },
                ),
        )
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvAndreVoksneIBpsHusstand(
        behandling: Behandling,
        periodisertBoforholdVoksne: Set<Bostatus>,
    ) {
        val husstandsmedlemBp = behandling.henteEllerOpprettBpHusstandsmedlem()
        husstandsmedlemBp.perioder.clear()
        husstandsmedlemBp.perioder.addAll(periodisertBoforholdVoksne.tilBostatusperiode(husstandsmedlemBp))
    }

    @Transactional
    fun lagreNyePeriodisertBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponseV2>,
    ) {
        behandling.husstandsmedlem.addAll(
            periodisertBoforhold
                .filter {
                    behandling.husstandsmedlem.none { hm -> it.gjelderPersonId == hm.ident }
                }.tilHusstandsmedlem(behandling),
        )
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvPeriodisertBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponseV2>,
    ) {
        behandling.husstandsmedlem
            .barn
            .filter {
                Kilde.OFFENTLIG == it.kilde
            }.forEach {
                sletteHusstandsmedlem(behandling, it)
            }

        behandling.husstandsmedlem.addAll(
            periodisertBoforhold
                .filter {
                    behandling.husstandsmedlem.none { hm -> it.gjelderPersonId == hm.ident }
                }.tilHusstandsmedlem(behandling),
        )
    }

    @Transactional
    fun oppdatereAutomatiskInnhentetBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponseV2>,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        overskriveManuelleOpplysninger: Boolean,
        gjelderHusstandsmedlem: Personident,
    ) {
        val nyeHusstandsmedlemMedPerioder = periodisertBoforhold.tilHusstandsmedlem(behandling).firstOrNull() ?: return
        // Ved overskriving bevares manuelle medlemmer, men dersom manuelt medlem med personident også finnes i grunnlag,
        // erstattes dette med offentlige opplysninger. Manuelle perioder til offisielle husstandsmedlem slettes.
        if (overskriveManuelleOpplysninger) {
            sletteOffentligeHusstandsmedlemSomIkkeFinnesINyesteGrunnlag(
                behandling,
                bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
                gjelderHusstandsmedlem,
            )
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsmedlemMedPerioder,
                true,
            )
            leggeTilNyttEllerOppdatereEksisterendeOffentligeHusstandsmedlemm(
                behandling,
                nyeHusstandsmedlemMedPerioder,
                true,
            )
        } else {
            endreKildePåOffentligeHusstandsmedlemmerSomIkkeFinnesINyesteGrunnlag(
                behandling,
                bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
                gjelderHusstandsmedlem,
            )
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsmedlemMedPerioder,
                false,
            )
            leggeTilNyttEllerOppdatereEksisterendeOffentligeHusstandsmedlemm(behandling, nyeHusstandsmedlemMedPerioder)
        }

        log.info {
            "Husstandsmedlem ble oppdatert for behandling ${behandling.id} " +
                "med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
        }
        secureLogger.info {
            "Husstandsmedlem ${gjelderHusstandsmedlem.verdi} ble oppdatert for behandling ${behandling.id} " +
                "med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
        }
    }

    @Transactional
    fun oppdatereAutomatiskInnhentetBoforholdAndreVoksneIHusstanden(
        behandling: Behandling,
        ikkeaktivertPeriodisertGrunnlag: Set<Bostatus>,
        ikkeaktivertGrunnlag: List<RelatertPersonGrunnlagDto>,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val husstandsmedlemBp =
            behandling.husstandsmedlem.voksneIHusstanden ?: run {
                log.error { "Fant ikke husstandsmedlem for BP. Kunne ikke oppdatere andre voksne i husstand." }
                return
            }

        if (overskriveManuelleOpplysninger) {
            log.info {
                "Overskriver andre voksne i husstand perioder med perioder fra nyeste innhentet grunnlag for behandling ${behandling.id}"
            }
            husstandsmedlemBp.overskriveMedBearbeidaBostatusperioder(ikkeaktivertPeriodisertGrunnlag.toList())
        } else {
            log.info {
                "Beholder eksisterende perioder etter aktivering av grunnlag for andre voksne i husstanden. " +
                    "Oppdaterer kilde på andre voksne i husstand perioder basert perioder fra nyeste innhentet grunnlag for behandling ${behandling.id}"
            }
            val periodiseringsrequest = husstandsmedlemBp.tilBoforholdVoksneRequest(behandling.bidragspliktig!!, null)

            val borMedAndreVoksneperioder =
                BoforholdApi.beregnBoforholdAndreVoksne(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    periodiseringsrequest.copy(
                        innhentedeOffentligeOpplysninger =
                            ikkeaktivertGrunnlag.tilHusstandsmedlemmer().filtrerUtUrelevantePerioder(
                                behandling,
                            ),
                    ),
                )

            husstandsmedlemBp.overskriveMedBearbeidaBostatusperioder(borMedAndreVoksneperioder)
        }
    }

    @Transactional
    fun rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandlingsid: Long) {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        behandling.husstandsmedlem
            .voksneIHusstanden
            ?.let { andreVoksneIHusstanden ->
                andreVoksneIHusstanden.lagreEksisterendePerioder()
                andreVoksneIHusstanden.oppdaterePerioderVoksne(behandling.bidragspliktig!!)

                behandling.husstandsmedlem.remove(behandling.husstandsmedlem.voksneIHusstanden)
                behandling.husstandsmedlem.add(husstandsmedlemRepository.save(andreVoksneIHusstanden))
            }
    }

    @Transactional
    fun rekalkulerOgLagreHusstandsmedlemPerioder(behandlingsid: Long) {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }
        val oppdaterHusstandsmedlemmer =
            behandling.husstandsmedlem.barn.map { husstandsmedlem ->
                husstandsmedlem.lagreEksisterendePerioder()
                husstandsmedlem.oppdaterePerioder()
                husstandsmedlemRepository.save(husstandsmedlem)
            }
        behandling.husstandsmedlem.removeAll(behandling.husstandsmedlem.barn.toSet())
        behandling.husstandsmedlem.addAll(oppdaterHusstandsmedlemmer)
    }

    @Transactional
    fun oppdatereHusstandsmedlemManuelt(
        behandlingsid: Long,
        oppdatereHusstandsmedlem: OppdatereHusstandsmedlem,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereHusstandsmedlem.validere(behandling)

        oppdatereHusstandsmedlem.slettHusstandsmedlem?.let { idHusstandsmedlem ->
            val husstandsmedlemSomSkalSlettes = behandling.husstandsmedlem.find { idHusstandsmedlem == it.id }
            if (Kilde.MANUELL == husstandsmedlemSomSkalSlettes?.kilde) {
                behandling.husstandsmedlem.remove(husstandsmedlemSomSkalSlettes)
                loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlemSomSkalSlettes)
                return dtomapper.tilOppdatereBoforholdResponse(husstandsmedlemSomSkalSlettes)
            }
        }

        oppdatereHusstandsmedlem.opprettHusstandsmedlem?.let { personalia ->
            val husstandsmedlem =
                Husstandsmedlem(
                    behandling,
                    Kilde.MANUELL,
                    ident = personalia.personident?.verdi,
                    fødselsdato = personalia.fødselsdato,
                    navn = personalia.navn,
                )

            val offentligePerioder =
                personalia.personident?.let {
                    val respons =
                        BoforholdApi.beregnBoforholdBarnV3(
                            behandling.virkningstidspunktEllerSøktFomDato,
                            behandling.globalOpphørsdato,
                            behandling.tilTypeBoforhold(),
                            behandling
                                .henteGrunnlagHusstandsmedlemMedHarkodetBmBpRelasjon(it)
                                .tilBoforholdBarnRequest(behandling),
                        )

                    if (respons.isNotEmpty()) {
                        husstandsmedlem.perioder.addAll(respons.tilPerioder(husstandsmedlem))
                        lagreBearbeidaBoforholdsgrunnlag(behandling, respons, personalia.personident)
                    }
                    respons
                }

            if (offentligePerioder.isNullOrEmpty()) {
                husstandsmedlem.oppdaterePerioder(
                    nyEllerOppdatertBostatusperiode =
                        Bostatusperiode(
                            husstandsmedlem = husstandsmedlem,
                            bostatus = Bostatuskode.MED_FORELDER,
                            datoFom = behandling.virkningstidspunktEllerSøktFomDato,
                            datoTom = null,
                            kilde = Kilde.MANUELL,
                        ),
                )
            }
            behandling.husstandsmedlem.add(husstandsmedlem)
            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlem)

            return dtomapper.tilOppdatereBoforholdResponse(husstandsmedlemRepository.save(husstandsmedlem))
        }

        oppdatereHusstandsmedlem.slettPeriode?.let { idHusstansmedlemsperiode ->
            val husstansmedlemsperiodeSomSkalSlettes =
                behandling.finnBostatusperiode(idHusstansmedlemsperiode)
            val husstandsmedlem = husstansmedlemsperiodeSomSkalSlettes!!.husstandsmedlem
            husstandsmedlem.lagreEksisterendePerioder()
            husstandsmedlem.oppdaterePerioder(sletteHusstandsmedlemsperiode = idHusstansmedlemsperiode)

            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return dtomapper.tilOppdatereBoforholdResponse(husstandsmedlemRepository.save(husstandsmedlem))
        }

        oppdatereHusstandsmedlem.oppdaterPeriode?.let { bostatusperiode ->

            val eksisterendeHusstandsmedlem =
                behandling.husstandsmedlem.find { it.id == bostatusperiode.idHusstandsmedlem }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)

            eksisterendeHusstandsmedlem.lagreEksisterendePerioder()

            eksisterendeHusstandsmedlem.oppdaterePerioder(
                nyEllerOppdatertBostatusperiode =
                    Bostatusperiode(
                        id = bostatusperiode.idPeriode,
                        husstandsmedlem = eksisterendeHusstandsmedlem,
                        bostatus = bostatusperiode.bostatus,
                        datoFom = bostatusperiode.datoFom,
                        datoTom = bostatusperiode.datoTom,
                        kilde = Kilde.MANUELL,
                    ),
            )

            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, eksisterendeHusstandsmedlem)
            return dtomapper.tilOppdatereBoforholdResponse(
                husstandsmedlemRepository.save(eksisterendeHusstandsmedlem),
            )
        }

        oppdatereHusstandsmedlem.tilbakestillPerioderForHusstandsmedlem?.let { husstandsmedlemId ->
            val husstandsmedlem =
                behandling.husstandsmedlem.find { it.id == husstandsmedlemId }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)
            husstandsmedlem.lagreEksisterendePerioder()
            husstandsmedlem.resetTilOffentligePerioder()
            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return dtomapper.tilOppdatereBoforholdResponse(husstandsmedlemRepository.save(husstandsmedlem))
        }

        oppdatereHusstandsmedlem.angreSisteStegForHusstandsmedlem?.let { husstandsmedlemId ->
            val husstandsmedlem =
                behandling.husstandsmedlem.find { it.id == husstandsmedlemId }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)
            husstandsmedlem.oppdaterTilForrigeLagredePerioder()
            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return dtomapper.tilOppdatereBoforholdResponse(husstandsmedlemRepository.save(husstandsmedlem))
        }
        oppdateringAvBoforholdFeilet("Oppdatering av boforhold feilet. Forespørsel mangler informasjon om hva som skal oppdateres")
    }

    @Transactional
    fun oppdatereAndreVoksneIHusstandenManuelt(
        behandlingsid: Long,
        oppdatereAndreVoksneIHusstanden: OppdatereAndreVoksneIHusstanden,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereAndreVoksneIHusstanden.validere(behandling)

        val rolleMedAndreVoksneIHusstaden = behandling.bidragspliktig!!

        val husstandsmedlemSomSkalOppdateres =
            behandling.husstandsmedlem.find { rolleMedAndreVoksneIHusstaden == it.rolle }!!

        oppdatereAndreVoksneIHusstanden.slettePeriode?.let { idHusstansmedlemsperiode ->
            husstandsmedlemSomSkalOppdateres.lagreEksisterendePerioder()
            husstandsmedlemSomSkalOppdateres.oppdaterePerioderVoksne(
                gjelderRolle = rolleMedAndreVoksneIHusstaden,
                sletteHusstandsmedlemsperiode = idHusstansmedlemsperiode,
            )

            loggeEndringAndreVoksneIHusstanden(
                behandling,
                oppdatereAndreVoksneIHusstanden,
                husstandsmedlemSomSkalOppdateres,
            )
            return dtomapper.tilOppdatereBoforholdResponse(
                husstandsmedlemRepository.save(husstandsmedlemSomSkalOppdateres),
            )
        }

        oppdatereAndreVoksneIHusstanden.oppdaterePeriode?.let { oppdatereStatus ->

            val nyBostatus =
                if (oppdatereStatus.borMedAndreVoksne) Bostatuskode.BOR_MED_ANDRE_VOKSNE else Bostatuskode.BOR_IKKE_MED_ANDRE_VOKSNE

            husstandsmedlemSomSkalOppdateres.lagreEksisterendePerioder()

            val nyPeriode = oppdatereStatus.periode
            husstandsmedlemSomSkalOppdateres.oppdaterePerioderVoksne(
                gjelderRolle = rolleMedAndreVoksneIHusstaden,
                nyEllerOppdatertBostatusperiode =
                    Bostatusperiode(
                        id = oppdatereStatus.idPeriode,
                        husstandsmedlem = husstandsmedlemSomSkalOppdateres,
                        bostatus = nyBostatus,
                        datoFom =
                            nyPeriode.fom.atDay(1)
                                ?: behandling.virkningstidspunkt!!,
                        datoTom = nyPeriode.til?.atEndOfMonth(),
                        kilde = Kilde.MANUELL,
                    ),
            )

            loggeEndringAndreVoksneIHusstanden(
                behandling,
                oppdatereAndreVoksneIHusstanden,
                husstandsmedlemSomSkalOppdateres,
            )
            return dtomapper.tilOppdatereBoforholdResponse(
                husstandsmedlemRepository.save(husstandsmedlemSomSkalOppdateres),
            )
        }

        if (oppdatereAndreVoksneIHusstanden.angreSisteEndring) {
            husstandsmedlemSomSkalOppdateres.oppdaterTilForrigeLagredePerioder()
            loggeEndringAndreVoksneIHusstanden(
                behandling,
                oppdatereAndreVoksneIHusstanden,
                husstandsmedlemSomSkalOppdateres,
            )
            return dtomapper.tilOppdatereBoforholdResponse(
                husstandsmedlemRepository.save(husstandsmedlemSomSkalOppdateres),
            )
        }

        if (oppdatereAndreVoksneIHusstanden.tilbakestilleHistorikk) {
            husstandsmedlemSomSkalOppdateres.lagreEksisterendePerioder()
            husstandsmedlemSomSkalOppdateres.resetTilOffentligePerioderAndreVoksneIHusstand()
            loggeEndringAndreVoksneIHusstanden(
                behandling,
                oppdatereAndreVoksneIHusstanden,
                husstandsmedlemSomSkalOppdateres,
            )

            return dtomapper.tilOppdatereBoforholdResponse(
                husstandsmedlemRepository.save(husstandsmedlemSomSkalOppdateres),
            )
        }

        oppdateringAvBoforholdFeilet("Oppdatering av boforhold andre-voksne-i-husstanden feilet.")
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvPeriodisertSivilstand(
        behandling: Behandling,
        periodisertSivilstand: Set<SivilstandBeregnV2Dto>,
    ) {
        behandling.sivilstand.removeAll(behandling.sivilstand.filter { Kilde.OFFENTLIG == it.kilde }.toSet())
        behandling.sivilstand.addAll(periodisertSivilstand.tilSivilstand(behandling))
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaSivilstand(
        behandling: Behandling,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val ikkeAktiverteGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()

        val nyesteIkkeaktivertePeriodiserteSivilstand =
            ikkeAktiverteGrunnlag
                .filter { Grunnlagsdatatype.SIVILSTAND == it.type }
                .filter { it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        val nyesteIkkeaktiverteSivilstand =
            ikkeAktiverteGrunnlag
                .filter { Grunnlagsdatatype.SIVILSTAND == it.type }
                .filter { !it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        if (nyesteIkkeaktivertePeriodiserteSivilstand == null && nyesteIkkeaktiverteSivilstand == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type SIVILSTAND å aktivere for  behandling $behandling.id",
            )
        }

        val data =
            when (overskriveManuelleOpplysninger) {
                true -> {
                    nyesteIkkeaktivertePeriodiserteSivilstand?.let {
                        jsonListeTilObjekt<SivilstandBeregnV2Dto>(it.data)
                    }
                }

                false -> {
                    nyesteIkkeaktiverteSivilstand?.let { g ->
                        val request =
                            jsonListeTilObjekt<SivilstandGrunnlagDto>(g.data)
                                .tilSivilstandRequest(
                                    behandling.sivilstand.filter { Kilde.MANUELL == it.kilde }.toSet(),
                                    behandling.bidragsmottaker!!.fødselsdato,
                                )
                        SivilstandApi.beregnV2(behandling.virkningstidspunktEllerSøktFomDato, request).toSet()
                    }
                }
            }
        data?.let {
            behandling.sivilstand.clear()
            behandling.sivilstand.addAll(
                it.tilSivilstand(behandling),
            )
        }

        nyesteIkkeaktiverteSivilstand?.aktiv = LocalDateTime.now()
        nyesteIkkeaktivertePeriodiserteSivilstand?.aktiv = LocalDateTime.now()
    }

    @Transactional
    fun oppdatereSivilstandManuelt(
        behandlingsid: Long,
        oppdatereSivilstand: OppdatereSivilstand,
    ): OppdatereBoforholdResponse? {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereSivilstand.validere(behandling)

        oppdatereSivilstand.sletteSivilstandsperiode?.let { idSivilstandsperiode ->
            behandling.bidragsmottaker!!.lagreSivilstandshistorikk(behandling.sivilstand)
            behandling.oppdatereSivilstandshistorikk(sletteInnslag = idSivilstandsperiode)
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return OppdatereBoforholdResponse(
                oppdatertSivilstandshistorikk =
                    sivilstandRepository
                        .saveAll(behandling.sivilstand)
                        .toSet()
                        .tilSivilstandDto(),
                valideringsfeil =
                    BoforholdValideringsfeil(
                        sivilstand = behandling.sivilstand.validereSivilstand(behandling.virkningstidspunktEllerSøktFomDato),
                    ),
            )
        }
        oppdatereSivilstand.nyEllerEndretSivilstandsperiode?.let {
            behandling.bidragsmottaker!!.lagreSivilstandshistorikk(behandling.sivilstand)
            behandling.oppdatereSivilstandshistorikk(it)
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return OppdatereBoforholdResponse(
                oppdatertSivilstandshistorikk =
                    sivilstandRepository.saveAll(behandling.sivilstand).toSet().tilSivilstandDto(),
                valideringsfeil =
                    BoforholdValideringsfeil(
                        sivilstand = behandling.sivilstand.validereSivilstand(behandling.virkningstidspunktEllerSøktFomDato),
                    ),
            )
        }

        if (oppdatereSivilstand.angreSisteEndring) {
            behandling.gjenoppretteForrigeSivilstandshistorikk(behandling.bidragsmottaker!!)
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return sivilstandRepository.saveAll(behandling.sivilstand).toSet().tilOppdatereBoforholdResponse(behandling)
        } else if (oppdatereSivilstand.tilbakestilleHistorikk) {
            behandling.bidragsmottaker!!.lagreSivilstandshistorikk(behandling.sivilstand)
            behandling.tilbakestilleTilOffentligSivilstandshistorikk()
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return sivilstandRepository.saveAll(behandling.sivilstand).toSet().tilOppdatereBoforholdResponse(behandling)
        }

        oppdateringAvBoforholdFeiletException(behandlingsid)
    }

    @Transactional
    fun oppdatereSivilstandshistorikk(behandling: Behandling) {
        behandling.bidragsmottaker!!.lagreSivilstandshistorikk(behandling.sivilstand)
        behandling.oppdatereSivilstandshistorikk()
    }

    private fun sletteHusstandsmedlem(
        behandling: Behandling,
        husstandsmedlemSomSkalSlettes: Husstandsmedlem,
    ) {
        husstandsmedlemSomSkalSlettes.perioder.clear()
        sletteHusstandsmedlem(behandling, setOf(husstandsmedlemSomSkalSlettes))
        secureLogger.info {
            "Slettet $husstandsmedlemSomSkalSlettes husstandsmedlem fra behandling ${behandling.id} i " +
                "forbindelse med førstegangsoppdatering av boforhold."
        }
    }

    // Sikrer mot ConcurrentModificationException
    private fun sletteHusstandsmedlem(
        behandling: Behandling,
        husstandsmedlemSomSkalSlettes: Set<Husstandsmedlem>,
    ) {
        behandling.husstandsmedlem.removeAll(husstandsmedlemSomSkalSlettes)
        log.info {
            "Slettet ${husstandsmedlemSomSkalSlettes.size} husstandsmedlem fra behandling ${behandling.id} i " +
                "forbindelse med førstegangsoppdatering av boforhold."
        }
    }

    private fun sletteOffentligeHusstandsmedlemSomIkkeFinnesINyesteGrunnlag(
        behandling: Behandling,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        gjelderHusstandsmedlem: Personident,
    ) {
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.any { it.verdi == gjelderHusstandsmedlem.verdi }) return

        behandling.husstandsmedlem
            .find { i -> Kilde.OFFENTLIG == i.kilde && i.ident == gjelderHusstandsmedlem.verdi }
            ?.let { eksisterendeHusstandsmedlem ->
                eksisterendeHusstandsmedlem.perioder.clear()
                sletteHusstandsmedlem(behandling, eksisterendeHusstandsmedlem)
            }
    }

    private fun leggeTilNyttEllerOppdatereEksisterendeOffentligeHusstandsmedlemm(
        behandling: Behandling,
        nyttHusstandsmedlem: Husstandsmedlem,
        overskriveManuelleOpplysninger: Boolean = false,
    ) {
        val husstandsmedlemSomSkalOppdateres =
            behandling.husstandsmedlem
                .asSequence()
                .filter { i -> Kilde.OFFENTLIG == i.kilde }
                .toSet()

        val eksisterendeHusstandsmedlem =
            husstandsmedlemSomSkalOppdateres.find { hentNyesteIdent(it.ident)?.verdi == nyttHusstandsmedlem.ident }

        // Oppdaterer eksisterende husstandsmedlem. Sletter manuelle perioder. Kjører ny periodisering av offentlige perioder.
        if (eksisterendeHusstandsmedlem != null) {
            val oppdatertePerioder =
                overskriveManuelleOpplysninger.ifTrue {
                    nyttHusstandsmedlem.perioder.forEach {
                        it.husstandsmedlem = eksisterendeHusstandsmedlem
                    }
                    nyttHusstandsmedlem.perioder
                } // Kjør ny periodisering for å oppdatere kilde på periodene basert på nye opplysninger
                    ?: BoforholdApi
                        .beregnBoforholdBarnV3(
                            behandling.virkningstidspunktEllerSøktFomDato,
                            eksisterendeHusstandsmedlem.rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                            behandling.tilTypeBoforhold(),
                            listOf(
                                eksisterendeHusstandsmedlem
                                    .tilBoforholdBarnRequest()
                                    .copy(
                                        innhentedeOffentligeOpplysninger = nyttHusstandsmedlem.perioder.map { it.tilBostatus() },
                                    ),
                            ),
                        ).tilPerioder(eksisterendeHusstandsmedlem)

            eksisterendeHusstandsmedlem.lagreEksisterendePerioder()
            eksisterendeHusstandsmedlem.perioder.clear()
            eksisterendeHusstandsmedlem.perioder.addAll(oppdatertePerioder)
            log.info {
                "Oppdaterte husstandsmedlem ${eksisterendeHusstandsmedlem.id} i behandling ${behandling.id} med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
            }
            secureLogger.info {
                "Oppdaterte husstandsmedlem $eksisterendeHusstandsmedlem i behandling ${behandling.id} med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
            }
        } else {
            // Legger nytt offentlig husstandsmedlem uten å kjøre ny periodisering
            val nyttHusstandsmedlem = husstandsmedlemRepository.save(nyttHusstandsmedlem)
            behandling.husstandsmedlem.add(nyttHusstandsmedlem)
            log.info { "Nytt husstandsmedlem ${nyttHusstandsmedlem.id} ble opprettet i behandling ${behandling.id}" }
            secureLogger.info { "Nytt husstandsmedlem $nyttHusstandsmedlem ble opprettet i behandling ${behandling.id}" }
        }
    }

    private fun slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
        behandling: Behandling,
        offisieltHusstandsmedlem: Husstandsmedlem,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val manuelleHusstandsmedlemmerMedIdent =
            behandling.husstandsmedlem.filter { Kilde.MANUELL == it.kilde }.filter { it.ident != null }
        val identOffisieltHusstandsmedlem = offisieltHusstandsmedlem.ident

        manuelleHusstandsmedlemmerMedIdent.forEach { manueltMedlem ->
            if (identOffisieltHusstandsmedlem == manueltMedlem.ident) {
                log.info {
                    "Slår sammen manuelt husstandsmedlem med id ${manueltMedlem.id} med informasjon fra offentlige registre. Oppgraderer kilde til medlemmet til OFFENTLIG"
                }
                offisieltHusstandsmedlem.resetTilOffentligePerioder()
                val request =
                    BoforholdBarnRequestV3(
                        gjelderPersonId = offisieltHusstandsmedlem.ident,
                        fødselsdato =
                            offisieltHusstandsmedlem.fødselsdato
                                ?: offisieltHusstandsmedlem.rolle!!.fødselsdato,
                        relasjon = Familierelasjon.BARN,
                        innhentedeOffentligeOpplysninger =
                            offisieltHusstandsmedlem.perioder
                                .map { it.tilBostatus() }
                                .sortedBy { it.periodeFom },
                        behandledeBostatusopplysninger = emptyList(),
                        erSøknadsbarn = offisieltHusstandsmedlem.erSøknadsbarn,
                        endreBostatus = null,
                    )
                val periodisertBoforhold =
                    if (overskriveManuelleOpplysninger) {
                        BoforholdApi.beregnBoforholdBarnV3(
                            behandling.virkningstidspunktEllerSøktFomDato,
                            offisieltHusstandsmedlem.rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                            behandling.tilTypeBoforhold(),
                            listOf(request),
                        )
                    } else {
                        BoforholdApi.beregnBoforholdBarnV3(
                            behandling.virkningstidspunktEllerSøktFomDato,
                            offisieltHusstandsmedlem.rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                            behandling.tilTypeBoforhold(),
                            listOf(
                                request.copy(
                                    behandledeBostatusopplysninger = manueltMedlem.perioder.map { it.tilBostatus() },
                                ),
                            ),
                        )
                    }

                val oppdatertPerioder = periodisertBoforhold.tilPerioder(offisieltHusstandsmedlem)
                manueltMedlem.perioder.clear()
                manueltMedlem.perioder.addAll(oppdatertPerioder.toSet())
                manueltMedlem.kilde = Kilde.OFFENTLIG
            }
        }
    }

    private fun endreKildePåOffentligeHusstandsmedlemmerSomIkkeFinnesINyesteGrunnlag(
        behandling: Behandling,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        gjelderHusstandsmedlem: Personident,
    ) {
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.any { it.verdi == gjelderHusstandsmedlem.verdi }) return

        behandling.husstandsmedlem
            .find { Kilde.OFFENTLIG == it.kilde && it.ident == gjelderHusstandsmedlem.verdi }
            ?.let { eksisterendeHusstandsmedlem ->
                log.info { "Endret husstandsbmedlem ${eksisterendeHusstandsmedlem.id} til kilde MANUELL i behandling ${behandling.id}" }
                secureLogger.info { "Endret husstandsmedlem $eksisterendeHusstandsmedlem til kilde MANUELL i behandling ${behandling.id}" }
                eksisterendeHusstandsmedlem.kilde = Kilde.MANUELL
                eksisterendeHusstandsmedlem.perioder.forEach { periode -> periode.kilde = Kilde.MANUELL }
            }
    }

    companion object {
        private fun Behandling.tilbakestilleTilOffentligSivilstandshistorikk() {
            this.grunnlag.henteSisteSivilstand(true)?.let { overskriveMedBearbeidaSivilstandshistorikk(it) }
                ?: run {
                    this.sivilstand.clear()
                    log.warn {
                        "Fant ikke ingen original bearbeida sivilstandshistorikk i behandling ${this.id}."
                    }
                }
        }

        private fun Husstandsmedlem.opprettDefaultPeriodeForOffentligHusstandsmedlem() =
            Bostatusperiode(
                husstandsmedlem = this,
                datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato ?: rolle!!.fødselsdato),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            )

        fun Husstandsmedlem.opprettDefaultPeriodeForAndreVoksneIHusstand() =
            Bostatusperiode(
                husstandsmedlem = this,
                datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato ?: rolle!!.fødselsdato),
                datoTom = null,
                bostatus = Bostatuskode.BOR_IKKE_MED_ANDRE_VOKSNE,
                kilde = Kilde.OFFENTLIG,
            )

        private fun Husstandsmedlem.oppdaterTilForrigeLagredePerioder() {
            val lagredePerioder = commonObjectmapper.writeValueAsString(perioder)
            perioder.clear()
            perioder.addAll(hentForrigeLagredePerioder())
            forrigePerioder = lagredePerioder
        }

        private fun Husstandsmedlem.hentForrigeLagredePerioder(): Set<Bostatusperiode> {
            val forrigePerioder: Set<JsonNode> =
                commonObjectmapper.readValue(
                    forrigePerioder
                        ?: oppdateringAvBoforholdFeilet("Mangler forrige perioder for husstandsmedlem $id i behandling ${behandling.id}"),
                )
            return forrigePerioder
                .map {
                    Bostatusperiode(
                        husstandsmedlem = this,
                        datoFom = LocalDate.parse(it["datoFom"].textValue()),
                        datoTom = it["datoTom"]?.textValue()?.let { LocalDate.parse(it) },
                        bostatus = Bostatuskode.valueOf(it["bostatus"].textValue()),
                        kilde = Kilde.valueOf(it["kilde"].textValue()),
                    )
                }.toSet()
        }

        fun Husstandsmedlem.lagreEksisterendePerioder() {
            forrigePerioder = commonObjectmapper.writeValueAsString(perioder)
        }
    }

    private fun loggeEndringAndreVoksneIHusstanden(
        behandling: Behandling,
        oppdatereAndreVoksne: OppdatereAndreVoksneIHusstanden,
        husstandsmedlem: Husstandsmedlem,
    ) {
        val detaljerPerioder =
            husstandsmedlem.perioder
                .map {
                    "{ datoFom: ${it.datoFom}, datoTom: ${it.datoTom}, " +
                        "bostatus: ${it.bostatus}, kilde: ${it.kilde} }"
                }.joinToString(", ", prefix = "[", postfix = "]")
        if (oppdatereAndreVoksne.angreSisteEndring) {
            log.info { "Angret siste endring for andre voksne i husstanden ${husstandsmedlem.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Angret siste steg for andre voksne i husstanden ${husstandsmedlem.id} i behandling ${behandling.id}. " +
                    "Gjeldende perioder etter endring: $detaljerPerioder"
            }
        }
        if (oppdatereAndreVoksne.tilbakestilleHistorikk) {
            log.info { "Tilbakestilte perioder for andre voksne i husstanden ${husstandsmedlem.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Tilbakestilte perioder for andre voksne i husstanden ${husstandsmedlem.id} i behandling ${behandling.id}." +
                    "Gjeldende perioder etter endring: $detaljerPerioder"
            }
        }

        oppdatereAndreVoksne.slettePeriode?.let { idBostatusperiode ->
            log.info { "Slettet bostatusperiode med id $idBostatusperiode for andre voksne i husstanden fra behandling ${behandling.id}." }
            secureLogger.info {
                "Slettet bostatusperiode med id $idBostatusperiode for andre voksne i husstanden fra behandling ${behandling.id}." +
                    "Gjeldende perioder etter endring: $detaljerPerioder"
            }
        }
        oppdatereAndreVoksne.oppdaterePeriode?.let { statusPåPeriode ->
            val nyStatus =
                if (statusPåPeriode.borMedAndreVoksne) Bostatuskode.BOR_MED_ANDRE_VOKSNE else Bostatuskode.BOR_IKKE_MED_ANDRE_VOKSNE

            if (statusPåPeriode.idPeriode != null) {
                log.info {
                    "Oppdaterte bostatus for periode ${statusPåPeriode.idPeriode} andre voksne i husstanden " +
                        "${husstandsmedlem.id} til $nyStatus i behandling ${behandling.id}"
                }
            }
        }
    }

    private fun loggeEndringHusstandsmedlem(
        behandling: Behandling,
        oppdatereHusstandsmedlem: OppdatereHusstandsmedlem,
        husstandsmedlem: Husstandsmedlem,
    ) {
        val perioderDetaljer =
            husstandsmedlem.perioder
                .map {
                    "{ datoFom: ${it.datoFom}, datoTom: ${it.datoTom}, " +
                        "bostatus: ${it.bostatus}, kilde: ${it.kilde} }"
                }.joinToString(", ", prefix = "[", postfix = "]")
        oppdatereHusstandsmedlem.angreSisteStegForHusstandsmedlem?.let {
            log.info { "Angret siste steg for husstandsmedlem ${husstandsmedlem.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Angret siste steg for husstandsmedlem ${husstandsmedlem.id} i behandling ${behandling.id}. " +
                    "Gjeldende perioder etter endring: $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.tilbakestillPerioderForHusstandsmedlem?.let {
            log.info { "Tilbakestilte perioder for husstandsmedlem ${husstandsmedlem.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Tilbakestilte perioder for husstandsmedlem ${husstandsmedlem.id} i behandling ${behandling.id}." +
                    "Gjeldende perioder etter endring: $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.opprettHusstandsmedlem?.let { personalia ->
            log.info { "Nytt husstandsmedlem (id ${husstandsmedlem.id}) ble manuelt lagt til behandling ${behandling.id}." }
        }
        oppdatereHusstandsmedlem.slettPeriode?.let { idHusstandsmedlemsperiode ->
            log.info { "Slettet husstandsmedlemperiode med id $idHusstandsmedlemsperiode fra behandling ${behandling.id}." }
            secureLogger.info {
                "Slettet husstandsmedlemperiode med id $idHusstandsmedlemsperiode fra behandling ${behandling.id}." +
                    "Gjeldende perioder etter endring: $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.oppdaterPeriode?.let { bostatusperiode ->
            val detaljer =
                "datoFom: ${bostatusperiode.datoFom}, datoTom: ${bostatusperiode.datoTom}, " +
                    "bostatus: ${bostatusperiode.bostatus}"
            if (bostatusperiode.idPeriode != null) {
                log.info {
                    "Oppdaterte periode ${bostatusperiode.idPeriode} for husstandsmedlem " +
                        "${bostatusperiode.idHusstandsmedlem} til $detaljer i behandling ${behandling.id}"
                }
            } else {
                log.info {
                    "Ny periode $detaljer ble lagt til husstandsmedlem ${bostatusperiode.idHusstandsmedlem} i behandling med " +
                        "${behandling.id}."
                }
            }
        }
        oppdatereHusstandsmedlem.slettHusstandsmedlem?.let { idHusstandsmedlem ->
            log.info { "Slettet husstandsmedlem med id $idHusstandsmedlem fra behandling ${behandling.id}." }
        }
    }

    private fun loggeEndringSivilstand(
        behandling: Behandling,
        oppdatereSivilstand: OppdatereSivilstand,
        historikk: Set<Sivilstand>,
    ) {
        val historikkstreng =
            historikk
                .map {
                    "{ datoFom: ${it.datoFom}, datoTom: ${it.datoTom}, " +
                        "sivilstand: ${it.sivilstand}, kilde: ${it.kilde} }"
                }.joinToString(", ", prefix = "[", postfix = "]")
        oppdatereSivilstand.angreSisteEndring.let {
            log.info { "Angret siste endring i sivilstand for behandling ${behandling.id}." }
            secureLogger.info {
                "Angret siste endring i sivilstandshistorikk for behandling ${behandling.id}. " +
                    "Gjeldende historikk etter endring: $historikkstreng"
            }
        }
        if (oppdatereSivilstand.tilbakestilleHistorikk) {
            log.info { "Tilbakestilte sivilstandshistorikk for behandling ${behandling.id}." }
            secureLogger.info {
                "Tilbakestilte sivilstandshistorikk til offentlige kilder for behandling  ${behandling.id}." +
                    "Gjeldende historikk etter endring: $historikkstreng"
            }
        }

        oppdatereSivilstand.sletteSivilstandsperiode?.let { idSivilstandsperiode ->
            log.info { "Slettet sivilstandsperiode med id $idSivilstandsperiode fra behandling ${behandling.id}." }
            secureLogger.info {
                "Slettet sivilstandsperiode med id $idSivilstandsperiode fra behandling ${behandling.id}." +
                    "Gjeldende historikk etter endring: $historikkstreng"
            }
        }
        oppdatereSivilstand.nyEllerEndretSivilstandsperiode?.let { sivilstandsperiode ->
            val detaljer =
                "datoFom: ${sivilstandsperiode.fraOgMed}, datoTom: ${sivilstandsperiode.tilOgMed}, " +
                    "sivilstand: ${sivilstandsperiode.sivilstand}"
            if (sivilstandsperiode.id != null) {
                log.info {
                    "Oppdaterte sivilstandsperiode ${sivilstandsperiode.id} i behandling ${behandling.id} til: $detaljer"
                }
            } else {
                log.info {
                    "Ny sivilstandsperiode: $detaljer, ble lagt til i behandling ${behandling.id}."
                }
            }
        }
        oppdatereSivilstand.sletteSivilstandsperiode?.let { sivilstandsperiode ->
            log.info { "Slettet sivilstandsperiode med id $sivilstandsperiode fra behandling ${behandling.id}." }
        }
    }

    private fun Behandling.oppdatereSivilstandshistorikk(
        nyttEllerEndretInnslag: Sivilstandsperiode? = null,
        sletteInnslag: Long? = null,
    ) {
        val request =
            this.sivilstand.tilSvilstandRequest(
                nyttEllerEndretInnslag,
                sletteInnslag,
                this.bidragsmottaker!!.fødselsdato,
                this,
            )
        val resultat = SivilstandApi.beregnV2(this.virkningstidspunktEllerSøktFomDato, request).toSet()
        this.overskriveMedBearbeidaSivilstandshistorikk(resultat)
    }

    private fun bestemmeEndringstype(
        nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
        sletteHusstandsmedlemsperiode: Long? = null,
    ): TypeEndring {
        nyEllerOppdatertBostatusperiode?.let {
            if (it.id != null) {
                return TypeEndring.ENDRET
            }
            return TypeEndring.NY
        }

        if (sletteHusstandsmedlemsperiode != null) {
            return TypeEndring.SLETTET
        }

        throw IllegalArgumentException(
            "Mangler data til å avgjøre endringstype. Motttok input: nyEllerOppdatertHusstandsmedlemperiode: " +
                "$nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemperiode: $sletteHusstandsmedlemsperiode",
        )
    }

    private fun Husstandsmedlem.resetTilOffentligePerioderAndreVoksneIHusstand() {
        behandling.grunnlag
            .henteNyesteGrunnlag(
                Grunnlagstype(
                    Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                    true,
                ),
                behandling.bidragspliktig!!,
            ).konvertereData<List<Bostatus>>()
            ?.let { overskriveAndreVoksneIHusstandMedBearbeidaPerioder(it) }
            ?: run {
                this.perioder.clear()
                if (kilde == Kilde.OFFENTLIG) {
                    this.perioder.add(opprettDefaultPeriodeForAndreVoksneIHusstand())
                    log.warn {
                        "Fant ikke originale bearbeidet perioder for offentlig husstandsmedlem $id i behandling ${behandling.id}. Lagt til initiell periode "
                    }
                }
            }
    }

    private fun Husstandsmedlem.resetTilOffentligePerioder() {
        hentSisteBearbeidetBoforhold()?.let { overskriveMedBearbeidaPerioder(it) }
            ?: run {
                this.perioder.clear()
                if (kilde == Kilde.OFFENTLIG) {
                    this.perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
                    log.warn {
                        "Fant ikke originale bearbeidet perioder for offentlig husstandsmedlem $id i behandling ${behandling.id}. Lagt til initiell periode "
                    }
                }
            }
    }

    private fun Husstandsmedlem.oppdaterePerioder(
        nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
        sletteHusstandsmedlemsperiode: Long? = null,
    ) {
        val endreBostatus = tilEndreBostatus(nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemsperiode)

        val periodiseringsrequest = tilBoforholdBarnRequest(endreBostatus)

        this.overskriveMedBearbeidaPerioder(
            BoforholdApi.beregnBoforholdBarnV3(
                behandling.virkningstidspunktEllerSøktFomDato,
                rolle?.opphørsdato ?: behandling.globalOpphørsdato,
                behandling.tilTypeBoforhold(),
                listOf(periodiseringsrequest),
            ),
        )
    }

    private fun Husstandsmedlem.oppdaterePerioderVoksne(
        gjelderRolle: Rolle,
        nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
        sletteHusstandsmedlemsperiode: Long? = null,
    ) {
        val endreBostatus = tilEndreBostatus(nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemsperiode)
        val periodiseringsrequest = tilBoforholdVoksneRequest(gjelderRolle, endreBostatus)

        val borMedAndreVoksneperioder =
            BoforholdApi.beregnBoforholdAndreVoksne(
                behandling.virkningstidspunktEllerSøktFomDato,
                periodiseringsrequest,
                behandling.globalOpphørsdato,
            )

        this.overskriveMedBearbeidaBostatusperioder(borMedAndreVoksneperioder)
    }

    private fun Husstandsmedlem.tilEndreBostatus(
        nyEllerOppdatertBostatusperiode: Bostatusperiode? = null,
        sletteHusstandsmedlemsperiode: Long? = null,
    ): EndreBostatus? {
        try {
            if (nyEllerOppdatertBostatusperiode == null && sletteHusstandsmedlemsperiode == null) {
                return null
            }

            return EndreBostatus(
                typeEndring = bestemmeEndringstype(nyEllerOppdatertBostatusperiode, sletteHusstandsmedlemsperiode),
                nyBostatus = bestemmeNyBostatus(nyEllerOppdatertBostatusperiode),
                originalBostatus =
                    bestemmeOriginalBostatus(
                        nyEllerOppdatertBostatusperiode,
                        sletteHusstandsmedlemsperiode,
                    ),
            )
        } catch (_: IllegalArgumentException) {
            log.warn {
                "Mottok mangelfulle opplysninger ved oppdatering av boforhold i behandling ${this.behandling.id}. " +
                    "Mottatt input: nyEllerOppdatertHusstandsmedlemsperiode=$nyEllerOppdatertBostatusperiode, " +
                    "sletteHusstansmedlemsperiode=$sletteHusstandsmedlemsperiode"
            }
            oppdateringAvBoforholdFeilet(
                "Oppdatering av boforhold i behandling ${this.behandling.id} feilet pga mangelfulle inputdata",
            )
        }
    }

    private fun bestemmeNyBostatus(nyEllerOppdatertBostatusperiode: Bostatusperiode? = null): Bostatus? =
        nyEllerOppdatertBostatusperiode?.let {
            Bostatus(
                periodeFom = it.datoFom,
                periodeTom = it.datoTom,
                bostatus = it.bostatus,
                kilde = it.kilde,
            )
        }

    private fun Husstandsmedlem.bestemmeOriginalBostatus(
        nyBostatusperiode: Bostatusperiode? = null,
        sletteHusstansmedlemsperiode: Long? = null,
    ): Bostatus? {
        nyBostatusperiode?.id?.let {
            return perioder.find { nyBostatusperiode.id == it.id }?.tilBostatus()
        }
        sletteHusstansmedlemsperiode.let { id -> return perioder.find { id == it.id }?.tilBostatus() }
    }

    private fun Behandling.gjenoppretteForrigeSivilstandshistorikk(rolle: Rolle) {
        val lagretHistorikk = rolle.henteLagretSivilstandshistorikk(this)
        rolle.lagreSivilstandshistorikk(this.sivilstand)
        this.sivilstand.clear()
        this.sivilstand.addAll(lagretHistorikk)
    }

    /**
     * Henter eksisterende boforholdsgrunnlag i gitt behandling for oppgitt personident. Setter relasjon til BARN.
     * Brukes til å hente evnt. husstandsmedlem som mangler relasjon til BM.
     */
    private fun Behandling.henteGrunnlagHusstandsmedlemMedHarkodetBmBpRelasjon(personident: Personident): Set<RelatertPersonGrunnlagDto> =
        this.grunnlag
            .filter { !it.erBearbeidet }
            .filter { it.aktiv != null }
            .filter { Grunnlagsdatatype.BOFORHOLD == it.type }
            .maxByOrNull { it.aktiv!! }
            .konvertereData<Set<RelatertPersonGrunnlagDto>>()
            ?.filter { personident.verdi == it.gjelderPersonId }
            ?.map { it.copy(relasjon = Familierelasjon.BARN) }
            ?.toSet() ?: emptySet()

    private fun lagreBearbeidaBoforholdsgrunnlag(
        behandling: Behandling,
        boforholdrespons: List<BoforholdResponseV2>,
        personidentBarn: Personident,
    ) {
        behandling.grunnlag.add(
            Grunnlag(
                behandling = behandling,
                type = Grunnlagsdatatype.BOFORHOLD,
                erBearbeidet = true,
                data = tilJson(boforholdrespons),
                innhentet = LocalDateTime.now(),
                aktiv = LocalDateTime.now(),
                rolle = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)!!,
                gjelder = personidentBarn.verdi,
            ),
        )
    }
}
