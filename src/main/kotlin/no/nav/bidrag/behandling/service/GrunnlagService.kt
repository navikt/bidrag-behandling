package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.getOrMigrate
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.database.grunnlag.tilGrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.objektTilJson
import no.nav.bidrag.behandling.transformers.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.tilSkattegrunnlagForLigningsår
import no.nav.bidrag.behandling.transformers.tilSmåbarnstillegg
import no.nav.bidrag.behandling.transformers.tilSummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.transformers.tilUtvidetBarnetrygd
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.grunnlag.request.GrunnlagRequestDto
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class GrunnlagService(
    private val grunnlagRepository: GrunnlagRepository,
    private val behandlingRepository: BehandlingRepository,
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val inntektApi: InntektApi,
    private val inntektService: InntektService,
) {
    @Transactional
    fun oppdatereGrunnlagForBehandling(behandling: Behandling) {
        val grunnlagRequestobjekter = bidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)

        grunnlagRequestobjekter.forEach {
            henteOglagreGrunnlag(
                behandling.id!!,
                it,
            )
        }
    }

    @Transactional
    fun aktivereGrunnlag(iderTilGrunnlagSomSkalAktiveres: Set<Long>) {
        grunnlagRepository.aktivereGrunnlag(iderTilGrunnlagSomSkalAktiveres, LocalDateTime.now())
    }

    fun hentSistInnhentet(
        behandlingsid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag? {
        return grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDesc(
            behandlingsid,
            grunnlagsdatatype.getOrMigrate(),
        )
    }

    fun hentAlleSistInnhentet(behandlingId: Long): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().mapNotNull {
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDesc(behandlingId, it)
        }

    fun henteGjeldendeAktiveGrunnlagsdata(behandlingId: Long): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().mapNotNull {
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByAktivDescIdDesc(behandlingId, it)
        }

    private fun henteOglagreGrunnlag(
        behandlingsid: Long,
        grunnlagsrequest: Map.Entry<Personident, List<GrunnlagRequestDto>>,
    ) {
        val innhentetGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(grunnlagsrequest.value)

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.ARBEIDSFORHOLD,
            innhentetGrunnlag.arbeidsforholdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.BARNETILLEGG,
            innhentetGrunnlag.barnetilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.BARNETILSYN,
            innhentetGrunnlag.barnetilsynListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.KONTANTSTØTTE,
            innhentetGrunnlag.kontantstøtteListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
            innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.SIVILSTAND,
            innhentetGrunnlag.sivilstandListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.SMÅBARNSTILLEGG,
            innhentetGrunnlag.småbarnstilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandlingsid,
            Grunnlagsdatatype.UTVIDET_BARNETRYGD,
            innhentetGrunnlag.utvidetBarnetrygdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreInntektHvisEndret(
            behandlingsid,
            grunnlagsrequest.key,
            innhentetGrunnlag.hentetTidspunkt,
            Grunnlagsdatatype.INNTEKT,
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
            grunnlagsrequest.key,
            innhentetGrunnlag.hentetTidspunkt,
            Grunnlagsdatatype.INNTEKT_BEARBEIDET,
            sammenstilteInntekter.tilSummerteMånedsOgÅrsinntekter(),
        )
    }

    private fun opprett(
        behandlingsid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        data: String,
        innhentet: LocalDateTime,
        aktiv: LocalDateTime? = null,
    ) {
        log.info { "Lagrer inntentet grunnlag $grunnlagsdatatype for behandling med id $behandlingsid" }

        behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                it.grunnlag.add(
                    Grunnlag(
                        it,
                        grunnlagsdatatype.getOrMigrate(),
                        data = data,
                        innhentet = innhentet,
                        aktiv = aktiv,
                    ),
                )
            }
    }

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandlingsid: Long,
        grunnlagstype: Grunnlagsdatatype,
        innhentetGrunnlag: Set<T>,
        hentetTidspunkt: LocalDateTime,
    ) {
        val sistInnhentedeGrunnlagAvType: Set<T> =
            henteNyesteGrunnlagsdatasett<T>(behandlingsid, grunnlagstype).toSet()

        if ((sistInnhentedeGrunnlagAvType.isEmpty() && innhentetGrunnlag.isNotEmpty()) ||
            (sistInnhentedeGrunnlagAvType.isNotEmpty() && innhentetGrunnlag != sistInnhentedeGrunnlagAvType)
        ) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag),
                grunnlagsdatatype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistInnhentedeGrunnlagAvType.isEmpty() && innhentetGrunnlag.isNotEmpty()) LocalDateTime.now() else null,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private inline fun <reified T> lagreInntektHvisEndret(
        behandlingsid: Long,
        personident: Personident,
        hentetTidspunkt: LocalDateTime,
        grunnlagstype: Grunnlagsdatatype,
        innhentetGrunnlag: T,
    ) {
        val sistInnhentedeGrunnlagAvType: T? =
            henteNyesteGrunnlagsdataobjekt<T>(behandlingsid, grunnlagstype)

        if ((sistInnhentedeGrunnlagAvType == null && inneholderInntekter(innhentetGrunnlag)) ||
            (sistInnhentedeGrunnlagAvType != null && innhentetGrunnlag != sistInnhentedeGrunnlagAvType)
        ) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag),
                grunnlagsdatatype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistInnhentedeGrunnlagAvType == null) LocalDateTime.now() else null,
            )

            // Oppdatere inntektstabell med sammenstilte offentlige inntekter
            if (Grunnlagsdatatype.INNTEKT_BEARBEIDET == grunnlagstype && sistInnhentedeGrunnlagAvType == null) {
                inntektService.oppdatereInntekterFraGrunnlag(
                    behandlingsid,
                    personident,
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
        grunnlagstype: Grunnlagsdatatype,
    ): Set<T> {
        val grunnlagsdata = hentSistInnhentet(behandlingsid, grunnlagstype)?.data

        return if (grunnlagsdata != null) {
            jsonListeTilObjekt<T>(grunnlagsdata)
        } else {
            emptySet()
        }
    }

    private inline fun <reified T> henteNyesteGrunnlagsdataobjekt(
        behandlingsid: Long,
        grunnlagstype: Grunnlagsdatatype,
    ): T? {
        val grunnlagsdata = hentSistInnhentet(behandlingsid, grunnlagstype)?.data

        return if (grunnlagsdata != null) {
            jsonTilObjekt<T>(grunnlagsdata)
        } else {
            null
        }
    }
}
