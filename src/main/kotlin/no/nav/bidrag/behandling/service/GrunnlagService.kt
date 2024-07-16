package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.aktiveringAvGrunnlagstypeIkkeStøttetException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentGrunnlagForType
import no.nav.bidrag.behandling.database.datamodell.hentIdenterForEgneBarnIHusstandFraGrunnlagForRolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.henteBearbeidaInntekterForType
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.getOrMigrate
import no.nav.bidrag.behandling.lagringAvGrunnlagFeiletException
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.behandling.erLik
import no.nav.bidrag.behandling.transformers.behandling.filtrerPerioderEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.behandling.filtrerSivilstandBeregnetEtterVirkningstidspunktV2
import no.nav.bidrag.behandling.transformers.behandling.finnEndringerBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerSivilstand
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIAndreVoksneIBpsHusstand
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdVoksneRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.grunnlag.inntekterOgYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.summertAinntektstyper
import no.nav.bidrag.behandling.transformers.grunnlag.summertSkattegrunnlagstyper
import no.nav.bidrag.behandling.transformers.inntekt.opprettTransformerInntekterRequest
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.BARNETILLEGG
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.KONTANTSTØTTE
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.SMÅBARNSTILLEGG
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering.UTVIDET_BARNETRYGD
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.dto.Sivilstand
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
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto

private val log = KotlinLogging.logger {}

