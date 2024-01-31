package no.nav.bidrag.behandling.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.getOrMigrate
import no.nav.bidrag.behandling.database.grunnlag.tilGrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.objektTilJson
import no.nav.bidrag.behandling.transformers.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.tilSkattegrunnlagForLigningsår
import no.nav.bidrag.behandling.transformers.tilSummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.transformers.tilUtvidetBarnetrygdOgSmåbarnstillegg
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Type
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class GrunnlagService(
    private val grunnlagRepository: GrunnlagRepository,
    private val behandlingRepository: BehandlingRepository,
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val inntektApi: InntektApi,
) {
    @Transactional
    fun oppdatereGrunnlagForBehandling(behandling: Behandling) {
        val innhentetGrunnlag =
            bidragGrunnlagConsumer.henteGrunnlagForBmOgBarnIBehandling(
                Personident(behandling.getBidragsmottaker()!!.ident!!),
                behandling.getSøknadsbarn().filter { it.ident != null }.map { Personident(it.ident!!) },
            )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.ARBEIDSFORHOLD,
            innhentetGrunnlag.arbeidsforholdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.BARNETILLEGG,
            innhentetGrunnlag.barnetilleggListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.BARNETILSYN,
            innhentetGrunnlag.barnetilsynListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.KONTANTSTØTTE,
            innhentetGrunnlag.kontantstøtteListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
            innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.SIVILSTAND,
            innhentetGrunnlag.sivilstandListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlagHvisEndret(
            behandling.id!!,
            Grunnlagsdatatype.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG,
            innhentetGrunnlag.ubstListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreInntektHvisEndret(
            behandling.id!!,
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
                utvidetBarnetrygdOgSmåbarnstilleggliste = innhentetGrunnlag.ubstListe.tilUtvidetBarnetrygdOgSmåbarnstillegg(),
            )

        val sammenstilteInntekter = inntektApi.transformerInntekter(transformereInntekter)

        lagreInntektHvisEndret(
            behandling.id!!,
            innhentetGrunnlag.hentetTidspunkt,
            Grunnlagsdatatype.INNTEKT_BEARBEIDET,
            sammenstilteInntekter.tilSummerteMånedsOgÅrsinntekter(),
        )
    }

    fun hentSistInnhentet(
        behandlingsid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag? {
        return grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
            behandlingsid,
            grunnlagsdatatype.getOrMigrate(),
        )
    }

    fun hentAlleSistInnhentet(behandlingId: Long): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().mapNotNull {
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(behandlingId, it)
        }

    fun henteGjeldendeAktiveGrunnlagsdatahenteGjeldendeAktiveGrunnlagsdata(behandlingId: Long): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().mapNotNull {
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByAktivDescIdDesc(behandlingId, it)
        }

    private fun opprett(
        behandlingsid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        data: String,
        innhentet: LocalDateTime,
        aktiv: LocalDateTime? = null,
    ) {
        log.info { "Lagrer inntentet grunnlag $grunnlagsdatatype for behandling med id $behandlingsid" }

        behandlingRepository
            .findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }
            .let {
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

    private fun <T> lagreGrunnlagHvisEndret(
        behandlingsid: Long,
        grunnlagstype: Grunnlagsdatatype,
        innhentetGrunnlag: Set<T>,
        hentetTidspunkt: LocalDateTime,
    ) {
        val sistInnhentedeGrunnlagAvType: Set<T>? =
            henteNyesteGrunnlagsdata(behandlingsid, grunnlagstype)

        if (sistInnhentedeGrunnlagAvType == null || innhentetGrunnlag != sistInnhentedeGrunnlagAvType) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag),
                grunnlagsdatatype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistInnhentedeGrunnlagAvType == null) LocalDateTime.now() else null,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private fun <T> lagreInntektHvisEndret(
        behandlingsid: Long,
        hentetTidspunkt: LocalDateTime,
        grunnlagstype: Grunnlagsdatatype,
        innhentetGrunnlag: T,
    ) {
        val sistLagredeGrunnlagAvSammeType = tilGrunnlagsdata<T>(hentSistInnhentet(behandlingsid, grunnlagstype))

        if (innhentetGrunnlag != sistLagredeGrunnlagAvSammeType) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag),
                grunnlagsdatatype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistLagredeGrunnlagAvSammeType == null) LocalDateTime.now() else null,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private fun <T> tilGrunnlagsdata(lagretGrunnlag: Grunnlag?): T {
        val targetClassType: Type = object : TypeToken<T?>() {}.type

        val lagretGrunnlagsdata: T =
            Gson().fromJson(
                lagretGrunnlag?.data,
                targetClassType,
            )

        return lagretGrunnlagsdata
    }

    private fun <T> henteNyesteGrunnlagsdata(
        behandlingsid: Long,
        grunnlagstype: Grunnlagsdatatype,
    ): Set<T>? {
        val typeinfo: Type = object : TypeToken<ArrayList<T>>() {}.type

        val grunnlagsdata = hentSistInnhentet(behandlingsid, grunnlagstype)?.data

        return if (grunnlagsdata != null) {
            Gson().fromJson(
                hentSistInnhentet(behandlingsid, grunnlagstype)?.data,
                typeinfo,
            )
        } else {
            null
        }
    }
}
