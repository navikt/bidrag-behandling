package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.inntektIkkeFunnetException
import no.nav.bidrag.behandling.transformers.konvertereTilInntekt
import no.nav.bidrag.behandling.transformers.tilInntekt
import no.nav.bidrag.domene.ident.Personident
import org.springframework.data.jpa.repository.Modifying
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

        inntektRepository.sletteOffentligeInntekterFraBehandling(behandlingsid)

        inntektRepository.saveAll(
            sammenstilteInntekter.summerteÅrsinntekter.tilInntekt(
                behandling,
                personident,
            ) + sammenstilteInntekter.summerteMånedsinntekter.konvertereTilInntekt(behandling, personident),
        )

        entityManager.refresh(behandling)
    }

    @Modifying
    @Transactional
    fun oppdatereInntekterManuelt(
        behandlingsid: Long,
        oppdatereInntekterRequest: OppdatereInntekterRequestV2,
    ) {
        oppdatereInntekterRequest.oppdatereInntektsperioder.forEach {
            val inntekt =
                inntektRepository.findById(it.id).orElseThrow { inntektIkkeFunnetException(it.id) }
            inntekt.datoFom = it.angittPeriode.fom
            inntekt.datoTom = it.angittPeriode.til?.minusDays(1)
            inntekt.taMed = it.taMedIBeregning
        }

        oppdatereInntekterRequest.oppdatereManuelleInntekter.forEach {
            if (it.id != null) {
                val inntekt = inntektRepository.findById(it.id).orElseThrow { inntektIkkeFunnetException(it.id) }
                it.tilInntekt(inntekt)
            } else {
                val behandling =
                    behandlingRepository.findById(behandlingsid)
                        .orElseThrow { behandlingNotFoundException(behandlingsid) }
                inntektRepository.save(it.tilInntekt(behandling))
            }
        }

        inntektRepository.sletteInntekterFraBehandling(behandlingsid, oppdatereInntekterRequest.sletteInntekter)

        if (oppdatereInntekterRequest.sletteInntekter.isNotEmpty()) {
            log.info {
                "Slettet ${oppdatereInntekterRequest.sletteInntekter} inntekter fra databasen."
            }
        }
    }
}
