package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.RolleRepository
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataEndretDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequest
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.TransformerInntekterRequestBuilder
import no.nav.bidrag.behandling.transformers.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.tilSkattegrunnlagForLigningsår
import no.nav.bidrag.behandling.transformers.tilSmåbarnstillegg
import no.nav.bidrag.behandling.transformers.tilUtvidetBarnetrygd
import no.nav.bidrag.behandling.transformers.toDto
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.grunnlag.request.GrunnlagRequestDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.apache.commons.lang3.Validate
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

val inntekterOgYtelser =
    setOf(
        Grunnlagsdatatype.AINNTEKT,
        Grunnlagsdatatype.KONTANTSTØTTE,
        Grunnlagsdatatype.SMÅBARNSTILLEGG,
        Grunnlagsdatatype.SKATTEGRUNNLAG,
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
    private val entityManager: EntityManager,
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
                val nyGrunnlagstype =
                    when (grunnlagstype) {
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
                    behandling,
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
        aktivereGrunnlagRequest: AktivereGrunnlagRequest,
    ) {
        val aktiveringstidspunkt = LocalDateTime.now()

        val rolleGrunnlagSkalAktiveresFor =
            behandling.roller.find { aktivereGrunnlagRequest.personident.verdi == it.ident }

        Validate.notNull(
            rolleGrunnlagSkalAktiveresFor,
            "Personident oppgitt i AktivereGrunnlagRequest har ikke rolle i behandling ${behandling.id}",
        )

        aktivereGrunnlagRequest.grunnlagsdatatyper.forEach { grunnlagstype ->
            aktivereGrunnlagOgOppdatereInntektstabell(
                behandling = behandling,
                rolle = rolleGrunnlagSkalAktiveresFor!!,
                inntektsOgYtelsesgrunnlag =
                    aktivereGrunnlagRequest.grunnlagsdatatyper.filter {
                        inntekterOgYtelser.contains(
                            grunnlagstype,
                        )
                    }.toSet(),
                aktiveringstidspunkt = aktiveringstidspunkt,
            )
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

    private fun aktivereGrunnlagOgOppdatereInntektstabell(
        behandling: Behandling,
        inntektsOgYtelsesgrunnlag: Set<Grunnlagsdatatype>,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val skalAktivereOgOppdatereAinntektOgSkattegrunnlag =
            inntektsOgYtelsesgrunnlag.any { Grunnlagsdatatype.AINNTEKT == it || Grunnlagsdatatype.SKATTEGRUNNLAG == it }

        if (skalAktivereOgOppdatereAinntektOgSkattegrunnlag) {
            aktivereOgOppdatereSkattbareInntekter(
                behandling,
                rolle,
                aktiveringstidspunkt,
            )
        }
    }

    private fun aktivereOgOppdatereSkattbareInntekter(
        behandling: Behandling,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val sistInnhentaAinntekt =
            behandling.grunnlag
                .filter { rolle.ident == it.rolle.ident }
                .filter { Grunnlagsdatatype.AINNTEKT == it.type }
                .maxByOrNull { it.innhentet }

        val sistInnhentaSkattegrunnlag =
            behandling.grunnlag
                .filter { rolle.ident == it.rolle.ident }
                .filter { Grunnlagsdatatype.SKATTEGRUNNLAG == it.type }
                .maxByOrNull { it.innhentet }

        val transformereInntekter =
            TransformerInntekterRequestBuilder(
                ainntektsposter =
                    sistInnhentaAinntekt
                        ?.let { grunnlag ->
                            var respons: List<AinntektspostDto> = emptyList()
                            if (grunnlag.data.trim().isNotEmpty()) {
                                respons =
                                    jsonListeTilObjekt<AinntektGrunnlagDto>(grunnlag.data).flatMap { it.ainntektspostListe }
                            }
                            respons.tilAinntektsposter()
                        } ?: emptyList(),
                skattegrunnlag =
                    sistInnhentaSkattegrunnlag?.let {
                        var respons: List<SkattegrunnlagGrunnlagDto> = emptyList()
                        if (it.data.trim().isNotEmpty()) {
                            respons = jsonListeTilObjekt<SkattegrunnlagGrunnlagDto>(it.data).toList()
                        }
                        respons.tilSkattegrunnlagForLigningsår()
                    } ?: emptyList(),
            ).bygge()

        val summertAinntektstyper =
            setOf(Inntektsrapportering.AINNTEKT_BEREGNET_3MND, Inntektsrapportering.AINNTEKT_BEREGNET_12MND)
        val summertAinntektOgSkattegrunnlag = inntektApi.transformerInntekter(transformereInntekter)

        lagreInntektsgrunnlagHvisEndret(
            behandling.id!!,
            rolle,
            LocalDateTime.now(),
            Grunnlagstype(Grunnlagsdatatype.AINNTEKT, true),
            SummerteInntekter(
                versjon = summertAinntektOgSkattegrunnlag.versjon,
                gjelderIdent = rolle.ident!!,
                inntekter =
                    summertAinntektOgSkattegrunnlag.summertÅrsinntektListe
                        .filter { summertAinntektstyper.contains(it.inntektRapportering) },
            ),
            aktiveringstidspunkt,
        )

        lagreInntektsgrunnlagHvisEndret(
            behandling.id!!,
            rolle,
            LocalDateTime.now(),
            Grunnlagstype(Grunnlagsdatatype.SKATTEGRUNNLAG, true),
            SummerteInntekter(
                versjon = summertAinntektOgSkattegrunnlag.versjon,
                gjelderIdent = rolle.ident!!,
                inntekter =
                    summertAinntektOgSkattegrunnlag.summertÅrsinntektListe
                        .filter { !summertAinntektstyper.contains(it.inntektRapportering) },
            ),
            aktiveringstidspunkt,
        )

        lagreInntektsgrunnlagHvisEndret(
            behandling.id!!,
            rolle,
            LocalDateTime.now(),
            Grunnlagstype(Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER, true),
            SummerteInntekter(
                versjon = summertAinntektOgSkattegrunnlag.versjon,
                gjelderIdent = rolle.ident!!,
                inntekter = summertAinntektOgSkattegrunnlag.summertMånedsinntektListe,
            ),
            aktiveringstidspunkt,
        )
        entityManager.persist(behandling)

        inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
            behandling,
            rolle,
            summertAinntektOgSkattegrunnlag.summertÅrsinntektListe,
        )

        sistInnhentaAinntekt?.aktiv = aktiveringstidspunkt
        sistInnhentaSkattegrunnlag?.aktiv = aktiveringstidspunkt
        entityManager.flush()
    }

    private fun foretaNyGrunnlagsinnhenting(behandling: Behandling): Boolean {
        return !behandling.erVedtakFattet && (
            behandling.grunnlagSistInnhentet == null ||
                LocalDateTime.now()
                    .minusHours(grenseInnhenting.toLong()) > behandling.grunnlagSistInnhentet
        )
    }

    private fun henteOglagreGrunnlag(
        behandling: Behandling,
        grunnlagsrequest: Map.Entry<Personident, List<GrunnlagRequestDto>>,
    ) {
        val innhentetGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(grunnlagsrequest.value)
        val rolleInhentetFor = behandling.roller.first { grunnlagsrequest.key.verdi == it.ident }

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.ARBEIDSFORHOLD, false),
            innhentetGrunnlag.arbeidsforholdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.BARNETILLEGG, false),
            innhentetGrunnlag.barnetilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.BARNETILSYN, false),
            innhentetGrunnlag.barnetilsynListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.KONTANTSTØTTE, false),
            innhentetGrunnlag.kontantstøtteListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
            innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
            innhentetGrunnlag.sivilstandListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.SMÅBARNSTILLEGG, false),
            innhentetGrunnlag.småbarnstilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.UTVIDET_BARNETRYGD, false),
            innhentetGrunnlag.utvidetBarnetrygdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.AINNTEKT, false),
            innhentetGrunnlag.ainntektListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            rolleInhentetFor,
            Grunnlagstype(Grunnlagsdatatype.SKATTEGRUNNLAG, false),
            innhentetGrunnlag.skattegrunnlagListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        val transformereInntekter =
            TransformerInntekterRequest(
                ainntektHentetDato = innhentetGrunnlag.hentetTidspunkt.toLocalDate(),
                ainntektsposter =
                    innhentetGrunnlag.ainntektListe.flatMap {
                        it.ainntektspostListe.tilAinntektsposter(
                            rolleInhentetFor,
                        )
                    },
                kontantstøtteliste =
                    innhentetGrunnlag.kontantstøtteListe.tilKontantstøtte(
                        rolleInhentetFor,
                    ),
                skattegrunnlagsliste =
                    innhentetGrunnlag.skattegrunnlagListe.tilSkattegrunnlagForLigningsår(
                        rolleInhentetFor,
                    ),
                småbarnstilleggliste =
                    innhentetGrunnlag.småbarnstilleggListe.tilSmåbarnstillegg(
                        rolleInhentetFor,
                    ),
                utvidetBarnetrygdliste =
                    innhentetGrunnlag.utvidetBarnetrygdListe.tilUtvidetBarnetrygd(
                        rolleInhentetFor,
                    ),
            )

        val sammenstilteInntekter = inntektApi.transformerInntekter(transformereInntekter)

        val grunnlagstyper: Set<Grunnlagsdatatype> =
            setOf(Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER, Grunnlagsdatatype.SUMMERTE_ÅRSINNTEKTER)

        val innhentetTidspunkt = LocalDateTime.now()

        grunnlagstyper.forEach { type ->
            val årsbaserteInntekterEllerYtelser: SummerteInntekter<*>? =
                when (type) {
                    Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER ->
                        SummerteInntekter(
                            versjon = sammenstilteInntekter.versjon,
                            gjelderIdent = rolleInhentetFor.ident!!,
                            inntekter = sammenstilteInntekter.summertMånedsinntektListe,
                        )

                    Grunnlagsdatatype.SUMMERTE_ÅRSINNTEKTER ->
                        SummerteInntekter(
                            versjon = sammenstilteInntekter.versjon,
                            gjelderIdent = rolleInhentetFor.ident!!,
                            inntekter = sammenstilteInntekter.summertÅrsinntektListe,
                        )

                    // Ikke-tilgjengelig kode
                    else -> null
                }

            lagreInntektsgrunnlagHvisEndret(
                behandling.id!!,
                rolleInhentetFor,
                innhentetTidspunkt,
                Grunnlagstype(type, true),
                årsbaserteInntekterEllerYtelser,
            )
        }
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

    private inline fun <reified T> lagreInntektsgrunnlagHvisEndret(
        behandlingsid: Long,
        rolle: Rolle,
        hentetTidspunkt: LocalDateTime,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: T,
        aktiveringstidspunkt: LocalDateTime? = null,
    ) {
        val sistInnhentedeGrunnlagAvType: T? =
            henteNyesteGrunnlagsdataobjekt<T>(behandlingsid, rolle.id!!, grunnlagstype)

        if ((sistInnhentedeGrunnlagAvType == null && innhentetGrunnlag != null && inneholderInntekter(innhentetGrunnlag)) ||
            (sistInnhentedeGrunnlagAvType != null && innhentetGrunnlag != sistInnhentedeGrunnlagAvType)
        ) {
            opprett(
                behandlingsid = behandlingsid,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = hentetTidspunkt,
                // Summerte månedsinntekter settes alltid til aktiv
                aktiv =
                    if (sistInnhentedeGrunnlagAvType == null ||
                        Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == grunnlagstype.type
                    ) {
                        LocalDateTime.now()
                    } else {
                        aktiveringstidspunkt
                    },
                idTilRolleInnhentetFor = rolle.id!!,
            )

            // Oppdatere inntektstabell med sammenstilte offentlige inntekter
            if (Grunnlagsdatatype.SUMMERTE_ÅRSINNTEKTER == grunnlagstype.type &&
                grunnlagstype.erBearbeidet &&
                sistInnhentedeGrunnlagAvType == null
            ) {
                @Suppress("UNCHECKED_CAST")
                inntektService.lagreInntekter(
                    behandlingsid,
                    Personident(rolle.ident!!),
                    innhentetGrunnlag as SummerteInntekter<SummertÅrsinntekt>,
                )
            }
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    fun <T> inneholderInntekter(grunnlag: T): Boolean {
        return when (grunnlag) {
            is AinntektGrunnlagDto -> grunnlag.ainntektspostListe.isNotEmpty()
            is SkattegrunnlagGrunnlagDto -> grunnlag.skattegrunnlagspostListe.isNotEmpty()
            is SummerteInntekter<*> -> grunnlag.inntekter.isNotEmpty()
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
            commonObjectmapper.readValue(grunnlagsdata)
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
            commonObjectmapper.readValue(grunnlagsdata)
        } else {
            null
        }
    }
}
