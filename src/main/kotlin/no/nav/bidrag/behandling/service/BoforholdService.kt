package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.finnHusstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.HusstandsbarnRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeiletException
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatus
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilDto
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilOppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandGrunnlagDto
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
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Service
class BoforholdService(
    private val behandlingRepository: BehandlingRepository,
    private val bidragPersonConsumer: BidragPersonConsumer,
    private val husstandsbarnRepository: HusstandsbarnRepository,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun oppdatereNotat(
        behandlingsid: Long,
        request: OppdaterNotat,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        behandling.inntektsbegrunnelseKunINotat = request.kunINotat ?: behandling.inntektsbegrunnelseKunINotat
        behandling.inntektsbegrunnelseIVedtakOgNotat =
            request.medIVedtaket ?: behandling.inntektsbegrunnelseIVedtakOgNotat

        return OppdatereBoforholdResponse(
            oppdatertNotat = request,
            valideringsfeil = BoforholdValideringsfeil(husstandsbarn = emptyList()),
        )
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvPeriodisertBoforhold(
        behandling: Behandling,
        personident: Personident,
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
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val nyeHusstandsbarnMedPerioder = periodisertBoforhold.tilHusstandsbarn(behandling)
        // Ved overskriving bevares manuelle barn, men dersom manuelt barn med personident også finnes i grunnlag,
        // erstattes dette med offentlige opplysninger. Manuelle perioder til offisielle barn slettes.
        if (overskriveManuelleOpplysninger) {
            sletteOffentligeHusstandsbarnSomIkkeFinnesINyesteGrunnlag(behandling, nyeHusstandsbarnMedPerioder)
            slåSammenHusstandsmedlemmmerSomEksistererBådeSomManuelleOgOffentlige(
                behandling,
                nyeHusstandsbarnMedPerioder,
                true,
            )
            leggeTilNyeEllerOppdatereEksisterendeOffentligeHusstandsbarn(behandling, nyeHusstandsbarnMedPerioder, true)
        } else {
            endreKildePåOffentligeBarnSomIkkeFinnesINyesteGrunnlag(behandling, nyeHusstandsbarnMedPerioder)
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
            husstandsbarn.perioder.add(
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
            husstandsmedlem.oppdaterTilOriginalePerioder()
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
        oppdateringAvBoforholdFeiletException(behandlingsid)
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
        val manuellePerioder =
            (perioder.filter { it.kilde == Kilde.MANUELL && it.id != slettHusstandsbarnperiode } + nyHusstandsbarnperiode).filterNotNull()
        oppdaterTilOriginalePerioder()
        val perioderTilPeriodsering = (perioder + manuellePerioder).tilBoforholdRequest(this)
        BoforholdApi.beregnV2(
            behandling.virkningstidspunktEllerSøktFomDato,
            listOf(perioderTilPeriodsering),
        ).tilHusstandsbarn(behandling, this)
    }

    private fun Husstandsbarn.oppdaterTilOriginalePerioder() {
        hentSisteBearbeidetBoforhold()
            ?: oppdateringAvBoforholdFeilet("Fant ikke originale bearbeidet perioder for husstandsbarn $id i behandling ${behandling.id}")
    }

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
        periodisertSivilstand: SivilstandBeregnet,
    ) {
        val sivilstand = periodisertSivilstand.tilSivilstand(behandling)

        behandling.sivilstand.removeAll(
            behandling.sivilstand.asSequence().filter { s -> Kilde.OFFENTLIG == s.kilde }
                .toSet(),
        )
        behandling.sivilstand.addAll(sivilstand)
        log.info { "Sivilstand fra offentlige kilder ble oppdatert for behandling ${behandling.id}" }
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
            val periodisertSivilstand =
                SivilstandApi.beregnV1(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.sivilstand.tilSivilstandGrunnlagDto(),
                )

            log.info { "Slettet sivilstand med id $idSivilstandsperiode fra behandling $behandlingsid." }
            return OppdatereBoforholdResponse(
                oppdatertSivilstand = periodeSomSkalSlettes!!.tilDto(),
                // Ingen grunn til å validere slettet periode
                valideringsfeil = BoforholdValideringsfeil(),
            )
        }

        oppdatereSivilstand.leggeTilSivilstandsperiode?.let {
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
        nyttPeriodisertBoforhold: Set<Husstandsbarn>,
    ) {
        var husstandsbarnSomSkalSlettes: Set<Husstandsbarn> = emptySet()

        behandling.husstandsbarn.asSequence().filter { i -> Kilde.OFFENTLIG == i.kilde }
            .forEach { eksisterendeHusstandsbarn ->

                val eksisterendeHusstandsbarnOppdateres =
                    nyttPeriodisertBoforhold.map { it.ident }.contains(eksisterendeHusstandsbarn.ident)

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
        val identerOffisielleBarn =
            nyttPeriodisertBoforhold.mapNotNull { it.ident }.toSet()

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
        nyttPeriodisertBoforhold: Set<Husstandsbarn>,
    ) {
        behandling.husstandsbarn.filter { Kilde.OFFENTLIG == it.kilde }.forEach { eksisterendeHusstandsbarn ->
            if (!nyttPeriodisertBoforhold.map { it.ident }.contains(eksisterendeHusstandsbarn.ident)) {
                eksisterendeHusstandsbarn.kilde = Kilde.MANUELL
                eksisterendeHusstandsbarn.perioder.forEach { periode -> periode.kilde = Kilde.MANUELL }
            }
        }
    }
}
