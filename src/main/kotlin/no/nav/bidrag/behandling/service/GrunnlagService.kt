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
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.grunnlag.tilGrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.objektTilJson
import no.nav.bidrag.behandling.transformers.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.tilSkattegrunnlagForLigningsår
import no.nav.bidrag.behandling.transformers.tilUtvidetBarnetrygdOgSmåbarnstillegg
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
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

        lagreGrunnlag(
            behandling.id!!,
            Grunnlagsdatatype.ARBEIDSFORHOLD,
            innhentetGrunnlag.arbeidsforholdListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlag(
            behandling.id!!,
            Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
            innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreGrunnlag(
            behandling.id!!,
            Grunnlagsdatatype.SIVILSTAND,
            innhentetGrunnlag.sivilstandListe.toSet(),
            innhentetGrunnlag.hentetTidspunkt,
        )

        lagreInntektHvisEndret(
            behandling.id!!,
            innhentetGrunnlag,
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

        sammenstilteInntekter.lagreGrunnlag(behandling.id!!, Grunnlagsdatatype.INNTEKT_BEARBEIDET)
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
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                behandlingId,
                it,
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

    private fun <T> lagreGrunnlag(
        behandlingsid: Long,
        grunnlagstype: Grunnlagsdatatype,
        innhentetGrunnlag: Set<T>,
        hentetTidspunkt: LocalDateTime,
    ) {
        val sistInnhentedeGrunnlagAvType: Set<T> =
            henteNyesteGrunnlagsdata(behandlingsid, grunnlagstype)

        if (sistInnhentedeGrunnlagAvType != innhentetGrunnlag) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag),
                grunnlagsdatatype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv = if (sistInnhentedeGrunnlagAvType.isEmpty()) LocalDateTime.now() else null,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private fun lagreInntektHvisEndret(
        behandlingsid: Long,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        val grunnlagstype = Grunnlagsdatatype.INNTEKT
        val innhentetGrunnlagInntekt = innhentetGrunnlag.tilGrunnlagInntekt()

        if (erInntektEndret(behandlingsid, innhentetGrunnlagInntekt)) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag.tilGrunnlagInntekt()),
                grunnlagsdatatype = grunnlagstype,
                innhentet = innhentetGrunnlag.hentetTidspunkt,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private fun erInntektEndret(
        behandlingsid: Long,
        grunnlagInn: GrunnlagInntekt,
    ): Boolean {
        val lagretGrunnlagInntekt =
            Gson().fromJson(
                hentSistInnhentet(behandlingsid, Grunnlagsdatatype.INNTEKT)?.data,
                GrunnlagInntekt::class.java,
            )
        val erEndret = lagretGrunnlagInntekt != grunnlagInn
        return erEndret
    }

    private fun lagreSummertInntektHvisEndret(
        behandlingsid: Long,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        val grunnlagstype = Grunnlagsdatatype.INNTEKT_BEARBEIDET
        val summertMånedsOgÅrsinntekt = innhentetGrunnlag.tilSummertMånedsOgÅrsinntekt()

        if (erInntektEndret(behandlingsid, innhentetGrunnlagInntekt)) {
            opprett(
                behandlingsid = behandlingsid,
                data = objektTilJson(innhentetGrunnlag.tilGrunnlagInntekt()),
                grunnlagsdatatype = grunnlagstype,
                innhentet = innhentetGrunnlag.hentetTidspunkt,
            )
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandlingsid." }
        }
    }

    private fun <T> henteNyesteGrunnlagsdata(
        behandlingsid: Long,
        grunnlagstype: Grunnlagsdatatype,
    ): Set<T> {
        val typeinfo: Type = object : TypeToken<ArrayList<T>>() {}.type

        val grunnlagsdata = hentSistInnhentet(behandlingsid, grunnlagstype)?.data

        return if (grunnlagsdata != null) {
            Gson().fromJson(
                hentSistInnhentet(behandlingsid, grunnlagstype)?.data,
                typeinfo,
            )
        } else {
            emptySet()
        }
    }
}
