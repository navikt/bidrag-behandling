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
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektResponse
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.inntektIkkeFunnetException
import no.nav.bidrag.behandling.transformers.behandling.hentBeregnetInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentInntekterValideringsfeil
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntektspost
import no.nav.bidrag.behandling.transformers.inntekt.lagreSomNyInntekt
import no.nav.bidrag.behandling.transformers.inntekt.oppdatereEksisterendeInntekt
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.behandling.transformers.validerKanOppdatere
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
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

        val inntekterSomSkalSlettes: MutableSet<Inntekt> = mutableSetOf()
        val inntektstyper = summerteÅrsinntekter.map { it.inntektRapportering }
        behandling.inntekter.filter { it.ident == personident.verdi && inntektstyper.contains(it.type) }.forEach {
            if (Kilde.OFFENTLIG == it.kilde) {
                it.inntektsposter.removeAll(it.inntektsposter)
                inntekterSomSkalSlettes.add(it)
            }
        }
        behandling.inntekter.removeAll(inntekterSomSkalSlettes)

        inntektRepository.saveAll(summerteÅrsinntekter.tilInntekt(behandling, personident))
    }

    @Transactional
    fun oppdatereAutomatiskInnhentetOffentligeInntekter(
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
    }

    @Transactional
    fun oppdatereInntektManuelt(
        behandlingsid: Long,
        oppdatereInntektRequest: OppdatereInntektRequest,
    ): OppdatereInntektResponse {
        oppdatereInntektRequest.valider()
        secureLogger.info { "Oppdaterer inntekt $oppdatereInntektRequest for behandling $behandlingsid" }
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        behandling.validerKanOppdatere()
        return OppdatereInntektResponse(
            inntekt = oppdatereInntekt(oppdatereInntektRequest, behandling),
            beregnetInntekter = behandling.hentBeregnetInntekter(),
            valideringsfeil = behandling.hentInntekterValideringsfeil(),
        )
    }

    private fun oppdatereInntekt(
        oppdatereInntektRequest: OppdatereInntektRequest,
        behandling: Behandling,
    ): InntektDtoV2? {
        oppdatereInntektRequest.oppdatereInntektsperiode?.let { periode ->
            val inntekt = henteInntektMedId(behandling, periode.id)
            periode.taMedIBeregning.ifTrue {
                inntekt.datoFom = periode.angittPeriode.fom
                inntekt.datoTom = periode.angittPeriode.til
            } ?: run {
                inntekt.datoFom = null
                inntekt.datoTom = null
            }

            inntekt.taMed = periode.taMedIBeregning
            entityManager.flush()
            return inntekt.tilInntektDtoV2()
        }

        oppdatereInntektRequest.oppdatereManuellInntekt?.let { manuellInntekt ->
            val inntekt =
                behandling.inntekter.filter { Kilde.MANUELL == it.kilde }.firstOrNull { manuellInntekt.id == it.id }

            val oppdatertInntekt =
                inntekt?.let {
                    manuellInntekt.oppdatereEksisterendeInntekt(inntekt)
                } ?: manuellInntekt.lagreSomNyInntekt(behandling)

            entityManager.flush()
            return oppdatertInntekt.tilInntektDtoV2()
        }

        oppdatereInntektRequest.sletteInntekt?.let {
            val inntektSomSkalSlettes =
                behandling.inntekter.filter { Kilde.MANUELL == it.kilde }.first { i -> it == i.id }
            behandling.inntekter.remove(inntektSomSkalSlettes)
            log.info { "Slettet inntekt med id $it fra behandling ${behandling.id}." }
            entityManager.flush()
            return inntektSomSkalSlettes.tilInntektDtoV2()
        }

        oppdatereInntektRequest.oppdatereNotat?.let {
            behandling.inntektsbegrunnelseKunINotat = it.kunINotat ?: behandling.inntektsbegrunnelseKunINotat
            behandling.inntektsbegrunnelseIVedtakOgNotat =
                it.medIVedtaket ?: behandling.inntektsbegrunnelseIVedtakOgNotat
        }

        return null
    }

    @Deprecated("Erstattes av oppdatereInntektManuelt")
    @Transactional
    fun oppdatereInntekterManuelt(
        behandlingsid: Long,
        oppdatereInntekterRequest: OppdatereInntekterRequestV2,
    ) {
        oppdatereInntekterRequest.valider()
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

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
                it.oppdatereEksisterendeInntekt(inntekt)
            } else {
                inntektRepository.save(it.lagreSomNyInntekt(behandling))
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

        if (oppdatereInntekterRequest.sletteInntekter.isNotEmpty()) {
            entityManager.flush()
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
            idTilInntekterSomBleOppdatert.add(inntektSomOppdateres.id!!)
        } else if (inntekterSomSkalOppdateres.size == 1) {
            val inntektSomOppdateres = inntekterSomSkalOppdateres.first()
            oppdatereBeløpPeriodeOgPoster(nyInntekt, inntektSomOppdateres)
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

    private fun henteInntektMedId(
        behandling: Behandling,
        inntektsid: Long,
    ): Inntekt {
        try {
            return behandling.inntekter.first { i -> inntektsid == i.id }
        } catch (e: NoSuchElementException) {
            inntektIkkeFunnetException(inntektsid)
        }
    }

    private fun <T> oppdatereBeløpPeriodeOgPoster(
        nyInntekt: T,
        eksisterendeInntekt: Inntekt,
    ) {
        if (nyInntekt is SummertÅrsinntekt) {
            eksisterendeInntekt.belop = nyInntekt.sumInntekt
            eksisterendeInntekt.opprinneligFom = nyInntekt.periode.fom.atDay(1)
            eksisterendeInntekt.opprinneligTom = nyInntekt.periode.til?.atEndOfMonth()
            eksisterendeInntekt.inntektsposter.clear()
            eksisterendeInntekt.inntektsposter.addAll(
                nyInntekt.inntektPostListe.tilInntektspost(
                    eksisterendeInntekt,
                ),
            )
        }
    }
}
