package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.aktiveringAvGrunnlagstypeIkkeStøttetException
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentBearbeidetInntekterForType
import no.nav.bidrag.behandling.database.datamodell.hentGrunnlagForType
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.getOrMigrate
import no.nav.bidrag.behandling.lagringAvGrunnlagFeiletException
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.transformers.grunnlag.inntekterOgYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.summertAinntektstyper
import no.nav.bidrag.behandling.transformers.grunnlag.summertSkattegrunnlagstyper
import no.nav.bidrag.behandling.transformers.inntekt.TransformerInntekterRequestBuilder
import no.nav.bidrag.behandling.transformers.inntekt.opprettTransformerInntekterRequest
import no.nav.bidrag.behandling.transformers.inntekt.tilAinntektsposter
import no.nav.bidrag.behandling.transformers.inntekt.tilBarnetillegg
import no.nav.bidrag.behandling.transformers.inntekt.tilKontantstøtte
import no.nav.bidrag.behandling.transformers.inntekt.tilSkattegrunnlagForLigningsår
import no.nav.bidrag.behandling.transformers.inntekt.tilSmåbarnstillegg
import no.nav.bidrag.behandling.transformers.inntekt.tilUtvidetBarnetrygd
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.BARNETILLEGG
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.KONTANTSTØTTE
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.SMÅBARNSTILLEGG
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.UTVIDET_BARNETRYGD
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.request.GrunnlagRequestDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.TransformerInntekterResponse
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.apache.commons.lang3.Validate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}
val grunnlagsdatatyperBm =
    setOf(
        Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
        Grunnlagsdatatype.SMÅBARNSTILLEGG,
        Grunnlagsdatatype.BARNETILLEGG,
        Grunnlagsdatatype.KONTANTSTØTTE,
        Grunnlagsdatatype.BOFORHOLD,
        Grunnlagsdatatype.SIVILSTAND,
        Grunnlagsdatatype.ARBEIDSFORHOLD,
    )

val grunnlagsdatatyperBarn =
    setOf(
        Grunnlagsdatatype.ARBEIDSFORHOLD,
        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
        Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
    )

