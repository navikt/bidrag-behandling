package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.inntektIkkeFunnetException
import no.nav.bidrag.behandling.transformers.konvertereTilInntekt
import no.nav.bidrag.behandling.transformers.tilInntekt
import no.nav.bidrag.domene.ident.Personident
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class InntektService(
    private val behandlingRepository: BehandlingRepository,
    private val inntektRepository: InntektRepository,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun lagreSammenstilteInntekter(
        behandlingsid: Long,
        personident: Personident,
        sammenstilteInntekter: SummerteMånedsOgÅrsinntekter,
    ) {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val inntekterSomSkalSlettes: Set<Inntekt> = emptySet()
        behandling.inntekter.forEach {
            if (Kilde.OFFENTLIG == it.kilde) {
                it.inntektsposter.removeAll(it.inntektsposter)
                inntekterSomSkalSlettes.plus(it)
            }
        }
        behandling.inntekter.removeAll(inntekterSomSkalSlettes)

        entityManager.flush()

        inntektRepository.saveAll(
            sammenstilteInntekter.summerteÅrsinntekter.tilInntekt(
                behandling,
                personident,
            ) + sammenstilteInntekter.summerteMånedsinntekter.konvertereTilInntekt(behandling, personident),
        )

        entityManager.refresh(behandling)
    }

    @Transactional
    fun oppdatereInntekterManuelt(
        behandlingsid: Long,
        oppdatereInntekterRequest: OppdatereInntekterRequestV2,
    ) {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereInntekterRequest.oppdatereInntektsperioder.forEach {
            val inntekt =
                inntektRepository.findById(it.id).orElseThrow { inntektIkkeFunnetException(it.id) }
            inntekt.datoFom = it.angittPeriode.fom
            inntekt.datoTom = it.angittPeriode.til?.minusDays(1)
            inntekt.taMed = it.taMedIBeregning
        }

        oppdatereInntekterRequest.oppdatereManuelleInntekter.forEach {
            if (it.id != null) {
                val inntekt = inntektRepository.findByIdAndKilde(it.id, Kilde.MANUELL).orElseThrow { inntektIkkeFunnetException(it.id) }
                it.tilInntekt(inntekt)
            } else {
                inntektRepository.save(it.tilInntekt(behandling))
            }
        }

        val manuelleInntekterSomSkalSlettes =
            inntektRepository.findAllById(oppdatereInntekterRequest.sletteInntekter)
                .filter { Kilde.MANUELL == it.kilde }.toSet()

        log.info {
            "Fant ${manuelleInntekterSomSkalSlettes.size} av de oppgitte ${oppdatereInntekterRequest.sletteInntekter} " +
                "inntektene som skal slettes"
        }

        behandling.inntekter.removeAll(manuelleInntekterSomSkalSlettes)
        entityManager.flush()

        if (oppdatereInntekterRequest.sletteInntekter.isNotEmpty()) {
            log.info {
                "Slettet ${oppdatereInntekterRequest.sletteInntekter} inntekter fra databasen."
            }
        }
    }
}
