package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.aktiveringAvGrunnlagFeiletException
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.henteBearbeidaInntekterForType
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.tilInntektrapporteringYtelse
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntektRequest
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.inntektIkkeFunnetException
import no.nav.bidrag.behandling.oppdateringAvInntektFeilet
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsdataType
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInntektspost
import no.nav.bidrag.behandling.transformers.inntekt.bestemDatoFomForOffentligInntekt
import no.nav.bidrag.behandling.transformers.inntekt.bestemDatoTomForOffentligInntekt
import no.nav.bidrag.behandling.transformers.inntekt.lagreSomNyInntekt
import no.nav.bidrag.behandling.transformers.inntekt.oppdatereEksisterendeInntekt
import no.nav.bidrag.behandling.transformers.inntekt.skalAutomatiskSettePeriode
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.inntektstypeListe
import no.nav.bidrag.behandling.transformers.opphørSisteTilDato
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.behandling.transformers.validerKanOppdatere
import no.nav.bidrag.behandling.transformers.vedtak.nullIfEmpty
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.YearMonth
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

@Service
class InntektService(
    private val behandlingRepository: BehandlingRepository,
    private val inntektRepository: InntektRepository,
    private val notatService: NotatService,
) {
    @Transactional
    fun justerOffentligePerioderEtterSisteGrunnlag(behandling: Behandling) {
        behandling.roller.forEach { rolle ->
            val inntekterRolle =
                behandling.inntekter.filter {
                    it.erSammeRolle(rolle) && eksplisitteYtelser.contains(it.type) &&
                        it.kilde == Kilde.OFFENTLIG
                }
            eksplisitteYtelser.forEach { type ->
                val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleAktiv()

                val summerteInntekter = ikkeAktiveGrunnlag.henteBearbeidaInntekterForType(type.tilGrunnlagsdataType(), rolle)
                if (summerteInntekter != null) {
                    val finnesMinstEnPeriodeMedAvvik =
                        summerteInntekter.inntekter.any { inntekt ->
                            val finnesMatchendeInntekt =
                                inntekterRolle.any {
                                    it.type == type && it.opprinneligFom == inntekt.periode.fom.atDay(1) &&
                                        it.opprinneligTom == inntekt.periode.til?.atEndOfMonth()
                                }
                            if (!finnesMatchendeInntekt) {
                                secureLogger.warn {
                                    "Avvikshåndtering!: Fant inntekter som ikke matcher med siste innhentet offentlige opplysninger for type=$type rolle=${rolle.ident}, inntekt=${inntekt.periode}. Justerer periodene"
                                }
                            }
                            return@any !finnesMatchendeInntekt
                        }
                    if (finnesMinstEnPeriodeMedAvvik) {
                        oppdatereAutomatiskInnhentaOffentligeInntekter(
                            behandling,
                            rolle,
                            summerteInntekter.inntekter,
                            type.tilGrunnlagsdataType(),
                        )
                    }
                }
            }
        }
    }

    @Transactional
    fun rekalkulerPerioderInntekter(
        behandlingsid: Long,
        opphørSlettet: Boolean = false,
        forrigeOpphørsdato: LocalDate? = null,
        forrigeVirkningstidspunkt: LocalDate? = null,
    ) {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }
        rekalkulerPerioderInntekter(behandling, opphørSlettet, forrigeOpphørsdato, forrigeVirkningstidspunkt)
    }

    @Transactional
    fun rekalkulerPerioderInntekter(
        behandling: Behandling,
        opphørSlettet: Boolean = false,
        forrigeOpphørsdato: LocalDate? = null,
        forrigeVirkningstidspunkt: LocalDate? = null,
    ) {
        if (behandling.virkningstidspunkt == null) return

        val inntekterTattMed = behandling.inntekter.filter { it.taMed && it.datoFom != null }
        inntekterTattMed
            .filter { it.datoFom!! < behandling.virkningstidspunkt }
            .forEach {
                if (it.datoTom != null && behandling.virkningstidspunkt!! >= it.datoTom) {
                    it.taMed = false
                    it.datoFom = null
                    it.datoTom = null
                } else {
                    it.datoFom = behandling.virkningstidspunkt
                }
            }
        behandling.inntekter
            .filter { it.taMed && it.datoFom != null }
            .filter { it.datoFom!! == forrigeVirkningstidspunkt }
            .forEach { periode ->
                periode.datoFom = behandling.virkningstidspunkt
            }
        behandling.inntekter
            .filter { it.taMed }
            .filter { eksplisitteYtelser.contains(it.type) && it.kilde == Kilde.OFFENTLIG && it.opprinneligFom != null }
            .forEach {
                it.taMed = it.skalAutomatiskSettePeriode()
                it.datoFom = it.bestemDatoFomForOffentligInntekt()
                it.datoTom = it.bestemDatoTomForOffentligInntekt()
            }

        if (behandling.minstEnRolleHarBegrensetBeregnTilDato) {
            behandling.inntekter
                .filter { it.taMed && it.datoFom != null }
                .filter { it.beregnTilDato != null && it.datoFom!! >= it.beregnTilDato }
                .forEach {
                    it.taMed = false
                    it.datoFom = null
                    it.datoTom = null
                }

            behandling.inntekter
                .filter { eksplisitteYtelser.contains(it.type) }
                .filter { it.taMed && it.kilde == Kilde.MANUELL }
                .groupBy { Triple(it.type, it.gjelderIdent, it.gjelderBarnIdent) }
                .forEach { (triple, inntekter) ->
                    if (triple.first == Inntektsrapportering.BARNETILLEGG) {
                        inntekter
                            .groupBy { it.inntektstypeListe.firstOrNull() }
                            .forEach { (_, inntekter) ->
                                inntekter.justerSistePeriodeForOpphørsdato(
                                    forrigeOpphørsdato,
                                )
                            }
                    } else {
                        inntekter.justerSistePeriodeForOpphørsdato(forrigeOpphørsdato)
                    }
                }
        }

        if (opphørSlettet || behandling.minstEnRolleHarBegrensetBeregnTilDato) {
            behandling.inntekter
                .filter { !eksplisitteYtelser.contains(it.type) }
                .groupBy { Pair(it.type, it.gjelderIdent) }
                .forEach { (_, inntekter) ->
                    inntekter.justerSistePeriodeForOpphørsdato(forrigeOpphørsdato)
                }
        }

        val manuelleInntekterSomErFjernet = behandling.inntekter.filter { !it.taMed && it.kilde == Kilde.MANUELL }
        behandling.inntekter.removeAll(manuelleInntekterSomErFjernet)
    }

    private fun List<Inntekt>.justerSistePeriodeForOpphørsdato(forrigeOpphørsdato: LocalDate?) {
        val inntektSomSkalOppdateres =
            filter { it.taMed }
                .filter {
                    it.beregnTilDato == null ||
                        it.datoTom == null ||
                        it.datoTom!!.isAfter(it.beregnTilDato) ||
                        it.datoTom == forrigeOpphørsdato?.opphørSisteTilDato()
                }.sortedBy { it.datoFom }
                .lastOrNull()

        if (inntektSomSkalOppdateres != null) {
            inntektSomSkalOppdateres.datoTom = justerPeriodeTomOpphørsdato(inntektSomSkalOppdateres.opphørsdato)
        }
    }

    @Transactional
    fun lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
        behandling: Behandling,
        rolle: Rolle,
        summerteÅrsinntekter: List<SummertÅrsinntekt>,
    ) {
        val inntekterSomSkalSlettes: MutableSet<Inntekt> = mutableSetOf()
        val inntektstyper = summerteÅrsinntekter.map { it.inntektRapportering }
        behandling.inntekter.filter { it.erSammeRolle(rolle) && inntektstyper.contains(it.type) }.forEach {
            if (Kilde.OFFENTLIG == it.kilde) {
                it.inntektsposter.removeAll(it.inntektsposter)
                inntekterSomSkalSlettes.add(it)
            }
        }
        behandling.inntekter.removeAll(inntekterSomSkalSlettes)

        behandling.inntekter.addAll(
            summerteÅrsinntekter.tilInntekt(behandling, rolle).map {
                it.automatiskTaMedYtelserFraNav()
                it
            },
        )
    }

    @Transactional
    fun oppdatereAutomatiskInnhentaOffentligeInntekter(
        behandling: Behandling,
        rolle: Rolle,
        summerteÅrsinntekter: List<SummertÅrsinntekt>,
        grunnlagstype: Grunnlagsdatatype? = null,
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

        val ytelsetypeSomOppdateres = grunnlagstype?.tilInntektrapporteringYtelse()
        // Sletter tidligere innhentede inntekter knyttet til ainntekt og skattegrunnlag som ikke finnes i nyeste uttrekk
        val offentligeInntekterSomSkalSlettes =
            behandling.inntekter
                .filter { Kilde.OFFENTLIG == it.kilde }
                .filter {
                    (
                        ytelsetypeSomOppdateres != null &&
                            it.type == ytelsetypeSomOppdateres
                    ) ||
                        (
                            ytelsetypeSomOppdateres == null &&
                                !inntektsrapporteringerForYtelser.contains(it.type)
                        )
                }.filter { it.erSammeRolle(rolle) }
                .filter { !idTilInntekterSomBleOppdatert.contains(it.id) }

        offentligeInntekterSomSkalSlettes.forEach {
            secureLogger.debug {
                "Sletter offentlig inntekt med type ${it.type} " +
                    "og periode ${it.opprinneligFom.toCompactString()} - ${it.opprinneligTom.toCompactString()} fra behandling ${behandling.id}"
            }
        }
        behandling.inntekter.removeAll(offentligeInntekterSomSkalSlettes)
    }

    @Transactional
    fun oppdatereInntektBegrunnelse(
        behandlingsid: Long,
        oppdatereInntektRequest: OppdatereInntektBegrunnelseRequest,
    ) {
        secureLogger.debug { "Oppdaterer begrunnelse inntekt $oppdatereInntektRequest for behandling $behandlingsid" }
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        oppdatereInntektRequest.oppdatereBegrunnelse?.let {
            val rolle =
                it.rolleid?.let { rolleid ->
                    val rolle = behandling.roller.find { it.id == rolleid }
                    if (rolle == null) {
                        throw HttpClientErrorException(
                            HttpStatus.NOT_FOUND,
                            "Fant ikke rolle med id $rolle i behandling ${behandling.id}",
                        )
                    }
                    rolle
                } ?: behandling.bidragsmottaker!!

            notatService.oppdatereNotat(
                behandling = behandling,
                notattype = Notattype.INNTEKT,
                notattekst = it.nyBegrunnelse,
                // TODO: Fjerne setting av rolle til bidragsmottaker når frontend angir rolle for inntektsnotat
                rolle = rolle,
            )
        }
    }

    @Transactional
    fun oppdatereInntektManuelt(
        behandlingsid: Long,
        oppdatereInntektRequest: OppdatereInntektRequest,
    ): InntektDtoV2? {
        oppdatereInntektRequest.valider()
        secureLogger.debug { "Oppdaterer inntekt $oppdatereInntektRequest for behandling $behandlingsid" }
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        behandling.validerKanOppdatere()

        return oppdatereInntekt(oppdatereInntektRequest, behandling)
    }

    private fun oppdatereInntekt(
        oppdatereInntektRequest: OppdatereInntektRequest,
        behandling: Behandling,
    ): InntektDtoV2? {
        oppdatereInntektRequest.oppdatereInntektsperiode?.apply {
            val inntekt = henteInntektMedId(behandling, id)
            taMedIBeregning.ifTrue {
                val forrigeInntektMedSammeType = behandling.hentSisteInntektMedSammeType2(inntekt)

                inntekt.datoFom =
                    if (inntekt.skalAutomatiskSettePeriode()) {
                        inntekt.bestemDatoFomForOffentligInntekt()
                    } else {
                        angittPeriode?.fom
                            ?: oppdateringAvInntektFeilet(
                                "Angitt periode må settes ved oppdatering av offentlig inntekt som er tatt med i beregningen",
                            )
                    }
                inntekt.datoTom =
                    if (inntekt.skalAutomatiskSettePeriode()) {
                        inntekt.bestemDatoTomForOffentligInntekt()
                    } else {
                        angittPeriode?.til
                            ?: justerPeriodeTomOpphørsdato(inntekt.beregnTilDato)
                    }
                forrigeInntektMedSammeType?.apply {
                    if (inntekt.datoFom!! > datoFom) {
                        datoTom = inntekt.datoFom!!.minusDays(1)
                    }
                }
                inntekt
            } ?: run {
                inntekt.datoFom = null
                inntekt.datoTom = null
            }

            inntekt.taMed = taMedIBeregning
            return inntekt.tilInntektDtoV2()
        }

        oppdatereInntektRequest.oppdatereManuellInntekt?.let { manuellInntekt ->
            val inntekt =
                behandling.inntekter.filter { Kilde.MANUELL == it.kilde }.firstOrNull { manuellInntekt.id == it.id }
            val forrigeInntektMedSammeType = behandling.hentSisteInntektMedSammeType(manuellInntekt)

            val oppdatertInntekt =
                inntekt?.let {
                    manuellInntekt.oppdatereEksisterendeInntekt(inntekt)
                } ?: run {
                    val nyInntekt = manuellInntekt.lagreSomNyInntekt(behandling)

                    nyInntekt
                }

            forrigeInntektMedSammeType?.let {
                if (oppdatertInntekt.datoFom!! > it.datoFom) {
                    it.datoTom = oppdatertInntekt.datoFom!!.minusDays(1)
                }
            }
            return oppdatertInntekt.tilInntektDtoV2()
        }

        oppdatereInntektRequest.sletteInntekt?.let {
            val inntektSomSkalSlettes =
                behandling.inntekter.filter { Kilde.MANUELL == it.kilde }.first { i -> it == i.id }
            behandling.inntekter.remove(inntektSomSkalSlettes)
            log.debug { "Slettet inntekt med id $it fra behandling ${behandling.id}." }
            return inntektSomSkalSlettes.tilInntektDtoV2()
        }

        oppdatereInntektRequest.henteOppdatereBegrunnelse?.let {
            val rolle =
                it.rolleid?.let { rolleid ->
                    val rolle = behandling.roller.find { it.id == rolleid }
                    if (rolle == null) {
                        throw HttpClientErrorException(
                            HttpStatus.NOT_FOUND,
                            "Fant ikke rolle med id $rolle i behandling ${behandling.id}",
                        )
                    }
                    rolle
                } ?: behandling.bidragsmottaker!!

            notatService.oppdatereNotat(
                behandling = behandling,
                notattype = Notattype.INNTEKT,
                notattekst = it.nyBegrunnelse,
                // TODO: Fjerne setting av rolle til bidragsmottaker når frontend angir rolle for inntektsnotat
                rolle = rolle,
            )
        }

        return null
    }

    private fun Behandling.hentSisteInntektMedSammeType2(offentligInntekt: Inntekt) =
        inntekter
            .filter {
                it.type == offentligInntekt.type &&
                    it.taMed &&
                    offentligInntekt.id != it.id
            }.filter {
                offentligInntekt.inntektsposter.isEmpty() ||
                    it.inntektsposter.any { offentligInntekt.inntektsposter.any { oit -> oit.inntektstype == it.inntektstype } }
            }.filter {
                offentligInntekt.tilhørerSammePerson(it) && offentligInntekt.tilhørerSammeBarn(it)
            }.sortedBy { it.datoFom }
            .lastOrNull()

    private fun Behandling.hentSisteInntektMedSammeType(manuellInntekt: OppdatereManuellInntekt) =
        inntekter
            .filter {
                it.type == manuellInntekt.type &&
                    it.taMed &&
                    manuellInntekt.id != it.id
            }.filter {
                manuellInntekt.inntektstype == null ||
                    it.inntektsposter.any { it.inntektstype == manuellInntekt.inntektstype }
            }.filter {
                it.tilhørerSammePerson(manuellInntekt.ident?.verdi, manuellInntekt.gjelderId) &&
                    it.tilhørerSammeBarn(manuellInntekt.gjelderBarn?.verdi, manuellInntekt.gjelderBarnId)
            }.sortedBy { it.datoFom }
            .lastOrNull()

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
            behandling.inntekter
                .asSequence()
                .filter { i -> Kilde.OFFENTLIG == i.kilde }
                .filter { i -> type == i.type }
                .filter { i -> i.opprinneligFom != null }
                .filter { i -> i.erSammeRolle(rolle) }
                .toList()
                .filter { i ->
                    inntekterSomKunIdentifiseresPåType.contains(i.type) ||
                        periode.fom == YearMonth.from(i.opprinneligFom)
                }.filter { i ->
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
            secureLogger.debug {
                "Eksisterende inntekt med id ${inntektSomOppdateres.id} for rolle " +
                    "${rolle.rolletype} i behandling ${behandling.id} ble oppdatert med nytt beløp og poster."
            }
            idTilInntekterSomBleOppdatert.add(inntektSomOppdateres.id!!)
        } else {
            val nyInntekt =
                inntektRepository.save(
                    nyInntekt.tilInntekt(
                        behandling,
                        rolle,
                    ),
                )

            idTilInntekterSomBleOppdatert.add(nyInntekt.id!!)
            behandling.inntekter.add(nyInntekt)
            secureLogger.debug {
                "Ny offisiell inntekt ${nyInntekt.id} ble lagt til i behandling ${behandling.id} for rolle ${rolle.rolletype}"
            }
        }
    }

    private fun Inntekt.automatiskTaMedYtelserFraNav() {
        if (skalAutomatiskSettePeriode()) {
            taMed = true
            datoFom = bestemDatoFomForOffentligInntekt()
            datoTom = bestemDatoTomForOffentligInntekt()
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
                if (nyInntekt.inntektRapportering == Inntektsrapportering.BARNETILLEGG) {
                    mutableSetOf(
                        Inntektspost(
                            kode = nyInntekt.inntektRapportering.name,
                            beløp = nyInntekt.sumInntekt,
                            // TODO: Hentes bare fra pensjon i dag. Dette bør endres når vi henter barnetillegg fra andre kilder
                            inntektstype = Inntektstype.BARNETILLEGG_PENSJON,
                            inntekt = eksisterendeInntekt,
                        ),
                    )
                } else {
                    nyInntekt.inntektPostListe.tilInntektspost(eksisterendeInntekt)
                },
            )
        }
    }
}
