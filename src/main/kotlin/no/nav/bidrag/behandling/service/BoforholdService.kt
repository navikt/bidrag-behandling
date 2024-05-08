package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.finnHusstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.HusstandsbarnRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeiletException
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.boforhold.overskriveMedBearbeidaPerioder
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatus
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilDto
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilOppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validere
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
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

        behandling.inntektsbegrunnelseKunINotat = request.kunINotat ?: behandling.inntektsbegrunnelseKunINotat

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
        entityManager.flush()
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponse>,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val nyeHusstandsbarnMedPerioder = periodisertBoforhold.tilHusstandsbarn(behandling)
        // Ved overskriving bevares manuelle barn, men dersom manuelt barn med personident også finnes i grunnlag,
        // erstattes dette med offentlige opplysninger. Manuelle perioder til offisielle barn slettes.
        if (overskriveManuelleOpplysninger) {
            sletteOffentligeHusstandsbarnSomIkkeFinnesINyesteGrunnlag(
                behandling,
                bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
            )
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsbarnMedPerioder,
                true,
            )
            leggeTilNyeEllerOppdatereEksisterendeOffentligeHusstandsbarn(behandling, nyeHusstandsbarnMedPerioder, true)
        } else {
            endreKildePåOffentligeBarnSomIkkeFinnesINyesteGrunnlag(
                behandling,
                bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
            )
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsbarnMedPerioder,
            )
            leggeTilNyeEllerOppdatereEksisterendeOffentligeHusstandsbarn(behandling, nyeHusstandsbarnMedPerioder)
        }

        log.info { "Husstandsbarn ble oppdatert for behandling ${behandling.id}" }
    }

    @Transactional
    fun rekalkulerOgLagreHusstandsmedlemPerioder(behandlingsid: Long) {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }
        val oppdaterHusstandsmedlemmer =
            behandling.husstandsbarn.map { husstandsbarn ->
                husstandsbarn.lagreEksisterendePerioder()
                husstandsbarn.oppdaterPerioder()
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
                logEndring(behandling, oppdatereHusstandsmedlem, husstandsbarnSomSkalSlettes)
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
            husstandsbarn.oppdaterPerioder(
                nyHusstandsbarnperiode =
                Husstandsbarnperiode(
                    husstandsbarn = husstandsbarn,
                    bostatus = Bostatuskode.MED_FORELDER,
                    datoFom = behandling.virkningstidspunktEllerSøktFomDato,
                    datoTom = null,
                    kilde = Kilde.MANUELL,
                ),
            )
            behandling.husstandsbarn.add(husstandsbarn)

            logEndring(behandling, oppdatereHusstandsmedlem, husstandsbarn)
            return husstandsbarnRepository.save(husstandsbarn).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.slettPeriode?.let { idHusstandsbarnperiode ->
            val husstandsbarnperiodeSomSkalSlettes =
                behandling.finnHusstandsbarnperiode(idHusstandsbarnperiode)
            val husstandsbarn = husstandsbarnperiodeSomSkalSlettes!!.husstandsbarn
            husstandsbarn.lagreEksisterendePerioder()
            husstandsbarn.oppdaterPerioder(slettHusstandsbarnperiode = idHusstandsbarnperiode)

            logEndring(behandling, oppdatereHusstandsmedlem, husstandsbarn)
            return husstandsbarnRepository.save(husstandsbarn).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.oppdaterPeriode?.let { bostatusperiode ->

            val eksisterendeHusstandsbarn =
                behandling.husstandsbarn.find { it.id == bostatusperiode.idHusstandsbarn }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)

            eksisterendeHusstandsbarn.lagreEksisterendePerioder()

            if (bostatusperiode.idPeriode != null) {
                eksisterendeHusstandsbarn.perioder.remove(behandling.finnHusstandsbarnperiode(bostatusperiode.idPeriode))
            }

            eksisterendeHusstandsbarn.oppdaterPerioder(
                nyHusstandsbarnperiode =
                Husstandsbarnperiode(
                    husstandsbarn = eksisterendeHusstandsbarn,
                    bostatus = bostatusperiode.bostatus,
                    datoFom = bostatusperiode.datoFom,
                    datoTom = bostatusperiode.datoTom,
                    kilde = Kilde.MANUELL,
                ),
            )

            logEndring(behandling, oppdatereHusstandsmedlem, eksisterendeHusstandsbarn)
            return husstandsbarnRepository.save(eksisterendeHusstandsbarn).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.tilbakestillPerioderForHusstandsmedlem?.let { husstandsmedlemId ->
            val husstandsmedlem =
                behandling.husstandsbarn.find { it.id == husstandsmedlemId }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)
            husstandsmedlem.lagreEksisterendePerioder()
            husstandsmedlem.resetTilOffentligePerioder()
            logEndring(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return husstandsbarnRepository.save(husstandsmedlem).tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsmedlem.angreSisteStegForHusstandsmedlem?.let { husstandsmedlemId ->
            val husstandsmedlem =
                behandling.husstandsbarn.find { it.id == husstandsmedlemId }
                    ?: oppdateringAvBoforholdFeiletException(behandlingsid)
            husstandsmedlem.oppdaterTilForrigeLagredePerioder()
            logEndring(behandling, oppdatereHusstandsmedlem, husstandsmedlem)
            return husstandsbarnRepository.save(husstandsmedlem).tilOppdatereBoforholdResponse(behandling)
        }
        oppdateringAvBoforholdFeilet("Oppdatering av boforhold feilet. Forespørsel mangler informasjon om hva som skal oppdateres")
    }

    private fun logEndring(
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
                        "Perioder = $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.tilbakestillPerioderForHusstandsmedlem?.let {
            log.info { "Tilbakestilte perioder for husstandsbarn ${husstandsbarn.id} i behandling ${behandling.id}." }
            secureLogger.info {
                "Tilbakestilte perioder for husstandsbarn ${husstandsbarn.id} i behandling ${behandling.id}." +
                        "Perioder = $perioderDetaljer"
            }
        }
        oppdatereHusstandsmedlem.opprettHusstandsmedlem?.let { personalia ->
            log.info { "Nytt husstandsmedlem (id ${husstandsbarn.id}) ble manuelt lagt til behandling ${behandling.id}." }
        }
        oppdatereHusstandsmedlem.slettPeriode?.let { idHusstandsbarnperiode ->
            log.info { "Slettet husstandsbarnperiode med id $idHusstandsbarnperiode fra behandling ${behandling.id}." }
            secureLogger.info {
                "Slettet husstandsbarnperiode med id $idHusstandsbarnperiode fra behandling ${behandling.id}." +
                        "Perioder = $perioderDetaljer"
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

    private fun Husstandsbarn.oppdaterPerioder(
        nyHusstandsbarnperiode: Husstandsbarnperiode? = null,
        slettHusstandsbarnperiode: Long? = null,
    ) {
        val virkningstidspunkt = behandling.virkningstidspunktEllerSøktFomDato
        val kanIkkeVæreSenereEnnDato =
            if (virkningstidspunkt.isAfter(LocalDate.now())) {
                maxOf(this.fødselsdato, virkningstidspunkt.withDayOfMonth(1))
            } else {
                LocalDate.now().withDayOfMonth(1)
            }
        val manuellePerioder =
            (perioder.filter { it.kilde == Kilde.MANUELL && it.id != slettHusstandsbarnperiode } + nyHusstandsbarnperiode).filterNotNull()
        // Boforhold beregning V2 forventer originale offfentlige perioder som input sammen med manuelle perioder.
        // Resetter derfor til offentlige perioder før de settes sammen med manuelle perioder
        this.resetTilOffentligePerioder()
        val perioderTilPeriodsering =
            (perioder + manuellePerioder).filter {
                it.datoFom?.isBefore(kanIkkeVæreSenereEnnDato) == true || it.datoFom?.isEqual(kanIkkeVæreSenereEnnDato) == true
            }.toSet().tilBoforholdRequest(this)
        this.overskriveMedBearbeidaPerioder(
            BoforholdApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                listOf(perioderTilPeriodsering),
            ),
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

    @Transactional
    fun lagreFørstegangsinnhentingAvPeriodisertSivilstand(
        behandling: Behandling,
        personident: Personident,
        periodisertSivilstand: SivilstandBeregnet,
    ) {
        behandling.sivilstand.removeAll(behandling.sivilstand.filter { Kilde.OFFENTLIG == it.kilde }.toSet())
        behandling.sivilstand.addAll(periodisertSivilstand.sivilstandListe.tilSivilstand(behandling))
        entityManager.flush()
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaSivilstand(
        behandling: Behandling,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val ikkeAktiverteGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()

        val nyesteIkkeAktivertePeriodisertSivilstand =
            ikkeAktiverteGrunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.filter { it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        val nyesteIkkeAktiverteSivilstand =
            ikkeAktiverteGrunnlag.filter { Grunnlagsdatatype.SIVILSTAND == it.type }.filter { !it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        if (nyesteIkkeAktivertePeriodisertSivilstand == null || nyesteIkkeAktiverteSivilstand == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type SIVILSTAND å aktivere for  behandling $behandling.id",
            )
        }

        val data = when (overskriveManuelleOpplysninger) {
            true -> {
                nyesteIkkeAktivertePeriodisertSivilstand.aktiv = LocalDateTime.now()
                jsonListeTilObjekt<SivilstandBeregnV2Dto>(nyesteIkkeAktivertePeriodisertSivilstand.data)
            }
            false -> {

                SivilstandApi.beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    jsonListeTilObjekt<SivilstandGrunnlagDto>(nyesteIkkeAktiverteSivilstand.data).tilSivilstandRequest()
                ).toSet()
            }
        }

        behandling.sivilstand.clear()
        behandling.sivilstand.addAll(
            data.tilSivilstand(behandling)
        )

        nyesteIkkeAktiverteSivilstand.aktiv = LocalDateTime.now()
        nyesteIkkeAktivertePeriodisertSivilstand.aktiv = LocalDateTime.now()
        entityManager.flush()
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
            val periodeSomSkalSlettes = behandling.sivilstand.find { idSivilstandsperiode == it.id }
            behandling.sivilstand.remove(periodeSomSkalSlettes)

            // TODO: Fikse etter  merge
            //    val nyesteAktiveSivilstandsgrunnlag =  grunnlagRepository.findTopByBehandlingIdAndTypeAndErBearbeidetAndRolleOrderByAktivDescIdDesc(
            //       behandling.id!!,Grunnlagsdatatype.SIVILSTAND, false, behandling.bidragsmottaker!!)

            val nyesteAktiveSivilstandsgrunnlag = behandling.grunnlag.first()

            val data = jsonListeTilObjekt<SivilstandGrunnlagDto>(nyesteAktiveSivilstandsgrunnlag.data)

            val periodisertSivilstand =
                SivilstandApi.beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    data.tilSivilstandRequest(),
                )

            log.info { "Slettet sivilstand med id $idSivilstandsperiode fra behandling $behandlingsid." }
            return OppdatereBoforholdResponse(
                oppdatertSivilstand = periodeSomSkalSlettes!!.tilDto(),
                // Ingen grunn til å validere slettet periode
                valideringsfeil = BoforholdValideringsfeil(),
            )
        }

        oppdatereSivilstand.nyEllerEndretSivilstandsperiode?.let {
            val sivilstand = Sivilstand(behandling, it.fraOgMed, it.tilOgMed, it.sivilstand, Kilde.MANUELL)
            behandling.sivilstand.add(sivilstand)
            entityManager.flush()
            log.info { "Sivilstandsperiode (id ${sivilstand.id}) ble manuelt lagt til behandling $behandlingsid." }
            return sivilstand.tilOppdatereBoforholdResponse(behandling.virkningstidspunktEllerSøktFomDato)
        }

        oppdateringAvBoforholdFeiletException(behandlingsid)
    }

    private fun sletteHusstandsbarn(
        behandling: Behandling,
        husstandsbarnSomSkalSlettes: Husstandsbarn,
    ) {
        husstandsbarnSomSkalSlettes.perioder.clear()
        sletteHusstandsbarn(behandling, setOf(husstandsbarnSomSkalSlettes))
    }

    // Sikrer mot ConcurrentModificationException
    private fun sletteHusstandsbarn(
        behandling: Behandling,
        husstandsbarnSomSkalSlettes: Set<Husstandsbarn>,
    ) {
        behandling.husstandsbarn.removeAll(husstandsbarnSomSkalSlettes)
        entityManager.flush()
        log.info {
            "Slettet ${husstandsbarnSomSkalSlettes.size} husstandsbarn fra behandling ${behandling.id} i " +
                    "forbindelse med førstegangsoppdatering av boforhold."
        }
    }

    private fun sletteOffentligeHusstandsbarnSomIkkeFinnesINyesteGrunnlag(
        behandling: Behandling,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
    ) {
        var husstandsbarnSomSkalSlettes: Set<Husstandsbarn> = emptySet()

        behandling.husstandsbarn.asSequence().filter { i -> Kilde.OFFENTLIG == i.kilde }
            .forEach { eksisterendeHusstandsbarn ->

                val eksisterendeHusstandsbarnOppdateres =
                    bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.map { it.verdi }
                        .contains(eksisterendeHusstandsbarn.ident)

                if (!eksisterendeHusstandsbarnOppdateres) {
                    eksisterendeHusstandsbarn.perioder.clear()
                    husstandsbarnSomSkalSlettes = husstandsbarnSomSkalSlettes.plus(eksisterendeHusstandsbarn)
                }
            }
        if (husstandsbarnSomSkalSlettes.isNotEmpty()) {
            sletteHusstandsbarn(behandling, husstandsbarnSomSkalSlettes)
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
            // Oppdaterer eksisterende husstandsbarn. Kjører ny periodisering med manuelle og offentlige perioder.
            if (husstandsbarnSomSkalOppdateres.map { it.ident }.contains(nyttHusstandsbarn.ident)) {
                val eksisterendeHusstandsbarn =
                    husstandsbarnSomSkalOppdateres.find { it.ident == nyttHusstandsbarn.ident }!!
                val manuellePerioder = eksisterendeHusstandsbarn.perioder.filter { it.kilde == Kilde.MANUELL }.toSet()

                val manuelleBostatuser = manuellePerioder.map { it.tilBostatus() }
                val offentligeBostatuser = nyttHusstandsbarn.perioder.map { it.tilBostatus() }
                val requestManuelleOgOffentlige =
                    offentligeBostatuser.plus(manuelleBostatuser).tilBostatusRequest(eksisterendeHusstandsbarn)

                val husstandsbarnperioder =
                    when (overskriveManuelleOpplysninger) {
                        false ->
                            BoforholdApi.beregnV2(
                                behandling.virkningstidspunktEllerSøktFomDato,
                                listOf(requestManuelleOgOffentlige),
                            ).tilHusstandsbarn(behandling).first().perioder

                        true -> nyttHusstandsbarn.perioder
                    }
                eksisterendeHusstandsbarn.perioder.clear()
                husstandsbarnperioder.forEach {
                    it.husstandsbarn = eksisterendeHusstandsbarn
                    entityManager.persist(it)
                    eksisterendeHusstandsbarn.perioder.add(it)
                }
                // Legger nye offisielle husstandsbarn uten å kjøre ny periodisering
            } else {
                entityManager.persist(nyttHusstandsbarn)
                behandling.husstandsbarn.add(nyttHusstandsbarn)
            }
        }
    }

    private fun slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
        behandling: Behandling,
        nyttPeriodisertBoforhold: Set<Husstandsbarn>,
        sletteManuellePerioder: Boolean = false,
    ) {
        val manuelleBarnMedIdent =
            behandling.husstandsbarn.filter { Kilde.MANUELL == it.kilde }.filter { it.ident != null }
        val identerOffisielleBarn = nyttPeriodisertBoforhold.mapNotNull { it.ident }.toSet()

        manuelleBarnMedIdent.forEach { manueltBarn ->
            if (identerOffisielleBarn.contains(manueltBarn.ident)) {
                val offisieltBarn = nyttPeriodisertBoforhold.find { manueltBarn.ident == it.ident }
                val bostatuser: List<Bostatus>? =
                    when (sletteManuellePerioder) {
                        false ->
                            offisieltBarn?.perioder?.map { it.tilBostatus() }
                                ?.plus(manueltBarn.perioder.map { it.tilBostatus() })

                        true -> offisieltBarn?.perioder?.map { it.tilBostatus() }
                    }

                bostatuser?.let {
                    val periodisertBoforhold =
                        BoforholdApi.beregnV2(
                            behandling.virkningstidspunktEllerSøktFomDato,
                            listOf(bostatuser.tilBostatusRequest(offisieltBarn!!)),
                        )
                    val hbp =
                        periodisertBoforhold.tilHusstandsbarn(offisieltBarn.behandling)
                            .flatMap { it.perioder }

                    manueltBarn.perioder.clear()
                    manueltBarn.perioder.addAll(hbp.toSet())
                    manueltBarn.kilde = Kilde.OFFENTLIG
                    nyttPeriodisertBoforhold.minus(offisieltBarn)
                }
            }
        }
    }

    private fun endreKildePåOffentligeBarnSomIkkeFinnesINyesteGrunnlag(
        behandling: Behandling,
        bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting: Set<Personident>,
    ) {
        behandling.husstandsbarn.filter { Kilde.OFFENTLIG == it.kilde }.forEach { eksisterendeHusstandsbarn ->
            if (!bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.map { it.verdi }
                    .contains(eksisterendeHusstandsbarn.ident)
            ) {
                eksisterendeHusstandsbarn.kilde = Kilde.MANUELL
                eksisterendeHusstandsbarn.perioder.forEach { periode -> periode.kilde = Kilde.MANUELL }
            }
        }
    }
}
