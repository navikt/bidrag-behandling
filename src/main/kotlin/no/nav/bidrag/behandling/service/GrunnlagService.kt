package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.database.grunnlag.tilGrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.RolleRepository
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataEndretDto
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.tilSkattegrunnlagForLigningsår
import no.nav.bidrag.behandling.transformers.tilSmåbarnstillegg
import no.nav.bidrag.behandling.transformers.tilSummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.transformers.tilUtvidetBarnetrygd
import no.nav.bidrag.behandling.transformers.toDto
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.grunnlag.request.GrunnlagRequestDto
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

val inntekterOgYtelser =
    setOf(
        Grunnlagsdatatype.INNTEKT,
        Grunnlagsdatatype.KONTANTSTØTTE,
        Grunnlagsdatatype.SMÅBARNSTILLEGG,
        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
    )

@Service
class GrunnlagService(
    private val grunnlagRepository: GrunnlagRepository,
    private val behandlingRepository: BehandlingRepository,
    private val rolleRepository: RolleRepository,
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val inntektApi: InntektApi,
    private val inntektService: InntektService,
) {
    @Value("\${egenskaper.grunnlag.min-antall-timer-siden-forrige-innhenting}")
    private lateinit var grenseInnhenting: String

    @Transactional
    @Deprecated("Grunnlagsinnhenting og opprettelse skal gjøres automatisk med oppdatereGrunnlagForBehandling")
    fun opprett(
        behandlingId: Long,
        grunnlagstype: Grunnlagsdatatype,
        data: String,
        innhentet: LocalDateTime,
    ): Grunnlag {
        behandlingRepository
            .findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }
            .let {

                val nyGrunnlagstype = when (grunnlagstype) {
                    Grunnlagsdatatype.BOFORHOLD_BEARBEIDET -> Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true)
                    Grunnlagsdatatype.HUSSTANDSMEDLEMMER -> Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false)
                    else -> {
                        throw HttpClientErrorException(
                            HttpStatus.BAD_REQUEST,
                            "Grunnlagstype $grunnlagstype støttes ikke ved oppdatering av opplysninger.",
                        )
                    }
                }

                return grunnlagRepository.save<Grunnlag>(
                    Grunnlag(
                        it,
                        nyGrunnlagstype.type,
                        erBearbeidet = nyGrunnlagstype.erBearbeidet,
                        data = data,
                        rolle = it.bidragsmottaker!!,
                        innhentet = innhentet,
                    ),
                )
            }
    }

    @Transactional
    fun oppdatereGrunnlagForBehandling(behandling: Behandling) {
        if (foretaNyGrunnlagsinnhenting(behandling)) {
            val grunnlagRequestobjekter =
                bidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)

            grunnlagRequestobjekter.forEach {
                henteOglagreGrunnlag(
                    behandling.id!!,
                    it,
                )
            }

            behandlingRepository.oppdatereTidspunktGrunnlagsinnhenting(behandling.id!!)
        } else {
            val nesteInnhenting =
                behandling.grunnlagSistInnhentet?.plusHours(grenseInnhenting.toLong())

            log.info {
                "Grunnlag for behandling ${behandling.id} ble sist innhentet ${behandling.grunnlagSistInnhentet}. " +
                        "Ny innhenting vil tidligst blir foretatt $nesteInnhenting."
            }
        }
    }

    @Transactional
    fun aktivereGrunnlag(
        behandling: Behandling,
        iderTilGrunnlagSomSkalAktiveres: Set<Long>,
    ) {
        val aktiveringstidspunkt = LocalDateTime.now()

        behandling.roller.filter { it.ident != null }.forEach { rolle ->
            val grunnlag =
                behandling.grunnlag.filter {
                    it.aktiv == null && rolle.ident == it.rolle.ident &&
                            iderTilGrunnlagSomSkalAktiveres.contains(it.id)
                }.filter { Grunnlagsdatatype.INNTEKT == it.type }.toSet()

            if (grunnlag.isNotEmpty()) {
                aktivereGrunnlag(
                    behandling,
                    rolle,
                    grunnlag,
                    Grunnlagsdatatype.INNTEKT,
                    aktiveringstidspunkt,
                )
            } else {
                log.info {
                    "Fant ingen grunnlag å oppdatere for ${rolle.rolletype} med id ${rolle.id} i behandling " +
                            "${behandling.id}"
                }
            }
        }
    }

    fun hentSistInnhentet(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagstype: Grunnlagstype,
    ): Grunnlag? {
        return grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
            behandlingsid,
            rolleid,
            grunnlagstype.type,
            grunnlagstype.erBearbeidet,
        )
    }

    fun henteNyeGrunnlagsdataMedEndringsdiff(
        behandlingsid: Long,
        roller: Set<Rolle>,
    ): Set<GrunnlagsdataEndretDto> {
        val nyinnhentaGrunnlag =
            roller.flatMap { hentAlleSistInnhentet(behandlingsid, it.id!!) }.toSet()
                .filter { g -> g.aktiv == null }

        val grunnlagstyperEndretIBearbeidaInntekter =
            nyinnhentaGrunnlag.filter { inntekterOgYtelser.contains(it.type) }.map { it.type }
                .toSet()

        return nyinnhentaGrunnlag.filter { it.type == Grunnlagsdatatype.INNTEKT_BEARBEIDET }.map {
            GrunnlagsdataEndretDto(
                nyeData = it.toDto(),
                endringerINyeData = grunnlagstyperEndretIBearbeidaInntekter,
            )
        }.toSet() +
                nyinnhentaGrunnlag.filter {
                    it.type != Grunnlagsdatatype.INNTEKT_BEARBEIDET &&
                            !inntekterOgYtelser.contains(
                                it.type,
                            )
                }.map {
                    GrunnlagsdataEndretDto(nyeData = it.toDto(), endringerINyeData = setOf(it.type))
                }.toSet()
    }

    fun hentAlleSistInnhentet(
        behandlingsid: Long,
        rolleid: Long,
    ): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().flatMap { grunnlagstype ->
            listOf(true, false).mapNotNull { erBearbeidet ->
                grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid,
                    rolleid,
                    grunnlagstype,
                    erBearbeidet,
                )
            }
        }

    fun henteGjeldendeAktiveGrunnlagsdata(behandling: Behandling): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().flatMap { type ->
            listOf(true, false).flatMap { erBearbeidet ->
                behandling.roller.mapNotNull { rolle ->
                    grunnlagRepository.findTopByBehandlingIdAndTypeAndErBearbeidetAndRolleOrderByAktivDescIdDesc(
                        behandling.id!!,
                        type,
                        erBearbeidet,
                        rolle,
                    )
                }
            }
        }

    private fun aktivereGrunnlag(
        behandling: Behandling,
        rolle: Rolle,
        grunnlagSomSkalAktiveres: Set<Grunnlag>,
        grunnlagsdatatype: Grunnlagsdatatype,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val inntektsgrunnlag = grunnlagSomSkalAktiveres.maxBy { it.innhentet }

        log.info {
            "Behandler forespørsel om å aktivere grunnlag av type $grunnlagsdatatype, innhentet " +
                    " $inntektsgrunnlag for behandling med id ${inntektsgrunnlag.behandling.id}"
        }

        when (grunnlagsdatatype) {
            Grunnlagsdatatype.INNTEKT ->
                aktivereGrunnlagOgOppdatereInntekter(
                    behandling,
                    inntektsgrunnlag,
                    rolle,
                    aktiveringstidspunkt,
                )

            else -> {
                // TODO: Implementere støtte for alle grunnlagstyper
                log.warn { "Implementasjon mangler for å håndtere oppdatering av grunnlag av type $grunnlagsdatatype" }
            }
        }
    }

    private fun aktivereGrunnlagOgOppdatereInntekter(
        behandling: Behandling,
        inntektsgrunnlag: Grunnlag,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val grunnlagInntekt = jsonTilObjekt<GrunnlagInntekt>(inntektsgrunnlag.data)

        val transformereInntekter =
            TransformerInntekterRequest(
                ainntektHentetDato = inntektsgrunnlag.innhentet.toLocalDate(),
                ainntektsposter = grunnlagInntekt.ainntekt.flatMap { it.ainntektspostListe.tilAinntektsposter() },
                kontantstøtteliste = emptyList(),
                skattegrunnlagsliste = grunnlagInntekt.skattegrunnlag.tilSkattegrunnlagForLigningsår(),
                småbarnstilleggliste = emptyList(),
                utvidetBarnetrygdliste = emptyList(),
            )

        val summertAinntektOgSkattegrunnlag = inntektApi.transformerInntekter(transformereInntekter)

        inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
            behandling,
            rolle,
            Grunnlagsdatatype.INNTEKT,
            summertAinntektOgSkattegrunnlag,
        )

        behandling.grunnlag.filter { it.id == inntektsgrunnlag.id }
            .forEach { it.aktiv = aktiveringstidspunkt }
    }

    private fun foretaNyGrunnlagsinnhenting(behandling: Behandling): Boolean {
        return behandling.grunnlagSistInnhentet == null ||
                LocalDateTime.now()
                    .minusHours(grenseInnhenting.toLong()) > behandling.grunnlagSistInnhentet
    }

    private fun henteOglagreGrunnlag(
        behandlingsid: Long,
        grunnlagsrequest: Map.Entry<Personident, List<GrunnlagRequestDto>>,
    ) {
        val innhentetGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(grunnlagsrequest.value)

        val rolleInhentetFor =
            rolleRepository.findRollerByBehandlingId(behandlingsid)
                .first { it.ident == grunnlagsrequest.key.verdi }

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.ARBEIDSFORHOLD, false),
            innhentetGrunnlag.arbeidsforholdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, false),
            innhentetGrunnlag.barnetilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.BARNETILSYN, false),
            innhentetGrunnlag.barnetilsynListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, false),
            innhentetGrunnlag.kontantstøtteListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
            innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
            innhentetGrunnlag.sivilstandListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, false),
            innhentetGrunnlag.småbarnstilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, false),
            innhentetGrunnlag.utvidetBarnetrygdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreInntektHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            innhentetGrunnlag.hentetTidspunkt,
            Grunnlagstype(Grunnlagsdatatype.INNTEKT, false),
            innhentetGrunnlag.tilGrunnlagInntekt(),
        )

        val transformereInntekter =
            TransformerInntekterRequest(
                ainntektHentetDato = innhentetGrunnlag.hentetTidspunkt.toLocalDate(),
                ainntektsposter = innhentetGrunnlag.ainntektListe.flatMap { it.ainntektspostListe.tilAinntektsposter() },
                kontantstøtteliste = innhentetGrunnlag.kontantstøtteListe.tilKontantstøtte(),
                skattegrunnlagsliste = innhentetGrunnlag.skattegrunnlagListe.tilSkattegrunnlagForLigningsår(),
                småbarnstilleggliste = innhentetGrunnlag.småbarnstilleggListe.tilSmåbarnstillegg(),
                utvidetBarnetrygdliste = innhentetGrunnlag.utvidetBarnetrygdListe.tilUtvidetBarnetrygd(),
            )

        val sammenstilteInntekter = inntektApi.transformerInntekter(transformereInntekter)

        lagreInntektHvisEndret(
            behandlingsid,
            rolleInhentetFor,
            innhentetGrunnlag.hentetTidspunkt,
            Grunnlagstype(Grunnlagsdatatype.INNTEKT, true),
            sammenstilteInntekter.tilSummerteMånedsOgÅrsinntekter(),
        )
    }

    private fun opprett(
        behandlingsid: Long,
        idTilRolleInnhentetFor: Long,
        grunnlagstype: Grunnlagstype,
        data: String,
        innhentet: LocalDateTime,
        aktiv: LocalDateTime? = null,
    ) {
        log.info { "Lagrer inntentet grunnlag $grunnlagstype for behandling med id $behandlingsid" }

        behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                it.grunnlag.add(
                    Grunnlag(
                        it,
                        grunnlagstype.type,
                        grunnlagstype.erBearbeidet,
                        data = data,
                        innhentet = innhentet,
                        aktiv = aktiv,
                        rolle = it.roller.first { r -> r.id == idTilRolleInnhentetFor },
                    ),
                )
            }
    }

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandlingsid: Long,
        rolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: Set<T>,
        hentetTidspunkt: LocalDateTime,
    ) {
        val sistInnhentedeGrunnlagAvTypeForRolle: Set<T> =
            henteNyesteGrunnlagsdatasett<T>(behandlingsid, rolle.id!!, grunnlagstype).toSet()

        if ((sistInnhentedeGrunnlagAvTypeForRolle.isEmpty() && innhentetGrunnlag.isNotEmpty()) ||
            (sistInnhentedeGrunnlagAvTypeForRolle.isNotEmpty() && innhentetGrunnlag != sistInnhentedeGrunnlagAvTypeForRolle)
        ) {
            opprett(
                behandlingsid = behandlingsid,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistInnhentedeGrunnlagAvTypeForRolle.isEmpty() && innhentetGrunnlag.isNotEmpty()) LocalDateTime.now() else null,
                idTilRolleInnhentetFor = rolle.id!!,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private inline fun <reified T> lagreInntektHvisEndret(
        behandlingsid: Long,
        rolle: Rolle,
        hentetTidspunkt: LocalDateTime,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: T,
    ) {
        val sistInnhentedeGrunnlagAvType: T? =
            henteNyesteGrunnlagsdataobjekt<T>(behandlingsid, rolle.id!!, grunnlagstype)

        if ((sistInnhentedeGrunnlagAvType == null && inneholderInntekter(innhentetGrunnlag)) ||
            (sistInnhentedeGrunnlagAvType != null && innhentetGrunnlag != sistInnhentedeGrunnlagAvType)
        ) {
            opprett(
                behandlingsid = behandlingsid,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistInnhentedeGrunnlagAvType == null) LocalDateTime.now() else null,
                idTilRolleInnhentetFor = rolle.id!!,
            )

            // Oppdatere inntektstabell med sammenstilte offentlige inntekter
            if (Grunnlagsdatatype.INNTEKT == grunnlagstype.type
                && grunnlagstype.erBearbeidet
                && sistInnhentedeGrunnlagAvType == null
            ) {
                inntektService.lagreInntekter(
                    behandlingsid,
                    Personident(rolle.ident!!),
                    innhentetGrunnlag as SummerteMånedsOgÅrsinntekter,
                )
            }
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    fun <T> inneholderInntekter(grunnlag: T): Boolean {
        return when (grunnlag) {
            is GrunnlagInntekt -> grunnlag.ainntekt.isNotEmpty() || grunnlag.skattegrunnlag.isNotEmpty()
            is SummerteMånedsOgÅrsinntekter -> grunnlag.summerteÅrsinntekter.isNotEmpty() || grunnlag.summerteMånedsinntekter.isNotEmpty()
            else -> {
                log.error { "Grunnlag er ikke en inntektstype" }
                secureLogger.error { "Grunnlag $grunnlag er ikke en inntektstype" }
                throw IllegalArgumentException("Grunnlag er ikke en inntektstype")
            }
        }
    }

    private inline fun <reified T> henteNyesteGrunnlagsdatasett(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagstype: Grunnlagstype,
    ): Set<T> {
        val grunnlagsdata = hentSistInnhentet(behandlingsid, rolleid, grunnlagstype)?.data

        return if (grunnlagsdata != null) {
            jsonListeTilObjekt<T>(grunnlagsdata)
        } else {
            emptySet()
        }
    }

    private inline fun <reified T> henteNyesteGrunnlagsdataobjekt(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagstype: Grunnlagstype,
    ): T? {
        val grunnlagsdata = hentSistInnhentet(behandlingsid, rolleid, grunnlagstype)?.data

        return if (grunnlagsdata != null) {
            jsonTilObjekt<T>(grunnlagsdata)
        } else {
            null
        }
    }
}
