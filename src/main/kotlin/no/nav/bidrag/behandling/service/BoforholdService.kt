package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.finnHusstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.database.datamodell.henteLagretSivilstandshistorikk
import no.nav.bidrag.behandling.database.datamodell.henteSisteSivilstand
import no.nav.bidrag.behandling.database.datamodell.lagreSivilstandshistorikk
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.HusstandsbarnRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeiletException
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.boforhold.overskriveMedBearbeidaPerioder
import no.nav.bidrag.behandling.transformers.boforhold.overskriveMedBearbeidaSivilstandshistorikk
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdbBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatus
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilOppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.boforhold.tilPerioder
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandDto
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSvilstandRequest
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validere
import no.nav.bidrag.behandling.transformers.validereSivilstand
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequest
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.boforhold.dto.TypeEndring
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto

private val log = KotlinLogging.logger {}

@Service
class BoforholdService(
    private val behandlingRepository: BehandlingRepository,
    private val husstandsbarnRepository: HusstandsbarnRepository,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun oppdatereNotat(
        behandlingsid: Long,
        request: OppdaterNotat,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        behandling.boforholdsbegrunnelseKunINotat = request.kunINotat ?: behandling.boforholdsbegrunnelseKunINotat

        return OppdatereBoforholdResponse(
            oppdatertNotat = request,
            valideringsfeil =
                BoforholdValideringsfeil(
                    husstandsbarn =
                        behandling.husstandsbarn.validerBoforhold(behandling.virkningstidspunktEllerSøktFomDato)
                            .filter { it.harFeil },
                ),
        )
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvPeriodisertBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponse>,
    ) {
        behandling.husstandsbarn.filter { (Kilde.OFFENTLIG == it.kilde) }.forEach {
            sletteHusstandsbarn(behandling, it)
        }

        behandling.husstandsbarn.addAll(periodisertBoforhold.tilHusstandsbarn(behandling))
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponse>,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        overskriveManuelleOpplysninger: Boolean,
        gjelderHusstandsbarn: Personident,
    ) {
        val nyeHusstandsbarnMedPerioder = periodisertBoforhold.tilHusstandsbarn(behandling)
        // Ved overskriving bevares manuelle barn, men dersom manuelt barn med personident også finnes i grunnlag,
        // erstattes dette med offentlige opplysninger. Manuelle perioder til offisielle barn slettes.
        if (overskriveManuelleOpplysninger) {
            sletteOffentligeHusstandsbarnSomIkkeFinnesINyesteGrunnlag(
                behandling,
                bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
                gjelderHusstandsbarn,
            )
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsbarnMedPerioder,
            )
            leggeTilNyeEllerOppdatereEksisterendeOffentligeHusstandsbarn(behandling, nyeHusstandsbarnMedPerioder, true)
        } else {
            endreKildePåOffentligeBarnSomIkkeFinnesINyesteGrunnlag(
                behandling,
                bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
                gjelderHusstandsbarn,
            )
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsbarnMedPerioder,
            )
            leggeTilNyeEllerOppdatereEksisterendeOffentligeHusstandsbarn(behandling, nyeHusstandsbarnMedPerioder)
        }

        log.info {
            "Husstandsbarn ble oppdatert for behandling ${behandling.id} " +
                "med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
        }
        secureLogger.info {
            "Husstandsbarn ${gjelderHusstandsbarn.verdi} ble oppdatert for behandling ${behandling.id} " +
                "med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
        }
    }

    @Transactional
    fun rekalkulerOgLagreHusstandsmedlemPerioder(behandlingsid: Long) {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }
        val oppdaterHusstandsmedlemmer =
            behandling.husstandsbarn.map { husstandsbarn ->
                husstandsbarn.lagreEksisterendePerioder()
                husstandsbarn.oppdaterePerioder()
                husstandsbarnRepository.save(husstandsbarn)
            }
        behandling.husstandsbarn.clear()
        behandling.husstandsbarn.addAll(oppdaterHusstandsmedlemmer)
    }

    @Transactional
    fun oppdatereHusstandsbarnManuelt(
        behandlingsid: Long,
        oppdatereHusstandsmedlem: OppdatereHusstandsmedlem,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereHusstandsmedlem.validere(behandling)

        oppdatereHusstandsmedlem.slettHusstandsmedlem?.let { idHusstandsbarn ->
            val husstandsbarnSomSkalSlettes = behandling.husstandsbarn.find { idHusstandsbarn == it.id }
            if (Kilde.MANUELL == husstandsbarnSomSkalSlettes?.kilde) {
                behandling.husstandsbarn.remove(husstandsbarnSomSkalSlettes)
                loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsbarnSomSkalSlettes)
                return husstandsbarnSomSkalSlettes.tilOppdatereBoforholdResponse(behandling)
            }
        }

        oppdatereHusstandsmedlem.opprettHusstandsmedlem?.let { personalia ->
            val husstandsbarn =
                Husstandsbarn(
                    behandling,
                    Kilde.MANUELL,
                    ident = personalia.personident?.verdi,
                    fødselsdato = personalia.fødselsdato,
                    navn = personalia.navn,
                )

            husstandsbarn.oppdaterePerioder(
                nyEllerOppdatertHusstandsbarnperiode =
                    Husstandsbarnperiode(
                        husstandsbarn = husstandsbarn,
                        bostatus = Bostatuskode.MED_FORELDER,
                        datoFom = behandling.virkningstidspunktEllerSøktFomDato,
                        datoTom = null,
                        kilde = Kilde.MANUELL,
                    ),
            )
            behandling.husstandsbarn.add(husstandsbarn)

            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsbarn)
            return husstandsbarnRepository.save(husstandsbarn).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.slettPeriode?.let { idHusstandsbarnperiode ->
            val husstandsbarnperiodeSomSkalSlettes =
                behandling.finnHusstandsbarnperiode(idHusstandsbarnperiode)
            val husstandsbarn = husstandsbarnperiodeSomSkalSlettes!!.husstandsbarn
            husstandsbarn.lagreEksisterendePerioder()
            husstandsbarn.oppdaterePerioder(slettHusstandsbarnperiode = idHusstandsbarnperiode)

            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsbarn)
            return husstandsbarnRepository.save(husstandsbarn).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.oppdaterPeriode?.let { bostatusperiode ->

            val eksisterendeHusstandsbarn =
                behandling.husstandsbarn.find { it.id == bostatusperiode.idHusstandsbarn }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)

            eksisterendeHusstandsbarn.lagreEksisterendePerioder()

            eksisterendeHusstandsbarn.oppdaterePerioder(
                nyEllerOppdatertHusstandsbarnperiode =
                    Husstandsbarnperiode(
                        id = bostatusperiode.idPeriode,
                        husstandsbarn = eksisterendeHusstandsbarn,
                        bostatus = bostatusperiode.bostatus,
                        datoFom = bostatusperiode.datoFom,
                        datoTom = bostatusperiode.datoTom,
                        kilde = Kilde.MANUELL,
                    ),
            )

            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, eksisterendeHusstandsbarn)
            return husstandsbarnRepository.save(eksisterendeHusstandsbarn).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.tilbakestillPerioderForHusstandsmedlem?.let { husstandsmedlemId ->
            val husstandsmedlem =
                behandling.husstandsbarn.find { it.id == husstandsmedlemId }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)
            husstandsmedlem.lagreEksisterendePerioder()
            husstandsmedlem.resetTilOffentligePerioder()
            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return husstandsbarnRepository.save(husstandsmedlem).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.angreSisteStegForHusstandsmedlem?.let { husstandsmedlemId ->
            val husstandsmedlem =
                behandling.husstandsbarn.find { it.id == husstandsmedlemId }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)
            husstandsmedlem.oppdaterTilForrigeLagredePerioder()
            loggeEndringHusstandsmedlem(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return husstandsbarnRepository.save(husstandsmedlem).tilOppdatereBoforholdResponse(behandling)
        }
        oppdateringAvBoforholdFeilet("Oppdatering av boforhold feilet. Forespørsel mangler informasjon om hva som skal oppdateres")
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvPeriodisertSivilstand(
        behandling: Behandling,
        personident: Personident,
        periodisertSivilstand: SivilstandBeregnet,
    ) {
        behandling.sivilstand.removeAll(behandling.sivilstand.filter { Kilde.OFFENTLIG == it.kilde }.toSet())
        behandling.sivilstand.addAll(periodisertSivilstand.sivilstandListe.tilSivilstand(behandling))
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaSivilstand(
        behandling: Behandling,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val ikkeAktiverteGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()

        val nyesteIkkeaktivertePeriodiserteSivilstand =
            ikkeAktiverteGrunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.filter { it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        val nyesteIkkeaktiverteSivilstand =
            ikkeAktiverteGrunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.filter { !it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        if (nyesteIkkeaktivertePeriodiserteSivilstand == null || nyesteIkkeaktiverteSivilstand == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type SIVILSTAND å aktivere for  behandling $behandling.id",
            )
        }

        val data =
            when (overskriveManuelleOpplysninger) {
                true -> {
                    nyesteIkkeaktivertePeriodiserteSivilstand.aktiv = LocalDateTime.now()
                    jsonListeTilObjekt<SivilstandBeregnV2Dto>(nyesteIkkeaktivertePeriodiserteSivilstand.data)
                }

                false -> {
                    SivilstandApi.beregnV2(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        jsonListeTilObjekt<SivilstandGrunnlagDto>(nyesteIkkeaktiverteSivilstand.data).tilSivilstandRequest(
                            behandling.sivilstand,
                        ),
                    ).toSet()
                }
            }

        behandling.sivilstand.clear()
        behandling.sivilstand.addAll(
            data.tilSivilstand(behandling),
        )

        nyesteIkkeaktiverteSivilstand.aktiv = LocalDateTime.now()
        nyesteIkkeaktivertePeriodiserteSivilstand.aktiv = LocalDateTime.now()
    }

    @Transactional
    fun oppdatereSivilstandManuelt(
        behandlingsid: Long,
        oppdatereSivilstand: OppdatereSivilstand,
    ): OppdatereBoforholdResponse? {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereSivilstand.validere(behandling)

        oppdatereSivilstand.sletteSivilstandsperiode?.let { idSivilstandsperiode ->
            behandling.bidragsmottaker!!.lagreSivilstandshistorikk(behandling.sivilstand)
            behandling.oppdatereSivilstandshistorikk(sletteInnslag = idSivilstandsperiode)
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return OppdatereBoforholdResponse(
                oppdatertSivilstandshistorikk = behandling.sivilstand.tilSivilstandDto(),
                valideringsfeil =
                    BoforholdValideringsfeil(
                        sivilstand = behandling.sivilstand.validereSivilstand(behandling.virkningstidspunktEllerSøktFomDato),
                    ),
            )
        }
        oppdatereSivilstand.nyEllerEndretSivilstandsperiode?.let {
            val sivilstand = Sivilstand(behandling, it.fraOgMed, it.tilOgMed, it.sivilstand, Kilde.MANUELL)
            behandling.sivilstand.add(sivilstand)
            log.info { "Sivilstandsperiode (id ${sivilstand.id}) ble manuelt lagt til behandling $behandlingsid." }
            return behandling.sivilstand.tilOppdatereBoforholdResponse()
        }

        if (oppdatereSivilstand.angreSisteEndring) {
            behandling.gjenoppretteForrigeSivilstandshistorikk(behandling.bidragsmottaker!!)
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return behandling.sivilstand.tilOppdatereBoforholdResponse()
        } else if (oppdatereSivilstand.tilbakestilleHistorikk) {
            behandling.bidragsmottaker!!.lagreSivilstandshistorikk(behandling.sivilstand)
            behandling.tilbakestilleTilOffentligSivilstandshistorikk()
            loggeEndringSivilstand(behandling, oppdatereSivilstand, behandling.sivilstand)
            return behandling.sivilstand.tilOppdatereBoforholdResponse()
        }

        oppdateringAvBoforholdFeiletException(behandlingsid)
    }

    private fun sletteHusstandsbarn(
        behandling: Behandling,
        husstandsbarnSomSkalSlettes: Husstandsbarn,
    ) {
        husstandsbarnSomSkalSlettes.perioder.clear()
        sletteHusstandsbarn(behandling, setOf(husstandsbarnSomSkalSlettes))
        secureLogger.info {
            "Slettet $husstandsbarnSomSkalSlettes husstandsbarn fra behandling ${behandling.id} i " +
                "forbindelse med førstegangsoppdatering av boforhold."
        }
    }

    // Sikrer mot ConcurrentModificationException
    private fun sletteHusstandsbarn(
        behandling: Behandling,
        husstandsbarnSomSkalSlettes: Set<Husstandsbarn>,
    ) {
        behandling.husstandsbarn.removeAll(husstandsbarnSomSkalSlettes)
        log.info {
            "Slettet ${husstandsbarnSomSkalSlettes.size} husstandsbarn fra behandling ${behandling.id} i " +
                "forbindelse med førstegangsoppdatering av boforhold."
        }
    }

    private fun sletteOffentligeHusstandsbarnSomIkkeFinnesINyesteGrunnlag(
        behandling: Behandling,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        gjelderHusstandsbarn: Personident,
    ) {
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.any { it.verdi == gjelderHusstandsbarn.verdi }) return

        behandling.husstandsbarn.find { i -> Kilde.OFFENTLIG == i.kilde && i.ident == gjelderHusstandsbarn.verdi }
            ?.let { eksisterendeHusstandsbarn ->
                eksisterendeHusstandsbarn.perioder.clear()
                sletteHusstandsbarn(behandling, eksisterendeHusstandsbarn)
            }
    }

    private fun leggeTilNyeEllerOppdatereEksisterendeOffentligeHusstandsbarn(
        behandling: Behandling,
        nyttPeriodisertBoforhold: Set<Husstandsbarn>,
        overskriveManuelleOpplysninger: Boolean = false,
    ) {
        val husstandsbarnSomSkalOppdateres =
            behandling.husstandsbarn.asSequence().filter { i -> Kilde.OFFENTLIG == i.kilde }.toSet()

        nyttPeriodisertBoforhold.forEach { nyttHusstandsbarn ->
            // Oppdaterer eksisterende husstandsbarn. Sletter manuelle perioder. Kjører ny periodisering av offentlige perioder.
            if (husstandsbarnSomSkalOppdateres.map { it.ident }.contains(nyttHusstandsbarn.ident)) {
                val eksisterendeHusstandsbarn =
                    husstandsbarnSomSkalOppdateres.find { it.ident == nyttHusstandsbarn.ident }!!

                val oppdatertePerioder =
                    overskriveManuelleOpplysninger.ifTrue {
                        nyttHusstandsbarn.perioder.forEach {
                            it.husstandsbarn = eksisterendeHusstandsbarn
                        }
                        nyttHusstandsbarn.perioder
                    } // Kjør ny periodisering for å oppdatere kilde på periodene basert på nye opplysninger
                        ?: BoforholdApi.beregnBoforholdBarnV2(
                            behandling.virkningstidspunktEllerSøktFomDato,
                            listOf(
                                eksisterendeHusstandsbarn.tilBoforholdbBarnRequest(),
                            ),
                        ).tilPerioder(eksisterendeHusstandsbarn)

                eksisterendeHusstandsbarn.lagreEksisterendePerioder()
                eksisterendeHusstandsbarn.perioder.clear()
                eksisterendeHusstandsbarn.perioder.addAll(oppdatertePerioder)
                log.info {
                    "Oppdaterte husstandsbarn ${eksisterendeHusstandsbarn.id} i behandling ${behandling.id} med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
                }
                secureLogger.info {
                    "Oppdaterte husstandsbarn $eksisterendeHusstandsbarn i behandling ${behandling.id} med overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
                }
                // Legger nye offisielle husstandsbarn uten å kjøre ny periodisering
            } else {
                val nyHusstandsbarn = husstandsbarnRepository.save(nyttHusstandsbarn)
                behandling.husstandsbarn.add(nyHusstandsbarn)
                log.info { "Ny husstandsbarn ${nyHusstandsbarn.id} ble opprettett i behandling ${behandling.id}" }
                secureLogger.info { "Ny husstandsbarn $nyHusstandsbarn ble opprettett i behandling ${behandling.id}" }
            }
        }
    }

    private fun slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
        behandling: Behandling,
        nyttPeriodisertBoforhold: Set<Husstandsbarn>,
    ) {
        val manuelleBarnMedIdent =
            behandling.husstandsbarn.filter { Kilde.MANUELL == it.kilde }.filter { it.ident != null }
        val identerOffisielleBarn = nyttPeriodisertBoforhold.mapNotNull { it.ident }.toSet()

        manuelleBarnMedIdent.forEach { manueltBarn ->
            if (identerOffisielleBarn.contains(manueltBarn.ident)) {
                val offisieltBarn = nyttPeriodisertBoforhold.find { manueltBarn.ident == it.ident }

                log.info {
                    "Slår sammen manuelt husstandsbarn med id ${manueltBarn.id} med informasjon fra offentlige registre. Oppgraderer kilde til barnet til OFFENTLIG"
                }
                offisieltBarn!!.resetTilOffentligePerioder()
                val periodisertBoforhold =
                    BoforholdApi.beregnBoforholdBarnV2(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        listOf(
                            BoforholdBarnRequest(
                                relatertPersonPersonId = offisieltBarn.ident,
                                fødselsdato = offisieltBarn.fødselsdato,
                                erBarnAvBmBp = true,
                                innhentedeOffentligeOpplysninger =
                                    offisieltBarn.perioder.map { it.tilBostatus() }
                                        .sortedBy { it.periodeFom },
                                behandledeBostatusopplysninger = emptyList(),
                                endreBostatus = null,
                            ),
                        ),
                    )
                val hbp = periodisertBoforhold.tilPerioder(offisieltBarn)

                manueltBarn.perioder.clear()
                manueltBarn.perioder.addAll(hbp.toSet())
                manueltBarn.kilde = Kilde.OFFENTLIG
                nyttPeriodisertBoforhold.minus(offisieltBarn)
            }
        }
    }

    private fun endreKildePåOffentligeBarnSomIkkeFinnesINyesteGrunnlag(
        behandling: Behandling,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        gjelderHusstandsbarn: Personident,
    ) {
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.any { it.verdi == gjelderHusstandsbarn.verdi }) return

        behandling.husstandsbarn.find { Kilde.OFFENTLIG == it.kilde && it.ident == gjelderHusstandsbarn.verdi }
            ?.let { eksisterendeHusstandsbarn ->
                log.info { "Oppdaterer husstandsbarn ${eksisterendeHusstandsbarn.id} til kilde MANUELL i behandling ${behandling.id}" }
                secureLogger.info { "Oppdaterer husstandsbarn $eksisterendeHusstandsbarn til kilde MANUELL i behandling ${behandling.id}" }
                eksisterendeHusstandsbarn.kilde = Kilde.MANUELL
                eksisterendeHusstandsbarn.perioder.forEach { periode -> periode.kilde = Kilde.MANUELL }
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

        private fun Husstandsbarn.opprettDefaultPeriodeForOffentligHusstandsmedlem() =
            Husstandsbarnperiode(
                husstandsbarn = this,
                datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            )

        private fun Husstandsbarn.oppdaterTilForrigeLagredePerioder() {
            val lagredePerioder = commonObjectmapper.writeValueAsString(perioder)
            perioder.clear()
            perioder.addAll(hentForrigeLagredePerioder())
            forrigePerioder = lagredePerioder
        }

        private fun Husstandsbarn.hentForrigeLagredePerioder(): Set<Husstandsbarnperiode> {
            val forrigePerioder: Set<JsonNode> =
                commonObjectmapper.readValue(
                    forrigePerioder
                        ?: oppdateringAvBoforholdFeilet("Mangler forrige perioder for husstandsbarn $id i behandling ${behandling.id}"),
                )
            return forrigePerioder.map {
                Husstandsbarnperiode(
                    husstandsbarn = this,
                    datoFom = LocalDate.parse(it["datoFom"].textValue()),
                    datoTom = it["datoTom"]?.textValue()?.let { LocalDate.parse(it) },
                    bostatus = Bostatuskode.valueOf(it["bostatus"].textValue()),
                    kilde = Kilde.valueOf(it["kilde"].textValue()),
                )
            }.toSet()
        }

        fun Husstandsbarn.lagreEksisterendePerioder() {
            forrigePerioder = commonObjectmapper.writeValueAsString(perioder)
        }
    }

    private fun loggeEndringHusstandsmedlem(
        behandling: Behandling,
        oppdatereHusstandsmedlem: OppdatereHusstandsmedlem,
        husstandsbarn: Husstandsbarn,
    ) {
        val perioderDetaljer =
            husstandsbarn.perioder.map {
                "{ datoFom: ${it.datoFom}, datoTom: ${it.datoTom}, " +
                    "bostatus: ${it.bostatus}, kilde: ${it.kilde} }"
            }.joinToString(", ", prefix = "[", postfix = "]")
        oppdatereHusstandsmedlem.angreSisteStegForHusstandsmedlem?.let {
            log.info { "Angret siste steg for husstandsbarn ${husstandsbarn.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Angret siste steg for husstandsbarn ${husstandsbarn.id} i behandling ${behandling.id}. " +
                    "Gjeldende perioder etter endring: $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.tilbakestillPerioderForHusstandsmedlem?.let {
            log.info { "Tilbakestilte perioder for husstandsbarn ${husstandsbarn.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Tilbakestilte perioder for husstandsbarn ${husstandsbarn.id} i behandling ${behandling.id}." +
                    "Gjeldende perioder etter endring: $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.opprettHusstandsmedlem?.let { personalia ->
            log.info { "Nytt husstandsmedlem (id ${husstandsbarn.id}) ble manuelt lagt til behandling ${behandling.id}." }
        }
        oppdatereHusstandsmedlem.slettPeriode?.let { idHusstandsbarnperiode ->
            log.info { "Slettet husstandsbarnperiode med id $idHusstandsbarnperiode fra behandling ${behandling.id}." }
            secureLogger.info {
                "Slettet husstandsbarnperiode med id $idHusstandsbarnperiode fra behandling ${behandling.id}." +
                    "Gjeldende perioder etter endring: $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.oppdaterPeriode?.let { bostatusperiode ->
            val detaljer =
                "datoFom: ${bostatusperiode.datoFom}, datoTom: ${bostatusperiode.datoTom}, " +
                    "bostatus: ${bostatusperiode.bostatus}"
            if (bostatusperiode.idPeriode != null) {
                log.info {
                    "Oppdaterte periode ${bostatusperiode.idPeriode} for husstandsbarn ${bostatusperiode.idHusstandsbarn} til $detaljer " +
                        " i behandling ${behandling.id}"
                }
            } else {
                log.info {
                    "Ny periode $detaljer ble lagt til husstandsbarn ${bostatusperiode.idHusstandsbarn} i behandling med " +
                        "${behandling.id}."
                }
            }
        }
        oppdatereHusstandsmedlem.slettHusstandsmedlem?.let { idHusstandsbarn ->
            log.info { "Slettet husstandsbarn med id $idHusstandsbarn fra behandling ${behandling.id}." }
        }
    }

    private fun loggeEndringSivilstand(
        behandling: Behandling,
        oppdatereSivilstand: OppdatereSivilstand,
        historikk: Set<Sivilstand>,
    ) {
        val historikkstreng =
            historikk.map {
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
        oppdatereSivilstand.tilbakestilleHistorikk?.let {
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
        val manuelleInnslag =
            (
                this.sivilstand.filter { Kilde.MANUELL == it.kilde }.filter { it.id != sletteInnslag } +
                    nyttEllerEndretInnslag?.tilSivilstand(this)
            ).filterNotNull().toMutableSet()

        this.tilbakestilleTilOffentligSivilstandshistorikk()
        val historikkTilPeriodisering = this.sivilstand + manuelleInnslag

        this.overskriveMedBearbeidaSivilstandshistorikk(
            SivilstandApi.beregnV2(
                this.virkningstidspunktEllerSøktFomDato,
                historikkTilPeriodisering.tilSvilstandRequest(),
            ).toSet(),
        )
    }

    private fun bestemmeEndringstype(
        nyEllerOppdatertHusstandsbarnperiode: Husstandsbarnperiode? = null,
        sletteHusstandsbarnperiode: Long? = null,
    ): TypeEndring {
        nyEllerOppdatertHusstandsbarnperiode?.let {
            if (it.id != null) {
                return TypeEndring.ENDRET
            }
            return TypeEndring.NY
        }

        if (sletteHusstandsbarnperiode != null) {
            return TypeEndring.SLETTET
        }

        throw IllegalArgumentException(
            "Mangler data til å avgjøre endringstype. Motttok input: nyEllerOppdatertHusstandsbarnperiode: " +
                "$nyEllerOppdatertHusstandsbarnperiode, sletteHusstandsbarnperiode: $sletteHusstandsbarnperiode",
        )
    }

    private fun Husstandsbarn.resetTilOffentligePerioder() {
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

    private fun Husstandsbarn.oppdaterePerioder(
        nyEllerOppdatertHusstandsbarnperiode: Husstandsbarnperiode? = null,
        slettHusstandsbarnperiode: Long? = null,
    ) {
        val endreBostatus = tilEndreBostatus(nyEllerOppdatertHusstandsbarnperiode, slettHusstandsbarnperiode)

        val periodiseringsrequest = tilBoforholdbBarnRequest(endreBostatus)

        this.overskriveMedBearbeidaPerioder(
            BoforholdApi.beregnBoforholdBarnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                listOf(periodiseringsrequest),
            ),
        )
    }

    private fun Husstandsbarn.tilEndreBostatus(
        nyEllerOppdatertHusstandsbarnperiode: Husstandsbarnperiode? = null,
        sletteHusstandsbarnperiode: Long? = null,
    ): EndreBostatus? {
        try {
            if (nyEllerOppdatertHusstandsbarnperiode == null && sletteHusstandsbarnperiode == null) {
                return null
            }

            return EndreBostatus(
                typeEndring = bestemmeEndringstype(nyEllerOppdatertHusstandsbarnperiode, sletteHusstandsbarnperiode),
                nyBostatus = bestemmeNyBostatus(nyEllerOppdatertHusstandsbarnperiode),
                originalBostatus =
                    bestemmeOriginalBostatus(
                        nyEllerOppdatertHusstandsbarnperiode,
                        sletteHusstandsbarnperiode,
                    ),
            )
        } catch (illegalArgumentException: IllegalArgumentException) {
            log.warn {
                "Mottok mangelfulle opplysninger ved oppdatering av boforhold i behandling ${this.behandling.id}. " +
                    "Mottatt input: nyEllerOppdatertHusstandsbarnperiode=$nyEllerOppdatertHusstandsbarnperiode, " +
                    "sletteHusstandsbarnperiode=$sletteHusstandsbarnperiode"
            }
            oppdateringAvBoforholdFeilet(
                "Oppdatering av boforhold i behandling ${this.behandling.id} feilet pga mangelfulle inputdata",
            )
        }
    }

    private fun bestemmeNyBostatus(nyEllerOppdatertHusstandsbarnperiode: Husstandsbarnperiode? = null): Bostatus? {
        return nyEllerOppdatertHusstandsbarnperiode?.let {
            Bostatus(
                periodeFom = it.datoFom,
                periodeTom = it.datoTom,
                bostatusKode = it.bostatus,
                kilde = it.kilde,
            )
        }
    }

    private fun Husstandsbarn.bestemmeOriginalBostatus(
        nyHusstandsbarnperiode: Husstandsbarnperiode? = null,
        sletteHusstandsbarnperiode: Long? = null,
    ): Bostatus? {
        nyHusstandsbarnperiode?.id?.let {
            return perioder.find { nyHusstandsbarnperiode.id == it.id }?.tilBostatus()
        }
        sletteHusstandsbarnperiode.let { id -> return perioder.find { id == it.id }?.tilBostatus() }
    }

    private fun Behandling.gjenoppretteForrigeSivilstandshistorikk(rolle: Rolle) {
        rolle.lagreSivilstandshistorikk(this.sivilstand)
        this.sivilstand.clear()
        this.sivilstand.addAll(rolle.henteLagretSivilstandshistorikk())
    }

    private fun Sivilstandsperiode.tilSivilstand(behandling: Behandling) =
        Sivilstand(
            behandling = behandling,
            kilde = Kilde.MANUELL,
            sivilstand = sivilstand,
            datoFom = fraOgMed,
            datoTom = tilOgMed,
        )
}