@Service
class GrunnlagService(
    private val behandlingRepository: BehandlingRepository,
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val boforholdService: BoforholdService,
    private val entityManager: EntityManager,
    private val grunnlagRepository: GrunnlagRepository,
    private val inntektApi: InntektApi,
    private val inntektService: InntektService,
) {
    @Value("\${egenskaper.grunnlag.min-antall-minutter-siden-forrige-innhenting}")
    private lateinit var grenseInnhenting: String

    @Transactional
    @Deprecated("Grunnlagsinnhenting og opprettelse skal gjøres automatisk med oppdatereGrunnlagForBehandling")
    fun opprett(
        behandlingId: Long,
        grunnlagstype: Grunnlagsdatatype,
        data: String,
        innhentet: LocalDateTime,
    ): Grunnlag {
        behandlingRepository.findBehandlingById(behandlingId).orElseThrow { behandlingNotFoundException(behandlingId) }
            .let {
                val nyGrunnlagstype =
                    when (grunnlagstype) {
                        Grunnlagsdatatype.BOFORHOLD_BEARBEIDET ->
                            Grunnlagstype(
                                Grunnlagsdatatype.BOFORHOLD,
                                true,
                            )

                        Grunnlagsdatatype.HUSSTANDSMEDLEMMER ->
                            Grunnlagstype(
                                Grunnlagsdatatype.BOFORHOLD,
                                false,
                            )

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
                        nyGrunnlagstype.type.getOrMigrate(),
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
            val grunnlagRequestobjekter = bidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)
            val feilrapporteringer = mutableMapOf<Grunnlagsdatatype, FeilrapporteringDto?>()

            grunnlagRequestobjekter.forEach {
                feilrapporteringer += henteOglagreGrunnlag(behandling, it)
            }

            behandlingRepository.oppdatereTidspunktGrunnlagsinnhenting(behandling.id!!)
            if (feilrapporteringer.isNotEmpty()) {
                log.error {
                    "Det oppstod feil i fbm. innhenting av grunnlag for behandling ${behandling.id}. " +
                        "Innhentingen ble derfor ikke gjort for følgende grunnlagstyper: " +
                        "${feilrapporteringer.map { it.key }}"
                }
            }
        } else {
            val nesteInnhenting =
                behandling.grunnlagSistInnhentet?.plusMinutes(grenseInnhenting.toLong())

            log.info {
                "Grunnlag for behandling ${behandling.id} ble sist innhentet ${behandling.grunnlagSistInnhentet}. " +
                    "Ny innhenting vil tidligst blir foretatt $nesteInnhenting."
            }
        }
    }

    @Transactional
    fun aktivereGrunnlag(
        behandling: Behandling,
        request: AktivereGrunnlagRequestV2,
    ) {
        val rolleGrunnlagSkalAktiveresFor = behandling.roller.find { request.personident.verdi == it.ident }

        Validate.notNull(
            rolleGrunnlagSkalAktiveresFor,
            "Personident oppgitt i AktivereGrunnlagRequest har ikke rolle i behandling ${behandling.id}",
        )

        aktivereGrunnlag(
            behandling,
            rolleGrunnlagSkalAktiveresFor!!,
            request.grunnlagstype,
            request.overskriveManuelleOpplysninger,
        )
    }

    @Transactional
    fun aktivereGrunnlag(
        behandling: Behandling,
        rolleGrunnlagSkalAktiveresFor: Rolle,
        grunnlagstype: Grunnlagsdatatype,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        Validate.notNull(
            rolleGrunnlagSkalAktiveresFor,
            "Personident oppgitt i AktivereGrunnlagRequest har ikke rolle i behandling ${behandling.id}",
        )

        val harIkkeAktivGrunnlag =
            behandling.grunnlag
                .filter { rolleGrunnlagSkalAktiveresFor.ident == it.rolle.ident }
                .filter { grunnlagstype == it.type }.any { it.aktiv == null }

        if (!harIkkeAktivGrunnlag) {
            log.warn {
                "Fant ingen grunnlag med type $grunnlagstype å aktivere for rolleid " +
                    "${rolleGrunnlagSkalAktiveresFor.id} i behandling ${behandling.id} "
            }
            return
        }

        val aktiveringstidspunkt = LocalDateTime.now()

        if (inntekterOgYtelser.contains(grunnlagstype)) {
            aktivereYtelserOgInntekter(
                behandling,
                grunnlagstype,
                rolleGrunnlagSkalAktiveresFor,
                aktiveringstidspunkt,
            )
        } else if (Grunnlagsdatatype.BOFORHOLD == grunnlagstype) {
            aktivereBoforhold(
                behandling,
                grunnlagstype,
                rolleGrunnlagSkalAktiveresFor,
                aktiveringstidspunkt,
                overskriveManuelleOpplysninger,
            )
        } else if (Grunnlagsdatatype.SIVILSTAND == grunnlagstype) {
            aktivereSivilstand(
                behandling,
                grunnlagstype,
                rolleGrunnlagSkalAktiveresFor,
                aktiveringstidspunkt,
            )
        } else {
            log.error {
                "Grunnlagstype $grunnlagstype ikke støttet ved aktivering av grunnlag. Aktivering feilet " +
                    "for behandling ${behandling.id}  "
            }
            aktiveringAvGrunnlagstypeIkkeStøttetException(behandling.id!!)
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
            grunnlagstype.type.getOrMigrate(),
            grunnlagstype.erBearbeidet,
        )
    }

    fun henteNyeGrunnlagsdataMedEndringsdiff(behandling: Behandling): IkkeAktiveGrunnlagsdata {
        val roller = behandling.roller
        val inntekter = behandling.inntekter
        val nyinnhentetGrunnlag =
            roller.flatMap { hentAlleGrunnlag(behandling.id!!, it.id!!) }
                .hentAlleIkkeAktiv()
        val aktiveGrunnlag =
            roller.flatMap { hentAlleGrunnlag(behandling.id!!, it.id!!) }.toList()
                .hentAlleAktiv()
        return IkkeAktiveGrunnlagsdata(
            inntekter =
                IkkeAktiveInntekter(
                    årsinntekter =
                        roller.flatMap {
                            nyinnhentetGrunnlag.hentEndringerInntekter(
                                it,
                                inntekter,
                                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                            )
                        }.toSet(),
                    småbarnstillegg =
                        roller.flatMap {
                            nyinnhentetGrunnlag.hentEndringerInntekter(
                                it,
                                inntekter,
                                Grunnlagsdatatype.SMÅBARNSTILLEGG,
                            )
                        }.toSet(),
                    utvidetBarnetrygd =
                        roller.flatMap {
                            nyinnhentetGrunnlag.hentEndringerInntekter(
                                it,
                                inntekter,
                                Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                            )
                        }.toSet(),
                    kontantstøtte =
                        roller.flatMap {
                            nyinnhentetGrunnlag.hentEndringerInntekter(
                                it,
                                inntekter,
                                Grunnlagsdatatype.KONTANTSTØTTE,
                            )
                        }.toSet(),
                    barnetillegg =
                        roller.flatMap {
                            nyinnhentetGrunnlag.hentEndringerInntekter(
                                it,
                                inntekter,
                                Grunnlagsdatatype.BARNETILLEGG,
                            )
                        }.toSet(),
                ),
            husstandsbarn = nyinnhentetGrunnlag.hentEndringerBoforhold(aktiveGrunnlag),
            sivilstand = nyinnhentetGrunnlag.hentEndringerSivilstand(aktiveGrunnlag),
        )
    }

    fun hentAlleGrunnlag(
        behandlingsid: Long,
        rolleid: Long,
    ): List<Grunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().flatMap { grunnlagstype ->
            listOf(true, false).flatMap { erBearbeidet ->
                grunnlagRepository.findByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
                    behandlingsid,
                    rolleid,
                    grunnlagstype,
                    erBearbeidet,
                )
            }
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

    private fun aktivereYtelserOgInntekter(
        behandling: Behandling,
        grunnlagstype: Grunnlagsdatatype,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val ikkeAktivGrunnlag = behandling.grunnlag.toList().hentAlleIkkeAktiv()

        val summerteInntekter = ikkeAktivGrunnlag.hentBearbeidetInntekterForType(grunnlagstype, rolle.ident!!)

        inntektService.oppdatereAutomatiskInnhentetOffentligeInntekter(
            behandling,
            rolle,
            summerteInntekter?.inntekter ?: emptyList(),
        )
        ikkeAktivGrunnlag.hentGrunnlagForType(grunnlagstype, rolle.ident!!).oppdaterStatusTilAktiv(aktiveringstidspunkt)
    }

    private fun aktivereBoforhold(
        behandling: Behandling,
        grunnlagstype: Grunnlagsdatatype,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val ikkeAktivGrunnlag = behandling.grunnlag.toList().hentAlleIkkeAktiv()
        val sistInnhentedeRådata =
            ikkeAktivGrunnlag
                .filter { rolle!!.ident == it.rolle.ident }
                .filter { grunnlagstype == it.type }
                .filter { !it.erBearbeidet }
                .maxByOrNull { it.innhentet }
        val periodisertBoforhold =
            BoforholdApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                jsonTilObjekt<List<RelatertPersonGrunnlagDto>>(sistInnhentedeRådata!!.data).tilBoforholdRequest(),
            )

        lagreGrunnlagHvisEndret<BoforholdResponse>(
            behandling,
            rolle,
            Grunnlagstype(grunnlagstype, true),
            periodisertBoforhold.toSet(),
            LocalDateTime.now(),
            aktiveringstidspunkt,
        )

        entityManager.merge(behandling)
        boforholdService.oppdatereAutomatiskInnhentaBoforhold(
            behandling,
            periodisertBoforhold,
            overskriveManuelleOpplysninger,
        )
        ikkeAktivGrunnlag.hentGrunnlagForType(grunnlagstype, rolle.ident!!).oppdaterStatusTilAktiv(aktiveringstidspunkt)
        entityManager.flush()
    }

    private fun List<Grunnlag>.oppdaterStatusTilAktiv(aktiveringstidspunkt: LocalDateTime) {
        forEach {
            it.aktiv = aktiveringstidspunkt
        }
    }

    private fun aktivereSivilstand(
        behandling: Behandling,
        grunnlagstype: Grunnlagsdatatype,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val sistInnhentedeRådata =
            behandling.grunnlag.toList().hentAlleIkkeAktiv()
                .filter { rolle!!.ident == it.rolle.ident }
                .filter { grunnlagstype == it.type }
                .filter { !it.erBearbeidet }
                .maxByOrNull { it.innhentet }!!
        val periodisertSivilstand =
            SivilstandApi.beregn(
                behandling.virkningstidspunktEllerSøktFomDato,
                jsonTilObjekt<List<SivilstandGrunnlagDto>>(sistInnhentedeRådata.data),
            )

        lagreGrunnlagHvisEndret<SivilstandBeregnet>(
            behandling,
            rolle,
            Grunnlagstype(grunnlagstype, true),
            periodisertSivilstand,
            LocalDateTime.now(),
            aktiveringstidspunkt,
        )

        entityManager.merge(behandling)
        boforholdService.oppdatereAutomatiskInnhentaSivilstand(
            behandling,
            periodisertSivilstand,
        )
        sistInnhentedeRådata.aktiv = aktiveringstidspunkt
        entityManager.flush()
    }

    private fun foretaNyGrunnlagsinnhenting(behandling: Behandling): Boolean {
        return behandling.grunnlagSistInnhentet == null ||
            LocalDateTime.now().minusMinutes(grenseInnhenting.toLong()) > behandling.grunnlagSistInnhentet
    }

    private fun henteOglagreGrunnlag(
        behandling: Behandling,
        grunnlagsrequest: Map.Entry<Personident, List<GrunnlagRequestDto>>,
    ): Map<Grunnlagsdatatype, FeilrapporteringDto?> {
        val innhentetGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(grunnlagsrequest.value)
        val rolleInhentetFor = behandling.roller.first { grunnlagsrequest.key.verdi == it.ident }

        val feilrapporteringer: Map<Grunnlagsdatatype, FeilrapporteringDto?> =
            grunnlagsdatatyperBm.associateWith {
                hentFeilrapporteringForGrunnlag(it, rolleInhentetFor, innhentetGrunnlag)
            }.filterNot { it.value == null }

        lagreGrunnlagHvisEndret(behandling, rolleInhentetFor, innhentetGrunnlag, feilrapporteringer)

        val feilSkattepliktig = feilrapporteringer[Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER]

        if (feilSkattepliktig == null) {
            lagreGrunnlagHvisEndret(
                behandling,
                rolleInhentetFor,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                SkattepliktigeInntekter(
                    innhentetGrunnlag.ainntektListe,
                    innhentetGrunnlag.skattegrunnlagListe,
                ),
                innhentetGrunnlag.hentetTidspunkt,
            )
        } else {
            log.warn {
                "Innhenting av ${Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER} for rolle ${rolleInhentetFor.rolletype} " +
                    "i behandling ${behandling.id} feilet for type ${feilSkattepliktig.grunnlagstype} med begrunnelse ${feilSkattepliktig.feilmelding}. Lagrer ikke grunnlag"
            }
        }

        // Oppdatere inntektstabell med sammenstilte inntekter
        if (innhentetGrunnlagInneholderInntekterEllerYtelser(innhentetGrunnlag)) {
            sammenstilleOgLagreInntekter(behandling, innhentetGrunnlag, rolleInhentetFor, feilrapporteringer)
        }

        // Oppdatere barn_i_husstand og tilhørende periode-tabell med periodisert boforhold
        if (innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.isNotEmpty()) {
            periodisereOgLagreBoforhold(behandling, innhentetGrunnlag)
        }

        // Oppdatere sivilstandstabell med periodisert sivilstand
        if (innhentetGrunnlag.sivilstandListe.isNotEmpty()) {
            periodisereOgLagreSivilstand(behandling, innhentetGrunnlag)
        }

        return feilrapporteringer
    }

    private fun periodisereOgLagreSivilstand(
        behandling: Behandling,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        val sivilstandPeriodisert =
            SivilstandApi.beregn(
                behandling.virkningstidspunktEllerSøktFomDato,
                innhentetGrunnlag.sivilstandListe,
            )

        lagreGrunnlagHvisEndret<SivilstandBeregnet>(
            behandling,
            behandling.bidragsmottaker!!,
            Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
            sivilstandPeriodisert,
            innhentetGrunnlag.hentetTidspunkt,
        )
    }

    private fun periodisereOgLagreBoforhold(
        behandling: Behandling,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        val boforholdPeriodisert =
            BoforholdApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.tilBoforholdRequest(),
            )

        lagreGrunnlagHvisEndret<BoforholdResponse>(
            behandling = behandling,
            rolle = behandling.bidragsmottaker!!,
            grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
            innhentetGrunnlag = boforholdPeriodisert.toSet(),
            hentetTidspunkt = innhentetGrunnlag.hentetTidspunkt,
        )
    }

    private fun innhentetGrunnlagInneholderInntekterEllerYtelser(innhentetGrunnlag: HentGrunnlagDto): Boolean =
        innhentetGrunnlag.ainntektListe.size > 0 || innhentetGrunnlag.skattegrunnlagListe.size > 0 ||
            innhentetGrunnlag.barnetilleggListe.size > 0 || innhentetGrunnlag.kontantstøtteListe.size > 0 ||
            innhentetGrunnlag.småbarnstilleggListe.size > 0 || innhentetGrunnlag.utvidetBarnetrygdListe.size > 0

    private fun sammenstilleOgLagreInntekter(
        behandling: Behandling,
        innhentetGrunnlag: HentGrunnlagDto,
        rolleInhentetFor: Rolle,
        feilliste: Map<Grunnlagsdatatype, FeilrapporteringDto?>,
    ) {
        val transformereInntekter = opprettTransformerInntekterRequest(behandling, innhentetGrunnlag, rolleInhentetFor)

        val sammenstilteInntekter = inntektApi.transformerInntekter(transformereInntekter)

        val grunnlagstyper: Set<Grunnlagsdatatype> =
            setOf(Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER) + inntekterOgYtelser

        val innhentetTidspunkt = LocalDateTime.now()

        grunnlagstyper.forEach { type ->
            val årsbaserteInntekterEllerYtelser: SummerteInntekter<*>? =
                tilSummerteInntekter(sammenstilteInntekter, type)

            val feilrapportering = feilliste[type]
            if (feilrapportering != null) {
                log.warn {
                    "Feil ved innhenting av grunnlagstype $type for rolle ${rolleInhentetFor.rolletype} " +
                        "i behandling ${behandling.id}. Lagrer ikke sammenstilte inntekter. Feilmelding: ${feilrapportering.feilmelding}"
                }
                return@forEach
            }
            @Suppress("UNCHECKED_CAST")
            if (inntekterOgYtelser.contains(type)) {
                lagreGrunnlagHvisEndret<SummerteInntekter<SummertÅrsinntekt>>(
                    rolleInhentetFor.behandling,
                    rolleInhentetFor,
                    Grunnlagstype(type, true),
                    årsbaserteInntekterEllerYtelser as SummerteInntekter<SummertÅrsinntekt>,
                    innhentetTidspunkt,
                )
            } else if (Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == type) {
                lagreGrunnlagHvisEndret<SummerteInntekter<SummertMånedsinntekt>>(
                    rolleInhentetFor.behandling,
                    rolleInhentetFor,
                    Grunnlagstype(type, true),
                    årsbaserteInntekterEllerYtelser as SummerteInntekter<SummertMånedsinntekt>,
                    innhentetTidspunkt,
                )
            } else {
                log.error {
                    "Grunnlagsdatatype $type skal ikke lagres som inntektsgrunnlag i behandling " +
                        "${rolleInhentetFor.behandling.id!!}"
                }
                lagringAvGrunnlagFeiletException(rolleInhentetFor.behandling.id!!)
            }

            aktiverGrunnlagForInntekterHvisIngenEndringSomMåBekreftes(behandling, type, rolleInhentetFor)
        }
    }

    private fun aktiverGrunnlagForInntekterHvisIngenEndringSomMåBekreftes(
        behandling: Behandling,
        type: Grunnlagsdatatype,
        rolleInhentetFor: Rolle,
    ) {
        val ikkeAktiveGrunnlag = behandling.grunnlag.toList().hentAlleIkkeAktiv()
        if (ikkeAktiveGrunnlag.isEmpty()) return
        val inneholderEndringerSomMåBekreftes =
            ikkeAktiveGrunnlag.hentEndringerInntekter(
                rolleInhentetFor,
                behandling.inntekter,
                type,
            ).isNotEmpty()
        if (!inneholderEndringerSomMåBekreftes) {
            log.info {
                "Ikke aktive grunnlag med type $type for rolle ${rolleInhentetFor.rolletype}" +
                    " i behandling ${behandling.id} har ingen endringer som må bekreftes av saksbehandler. Automatisk aktiverer ny innhentet grunnlag."
            }
            ikkeAktiveGrunnlag.hentGrunnlagForType(type, rolleInhentetFor.ident!!)
                .oppdaterStatusTilAktiv(LocalDateTime.now())
        }
    }

    private fun tilSummerteInntekter(
        sammenstilteInntekter: TransformerInntekterResponse,
        type: Grunnlagsdatatype,
    ): SummerteInntekter<*>? {
        return when (type) {
            Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter = sammenstilteInntekter.summertMånedsinntektListe,
                )

            Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter =
                        sammenstilteInntekter.summertÅrsinntektListe
                            .filter { summertAinntektstyper.contains(it.inntektRapportering) } +
                            sammenstilteInntekter.summertÅrsinntektListe.filter {
                                summertSkattegrunnlagstyper.contains(it.inntektRapportering)
                            },
                )

            Grunnlagsdatatype.BARNETILLEGG ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter =
                        sammenstilteInntekter.summertÅrsinntektListe
                            .filter { BARNETILLEGG == it.inntektRapportering },
                )

            Grunnlagsdatatype.KONTANTSTØTTE ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter =
                        sammenstilteInntekter.summertÅrsinntektListe
                            .filter { KONTANTSTØTTE == it.inntektRapportering },
                )

            Grunnlagsdatatype.SMÅBARNSTILLEGG ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter =
                        sammenstilteInntekter.summertÅrsinntektListe
                            .filter { SMÅBARNSTILLEGG == it.inntektRapportering },
                )

            Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter =
                        sammenstilteInntekter.summertÅrsinntektListe
                            .filter { UTVIDET_BARNETRYGD == it.inntektRapportering },
                )

            // Ikke-tilgjengelig kode
            else -> null
        }
    }

    private fun opprett(
        behandling: Behandling,
        idTilRolleInnhentetFor: Long,
        grunnlagstype: Grunnlagstype,
        data: String,
        innhentet: LocalDateTime,
        aktiv: LocalDateTime? = null,
    ) {
        log.info { "Lagrer inntentet grunnlag $grunnlagstype for behandling med id $behandling" }

        behandling.grunnlag.add(
            Grunnlag(
                behandling = behandling,
                type = grunnlagstype.type.getOrMigrate(),
                erBearbeidet = grunnlagstype.erBearbeidet,
                data = data,
                innhentet = innhentet,
                aktiv = aktiv,
                rolle = behandling.roller.first { r -> r.id == idTilRolleInnhentetFor },
            ),
        )
    }

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandling: Behandling,
        rolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: Set<T>,
        hentetTidspunkt: LocalDateTime,
        aktiveringstidspunkt: LocalDateTime? = null,
    ) {
        val sistInnhentedeGrunnlagAvTypeForRolle: Set<T> =
            henteNyesteGrunnlagsdatasett<T>(behandling.id!!, rolle.id!!, grunnlagstype).toSet()

        if ((sistInnhentedeGrunnlagAvTypeForRolle.isEmpty() && innhentetGrunnlag.isNotEmpty()) ||
            (
                sistInnhentedeGrunnlagAvTypeForRolle.isNotEmpty() &&
                    innhentetGrunnlag != sistInnhentedeGrunnlagAvTypeForRolle
            )
        ) {
            opprett(
                behandling = behandling,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = hentetTidspunkt,
                aktiv =
                    if (sistInnhentedeGrunnlagAvTypeForRolle.isEmpty() &&
                        innhentetGrunnlag.isNotEmpty()
                    ) {
                        LocalDateTime.now()
                    } else {
                        aktiveringstidspunkt
                    },
                idTilRolleInnhentetFor = rolle.id!!,
            )

            if (grunnlagstype ==
                Grunnlagstype(
                    Grunnlagsdatatype.BOFORHOLD.getOrMigrate(),
                    true,
                ) && sistInnhentedeGrunnlagAvTypeForRolle.isEmpty()
            ) {
                @Suppress("UNCHECKED_CAST")
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                    behandling,
                    Personident(rolle.ident!!),
                    innhentetGrunnlag.toList() as List<BoforholdResponse>,
                )
            }
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandling." }
        }
    }

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandling: Behandling,
        rolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: T,
        hentetTidspunkt: LocalDateTime,
        aktiveringstidspunkt: LocalDateTime? = null,
    ) {
        val sistInnhentedeGrunnlagAvType: T? =
            henteNyesteGrunnlagsdataobjekt<T>(behandling.id!!, rolle.id!!, grunnlagstype)
        val erAvTypeBearbeidetSivilstand = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true) == grunnlagstype
        val erFørstegangsinnhentingAvInntekter =
            sistInnhentedeGrunnlagAvType == null && (inneholderInntekter(innhentetGrunnlag) || erAvTypeBearbeidetSivilstand)
        val erGrunnlagEndretSidenSistInnhentet =
            sistInnhentedeGrunnlagAvType != null && innhentetGrunnlag != sistInnhentedeGrunnlagAvType
        if (erFørstegangsinnhentingAvInntekter || erGrunnlagEndretSidenSistInnhentet) {
            opprett(
                behandling = behandling,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = hentetTidspunkt,
                // Summerte månedsinntekter settes alltid til aktiv
                aktiv =
                    if (sistInnhentedeGrunnlagAvType == null ||
                        Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == grunnlagstype.type.getOrMigrate()
                    ) {
                        LocalDateTime.now()
                    } else {
                        aktiveringstidspunkt
                    },
                idTilRolleInnhentetFor = rolle.id!!,
            )

            // Oppdatere inntektstabell med sammenstilte offentlige inntekter
            if (inntekterOgYtelser.contains(
                    grunnlagstype.type.getOrMigrate(),
                ) && grunnlagstype.erBearbeidet && sistInnhentedeGrunnlagAvType == null
            ) {
                @Suppress("UNCHECKED_CAST")
                inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
                    behandling.id!!,
                    Personident(rolle.ident!!),
                    (innhentetGrunnlag as SummerteInntekter<SummertÅrsinntekt>).inntekter,
                )
            } else if (erAvTypeBearbeidetSivilstand && sistInnhentedeGrunnlagAvType == null) {
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertSivilstand(
                    behandling,
                    Personident(rolle.ident!!),
                    innhentetGrunnlag as SivilstandBeregnet,
                )
            }
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id ${behandling.id!!}." }
        }
    }

    fun <T> inneholderInntekter(grunnlag: T): Boolean {
        return when (grunnlag) {
            is SkattepliktigeInntekter -> grunnlag.ainntekter.isNotEmpty() || grunnlag.skattegrunnlag.isNotEmpty()
            is SummerteInntekter<*> -> grunnlag.inntekter.isNotEmpty()
            else -> false
        }
    }

    private inline fun <reified T> henteNyesteGrunnlagsdatasett(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagstype: Grunnlagstype,
    ): Set<T> {
        val grunnlagsdata = hentSistInnhentet(behandlingsid, rolleid, grunnlagstype)?.data
        return if (grunnlagsdata != null) {
            commonObjectmapper.readValue<Set<T>>(grunnlagsdata)
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

    private fun oppretteTransformereInntekterRequestPerType(
        grunnlagstype: Grunnlagsdatatype,
        grunnlag: Grunnlag,
        rolle: Rolle,
    ) = when (grunnlagstype) {
        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER ->
            TransformerInntekterRequestBuilder(
                ainntektsposter =
                    grunnlag.let { g ->
                        var dataInn: List<AinntektspostDto> = emptyList()
                        if (g.data.trim().isNotEmpty()) {
                            dataInn =
                                jsonTilObjekt<SkattepliktigeInntekter>(
                                    g.data,
                                ).ainntekter.flatMap { it.ainntektspostListe }
                        }
                        dataInn.tilAinntektsposter(rolle)
                    },
                skattegrunnlag =
                    grunnlag.let { g ->
                        var dataInn: List<SkattegrunnlagGrunnlagDto> = emptyList()
                        if (g.data.trim().isNotEmpty()) {
                            dataInn =
                                jsonTilObjekt<SkattepliktigeInntekter>(
                                    g.data,
                                ).skattegrunnlag
                        }
                        dataInn.tilSkattegrunnlagForLigningsår(rolle)
                    },
            ).bygge()

        Grunnlagsdatatype.BARNETILLEGG ->
            TransformerInntekterRequestBuilder(
                barnetillegg =
                    grunnlag.let { g ->
                        var rådata: List<BarnetilleggGrunnlagDto> = emptyList()
                        if (g.data.trim().isNotEmpty()) {
                            rådata =
                                jsonListeTilObjekt<BarnetilleggGrunnlagDto>(
                                    g.data,
                                ).toList()
                        }
                        rådata.tilBarnetillegg(rolle)
                    },
            ).bygge()

        Grunnlagsdatatype.KONTANTSTØTTE ->
            TransformerInntekterRequestBuilder(
                kontantstøtte =
                    grunnlag.let { g ->
                        var rådata: List<KontantstøtteGrunnlagDto> = emptyList()
                        if (g.data.trim().isNotEmpty()) {
                            rådata =
                                jsonListeTilObjekt<KontantstøtteGrunnlagDto>(
                                    g.data,
                                ).toList()
                        }
                        rådata.tilKontantstøtte(rolle)
                    },
            ).bygge()

        Grunnlagsdatatype.SMÅBARNSTILLEGG ->
            TransformerInntekterRequestBuilder(
                småbarnstillegg =
                    grunnlag.let { g ->
                        var rådata: List<SmåbarnstilleggGrunnlagDto> = emptyList()
                        if (g.data.trim().isNotEmpty()) {
                            rådata =
                                jsonListeTilObjekt<SmåbarnstilleggGrunnlagDto>(
                                    g.data,
                                ).toList()
                        }
                        rådata.tilSmåbarnstillegg(rolle)
                    },
            ).bygge()

        Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
            TransformerInntekterRequestBuilder(
                utvidetBarnetrygd =
                    grunnlag.let { g ->
                        var rådata: List<UtvidetBarnetrygdGrunnlagDto> = emptyList()
                        if (g.data.trim().isNotEmpty()) {
                            rådata =
                                jsonListeTilObjekt<UtvidetBarnetrygdGrunnlagDto>(
                                    g.data,
                                ).toList()
                        }
                        rådata.tilUtvidetBarnetrygd(rolle)
                    },
            ).bygge()

        else -> lagringAvGrunnlagFeiletException(grunnlag.behandling.id!!)
    }

    private fun lagreGrunnlagHvisEndret(
        behandling: Behandling,
        rolleInhentetFor: Rolle,
        innhentetGrunnlag: HentGrunnlagDto,
        feilrapporteringer: Map<Grunnlagsdatatype, FeilrapporteringDto?>,
    ) {
        val grunnlagsdatatyper =
            when (rolleInhentetFor.rolletype) {
                Rolletype.BIDRAGSMOTTAKER ->
                    setOf(
                        Grunnlagsdatatype.ARBEIDSFORHOLD,
                        Grunnlagsdatatype.BARNETILLEGG,
                        Grunnlagsdatatype.BARNETILSYN,
                        Grunnlagsdatatype.KONTANTSTØTTE,
                        Grunnlagsdatatype.BOFORHOLD,
                        Grunnlagsdatatype.SIVILSTAND,
                        Grunnlagsdatatype.SMÅBARNSTILLEGG,
                        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    )

                Rolletype.BARN -> setOf(Grunnlagsdatatype.ARBEIDSFORHOLD)
                else -> {
                    log.warn {
                        "Forsøkte å lagre grunnlag for rolle ${rolleInhentetFor.rolletype} i behandling " +
                            "${behandling.id}"
                    }
                    lagringAvGrunnlagFeiletException(behandling.id!!)
                }
            }
        grunnlagsdatatyper.forEach {
            val feilrapportering = feilrapporteringer[it]
            if (feilrapportering == null) {
                lagreGrunnlagHvisEndret(it, behandling, rolleInhentetFor, innhentetGrunnlag)
            } else {
                log.warn {
                    "Innhenting av $it for rolle ${rolleInhentetFor.rolletype} " +
                        "i behandling ${behandling.id} feilet for type ${feilrapportering.grunnlagstype} med begrunnelse ${feilrapportering.feilmelding}. Lagrer ikke grunnlag"
                }
            }
        }
    }

    private fun harBarnRolleIBehandling(
        personidentBarn: String,
        behandling: Behandling,
    ) = behandling.roller.filter { Rolletype.BARN == it.rolletype }.any { personidentBarn == it.ident }

    private fun hentFeilrapporteringForGrunnlag(
        grunnlagsdatatype: Grunnlagsdatatype,
        rolleInhentetFor: Rolle,
        innhentetGrunnlag: HentGrunnlagDto,
    ): FeilrapporteringDto? {
        return when (grunnlagsdatatype) {
            Grunnlagsdatatype.ARBEIDSFORHOLD ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.ARBEIDSFORHOLD,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.BARNETILLEGG ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.BARNETILLEGG,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.SMÅBARNSTILLEGG ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER, Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.SKATTEGRUNNLAG,
                    rolleInhentetFor,
                ) ?: innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.AINNTEKT,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.KONTANTSTØTTE ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.KONTANTSTØTTE,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.BOFORHOLD ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.HUSSTANDSMEDLEMMER_OG_EGNE_BARN,
                    rolleInhentetFor,
                )

            Grunnlagsdatatype.SIVILSTAND ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.SIVILSTAND,
                    rolleInhentetFor,
                )

            else -> null
        }
    }

    private fun HentGrunnlagDto.hentFeilFor(
        type: GrunnlagRequestType,
        rolle: Rolle,
    ) = feilrapporteringListe.find {
        it.grunnlagstype == type && it.personId == rolle.ident
    }

    private fun lagreGrunnlagHvisEndret(
        grunnlagsdatatype: Grunnlagsdatatype,
        behandling: Behandling,
        rolleInhentetFor: Rolle,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        when (grunnlagsdatatype) {
            Grunnlagsdatatype.ARBEIDSFORHOLD -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.arbeidsforholdListe.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.BARNETILLEGG -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.barnetilleggListe.filter {
                        harBarnRolleIBehandling(it.barnPersonId, behandling)
                    }.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.BARNETILSYN -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.barnetilsynListe.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.KONTANTSTØTTE -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.kontantstøtteListe.filter {
                        harBarnRolleIBehandling(it.barnPersonId, behandling)
                    }.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.BOFORHOLD -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.SIVILSTAND -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.sivilstandListe.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.SMÅBARNSTILLEGG -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.småbarnstilleggListe.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            Grunnlagsdatatype.UTVIDET_BARNETRYGD -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.utvidetBarnetrygdListe.toSet(),
                    innhentetGrunnlag.hentetTidspunkt,
                )
            }

            else -> {
                log.warn {
                    "Forsøkte å lagre grunnlag av type $grunnlagsdatatype for rolle ${rolleInhentetFor.rolletype} " +
                        "i behandling ${behandling.id}"
                }
                lagringAvGrunnlagFeiletException(behandling.id!!)
            }
        }
    }
}