@Service
class GrunnlagService(
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val boforholdService: BoforholdService,
    private val grunnlagRepository: GrunnlagRepository,
    private val inntektApi: InntektApi,
    private val inntektService: InntektService,
) {
    @Value("\${egenskaper.grunnlag.min-antall-minutter-siden-forrige-innhenting}")
    private lateinit var grenseInnhenting: String

    @Transactional
    fun oppdatereGrunnlagForBehandling(behandling: Behandling) {
        if (foretaNyGrunnlagsinnhenting(behandling)) {
            val grunnlagRequestobjekter = bidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)
            val feilrapporteringer = mutableMapOf<Grunnlagsdatatype, FeilrapporteringDto?>()
            behandling.grunnlagsinnhentingFeilet = null

            grunnlagRequestobjekter.forEach {
                feilrapporteringer += henteOglagreGrunnlag(behandling, it)
            }

            behandling.grunnlagSistInnhentet = LocalDateTime.now()

            if (feilrapporteringer.isNotEmpty()) {
                behandling.grunnlagsinnhentingFeilet = objectmapper.writeValueAsString(feilrapporteringer)
                secureLogger.error {
                    "Det oppstod feil i fbm. innhenting av grunnlag for behandling ${behandling.id}. " +
                        "Innhentingen ble derfor ikke gjort for følgende grunnlag: " +
                        "${feilrapporteringer.map { "${it.key}: ${it.value}" }}"
                }
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
            when (request.grunnlagstype) {
                Grunnlagsdatatype.BARNETILLEGG -> behandling.rolleGrunnlagSkalHentesFor
                Grunnlagsdatatype.BOFORHOLD -> behandling.rolleGrunnlagSkalHentesFor
                Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN -> behandling.rolleGrunnlagSkalHentesFor
                Grunnlagsdatatype.KONTANTSTØTTE -> behandling.rolleGrunnlagSkalHentesFor
                else ->
                    behandling.roller.find { request.personident.verdi == it.ident }
                        ?: behandling.rolleGrunnlagSkalHentesFor
            }

        if (Grunnlagsdatatype.BOFORHOLD != request.grunnlagstype) {
            Validate.notNull(
                rolleGrunnlagErInnhentetFor,
                "Personident oppgitt i AktivereGrunnlagRequest har ikke rolle i behandling ${behandling.id}",
            )
        }

        val harIkkeaktivertGrunnlag =
            behandling.grunnlag.hentSisteIkkeAktiv().filter { rolleGrunnlagErInnhentetFor!!.ident == it.rolle.ident }
                .any { request.grunnlagstype == it.type }

        if (!harIkkeaktivertGrunnlag) {
            log.warn {
                "Fant ingen grunnlag med type ${request.grunnlagstype} å aktivere for i behandling ${behandling.id} " +
                    " for oppgitt person."
            }
            ressursIkkeFunnetException(
                "Fant ikke grunnlag av type ${request.grunnlagstype} å aktivere i behandling ${behandling.id} " +
                    "for oppgitt personident.",
            )
        }

        if (inntekterOgYtelser.contains(request.grunnlagstype)) {
            aktivereYtelserOgInntekter(behandling, request.grunnlagstype, rolleGrunnlagErInnhentetFor!!)
        } else if (Grunnlagsdatatype.BOFORHOLD == request.grunnlagstype) {
            aktivereBoforhold(
                behandling,
                request.grunnlagstype,
                request.personident,
                request.overskriveManuelleOpplysninger,
            )
        } else if (Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == request.grunnlagstype) {
            aktivereBoforholdAndreVoksneIHusstanden(behandling, request.overskriveManuelleOpplysninger)
        } else if (Grunnlagsdatatype.SIVILSTAND == request.grunnlagstype) {
            boforholdService.oppdatereAutomatiskInnhentaSivilstand(
                behandling,
                request.overskriveManuelleOpplysninger,
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
    fun oppdatereAktivSivilstandEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val sisteAktiveGrunnlag =
            behandling.henteNyesteAktiveGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
                behandling.bidragsmottaker!!,
            ) ?: run {
                log.warn { "Fant ingen aktive sivilstandsgrunnlag. Gjør ingen endring etter oppdatert virkningstidspunkt" }
                return
            }
        val sivilstandBeregnet = sisteAktiveGrunnlag.konvertereData<Set<SivilstandGrunnlagDto>>()!!
        val sivilstandPeriodisert =
            SivilstandApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                sivilstandBeregnet.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.fødselsdato),
            )
        behandling.henteNyesteAktiveGrunnlag(
            Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
            behandling.bidragsmottaker!!,
        )?.let {
            it.data = commonObjectmapper.writeValueAsString(sivilstandPeriodisert)
        }
    }

    @Transactional
    fun oppdatereIkkeAktivSivilstandEtterEndretVirkningsdato(behandling: Behandling) {
        val sisteIkkeAktiveGrunnlag =
            behandling.henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
                behandling.bidragsmottaker!!,
            ) ?: run {
                log.debug { "Fant ingen ikke-aktive sivilstandsgrunnlag. Gjør ingen endringer" }
                return
            }

        val sivilstand = sisteIkkeAktiveGrunnlag.konvertereData<Set<SivilstandGrunnlagDto>>()!!
        val periodisertHistorikk =
            SivilstandApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                sivilstand.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.fødselsdato),
            )

        behandling.henteNyesteIkkeAktiveGrunnlag(
            Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
            behandling.bidragsmottaker!!,
        )?.let {
            it.data = commonObjectmapper.writeValueAsString(periodisertHistorikk)
        }
    }

    @Transactional
    fun oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val sisteIkkeAktiveGrunnlag =
            behandling.henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                behandling.rolleGrunnlagSkalHentesFor!!,
            ) ?: run {
                log.debug { "Fant ingen ikke-aktive boforholdsgrunnlag. Gjør ingen endringer" }
                return
            }
        sisteIkkeAktiveGrunnlag.rekalkulerOgOppdaterBoforholdBearbeidetGrunnlag(false)
    }

    @Transactional
    fun oppdaterAktiveBoforholdEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val sisteAktiveGrunnlag =
            behandling.henteNyesteAktiveGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                behandling.rolleGrunnlagSkalHentesFor!!,
            ) ?: run {
                log.warn { "Fant ingen aktive boforholdsgrunnlag. Oppdaterer ikke boforhold beregnet etter virkningstidspunkt ble endret" }
                return
            }
        sisteAktiveGrunnlag.rekalkulerOgOppdaterBoforholdBearbeidetGrunnlag()
    }

    @Transactional
    fun oppdatereAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling: Behandling) {
        // TODO: Implementere
    }

    @Transactional
    fun oppdatereIkkeAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling: Behandling) {
        // TODO: Implementere
    }

    private fun Grunnlag.rekalkulerOgOppdaterBoforholdBearbeidetGrunnlag(rekalkulerOgOverskriveAktiverte: Boolean = true) {
        val boforhold = konvertereData<List<RelatertPersonGrunnlagDto>>()!!
        val boforholdPeriodisert =
            BoforholdApi.beregnBoforholdBarnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                boforhold.tilBoforholdBarnRequest(behandling),
            )
        boforholdPeriodisert.filter { it.relatertPersonPersonId != null }.groupBy { it.relatertPersonPersonId }
            .forEach { (gjelder, perioder) ->
                overskrivBearbeidetBoforholdGrunnlag(behandling, gjelder, perioder, rekalkulerOgOverskriveAktiverte)
            }
    }

    private fun overskrivBearbeidetBoforholdGrunnlag(
        behandling: Behandling,
        gjelder: String?,
        perioder: List<BoforholdResponse>,
        rekalkulerOgOverskriveAktiverte: Boolean = true,
    ) {
        val grunnlagSomSkalOverskrives =
            if (rekalkulerOgOverskriveAktiverte) {
                behandling.henteAktiverteGrunnlag(
                    Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    behandling.rolleGrunnlagSkalHentesFor!!,
                )
            } else {
                behandling.henteUaktiverteGrunnlag(
                    Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    behandling.rolleGrunnlagSkalHentesFor!!,
                )
            }
        grunnlagSomSkalOverskrives.find { it.gjelder == gjelder }?.let {
            it.data = tilJson(perioder)
        }
    }

    fun hentSistInnhentet(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagstype: Grunnlagstype,
    ): Grunnlag? =
        grunnlagRepository.findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
            behandlingsid,
            rolleid,
            grunnlagstype.type.getOrMigrate(),
            grunnlagstype.erBearbeidet,
        )

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
            husstandsmedlem =
                nyinnhentetGrunnlag.hentEndringerBoforhold(
                    aktiveGrunnlag,
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.husstandsmedlem,
                    behandling.rolleGrunnlagSkalHentesFor!!,
                ),
            andreVoksneIHusstanden = nyinnhentetGrunnlag.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag),
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
    ) {
        val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()

        val summerteInntekter = ikkeAktiveGrunnlag.henteBearbeidaInntekterForType(grunnlagstype, rolle.ident!!)

        inntektService.oppdatereAutomatiskInnhentaOffentligeInntekter(
            behandling,
            rolle,
            summerteInntekter?.inntekter ?: emptyList(),
            grunnlagstype,
        )
        ikkeAktiveGrunnlag.hentGrunnlagForType(grunnlagstype, rolle.ident!!).oppdaterStatusTilAktiv(LocalDateTime.now())
    }

    private fun aktivereBoforholdAndreVoksneIHusstanden(
        behandling: Behandling,
        overskriveManuelleOpplysninger: Boolean = false,
    ) {
        val nyesteIkkeaktiverteBoforhold =
            behandling.grunnlag.hentSisteIkkeAktiv()
                .filter { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type }


        if (nyesteIkkeaktiverteBoforhold.firstOrNull { it.erBearbeidet } == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type ${Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN}  " +
                    "å aktivere for BP i  behandling ${behandling.id}",
            )
        }

        boforholdService.oppdatereAutomatiskInnhentetBoforholdAndreVoksneIHusstanden(
            behandling,
            commonObjectmapper.readValue<Set<Bostatus>>(nyesteIkkeaktiverteBoforhold.first { it.erBearbeidet }.data),
            overskriveManuelleOpplysninger,
        )

        nyesteIkkeaktiverteBoforhold.forEach {
            it.aktiv =LocalDateTime.now()
        }
    }

    private fun aktivereBoforhold(
        behandling: Behandling,
        grunnlagstype: Grunnlagsdatatype,
        gjelderHusstandsmedlem: Personident,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val nyesteIkkeAktiverteBoforholdForHusstandsmedlem =
            behandling.grunnlag.hentSisteIkkeAktiv()
                .filter { gjelderHusstandsmedlem.verdi == it.gjelder && grunnlagstype == it.type }
                .firstOrNull { it.erBearbeidet }

        if (nyesteIkkeAktiverteBoforholdForHusstandsmedlem == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type $grunnlagstype å aktivere for oppgitt husstandsmeldem i  behandling " +
                    behandling.id,
            )
        }

        val bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting =
            behandling.grunnlag.hentIdenterForEgneBarnIHusstandFraGrunnlagForRolle(
                behandling.rolleGrunnlagSkalHentesFor!!,
            )

        // TOOD: Vurdere å trigge ny grunnlagsinnhenting
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.isNullOrEmpty()) {
            log.error {
                "Fant ingen husstandsmedlemmer som er barn av ${behandling.rolleGrunnlagSkalHentesFor!!.rolletype} i " +
                    "nyeste boforholdsgrunnlag i behandling ${behandling.id}"
            }
            throw HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Fant ingen husstandsmedlemmer som er barn av ${behandling.rolleGrunnlagSkalHentesFor!!.rolletype} " +
                    "i nyeste boforholdsgrunnlag i behandling ${behandling.id}",
            )
        }

        boforholdService.oppdatereAutomatiskInnhentetBoforhold(
            behandling,
            jsonTilObjekt<List<BoforholdResponse>>(nyesteIkkeAktiverteBoforholdForHusstandsmedlem.data),
            bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
            overskriveManuelleOpplysninger,
            gjelderHusstandsmedlem,
        )

        nyesteIkkeAktiverteBoforholdForHusstandsmedlem.aktiv = LocalDateTime.now()
        aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling)
    }

    private fun aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling: Behandling) {
        val nyesteIkkeBearbeidaBoforholdsgrunnlag =
            behandling.henteNyesteGrunnlag(
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
                behandling.rolleGrunnlagSkalHentesFor!!,
            )

        nyesteIkkeBearbeidaBoforholdsgrunnlag?.let {
            if (nyesteIkkeBearbeidaBoforholdsgrunnlag.aktiv == null &&
                erGrunnlagAktivertForAlleHusstandsmedlemmene(
                    it,
                    behandling,
                )
            ) {
                nyesteIkkeBearbeidaBoforholdsgrunnlag.aktiv = LocalDateTime.now()
            }
        }
    }

    private fun erGrunnlagAktivertForAlleHusstandsmedlemmene(
        bmsNyesteIkkeBearbeidaBoforholdsgrunnlag: Grunnlag,
        behandling: Behandling,
    ): Boolean {
        jsonListeTilObjekt<RelatertPersonGrunnlagDto>(bmsNyesteIkkeBearbeidaBoforholdsgrunnlag.data).filter {
            it.gjelderPersonId != null && it.erBarn
        }
            .groupBy {
                it.gjelderPersonId
            }.forEach {
                val nyesteGrunnlagForHusstandsmedlem =
                    behandling.henteNyesteGrunnlag(
                        Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                        behandling.rolleGrunnlagSkalHentesFor!!,
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

    private fun foretaNyGrunnlagsinnhenting(behandling: Behandling): Boolean =
        behandling.grunnlagSistInnhentet == null || behandling.grunnlagsinnhentingFeilet != null || LocalDateTime.now()
            .minusMinutes(grenseInnhenting.toLong()) > behandling.grunnlagSistInnhentet

    private fun henteOglagreGrunnlag(
        behandling: Behandling,
        grunnlagsrequest: Map.Entry<Personident, List<GrunnlagRequestDto>>,
    ): Map<Grunnlagsdatatype, FeilrapporteringDto?> {
        val innhentetGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(grunnlagsrequest.value)

        val feilrapporteringer: Map<Grunnlagsdatatype, FeilrapporteringDto?> =
            Grunnlagsdatatype.grunnlagsdatatypeobjekter(behandling.tilType()).associateWith {
                hentFeilrapporteringForGrunnlag(it, grunnlagsrequest.key, innhentetGrunnlag)
            }.filterNot { it.value == null }

        val rolleInnhentetFor = behandling.roller.find { it.ident == grunnlagsrequest.key.verdi }!!
        lagreGrunnlagHvisEndret(behandling, rolleInnhentetFor, innhentetGrunnlag, feilrapporteringer)

        val feilSkattepliktig = feilrapporteringer[Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER]

        if (feilSkattepliktig == null) {
            lagreGrunnlagHvisEndret(
                behandling,
                rolleInnhentetFor,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                SkattepliktigeInntekter(
                    innhentetGrunnlag.ainntektListe,
                    innhentetGrunnlag.skattegrunnlagListe,
                ),
                innhentetGrunnlag.hentetTidspunkt,
            )
        } else {
            log.warn {
                "Innhenting av ${Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER} for rolle ${rolleInnhentetFor.rolletype} " +
                    "i behandling ${behandling.id} feilet for type ${feilSkattepliktig.grunnlagstype} " +
                    "med begrunnelse ${feilSkattepliktig.feilmelding}. Lagrer ikke grunnlag"
            }
        }

        // Oppdatere inntektstabell med sammenstilte inntekter
        if (innhentetGrunnlagInneholderInntekterEllerYtelser(innhentetGrunnlag)) {
            sammenstilleOgLagreInntekter(behandling, innhentetGrunnlag, rolleInnhentetFor, feilrapporteringer)
        }

        val innhentingAvBoforholdFeilet =
            feilrapporteringer.filter { Grunnlagsdatatype.BOFORHOLD == it.key }.isNotEmpty()

        // Husstandsmedlem og bostedsperiode
        if (innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.isNotEmpty() && !innhentingAvBoforholdFeilet) {
            periodisereOgLagreBoforhold(
                behandling,
                innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
            )

            if (TypeBehandling.SÆRBIDRAG == behandling.tilType() && Rolletype.BIDRAGSPLIKTIG == rolleInnhentetFor.rolletype) {
                periodisereOgLagreBpsBoforholdAndreVoksne(
                    behandling,
                    innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
                )
            }
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
            SivilstandApi.beregnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                innhentetGrunnlag.sivilstandListe.toSet()
                    .tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.fødselsdato),
            ).toSet()

        val bmsNyesteBearbeidaSivilstandFørLagring =
            sistAktiverteGrunnlag<SivilstandBeregnV2Dto>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                behandling.bidragsmottaker!!,
            )

        lagreGrunnlagHvisEndret<SivilstandBeregnV2Dto>(
            behandling,
            behandling.bidragsmottaker!!,
            Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
            sivilstandPeriodisert.toSet(),
        )

        val bmsNyesteBearbeidaSivilstandEtterLagring =
            sistAktiverteGrunnlag<SivilstandBeregnV2Dto>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                behandling.bidragsmottaker!!,
            )

        if (bmsNyesteBearbeidaSivilstandFørLagring.isEmpty() && bmsNyesteBearbeidaSivilstandEtterLagring.isNotEmpty()) {
            boforholdService.lagreFørstegangsinnhentingAvPeriodisertSivilstand(behandling, sivilstandPeriodisert)
        }
        aktivereSivilstandHvisEndringIkkeKreverGodkjenning(behandling)
    }

    private fun periodisereOgLagreBpsBoforholdAndreVoksne(
        behandling: Behandling,
        husstandsmedlemmerOgEgneBarn: Set<RelatertPersonGrunnlagDto>,
    ) {
        val andreVoksneIHusstanden =
            BoforholdApi.beregnBoforholdAndreVoksne(
                behandling.virkningstidspunktEllerSøktFomDato,
                husstandsmedlemmerOgEgneBarn.tilBoforholdVoksneRequest(),
            ).toSet()

        val bpsNyesteBearbeidaBoforholdFørLagring =
            sistAktiverteGrunnlag<Bostatus>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN, true),
                behandling.bidragspliktig!!,
            )

        lagreGrunnlagHvisEndret(
            behandling,
            behandling.bidragspliktig!!,
            Grunnlagstype(Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN, true),
            andreVoksneIHusstanden,
        )

        val bpsNyesteBearbeidaBoforholdEtterLagring =
            sistAktiverteGrunnlag<Bostatus>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN, true),
                behandling.bidragspliktig!!,
            )

        if (bpsNyesteBearbeidaBoforholdFørLagring.isEmpty() && bpsNyesteBearbeidaBoforholdEtterLagring.isNotEmpty()) {
            boforholdService.lagreFørstegangsinnhentingAvAndreVoksneIBpsHusstand(behandling, andreVoksneIHusstanden)
        }

        aktivereGrunnlagForBoforholdAndreVoksneIHusstandenHvisIngenEndringerMåAksepteres(behandling)
    }

    private fun periodisereOgLagreBoforhold(
        behandling: Behandling,
        husstandsmedlemmerOgEgneBarn: Set<RelatertPersonGrunnlagDto>,
    ) {
        val boforholdPeriodisert =
            BoforholdApi.beregnBoforholdBarnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                husstandsmedlemmerOgEgneBarn.tilBoforholdBarnRequest(behandling),
            )

        val nyesteBearbeidaBoforholdFørLagring =
            sistAktiverteGrunnlag<BoforholdResponse>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                behandling.rolleGrunnlagSkalHentesFor!!,
            )

        // lagre bearbeidet grunnlag per husstandsmedlem i grunnlagstabellen
        boforholdPeriodisert.filter { it.relatertPersonPersonId != null }.groupBy { it.relatertPersonPersonId }
            .forEach {
                lagreGrunnlagHvisEndret<BoforholdResponse>(
                    behandling = behandling,
                    innhentetForRolle = behandling.rolleGrunnlagSkalHentesFor!!,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                    innhentetGrunnlag = it.value.toSet(),
                    gjelderPerson = Personident(it.key!!),
                )
            }

        val innhentetRollesNyesteBearbeidaBoforholdEtterLagring =
            sistAktiverteGrunnlag<BoforholdResponse>(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                behandling.rolleGrunnlagSkalHentesFor!!,
            )

        // oppdatere husstandsmedlem og bostatusperiode-tabellene hvis førstegangslagring
        if (nyesteBearbeidaBoforholdFørLagring.isEmpty() && innhentetRollesNyesteBearbeidaBoforholdEtterLagring.isNotEmpty()) {
            boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(behandling, boforholdPeriodisert)
        }

        aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling)
    }

    fun aktivereGrunnlagForBoforholdAndreVoksneIHusstandenHvisIngenEndringerMåAksepteres(behandling: Behandling) {
        val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()
        val aktiveGrunnlag = behandling.grunnlag.hentAlleAktiv()
        if (ikkeAktiveGrunnlag.isEmpty()) return

        val endringerSomMåBekreftes = ikkeAktiveGrunnlag.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag)

        if (endringerSomMåBekreftes == null || endringerSomMåBekreftes.perioder.isEmpty()) {
            val ikkeAktivtBoforholdBp =
                ikkeAktiveGrunnlag.hentGrunnlagForType(
                    Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                    behandling.bidragspliktig!!.ident!!,
                ).firstOrNull()
            ikkeAktivtBoforholdBp?.let {
                log.info {
                    "Bps ikke aktive boforholdsgrunnlag ${ikkeAktivtBoforholdBp.id} med type " +
                        "${Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN} i behandling ${behandling.id} har " +
                        "ingen endringer som må bekreftes av saksbehandler. Automatisk aktiverer ny innhentet " +
                        "grunnlag."
                }
                it.aktiv = LocalDateTime.now()
            }
        }
    }

    fun aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling: Behandling) {
        val rolleInhentetFor = behandling.rolleGrunnlagSkalHentesFor
        val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()
        val aktiveGrunnlag = behandling.grunnlag.hentAlleAktiv()
        if (ikkeAktiveGrunnlag.isEmpty()) return
        val endringerSomMåBekreftes =
            ikkeAktiveGrunnlag.hentEndringerBoforhold(
                aktiveGrunnlag,
                behandling.virkningstidspunktEllerSøktFomDato,
                behandling.husstandsmedlem,
                rolleInhentetFor!!,
            )

        behandling.husstandsmedlem.filter { it.kilde == Kilde.OFFENTLIG }
            .filter { hb -> endringerSomMåBekreftes.none { it.ident == hb.ident } }.forEach { hb ->
                val ikkeAktivGrunnlag =
                    ikkeAktiveGrunnlag.hentGrunnlagForType(Grunnlagsdatatype.BOFORHOLD, rolleInhentetFor.ident!!)
                        .find { it.gjelder != null && it.gjelder == hb.ident } ?: return@forEach
                log.info {
                    "Ikke aktive boforhold grunnlag ${ikkeAktivGrunnlag.id} med type ${Grunnlagsdatatype.BOFORHOLD}" +
                        " for rolle ${rolleInhentetFor.rolletype}" +
                        " i behandling ${behandling.id} har ingen endringer som må bekreftes av saksbehandler. " +
                        "Automatisk aktiverer ny innhentet grunnlag."
                }
                ikkeAktivGrunnlag.aktiv = LocalDateTime.now()
            }

        aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling)
    }

    fun aktivereSivilstandHvisEndringIkkeKreverGodkjenning(behandling: Behandling) {
        val rolleInhentetFor = behandling.bidragsmottaker!!
        val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()
        val aktiveGrunnlag = behandling.grunnlag.hentAlleAktiv()
        if (ikkeAktiveGrunnlag.isEmpty()) return
        val endringerSomMåBekreftes =
            ikkeAktiveGrunnlag.hentEndringerSivilstand(aktiveGrunnlag, behandling.virkningstidspunktEllerSøktFomDato)

        if (endringerSomMåBekreftes == null) {
            val ikkeAktiverteSivilstandsgrunnlag =
                ikkeAktiveGrunnlag.hentGrunnlagForType(Grunnlagsdatatype.SIVILSTAND, rolleInhentetFor.ident!!)

            ikkeAktiverteSivilstandsgrunnlag.forEach {
                val type =
                    when (it.erBearbeidet) {
                        true -> "bearbeida"
                        false -> "ikke-bearbeida"
                    }

                log.info {
                    "Ikke-aktivert $type sivilstandsgrunnlag med id ${it.id} i behandling ${behandling.id},"
                    "har ingen endringer som må aksepeteres av saksbehandler. Grunnlaget aktiveres derfor automatisk."
                }

                it.aktiv = LocalDateTime.now()
            }
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
    ): SummerteInntekter<*>? =
        when (type) {
            Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter = sammenstilteInntekter.summertMånedsinntektListe,
                )

            Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter =
                        sammenstilteInntekter.summertÅrsinntektListe.filter { summertAinntektstyper.contains(it.inntektRapportering) } +
                            sammenstilteInntekter.summertÅrsinntektListe.filter {
                                summertSkattegrunnlagstyper.contains(it.inntektRapportering)
                            },
                )

            Grunnlagsdatatype.BARNETILLEGG ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter = sammenstilteInntekter.summertÅrsinntektListe.filter { BARNETILLEGG == it.inntektRapportering },
                )

            Grunnlagsdatatype.KONTANTSTØTTE ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter = sammenstilteInntekter.summertÅrsinntektListe.filter { KONTANTSTØTTE == it.inntektRapportering },
                )

            Grunnlagsdatatype.SMÅBARNSTILLEGG ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter = sammenstilteInntekter.summertÅrsinntektListe.filter { SMÅBARNSTILLEGG == it.inntektRapportering },
                )

            Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
                SummerteInntekter(
                    versjon = sammenstilteInntekter.versjon,
                    inntekter = sammenstilteInntekter.summertÅrsinntektListe.filter { UTVIDET_BARNETRYGD == it.inntektRapportering },
                )

            // Ikke-tilgjengelig kode
            else -> null
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
            it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }?.let { commonObjectmapper.readValue<Set<T>>(it.data) }?.toSet() ?: emptySet()

    private inline fun <reified T> nyesteGrunnlag(
        behandling: Behandling,
        innhentetForRolle: Rolle,
        grunnlagstype: Grunnlagstype,
        gjelderPerson: Personident?,
    ): Set<T> {
        // TODO: Fjerne håndtering av SivilstandApi versjon 1-data når Sivsilstandsgrunnlag er oppdatert for samtlige behandlinger
        if (Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true) == grunnlagstype) {
            try {
                return behandling.hentSisteInnhentetGrunnlagSet(
                    grunnlagstype,
                    innhentetForRolle,
                    gjelderPerson,
                )
            } catch (exception: Exception) {
                log.warn {
                    "Exception oppstod ved parsing av nyeste bearbeida sivilstandsgrunnlag:  ${exception.message}. " +
                        "Dette skyldes mest sannsynlig gamle data."
                }
                return emptySet()
            }
        }

        return behandling.hentSisteInnhentetGrunnlagSet(
            grunnlagstype,
            innhentetForRolle,
            gjelderPerson,
        )
    }

    private inline fun <reified T> lagreGrunnlagHvisEndret(
        behandling: Behandling,
        innhentetForRolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: Set<T>,
        aktiveringstidspunkt: LocalDateTime? = null,
        gjelderPerson: Personident? = null,
    ) {
        log.info { "Lagrer grunnlag $grunnlagstype, $innhentetGrunnlag hvis endret" }
        val sistInnhentedeGrunnlagAvTypeForRolle: Set<T> =
            nyesteGrunnlag(behandling, innhentetForRolle, grunnlagstype, gjelderPerson)

        val nyesteGrunnlag = behandling.henteNyesteGrunnlag(grunnlagstype, innhentetForRolle, gjelderPerson)
        val erFørstegangsinnhenting = nyesteGrunnlag == null

        val erGrunnlagEndret =
            erGrunnlagEndret(
                grunnlagstype = grunnlagstype,
                nyttGrunnlag = innhentetGrunnlag,
                aktivtGrunnlag = sistInnhentedeGrunnlagAvTypeForRolle,
                behandling = behandling,
            )

        if (erFørstegangsinnhenting && innhentetGrunnlag.isNotEmpty() || erGrunnlagEndret &&
            nyesteGrunnlag?.aktiv != null
        ) {
            val aktivert =
                if (nyesteGrunnlag?.aktiv != null) {
                    aktiveringstidspunkt
                } else {
                    LocalDateTime.now()
                }
            opprett(
                behandling = behandling,
                data = tilJson(innhentetGrunnlag),
                grunnlagstype = grunnlagstype,
                innhentet = LocalDateTime.now(),
                aktiv = aktivert,
                idTilRolleInnhentetFor = innhentetForRolle.id!!,
                gjelder = gjelderPerson,
            )
            if (grunnlagstype.erBearbeidet && aktivert != null) {
                aktivereSisteInnhentedeRådata(grunnlagstype.type, innhentetForRolle, behandling)
            }
        } else if (erGrunnlagEndret) {
            val uaktiverteGrunnlag =
                behandling.henteUaktiverteGrunnlag(grunnlagstype, innhentetForRolle)
                    .filter { gjelderPerson == null || it.gjelder == gjelderPerson.verdi }
            val grunnlagSomSkalOppdateres = uaktiverteGrunnlag.maxBy { it.innhentet }

            log.info {
                "Oppdaterer uaktivert grunnlag ${grunnlagSomSkalOppdateres.id} " +
                    "i behandling ${behandling.id} med ny innhentet grunnlagsdata"
            }
            grunnlagSomSkalOppdateres.data = tilJson(innhentetGrunnlag)
            grunnlagSomSkalOppdateres.innhentet = LocalDateTime.now()
            grunnlagSomSkalOppdateres.aktiv = aktiveringstidspunkt

            uaktiverteGrunnlag.filter { it.id != grunnlagSomSkalOppdateres.id }.forEach {
                log.info {
                    "Sletter grunnlag ${it.id} fra behandling ${behandling.id} " +
                        "fordi den er duplikat av grunnlag ${grunnlagSomSkalOppdateres.id}"
                }
                secureLogger.info {
                    "Sletter grunnlag ${it.id} fra behandling ${behandling.id} " +
                        "fordi den er duplikat av grunnlag ${grunnlagSomSkalOppdateres.id}: $it"
                }
                behandling.grunnlag.remove(it)
                grunnlagRepository.deleteById(it.id!!)
            }
        } else {
            log.info { "Ingen endringer i grunnlag $grunnlagstype for behandling med id $behandling." }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> erGrunnlagEndret(
        grunnlagstype: Grunnlagstype,
        aktivtGrunnlag: Set<T>,
        nyttGrunnlag: Set<T>,
        behandling: Behandling,
    ): Boolean {
        if (!grunnlagstype.erBearbeidet) return aktivtGrunnlag.toSet() != nyttGrunnlag.toSet()
        return if (grunnlagstype.type == Grunnlagsdatatype.BOFORHOLD) {
            val aktivtGrunnlagFiltrert =
                (aktivtGrunnlag as Set<BoforholdResponse>).toList().filtrerPerioderEtterVirkningstidspunkt(
                    behandling.husstandsmedlem,
                    behandling.virkningstidspunktEllerSøktFomDato,
                ).toSet()
            val nyttGrunnlagFiltrert =
                (nyttGrunnlag as Set<BoforholdResponse>).toList().filtrerPerioderEtterVirkningstidspunkt(
                    behandling.husstandsmedlem,
                    behandling.virkningstidspunktEllerSøktFomDato,
                ).toSet()
            aktivtGrunnlagFiltrert.finnEndringerBoforhold(
                behandling.virkningstidspunktEllerSøktFomDato,
                nyttGrunnlagFiltrert,
            ).isNotEmpty()
        } else if (grunnlagstype.type == Grunnlagsdatatype.SIVILSTAND) {
            if (aktivtGrunnlag.isEmpty() && behandling.sivilstand.isNotEmpty()) {
                return true
            }
            try {
                val nyinnhentetGrunnlag =
                    (nyttGrunnlag as Set<Sivilstand>).toList()
                        .filtrerSivilstandBeregnetEtterVirkningstidspunktV2(behandling.virkningstidspunktEllerSøktFomDato)
                val aktiveGrunnlag =
                    (aktivtGrunnlag as Set<Sivilstand>).toList()
                        .filtrerSivilstandBeregnetEtterVirkningstidspunktV2(behandling.virkningstidspunktEllerSøktFomDato)
                !nyinnhentetGrunnlag.erLik(aktiveGrunnlag)
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved sjekk mot sivilstand diff ved grunnlagsinnhenting" }
                aktivtGrunnlag.toSet() != nyttGrunnlag.toSet()
            }
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
                .filter { innhentetForRolle == it.rolle }.maxByOrNull { it.innhentet }

        sisteInnhentedeIkkeBearbeidaGrunnlag?.let {
            if (it.aktiv == null) {
                when (grunnlagsdatatype) {
                    Grunnlagsdatatype.BOFORHOLD -> {
                        if (grunnlagErAktivertForAlleHusstandsmedlemmer(
                                behandling,
                                sisteInnhentedeIkkeBearbeidaGrunnlag,
                                innhentetForRolle,
                            )
                        ) {
                            it.aktiv = LocalDateTime.now()
                        }
                    }

                    else -> it.aktiv = LocalDateTime.now()
                }
            }
        }
    }

    private fun grunnlagErAktivertForAlleHusstandsmedlemmer(
        behandling: Behandling,
        sisteInnhentedeIkkeBearbeidaGrunnlag: Grunnlag?,
        innhentetForRolle: Rolle,
    ): Boolean {
        val nyesteRådata = jsonTilObjekt<List<RelatertPersonGrunnlagDto>>(sisteInnhentedeIkkeBearbeidaGrunnlag!!.data)
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
            sistInnhentedeGrunnlagAvType == null && (inneholderInntekter(innhentetGrunnlag) || erAvTypeBearbeidetSivilstand)
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
            }
        } else if (erGrunnlagEndretSidenSistInnhentet) {
            val grunnlagSomSkalOppdateres =
                behandling.henteUaktiverteGrunnlag(grunnlagstype, rolle).maxByOrNull { it.innhentet }
            grunnlagSomSkalOppdateres?.data = tilJson(innhentetGrunnlag)
            grunnlagSomSkalOppdateres?.innhentet = hentetTidspunkt
            // Summerte månedsinntekter settes alltid til aktiv
            grunnlagSomSkalOppdateres?.aktiv =
                if (Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == grunnlagstype.type.getOrMigrate()) {
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

    fun <T> inneholderInntekter(grunnlag: T): Boolean =
        when (grunnlag) {
            is SkattepliktigeInntekter -> grunnlag.ainntekter.isNotEmpty() || grunnlag.skattegrunnlag.isNotEmpty()
            is SummerteInntekter<*> -> grunnlag.inntekter.isNotEmpty()
            else -> false
        }

    private fun Behandling.henteUaktiverteGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
    ): Set<Grunnlag> =
        grunnlag.hentAlleIkkeAktiv().filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet()

    private fun Behandling.henteAktiverteGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
    ): Set<Grunnlag> =
        grunnlag.hentAlleAktiv().filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet()

    private fun Behandling.henteNyesteGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
        gjelder: Personident?,
    ): Grunnlag? =
        grunnlag.filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id &&
                grunnlagstype.erBearbeidet == it.erBearbeidet && it.gjelder == gjelder?.verdi
        }.toSet().maxByOrNull { it.innhentet }

    private fun Behandling.henteNyesteIkkeAktiveGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolleInnhentetFor: Rolle,
    ): Grunnlag? =
        grunnlag.filter {
            it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id &&
                grunnlagstype.erBearbeidet == it.erBearbeidet && it.aktiv == null
        }.toSet().maxByOrNull { it.innhentet }

    private fun Behandling.henteNyesteAktiveGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolleInnhentetFor: Rolle,
    ): Grunnlag? =
        grunnlag.filter {
            it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id &&
                grunnlagstype.erBearbeidet == it.erBearbeidet && it.aktiv != null
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
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && it.gjelder == gjelderPerson?.verdi &&
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
        Grunnlagsdatatype.grunnlagsdatatypeobjekter(behandling.tilType(), rolleInhentetFor.rolletype).filter {
            !setOf(
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
            ).contains(it)
        }.forEach {
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
        innhentetFor: Personident,
        innhentetGrunnlag: HentGrunnlagDto,
    ): FeilrapporteringDto? =
        when (grunnlagsdatatype) {
            Grunnlagsdatatype.ARBEIDSFORHOLD ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.ARBEIDSFORHOLD,
                    innhentetFor,
                )

            Grunnlagsdatatype.BARNETILLEGG ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.BARNETILLEGG,
                    innhentetFor,
                )

            Grunnlagsdatatype.SMÅBARNSTILLEGG ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG,
                    innhentetFor,
                )

            Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER, Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.SKATTEGRUNNLAG,
                    innhentetFor,
                ) ?: innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.AINNTEKT,
                    innhentetFor,
                )

            Grunnlagsdatatype.KONTANTSTØTTE ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.KONTANTSTØTTE,
                    innhentetFor,
                )

            Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG,
                    innhentetFor,
                )

            Grunnlagsdatatype.BOFORHOLD ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.HUSSTANDSMEDLEMMER_OG_EGNE_BARN,
                    innhentetFor,
                )

            Grunnlagsdatatype.SIVILSTAND ->
                innhentetGrunnlag.hentFeilFor(
                    GrunnlagRequestType.SIVILSTAND,
                    innhentetFor,
                )

            else -> null
        }

    private fun HentGrunnlagDto.hentFeilFor(
        type: GrunnlagRequestType,
        personident: Personident,
    ) = feilrapporteringListe.find {
        it.grunnlagstype == type && it.personId == personident.verdi
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
                if (innhentetGrunnlag.barnetilsynListe.isNotEmpty()) {
                    log.info { "Barnetilsyn er ikke relevant for forskudd." }
                }
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

            Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN -> {
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
