package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsbarn
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeiletException
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilOppdatereBoforholdResponse
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.validere
import no.nav.bidrag.boforhold.response.BoforholdBeregnet
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class BoforholdService(
    private val behandlingRepository: BehandlingRepository,
    private val bidragPersonConsumer: BidragPersonConsumer,
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
        periodisertBoforhold: List<BoforholdBeregnet>,
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
        periodisertBoforhold: List<BoforholdBeregnet>,
    ) {
        val nyeHusstandsbarnMedPerioder = periodisertBoforhold.tilHusstandsbarn(behandling, bidragPersonConsumer)
        var husstandsbarnSomSkalSlettes: Set<Husstandsbarn> = emptySet()

        behandling.husstandsbarn.asSequence().filter { i -> Kilde.OFFENTLIG == i.kilde }
            .forEach { eksisterendeHusstandsbarn ->

                val eksisterendeHusstandsbarnOppdateres =
                    nyeHusstandsbarnMedPerioder.map { it.ident }.contains(eksisterendeHusstandsbarn.ident)

                if (!eksisterendeHusstandsbarnOppdateres) {
                    eksisterendeHusstandsbarn.perioder.clear()
                    husstandsbarnSomSkalSlettes = husstandsbarnSomSkalSlettes.plus(eksisterendeHusstandsbarn)
                }
            }

        if (husstandsbarnSomSkalSlettes.isNotEmpty()) {
            sletteHusstandsbarn(behandling, husstandsbarnSomSkalSlettes)
        }

        val husstandsbarnSomSkalOppdateres =
            behandling.husstandsbarn.asSequence().filter { i -> Kilde.OFFENTLIG == i.kilde }.toSet()

        nyeHusstandsbarnMedPerioder.forEach { nyttHusstandsbarn ->
            if (husstandsbarnSomSkalOppdateres.map { it.ident }.contains(nyttHusstandsbarn.ident)) {
                val eksisterendeHusstandsbarn =
                    husstandsbarnSomSkalOppdateres.find { it.ident == nyttHusstandsbarn.ident }!!
                eksisterendeHusstandsbarn.perioder.clear()
                eksisterendeHusstandsbarn.perioder.addAll(nyttHusstandsbarn.perioder)
            } else {
                entityManager.persist(nyttHusstandsbarn)
                behandling.husstandsbarn.add(nyttHusstandsbarn)
            }
        }
        entityManager.refresh(behandling)
        log.info { "Husstandsbarn ble oppdatert for behandling ${behandling.id}" }
    }

    @Transactional
    fun oppdatereHusstandsbarnManuelt(
        behandlingsid: Long,
        oppdatereHusstandsbarn: OppdatereHusstandsbarn,
    ): OppdatereBoforholdResponse {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereHusstandsbarn.validere(behandling)

        oppdatereHusstandsbarn.sletteHusstandsbarn?.let { idHusstandsbarn ->
            val husstandsbarnSomSkalSlettes = behandling.husstandsbarn.find { idHusstandsbarn == it.id }
            behandling.husstandsbarn.remove(husstandsbarnSomSkalSlettes)
            log.info { "Slettet husstandsbarn med id $idHusstandsbarn fra behandling $behandlingsid." }
            return husstandsbarnSomSkalSlettes!!.tilOppdatereBoforholdResponse(behandling)
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

        oppdatereHusstandsbarn.nyBostatusperiode?.let { bostatusperiode ->

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
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaSivilstand() {
        // TODO: Implementere
    }

    @Transactional
    fun oppdatereSivilstandManuelt(
        behandlingsid: Long,
        oppdatereSivilstand: OppdatereSivilstand,
    ): OppdatereBoforholdResponse? {
        // TODO: Implementere
        return null
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
}
