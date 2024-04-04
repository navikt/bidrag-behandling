package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsbarn
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.finnesFraFørException
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
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
    ) {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereHusstandsbarn.sletteHusstandsbarn?.let { idHusstandsbarn ->
            val husstandsbarnSomSkalSlettes = behandling.husstandsbarn.find { idHusstandsbarn == it.id }
            behandling.husstandsbarn.remove(husstandsbarnSomSkalSlettes)
            log.info { "Slettet husstandsbarn med id $idHusstandsbarn fra behandling $behandlingsid." }
            return
        }

        oppdatereHusstandsbarn.nyttHusstandsbarn?.let { personalia ->
            val eksisterendeHusstandsbarn =
                behandling.husstandsbarn.find { it.ident != null && it.ident == personalia.personident?.verdi }

            if (eksisterendeHusstandsbarn != null) {
                log.error { "Husstandsbarn med id ${eksisterendeHusstandsbarn.id} finnes allerede i behandling $behandlingsid." }
                finnesFraFørException(behandlingsid)
            } else {
                behandling.husstandsbarn.add(
                    Husstandsbarn(
                        behandling,
                        Kilde.MANUELL,
                        ident = personalia.personident?.verdi,
                        fødselsdato = personalia.fødselsdato,
                        navn = personalia.navn,
                    ),
                )
                log.info { "Nytt husstandsbarn ble manuelt lagt til behandling $behandlingsid." }
            }
        }
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
    }

    @Transactional
    fun oppdatereSivilstandManuelt(
        behandlingsid: Long,
        oppdatereSivilstand: OppdatereSivilstand,
    ) {
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
