package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.aktiveringAvGrunnlagFeiletException
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.inntektIkkeFunnetException
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntektspost
import no.nav.bidrag.behandling.transformers.inntekt.tilInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

private val log = KotlinLogging.logger {}

@Service
class InntektService(
    private val behandlingRepository: BehandlingRepository,
    private val inntektRepository: InntektRepository,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
        behandlingsid: Long,
        personident: Personident,
        summerteÅrsinntekter: List<SummertÅrsinntekt>,
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
            summerteÅrsinntekter.tilInntekt(
                behandling,
                personident,
            ),
        )

        entityManager.refresh(behandling)
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaOffentligeInntekter(
        behandling: Behandling,
        rolle: Rolle,
        summerteÅrsinntekter: List<SummertÅrsinntekt>,
    ) {
        val idTilInntekterSomBleOppdatert: MutableSet<Long> = mutableSetOf()

        summerteÅrsinntekter.forEach { nyInntekt ->
            behandleInntektsoppdatering(behandling, nyInntekt, rolle, idTilInntekterSomBleOppdatert)
        }

        val inntektsrapporteringerForYtelser =
            setOf(
                Inntektsrapportering.BARNETILSYN,
                Inntektsrapportering.BARNETILLEGG,
                Inntektsrapportering.KONTANTSTØTTE,
                Inntektsrapportering.UTVIDET_BARNETRYGD,
            )

        // Sletter tidligere innhentede inntekter knyttet til ainntekt og skattegrunnlag som ikke finnes i nyeste uttrekk
        val offentligeInntekterSomSkalSlettes =
            behandling.inntekter.filter { Kilde.OFFENTLIG == it.kilde }
                .filter { !inntektsrapporteringerForYtelser.contains(it.type) }.filter { rolle.ident == it.ident }
                .filter { !idTilInntekterSomBleOppdatert.contains(it.id) }

        behandling.inntekter.removeAll(offentligeInntekterSomSkalSlettes)
        entityManager.flush()
    }

    @Transactional
    fun oppdatereInntekterManuelt(
        behandlingsid: Long,
        oppdatereInntekterRequest: OppdatereInntekterRequestV2,
    ) {
        val behandling =
            behandlingRepository.findById(behandlingsid).orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereInntekterRequest.oppdatereInntektsperioder.forEach {
            val inntekt = inntektRepository.findById(it.id).orElseThrow { inntektIkkeFunnetException(it.id) }
            inntekt.datoFom = it.angittPeriode.fom
            inntekt.datoTom = it.angittPeriode.til
            inntekt.taMed = it.taMedIBeregning
        }

        oppdatereInntekterRequest.oppdatereManuelleInntekter.forEach {
            if (it.id != null) {
                val inntekt =
                    inntektRepository.findByIdAndKilde(it.id, Kilde.MANUELL)
                        .orElseThrow { inntektIkkeFunnetException(it.id) }
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

    private fun <T> behandleInntektsoppdatering(
        behandling: Behandling,
        nyInntekt: T,
        rolle: Rolle,
        idTilInntekterSomBleOppdatert: MutableSet<Long>,
    ) {
        val type: Inntektsrapportering
        val periode: ÅrMånedsperiode?

        when (nyInntekt) {
            is SummertÅrsinntekt -> {
                type = nyInntekt.inntektRapportering
                periode = nyInntekt.periode.copy(til = nyInntekt.periode.til?.plusMonths(1))
            }

            else -> {
                log.error {
                    "Feil klassetype for nyInntekt - aktivering av inntektsgrunnlag feilet for behandling: " +
                        "${behandling.id!!}."
                }
                aktiveringAvGrunnlagFeiletException(behandling.id!!)
            }
        }

        val inntekterSomKunIdentifiseresPåType =
            setOf(Inntektsrapportering.AINNTEKT_BEREGNET_3MND, Inntektsrapportering.AINNTEKT_BEREGNET_12MND)

        val inntekterSomSkalOppdateres =
            behandling.inntekter.asSequence()
                .filter { i -> Kilde.OFFENTLIG == i.kilde }
                .filter { i -> type == i.type }
                .filter { i -> i.opprinneligFom != null }
                .filter { i -> rolle.ident == i.ident }.toList()
                .filter { i ->
                    inntekterSomKunIdentifiseresPåType.contains(i.type) ||
                        periode.fom == YearMonth.from(i.opprinneligFom)
                }
                .filter { i ->
                    inntekterSomKunIdentifiseresPåType.contains(i.type) ||
                        periode.til ==
                        if (i.opprinneligTom != null) {
                            YearMonth.from(i.opprinneligTom?.plusDays(1))
                        } else {
                            null
                        }
                }

        if (inntekterSomSkalOppdateres.size > 1) {
            log.warn {
                "Forventet kun å finne èn inntekt, fant ${inntekterSomSkalOppdateres.size} inntekter med" +
                    "samme type og periode for rolle med id ${rolle.id} i behandling med id ${behandling.id}. " +
                    "Fjerner duplikatene."
            }

            val inntektSomOppdateres = inntekterSomSkalOppdateres.maxBy { it.id!! }
            oppdatereBeløpPeriodeOgPoster(nyInntekt, inntektSomOppdateres)
            entityManager.refresh(behandling)
            idTilInntekterSomBleOppdatert.add(inntektSomOppdateres.id!!)
        } else if (inntekterSomSkalOppdateres.size == 1) {
            val inntektSomOppdateres = inntekterSomSkalOppdateres.first()
            oppdatereBeløpPeriodeOgPoster(nyInntekt, inntektSomOppdateres)
            entityManager.refresh(behandling)
            log.info {
                "Eksisterende inntekt med id ${inntektSomOppdateres.id} for rolle " +
                    "${rolle.rolletype} i behandling ${behandling.id} ble oppdatert med nytt beløp og poster."
            }
            idTilInntekterSomBleOppdatert.add(inntektSomOppdateres.id!!)
        } else {
            val i =
                inntektRepository.save(
                    nyInntekt.tilInntekt(
                        behandling,
                        Personident(rolle.ident!!),
                    ),
                )

            idTilInntekterSomBleOppdatert.add(i.id!!)
            entityManager.refresh(behandling)
            log.info { "Ny offisiell inntekt ble lagt til i behandling ${behandling.id} for rolle ${rolle.rolletype}" }
        }
    }

    private fun <T> oppdatereBeløpPeriodeOgPoster(
        nyInntekt: T,
        eksisterendeInntekt: Inntekt,
    ) {
        if (nyInntekt is SummertÅrsinntekt) {
            eksisterendeInntekt.belop = nyInntekt.sumInntekt
            eksisterendeInntekt.datoFom = nyInntekt.periode.fom.atDay(1)
            eksisterendeInntekt.datoTom = nyInntekt.periode.til?.atEndOfMonth()
            eksisterendeInntekt.inntektsposter.clear()
            eksisterendeInntekt.inntektsposter.addAll(
                nyInntekt.inntektPostListe.tilInntektspost(
                    eksisterendeInntekt,
                ),
            )
        } else if (nyInntekt is SummertMånedsinntekt) {
            eksisterendeInntekt.belop = nyInntekt.sumInntekt
            eksisterendeInntekt.datoFom = nyInntekt.gjelderÅrMåned.atDay(1)
            eksisterendeInntekt.datoTom = nyInntekt.gjelderÅrMåned.atEndOfMonth()
            eksisterendeInntekt.inntektsposter.clear()
            eksisterendeInntekt.inntektsposter.addAll(
                nyInntekt.inntektPostListe.tilInntektspost(
                    eksisterendeInntekt,
                ),
            )
        }

        entityManager.flush()
    }
}
