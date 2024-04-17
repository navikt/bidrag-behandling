package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.HusstandsbarnperiodeRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsbarn
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeiletException
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilDto
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilOppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandGrunnlagDto
import no.nav.bidrag.behandling.transformers.validere
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class BoforholdService(
    private val behandlingRepository: BehandlingRepository,
    private val bidragPersonConsumer: BidragPersonConsumer,
    private val husstandsbarnperiodeRepository: HusstandsbarnperiodeRepository,
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

        behandling.husstandsbarn.addAll(periodisertBoforhold.tilHusstandsbarn(behandling, bidragPersonConsumer))
        entityManager.flush()
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaBoforhold(
        behandling: Behandling,
        periodisertBoforhold: List<BoforholdResponse>,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val nyeHusstandsbarnMedPerioder = periodisertBoforhold.tilHusstandsbarn(behandling, bidragPersonConsumer)
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
    fun oppdatereHusstandsbarnManuelt(
        behandlingsid: Long,
        oppdatereHusstandsbarn: OppdatereHusstandsbarn,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereHusstandsbarn.validere(behandling, husstandsbarnperiodeRepository)

        oppdatereHusstandsbarn.sletteHusstandsbarn?.let { idHusstandsbarn ->
            val husstandsbarnSomSkalSlettes = behandling.husstandsbarn.find { idHusstandsbarn == it.id }
            if (Kilde.MANUELL == husstandsbarnSomSkalSlettes?.kilde) {
                behandling.husstandsbarn.remove(husstandsbarnSomSkalSlettes)
                log.info { "Slettet husstandsbarn med id $idHusstandsbarn fra behandling $behandlingsid." }
                return husstandsbarnSomSkalSlettes.tilOppdatereBoforholdResponse(behandling)
            }
        }

        oppdatereHusstandsbarn.nyttHusstandsbarn?.let { personalia ->
            val husstandsbarn =
                Husstandsbarn(
                    behandling,
                    Kilde.MANUELL,
                    ident = personalia.personident?.verdi,
                    fødselsdato = personalia.fødselsdato,
                    navn = personalia.navn,
                )
            behandling.husstandsbarn.add(husstandsbarn)
            entityManager.flush()
            log.info { "Nytt husstandsbarn (id ${husstandsbarn.id}) ble manuelt lagt til behandling $behandlingsid." }
            return husstandsbarn.tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsbarn.sletteHusstandsbarnperiode?.let { idHusstandsbarnperiode ->
            val husstandsbarnperiodeSomSkalSlettes = husstandsbarnperiodeRepository.findById(idHusstandsbarnperiode)
            husstandsbarnperiodeRepository.delete(husstandsbarnperiodeSomSkalSlettes.get())
            log.info { "Slettet husstandsbarnperiode med id $idHusstandsbarnperiode fra behandling $behandlingsid." }
            return husstandsbarnperiodeSomSkalSlettes.get().tilOppdatereBoforholdResponse(behandling)
        }

        oppdatereHusstandsbarn.nyHusstandsbarnperiode?.let { bostatusperiode ->

            val eksisterendeHusstandsbarn =
                behandling.husstandsbarn.find { it.id != null && it.id == bostatusperiode.idHusstandsbarn }

            val periode =
                Husstandsbarnperiode(
                    husstandsbarn = eksisterendeHusstandsbarn!!,
                    bostatus = bostatusperiode.bostatus,
                    datoFom = bostatusperiode.fraOgMed,
                    datoTom = bostatusperiode.tilOgMed,
                    kilde = Kilde.MANUELL,
                )

            eksisterendeHusstandsbarn.perioder.add(periode)
            entityManager.flush()
            log.info {
                "Ny periode ble lagt til husstandsbarn ${bostatusperiode.idHusstandsbarn} i behandling " +
                    "$behandlingsid."
            }

            return eksisterendeHusstandsbarn.tilOppdatereBoforholdResponse(behandling)
        }
        oppdateringAvBoforholdFeiletException(behandlingsid)
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
                SivilstandApi.beregn(
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

                val requestManuelle = manuellePerioder.tilBoforholdRequest()
                val requestOffentlige = nyttHusstandsbarn.perioder.tilBoforholdRequest()
                val requestManuelleOgOffentlige = requestOffentlige.plus(requestManuelle)

                val husstandsbarnperioder =
                    when (overskriveManuelleOpplysninger) {
                        false ->
                            BoforholdApi.beregnV2(
                                behandling.virkningstidspunktEllerSøktFomDato,
                                requestManuelleOgOffentlige,
                            ).tilHusstandsbarn(behandling, bidragPersonConsumer).first().perioder

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
            nyttPeriodisertBoforhold.filter { Kilde.MANUELL == it.kilde }.mapNotNull { it.ident }.toSet()

        manuelleBarnMedIdent.forEach { manueltBarn ->
            if (identerOffisielleBarn.contains(manueltBarn.ident)) {
                val offisieltBarn = nyttPeriodisertBoforhold.find { manueltBarn.ident == it.ident }
                val perioder =
                    when (sletteManuellePerioder) {
                        false ->
                            offisieltBarn?.perioder?.tilBoforholdRequest()
                                ?.plus(manueltBarn.perioder.tilBoforholdRequest())

                        true -> offisieltBarn?.perioder?.tilBoforholdRequest()
                    }

                offisieltBarn?.perioder?.tilBoforholdRequest()?.plus(manueltBarn.perioder.tilBoforholdRequest())

                perioder?.let {
                    val periodisertBoforhold =
                        BoforholdApi.beregnV2(behandling.virkningstidspunktEllerSøktFomDato, perioder)
                    val hbp =
                        periodisertBoforhold.tilHusstandsbarn(offisieltBarn?.behandling!!, bidragPersonConsumer)
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
