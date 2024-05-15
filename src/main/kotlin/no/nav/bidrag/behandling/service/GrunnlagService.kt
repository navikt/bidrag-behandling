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
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentBearbeidetInntekterForType
import no.nav.bidrag.behandling.database.datamodell.hentGrunnlagForType
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteIkkeAktiv
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
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.behandling.filtrerPerioderEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.behandling.finnEndringerBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.transformers.grunnlag.inntekterOgYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.summertAinntektstyper
import no.nav.bidrag.behandling.transformers.grunnlag.summertSkattegrunnlagstyper
import no.nav.bidrag.behandling.transformers.inntekt.opprettTransformerInntekterRequest
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.util.secureLogger
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
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
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
            val nesteInnhenting = behandling.grunnlagSistInnhentet?.plusMinutes(grenseInnhenting.toLong())

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
        val rolleGrunnlagErInnhentetFor =
            behandling.roller.find { request.personident.verdi == it.ident } ?: behandling.bidragsmottaker

        if (Grunnlagsdatatype.BOFORHOLD != request.grunnlagstype) {
            Validate.notNull(
                rolleGrunnlagErInnhentetFor,
                "Personident oppgitt i AktivereGrunnlagRequest har ikke rolle i behandling ${behandling.id}",
            )
        }

        val rolleGrunnlagetErLagretPå =
            when (request.grunnlagstype) {
                Grunnlagsdatatype.BOFORHOLD -> behandling.bidragsmottaker
                Grunnlagsdatatype.KONTANTSTØTTE -> behandling.bidragsmottaker
                Grunnlagsdatatype.BARNETILLEGG -> behandling.bidragsmottaker
                else -> rolleGrunnlagErInnhentetFor
            }

        val harIkkeAktivtGrunnlag =
            behandling.grunnlag.hentSisteIkkeAktiv().filter { rolleGrunnlagetErLagretPå?.ident == it.rolle.ident }
                .any { request.grunnlagstype == it.type }

        if (!harIkkeAktivtGrunnlag) {
            log.warn {
                "Fant ingen grunnlag med type ${request.grunnlagstype} å aktivere for i behandling ${behandling.id} " +
                    " for oppgitt person."
            }
            ressursIkkeFunnetException(
                "Fant ikke grunnlag av tye ${request.grunnlagstype} å aktivere i behandling ${behandling.id} " +
                    "for oppgitt personident.",
            )
        }

        val aktiveringstidspunkt = LocalDateTime.now()

        if (inntekterOgYtelser.contains(request.grunnlagstype)) {
            aktivereYtelserOgInntekter(
                behandling,
                request.grunnlagstype,
                rolleGrunnlagetErLagretPå!!,
                aktiveringstidspunkt,
            )
        } else if (Grunnlagsdatatype.BOFORHOLD == request.grunnlagstype) {
            aktivereBoforhold(
                behandling,
                request.grunnlagstype,
                request.personident,
                aktiveringstidspunkt,
                request.overskriveManuelleOpplysninger,
            )
        } else if (Grunnlagsdatatype.SIVILSTAND == request.grunnlagstype) {
            aktivereSivilstand(
                behandling,
                request.grunnlagstype,
                rolleGrunnlagErInnhentetFor!!,
                aktiveringstidspunkt,
            )
        } else {
            log.error {
                "Grunnlagstype ${request.grunnlagstype} ikke støttet ved aktivering av grunnlag. Aktivering feilet " +
                    "for behandling ${behandling.id}  "
            }
            aktiveringAvGrunnlagstypeIkkeStøttetException(behandling.id!!)
        }
    }

    @Transactional
    fun aktivereBearbeidaBoforholdEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val ikkeAktiverteBearbeidaBoforhold =
            behandling.henteUaktiverteGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                behandling.bidragsmottaker!!,
            )

        ikkeAktiverteBearbeidaBoforhold.forEach {
            it.aktiv = LocalDateTime.now()
        }
    }

    @Transactional
    fun oppdatereBearbeidaBoforhold(behandling: Behandling) {
        val sistAktiverteBoforholdsgrunnlag =
            behandling.hentSisteInnhentetGrunnlagSet<RelatertPersonGrunnlagDto>(
                Grunnlagstype(
                    Grunnlagsdatatype.BOFORHOLD,
                    false,
                ),
                behandling.bidragsmottaker!!,
                null,
            )
        periodisereOgLagreBoforhold(behandling, sistAktiverteBoforholdsgrunnlag)
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
        val roller = behandling.roller.sortedBy { if (it.rolletype == Rolletype.BARN) 1 else -1 }
        val inntekter = behandling.inntekter
        val nyinnhentetGrunnlag = behandling.grunnlagListe.toSet().hentSisteIkkeAktiv()
        val aktiveGrunnlag = behandling.grunnlagListe.toSet().hentSisteAktiv()
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
            husstandsbarn =
                nyinnhentetGrunnlag.hentEndringerBoforhold(
                    aktiveGrunnlag,
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.husstandsbarn,
                    behandling.bidragsmottaker!!,
                ),
            sivilstand =
                nyinnhentetGrunnlag.hentEndringerSivilstand(
                    aktiveGrunnlag,
                    behandling.virkningstidspunktEllerSøktFomDato,
                ),
        )
    }

    private fun aktivereYtelserOgInntekter(
        behandling: Behandling,
        grunnlagstype: Grunnlagsdatatype,
        rolle: Rolle,
        aktiveringstidspunkt: LocalDateTime,
    ) {
        val ikkeAktivGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()

        val summerteInntekter = ikkeAktivGrunnlag.hentBearbeidetInntekterForType(grunnlagstype, rolle.ident!!)

        inntektService.oppdatereAutomatiskInnhentetOffentligeInntekter(
            behandling,
            rolle,
            summerteInntekter?.inntekter ?: emptyList(),
            grunnlagstype,
        )
        ikkeAktivGrunnlag.hentGrunnlagForType(grunnlagstype, rolle.ident!!).oppdaterStatusTilAktiv(aktiveringstidspunkt)
    }

    private fun aktivereBoforhold(
        behandling: Behandling,
        grunnlagstype: Grunnlagsdatatype,
        gjelderHusstandsbarn: Personident,
        aktiveringstidspunkt: LocalDateTime,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val nyesteIkkeAktiverteBoforholdForHusstandsmedlem =
            behandling.grunnlag.hentSisteIkkeAktiv()
                .filter { gjelderHusstandsbarn.verdi == it.gjelder && grunnlagstype == it.type }
                .firstOrNull { it.erBearbeidet }

        val bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting =
            behandling
                .henteNyesteGrunnlag(Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false), behandling.bidragsmottaker!!)
                ?.data?.let { jsonListeTilObjekt<RelatertPersonGrunnlagDto>(it) }
                ?.filter { it.erBarnAvBmBp && it.relatertPersonPersonId != null }
                ?.groupBy { it.relatertPersonPersonId!! }?.map { Personident(it.key) }?.toSet()

        if (nyesteIkkeAktiverteBoforholdForHusstandsmedlem == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type $grunnlagstype å aktivere for oppgitt husstandsmeldem i  behandling " +
                    behandling.id,
            )
        }

        // TOOD: Vurdere å trigge ny grunnlagsinnhenting
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.isNullOrEmpty()) {
            log.error { "Fant ingen husstandsmedlemmer i nyeste boforholdsgrunnlag for BM i behandling ${behandling.id}" }
            throw HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Fant ingen husstandsmedlemmer i nyeste boforholdsgrunnlag for BM i behandling ${behandling.id}",
            )
        }

        boforholdService.oppdatereAutomatiskInnhentaBoforhold(
            behandling,
            jsonTilObjekt<List<BoforholdResponse>>(nyesteIkkeAktiverteBoforholdForHusstandsmedlem.data),
            bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
            overskriveManuelleOpplysninger,
        )

        nyesteIkkeAktiverteBoforholdForHusstandsmedlem.aktiv = aktiveringstidspunkt
        aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling)

        entityManager.merge(behandling)
        entityManager.flush()
    }

    private fun aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling: Behandling) {
        val bmsNyesteIkkeBearbeidaBoforholdsgrunnlag =
            behandling.henteNyesteGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                behandling.bidragsmottaker!!,
            )

        bmsNyesteIkkeBearbeidaBoforholdsgrunnlag?.let {
            if (bmsNyesteIkkeBearbeidaBoforholdsgrunnlag.aktiv == null &&
                erGrunnlagAktivertForAlleHusstandsmedlemmene(it, behandling)
            ) {
                bmsNyesteIkkeBearbeidaBoforholdsgrunnlag.aktiv = LocalDateTime.now()
            }
        }
    }

    private fun erGrunnlagAktivertForAlleHusstandsmedlemmene(
        bmsNyesteIkkeBearbeidaBoforholdsgrunnlag: Grunnlag,
        behandling: Behandling,
    ): Boolean {
        jsonListeTilObjekt<RelatertPersonGrunnlagDto>(bmsNyesteIkkeBearbeidaBoforholdsgrunnlag.data)
            .filter { it.relatertPersonPersonId != null && it.erBarnAvBmBp }.groupBy {
                it.relatertPersonPersonId
            }.forEach {
                val nyesteGrunnlagForHusstandsmedlem =
                    behandling.henteNyesteGrunnlag(
                        Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                        behandling.bidragsmottaker!!,
                        Personident(it.key!!),
                    )
                if (nyesteGrunnlagForHusstandsmedlem?.aktiv == null) {
                    return false
                }
            }
        return true
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
            behandling.grunnlag.hentAlleIkkeAktiv().filter { rolle.ident == it.rolle.ident }
                .filter { grunnlagstype == it.type }.filter { !it.erBearbeidet }.maxByOrNull { it.innhentet }!!
        val periodisertSivilstand =
            SivilstandApi.beregnV1(
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
        return behandling.grunnlagSistInnhentet == null || LocalDateTime.now()
            .minusMinutes(grenseInnhenting.toLong()) > behandling.grunnlagSistInnhentet
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
                    "i behandling ${behandling.id} feilet for type ${feilSkattepliktig.grunnlagstype} " +
                    "med begrunnelse ${feilSkattepliktig.feilmelding}. Lagrer ikke grunnlag"
            }
        }

        // Oppdatere inntektstabell med sammenstilte inntekter
        if (innhentetGrunnlagInneholderInntekterEllerYtelser(innhentetGrunnlag)) {
            sammenstilleOgLagreInntekter(behandling, innhentetGrunnlag, rolleInhentetFor, feilrapporteringer)
        }

        val innhentingAvBoforholdFeilet =
            feilrapporteringer.filter { Grunnlagsdatatype.BOFORHOLD == it.key }.isNotEmpty()

        // Oppdatere barn_i_husstand og tilhørende periode-tabell med periodisert boforhold
        if (innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.isNotEmpty() && !innhentingAvBoforholdFeilet) {
            periodisereOgLagreBoforhold(behandling, innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet())
        }

        val innhentingAvSivilstandFeilet =
            feilrapporteringer.filter { Grunnlagsdatatype.SIVILSTAND == it.key }.isNotEmpty()

        // Oppdatere sivilstandstabell med periodisert sivilstand
        if (innhentetGrunnlag.sivilstandListe.isNotEmpty() && !innhentingAvSivilstandFeilet) {
            periodisereOgLagreSivilstand(behandling, innhentetGrunnlag)
        }

        return feilrapporteringer
    }

    private fun periodisereOgLagreSivilstand(
        behandling: Behandling,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        val sivilstandPeriodisert =
            SivilstandApi.beregnV1(
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
        husstandsmedlemmerOgEgneBarn: Set<RelatertPersonGrunnlagDto>,
    ) {
        val boforholdPeriodisert =
            BoforholdApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                husstandsmedlemmerOgEgneBarn.tilBoforholdRequest(behandling.virkningstidspunktEllerSøktFomDato),
            )

        val bmsNyesteBearbeidaBoforholdFørLagring =
            sistAktiverteGrunnlag<BoforholdResponse>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                behandling.bidragsmottaker!!,
            )

        // lagre bearbeidet grunnlag per husstandsmedlem i grunnlagstabellen
        boforholdPeriodisert.filter { it.relatertPersonPersonId != null }.groupBy { it.relatertPersonPersonId }
            .forEach {
                lagreGrunnlagHvisEndret<BoforholdResponse>(
                    behandling = behandling,
                    innhentetForRolle = behandling.bidragsmottaker!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentetGrunnlag = it.value.toSet(),
                    gjelderPerson = Personident(it.key!!),
                )
            }

        val bmsNyesteBearbeidaBoforholdEtterLagring =
            sistAktiverteGrunnlag<BoforholdResponse>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                behandling.bidragsmottaker!!,
            )

        // oppdatere husstandsbarn og husstandsbarnperiode-tabellene hvis førstegangslagring
        if (bmsNyesteBearbeidaBoforholdFørLagring.isEmpty() && bmsNyesteBearbeidaBoforholdEtterLagring.isNotEmpty()) {
            boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(behandling, boforholdPeriodisert)
        }
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
                        "i behandling ${behandling.id}. Lagrer ikke sammenstilte inntekter. Feilmelding: " +
                        feilrapportering.feilmelding
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
                        rolleInhentetFor.behandling.id!!
                }
                lagringAvGrunnlagFeiletException(rolleInhentetFor.behandling.id!!)
            }

            aktiverGrunnlagForInntekterHvisIngenEndringMåAksepteres(behandling, type, rolleInhentetFor)
        }
    }

    private fun aktiverGrunnlagForInntekterHvisIngenEndringMåAksepteres(
        behandling: Behandling,
        type: Grunnlagsdatatype,
        rolleInhentetFor: Rolle,
    ) {
        val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()
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
                    " i behandling ${behandling.id} har ingen endringer som må bekreftes av saksbehandler. " +
                    "Automatisk aktiverer ny innhentet grunnlag."
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
        gjelder: Personident? = null,
    ) {
        log.info { "Lagrer inntentet grunnlag $grunnlagstype for behandling med id ${behandling.id}" }
        secureLogger.info { "Lagrer inntentet grunnlag $grunnlagstype for behandling med id ${behandling.id} og gjelder ${gjelder?.verdi}" }

        behandling.grunnlag.add(
            Grunnlag(
                behandling = behandling,
                type = grunnlagstype.type.getOrMigrate(),
                erBearbeidet = grunnlagstype.erBearbeidet,
                data = data,
                innhentet = innhentet,
                aktiv = aktiv,
                rolle = behandling.roller.first { r -> r.id == idTilRolleInnhentetFor },
                gjelder = gjelder?.verdi,
            ),
        )
    }

    private inline fun <reified T> sistAktiverteGrunnlag(
        behandling: Behandling,
        grunnlagstype: Grunnlagstype,
        rolleInnhentetFor: Rolle,
    ): Set<T> =
        behandling.grunnlag.hentSisteAktiv().find {
            it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id &&
                grunnlagstype.erBearbeidet == it.erBearbeidet
        }?.let { commonObjectmapper.readValue<Set<T>>(it.data) }?.toSet() ?: emptySet()

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandling: Behandling,
        innhentetForRolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: Set<T>,
        aktiveringstidspunkt: LocalDateTime? = null,
        gjelderPerson: Personident? = null,
    ) {
        val sistInnhentedeGrunnlagAvTypeForRolle: Set<T> =
            behandling.hentSisteInnhentetGrunnlagSet(
                grunnlagstype,
                innhentetForRolle,
                gjelderPerson,
            )

        val nyesteGrunnlag = behandling.henteNyesteGrunnlag(grunnlagstype, innhentetForRolle, gjelderPerson)
        val erFørstegangsinnhenting = sistInnhentedeGrunnlagAvTypeForRolle.isEmpty() && innhentetGrunnlag.isNotEmpty()
        val erGrunnlagEndret =
            sistInnhentedeGrunnlagAvTypeForRolle.isNotEmpty() && innhentetGrunnlag !=
                nyesteGrunnlag?.data?.let {
                    jsonListeTilObjekt<T>(it)
                }
//        val erGrunnlagEndret =
//            sistInnhentedeGrunnlagAvTypeForRolle.isNotEmpty() &&
//                    erBoforholdEllerSivilstandGrunnlagEndret(
//                        grunnlagstype,
//                        innhentetGrunnlag,
//                        sistInnhentedeGrunnlagAvTypeForRolle,
//                        behandling,
//                    )

        if (erFørstegangsinnhenting || erGrunnlagEndret && nyesteGrunnlag?.aktiv != null) {
            opprett(
                behandling = behandling,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = LocalDateTime.now(),
                aktiv =
                    if (nyesteGrunnlag?.aktiv != null) {
                        aktiveringstidspunkt
                    } else {
                        LocalDateTime.now()
                    },
                idTilRolleInnhentetFor = innhentetForRolle.id!!,
                gjelder = gjelderPerson,
            )
            if (grunnlagstype.erBearbeidet) {
                aktivereSisteInnhentedeRådata(grunnlagstype.type, innhentetForRolle, behandling)
            }
        } else if (erGrunnlagEndret) {
            val grunnlagSomSkalOppdateres =
                behandling.henteUaktiverteGrunnlag(grunnlagstype, innhentetForRolle).maxBy { it.innhentet }
            grunnlagSomSkalOppdateres.data = tilJson(innhentetGrunnlag)
            grunnlagSomSkalOppdateres.innhentet = LocalDateTime.now()
            grunnlagSomSkalOppdateres.aktiv = aktiveringstidspunkt

            behandling.henteUaktiverteGrunnlag(grunnlagstype, innhentetForRolle).forEach {
                if (it.id != grunnlagSomSkalOppdateres.id) {
                    behandling.grunnlag.remove(it)
                    grunnlagRepository.deleteById(it.id!!)
                }
            }
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandling." }
        }
    }

    // TODO: Ikke fjern dette. Skal brukes ved sjekk for endringer
    private fun <T> erBoforholdEllerSivilstandGrunnlagEndret(
        grunnlagstype: Grunnlagstype,
        aktivtGrunnlag: Set<T>,
        nyttGrunnlag: Set<T>,
        behandling: Behandling,
    ): Boolean {
        if (!grunnlagstype.erBearbeidet) return aktivtGrunnlag.toSet() != nyttGrunnlag.toSet()
        return if (grunnlagstype.type == Grunnlagsdatatype.BOFORHOLD) {
            val aktivtGrunnlagFiltrert =
                (aktivtGrunnlag as Set<BoforholdResponse>)
                    .toList()
                    .filtrerPerioderEtterVirkningstidspunkt(
                        behandling.husstandsbarn,
                        behandling.virkningstidspunktEllerSøktFomDato,
                    )
                    .toSet()
            val nyttGrunnlagFiltrert =
                (nyttGrunnlag as Set<BoforholdResponse>)
                    .toList()
                    .filtrerPerioderEtterVirkningstidspunkt(
                        behandling.husstandsbarn,
                        behandling.virkningstidspunktEllerSøktFomDato,
                    )
                    .toSet()
            aktivtGrunnlagFiltrert
                .finnEndringerBoforhold(behandling.virkningstidspunktEllerSøktFomDato, nyttGrunnlagFiltrert)
                .isNotEmpty()
        } else if (grunnlagstype.type == Grunnlagsdatatype.SIVILSTAND) {
            aktivtGrunnlag.toSet() != nyttGrunnlag.toSet()
            // TODO
        } else {
            aktivtGrunnlag.toSet() != nyttGrunnlag.toSet()
        }
    }

    private fun aktivereSisteInnhentedeRådata(
        grunnlagsdatatype: Grunnlagsdatatype,
        innhentetForRolle: Rolle,
        behandling: Behandling,
    ) {
        val sisteInnhentedeIkkeBearbeidaGrunnlag =
            behandling.grunnlag.filter { grunnlagsdatatype == it.type && !it.erBearbeidet }
                .filter { innhentetForRolle == it.rolle }
                .maxByOrNull { it.innhentet }

        if (sisteInnhentedeIkkeBearbeidaGrunnlag?.aktiv == null) {
            when (grunnlagsdatatype) {
                Grunnlagsdatatype.BOFORHOLD -> {
                    if (grunnlagErAktivertForAlleHusstandsmedlemmer(
                            behandling,
                            sisteInnhentedeIkkeBearbeidaGrunnlag,
                            innhentetForRolle,
                        )
                    ) {
                        sisteInnhentedeIkkeBearbeidaGrunnlag?.aktiv = LocalDateTime.now()
                    }
                }

                else -> sisteInnhentedeIkkeBearbeidaGrunnlag?.aktiv = LocalDateTime.now()
            }
        }
    }

    private fun grunnlagErAktivertForAlleHusstandsmedlemmer(
        behandling: Behandling,
        sisteInnhentedeIkkeBearbeidaGrunnlag: Grunnlag?,
        innhentetForRolle: Rolle,
    ): Boolean {
        val nyesteRådata =
            jsonTilObjekt<List<RelatertPersonGrunnlagDto>>(sisteInnhentedeIkkeBearbeidaGrunnlag!!.data)
        nyesteRådata.mapNotNull { it.relatertPersonPersonId }.forEach {
            val nyesteBearbeidaDataForHusstandsmedlem =
                behandling.henteNyesteGrunnlag(
                    Grunnlagstype(
                        Grunnlagsdatatype.BOFORHOLD,
                        true,
                    ),
                    innhentetForRolle,
                    Personident(it),
                )

            if (nyesteBearbeidaDataForHusstandsmedlem?.aktiv == null) {
                return false
            }
        }
        return true
    }

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandling: Behandling,
        rolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: T,
        hentetTidspunkt: LocalDateTime,
        aktiveringstidspunkt: LocalDateTime? = null,
    ) {
        val sistInnhentedeGrunnlagAvType: T? = behandling.hentSisteInnhentetGrunnlag(grunnlagstype, rolle)
        val nyesteGrunnlag = behandling.henteNyesteGrunnlag(grunnlagstype, rolle)

        val erAvTypeBearbeidetSivilstand = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true) == grunnlagstype
        val erFørstegangsinnhentingAvInntekter =
            sistInnhentedeGrunnlagAvType == null && (
                inneholderInntekter(innhentetGrunnlag) ||
                    erAvTypeBearbeidetSivilstand
            )
        val erGrunnlagEndretSidenSistInnhentet =
            sistInnhentedeGrunnlagAvType != null && innhentetGrunnlag != sistInnhentedeGrunnlagAvType

        if (erFørstegangsinnhentingAvInntekter || erGrunnlagEndretSidenSistInnhentet && nyesteGrunnlag?.aktiv != null) {
            opprett(
                behandling = behandling,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = hentetTidspunkt,
                // Summerte månedsinntekter settes alltid til aktiv
                aktiv =
                    if (nyesteGrunnlag?.aktiv != null &&
                        Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER != grunnlagstype.type.getOrMigrate()
                    ) {
                        aktiveringstidspunkt
                    } else {
                        LocalDateTime.now()
                    },
                idTilRolleInnhentetFor = rolle.id!!,
            )
            if (grunnlagstype.erBearbeidet) {
                aktivereSisteInnhentedeRådata(grunnlagstype.type, rolle, behandling)
            }
            // Oppdatere inntektstabell med sammenstilte offentlige inntekter
            if (nyesteGrunnlag == null && inntekterOgYtelser.contains(grunnlagstype.type.getOrMigrate()) &&
                grunnlagstype.erBearbeidet
            ) {
                @Suppress("UNCHECKED_CAST")
                inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
                    behandling.id!!,
                    Personident(rolle.ident!!),
                    (innhentetGrunnlag as SummerteInntekter<SummertÅrsinntekt>).inntekter,
                )
            } else if (nyesteGrunnlag == null && erAvTypeBearbeidetSivilstand) {
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertSivilstand(
                    behandling,
                    Personident(rolle.ident!!),
                    innhentetGrunnlag as SivilstandBeregnet,
                )
            }
        } else if (erGrunnlagEndretSidenSistInnhentet) {
            val grunnlagSomSkalOppdateres =
                behandling.henteUaktiverteGrunnlag(grunnlagstype, rolle).maxByOrNull { it.innhentet }
            grunnlagSomSkalOppdateres?.data = tilJson(innhentetGrunnlag)
            grunnlagSomSkalOppdateres?.innhentet = hentetTidspunkt
            // Summerte månedsinntekter settes alltid til aktiv
            grunnlagSomSkalOppdateres?.aktiv =
                if (Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == grunnlagstype.type.getOrMigrate()
                ) {
                    LocalDateTime.now()
                } else {
                    aktiveringstidspunkt
                }
            behandling.henteUaktiverteGrunnlag(grunnlagstype, rolle).forEach {
                if (it.id != grunnlagSomSkalOppdateres?.id) {
                    behandling.grunnlag.remove(it)
                    grunnlagRepository.deleteById(it.id!!)
                }
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

    private fun Behandling.henteUaktiverteGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
    ): Set<Grunnlag> =
        grunnlag.hentAlleIkkeAktiv().filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet()

    private fun Behandling.henteNyesteGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
        gjelder: Personident?,
    ): Grunnlag? =
        grunnlag.filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && grunnlagstype.erBearbeidet == it.erBearbeidet &&
                it.gjelder == gjelder?.verdi
        }.toSet().maxByOrNull { it.innhentet }

    private fun Behandling.henteNyesteGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolleInnhentetFor: Rolle,
    ): Grunnlag? =
        grunnlag.filter {
            it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet().maxByOrNull { it.innhentet }

    private inline fun <reified T> Behandling.hentSisteInnhentetGrunnlagSet(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
        gjelderPerson: Personident?,
    ): Set<T> =
        grunnlag.hentSisteAktiv().find {
            it.type == grunnlagstype.type &&
                it.rolle.id == rolle.id && it.gjelder == gjelderPerson?.verdi &&
                grunnlagstype.erBearbeidet == it.erBearbeidet
        }?.let { commonObjectmapper.readValue<Set<T>>(it.data) }?.toSet() ?: emptySet()

    private inline fun <reified T> Behandling.hentSisteInnhentetGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
    ): T? =
        grunnlag.hentSisteAktiv().find {
            it.rolle.id == rolle.id && it.type == grunnlagstype.type.getOrMigrate() &&
                it.erBearbeidet == grunnlagstype.erBearbeidet
        }?.let { commonObjectmapper.readValue<T>(it.data) }

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
                    "Innhenting av $it for rolle ${rolleInhentetFor.rolletype} " + "i behandling ${behandling.id} " +
                        "feilet for type ${feilrapportering.grunnlagstype} med begrunnelse " +
                        "${feilrapportering.feilmelding}. Lagrer ikke grunnlag"
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
                )
            }

            Grunnlagsdatatype.BARNETILSYN -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.barnetilsynListe.toSet(),
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
                )
            }

            Grunnlagsdatatype.BOFORHOLD -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
                )
            }

            Grunnlagsdatatype.SIVILSTAND -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.sivilstandListe.toSet(),
                )
            }

            Grunnlagsdatatype.SMÅBARNSTILLEGG -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.småbarnstilleggListe.toSet(),
                )
            }

            Grunnlagsdatatype.UTVIDET_BARNETRYGD -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.utvidetBarnetrygdListe.toSet(),
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
