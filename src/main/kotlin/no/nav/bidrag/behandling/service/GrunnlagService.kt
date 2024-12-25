package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.aktiveringAvGrunnlagstypeIkkeStøttetException
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.consumer.HentetGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentGrunnlagForType
import no.nav.bidrag.behandling.database.datamodell.hentIdenterForEgneBarnIHusstandFraGrunnlagForRolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.henteBearbeidaInntekterForType
import no.nav.bidrag.behandling.database.datamodell.henteNyesteAktiveGrunnlag
import no.nav.bidrag.behandling.database.datamodell.henteNyesteIkkeAktiveGrunnlag
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.getOrMigrate
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.lagringAvGrunnlagFeiletException
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.ressursIkkeFunnetException
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonTilObjekt
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.behandling.erDetSammeSom
import no.nav.bidrag.behandling.transformers.behandling.filtrerPerioderEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.behandling.filtrerSivilstandBeregnetEtterVirkningstidspunktV2
import no.nav.bidrag.behandling.transformers.behandling.finnEndringerBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerSivilstand
import no.nav.bidrag.behandling.transformers.behandling.henteAktiverteGrunnlag
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIBoforhold
import no.nav.bidrag.behandling.transformers.behandling.henteUaktiverteGrunnlag
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdVoksneRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.grunnlag.henteNyesteGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.inntekterOgYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.summertAinntektstyper
import no.nav.bidrag.behandling.transformers.grunnlag.summertSkattegrunnlagstyper
import no.nav.bidrag.behandling.transformers.inntekt.opprettTransformerInntekterRequest
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.underhold.aktivereBarnetilsynHvisIngenEndringerMåAksepteres
import no.nav.bidrag.behandling.transformers.underhold.tilBarnetilsyn
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.grunnlag.HentGrunnlagFeiltype
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
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto

private val log = KotlinLogging.logger {}

@Service
class GrunnlagService(
    private val bidragGrunnlagConsumer: BidragGrunnlagConsumer,
    private val boforholdService: BoforholdService,
    private val grunnlagRepository: GrunnlagRepository,
    private val inntektApi: InntektApi,
    private val inntektService: InntektService,
    private val mapper: Dtomapper,
    private val underholdService: UnderholdService,
) {
    @Value("\${egenskaper.grunnlag.min-antall-minutter-siden-forrige-innhenting}")
    private lateinit var grenseInnhenting: String

    @Transactional
    fun oppdatereGrunnlagForBehandling(behandling: Behandling) {
        if (foretaNyGrunnlagsinnhenting(behandling)) {
            val grunnlagRequestobjekter = BidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)
            val feilrapporteringer = mutableMapOf<Grunnlagsdatatype, FeilrapporteringDto?>()
            val tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter =
                tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter(behandling)
            behandling.grunnlagsinnhentingFeilet = null

            grunnlagRequestobjekter.forEach {
                feilrapporteringer +=
                    henteOglagreGrunnlag(
                        behandling,
                        it,
                        tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter,
                    )
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
                Grunnlagsdatatype.BARNETILSYN, Grunnlagsdatatype.BOFORHOLD, Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN ->
                    request.grunnlagstype.innhentesForRolle(
                        behandling,
                    )

                else ->
                    behandling.roller.find { request.personident == it.personident }
                        ?: request.grunnlagstype.innhentesForRolle(behandling)
            }

        if (!listOf(Grunnlagsdatatype.BARNETILSYN, Grunnlagsdatatype.BOFORHOLD).contains(request.grunnlagstype)) {
            Validate.notNull(
                rolleGrunnlagErInnhentetFor,
                "Personident oppgitt i AktivereGrunnlagRequest har ikke rolle i behandling ${behandling.id}",
            )
        }

        val harIkkeaktivertGrunnlag =
            behandling.grunnlag
                .hentSisteIkkeAktiv()
                .filter { rolleGrunnlagErInnhentetFor!!.ident == it.rolle.ident }
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
        } else if (Grunnlagsdatatype.BARNETILSYN == request.grunnlagstype) {
            underholdService.oppdatereAutomatiskInnhentaStønadTilBarnetilsyn(
                behandling,
                request.gjelderIdent!!,
                request.overskriveManuelleOpplysninger,
            )
        } else if (Grunnlagsdatatype.BOFORHOLD == request.grunnlagstype) {
            aktivereBoforhold(
                behandling,
                request.gjelderIdent!!,
                request.overskriveManuelleOpplysninger,
            )
        } else if (Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == request.grunnlagstype) {
            aktivereBoforholdAndreVoksneIHusstanden(behandling, request.overskriveManuelleOpplysninger)
        } else if (Grunnlagsdatatype.SIVILSTAND == request.grunnlagstype) {
            boforholdService.oppdatereAutomatiskInnhentaSivilstand(
                behandling,
                request.overskriveManuelleOpplysninger,
            )
        } else if (Grunnlagsdatatype.ARBEIDSFORHOLD == request.grunnlagstype) {
            log.info { "Aktiverer arbeidsforhold for rolleid ${rolleGrunnlagErInnhentetFor?.id} i behandling med id ${behandling.id}." }
            behandling.grunnlag
                .hentAlleIkkeAktiv()
                .hentGrunnlagForType(Grunnlagsdatatype.ARBEIDSFORHOLD, request.personident!!.verdi)
                .oppdaterStatusTilAktiv(LocalDateTime.now())
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
        val grunnlagsdatatype = Grunnlagsdatatype.SIVILSTAND
        val sisteAktiveGrunnlag =
            behandling.henteNyesteAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
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
        behandling
            .henteNyesteAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, true),
                behandling.bidragsmottaker!!,
            )?.let {
                it.data = commonObjectmapper.writeValueAsString(sivilstandPeriodisert)
            }
    }

    @Transactional
    fun oppdatereIkkeAktivSivilstandEtterEndretVirkningsdato(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.SIVILSTAND
        val sisteIkkeAktiveGrunnlag =
            behandling.henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
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

        behandling
            .henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, true),
                behandling.bidragsmottaker!!,
            )?.let {
                it.data = commonObjectmapper.writeValueAsString(periodisertHistorikk)
            }
    }

    @Transactional
    fun oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD
        val sisteIkkeAktiveGrunnlag =
            behandling.henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
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
                Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)!!,
            ) ?: run {
                log.warn { "Fant ingen aktive boforholdsgrunnlag. Oppdaterer ikke boforhold beregnet etter virkningstidspunkt ble endret" }
                return
            }
        sisteAktiveGrunnlag.rekalkulerOgOppdaterBoforholdBearbeidetGrunnlag()
    }

    @Transactional
    fun oppdatereAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN
        val sisteAktiveGrunnlag =
            behandling.henteNyesteAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
            ) ?: run {
                log.warn {
                    "Fant ingen aktive andre voksne i husstanden. Oppdaterer ikke andre voksne i husstanden beregnet etter virkningstidspunkt ble endret"
                }
                return
            }
        sisteAktiveGrunnlag.rekalkulerOgOppdaterAndreVoksneIHusstandenBearbeidetGrunnlag()
    }

    @Transactional
    fun oppdatereIkkeAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN
        val sisteIkkeAktiveGrunnlag =
            behandling.henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
            ) ?: run {
                log.debug { "Fant ingen ikke-aktive andre voksne i husstanden grunnlag. Gjør ingen endringer" }
                return
            }
        sisteIkkeAktiveGrunnlag.rekalkulerOgOppdaterAndreVoksneIHusstandenBearbeidetGrunnlag(false)
    }

    private fun tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter(behandling: Behandling) =
        behandling.grunnlagsinnhentingFeilet?.let {
            val t =
                commonObjectmapper
                    .readValue<Map<Grunnlagsdatatype, FeilrapporteringDto?>>(it)
                    .any { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.key }
            t
        } ?: false

    private fun Grunnlag.rekalkulerOgOppdaterAndreVoksneIHusstandenBearbeidetGrunnlag(rekalkulerOgOverskriveAktiverte: Boolean = true) {
        val boforhold = konvertereData<Set<RelatertPersonGrunnlagDto>>()!!
        val andreVoksneIHusstandenPeriodisert =
            BoforholdApi.beregnBoforholdAndreVoksne(
                behandling.virkningstidspunktEllerSøktFomDato,
                boforhold.tilBoforholdVoksneRequest(),
            )

        overskrivBearbeidetAndreVoksneIHusstandenGrunnlag(
            behandling,
            andreVoksneIHusstandenPeriodisert,
            rekalkulerOgOverskriveAktiverte,
        )
    }

    private fun Grunnlag.rekalkulerOgOppdaterBoforholdBearbeidetGrunnlag(rekalkulerOgOverskriveAktiverte: Boolean = true) {
        val boforhold = konvertereData<List<RelatertPersonGrunnlagDto>>()!!
        val boforholdPeriodisert =
            BoforholdApi.beregnBoforholdBarnV3(
                behandling.virkningstidspunktEllerSøktFomDato,
                behandling.tilType(),
                boforhold.tilBoforholdBarnRequest(behandling, true),
            )
        boforholdPeriodisert
            .filter { it.gjelderPersonId != null }
            .groupBy { it.gjelderPersonId }
            .forEach { (gjelder, perioder) ->
                overskrivBearbeidetBoforholdGrunnlag(behandling, gjelder, perioder, rekalkulerOgOverskriveAktiverte)
            }
    }

    private fun overskrivBearbeidetAndreVoksneIHusstandenGrunnlag(
        behandling: Behandling,
        perioder: List<Bostatus>,
        rekalkulerOgOverskriveAktiverte: Boolean = true,
    ) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN

        val grunnlagSomSkalOverskrives =
            if (rekalkulerOgOverskriveAktiverte) {
                behandling.henteAktiverteGrunnlag(
                    Grunnlagstype(grunnlagsdatatype, true),
                    grunnlagsdatatype.innhentesForRolle(behandling)!!,
                )
            } else {
                behandling.henteUaktiverteGrunnlag(
                    Grunnlagstype(grunnlagsdatatype, true),
                    grunnlagsdatatype.innhentesForRolle(behandling)!!,
                )
            }
        grunnlagSomSkalOverskrives.forEach {
            it.data = tilJson(perioder)
        }
    }

    private fun overskrivBearbeidetBoforholdGrunnlag(
        behandling: Behandling,
        gjelder: String?,
        perioder: List<BoforholdResponseV2>,
        rekalkulerOgOverskriveAktiverte: Boolean = true,
    ) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD

        val grunnlagSomSkalOverskrives =
            if (rekalkulerOgOverskriveAktiverte) {
                behandling.henteAktiverteGrunnlag(
                    Grunnlagstype(grunnlagsdatatype, true),
                    grunnlagsdatatype.innhentesForRolle(behandling)!!,
                )
            } else {
                behandling.henteUaktiverteGrunnlag(
                    Grunnlagstype(grunnlagsdatatype, true),
                    grunnlagsdatatype.innhentesForRolle(behandling)!!,
                )
            }
        grunnlagSomSkalOverskrives.find { it.gjelder == gjelder }?.let { it.data = tilJson(perioder) }
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
        log.info {
            "Aktiverer boforhold for andre voksne i husstanden for behandling ${behandling.id}. overskriveManuelleOpplysninger=$overskriveManuelleOpplysninger"
        }
        val nyesteIkkeaktiverteBoforhold =
            behandling.grunnlag
                .hentSisteIkkeAktiv()
                .filter { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type }

        val nyesteIkkeAktivertBearbeidetBoforhold = nyesteIkkeaktiverteBoforhold.firstOrNull { it.erBearbeidet }
        val nyesteIkkeAktivertGrunnlagBoforhold = nyesteIkkeaktiverteBoforhold.firstOrNull { !it.erBearbeidet }

        if (nyesteIkkeAktivertGrunnlagBoforhold == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type ${Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN}  " +
                    "å aktivere for BP i  behandling ${behandling.id}",
            )
        }

        boforholdService.oppdatereAutomatiskInnhentetBoforholdAndreVoksneIHusstanden(
            behandling,
            nyesteIkkeAktivertBearbeidetBoforhold?.konvertereData<Set<Bostatus>>() ?: emptySet(),
            nyesteIkkeAktivertGrunnlagBoforhold.konvertereData<List<RelatertPersonGrunnlagDto>>()!!,
            overskriveManuelleOpplysninger,
        )

        nyesteIkkeaktiverteBoforhold.forEach {
            it.aktiv = LocalDateTime.now()
        }
    }

    private fun aktivereBoforhold(
        behandling: Behandling,
        gjelderHusstandsmedlem: Personident,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD
        val nyesteIkkeAktiverteBoforholdForHusstandsmedlem =
            behandling.grunnlag
                .hentSisteIkkeAktiv()
                .filter { gjelderHusstandsmedlem.verdi == it.gjelder && grunnlagsdatatype == it.type }
                .firstOrNull { it.erBearbeidet }

        if (nyesteIkkeAktiverteBoforholdForHusstandsmedlem == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type $grunnlagsdatatype å aktivere for oppgitt husstandsmeldem i  behandling " +
                    behandling.id,
            )
        }

        val bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting =
            behandling.grunnlag.hentIdenterForEgneBarnIHusstandFraGrunnlagForRolle(
                grunnlagsdatatype.innhentesForRolle(behandling) ?: throw HttpClientErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Det oppstod en feil ved aktivering av boforhold i behandling ${behandling.id}",
                ),
            )

        // TOOD: Vurdere å trigge ny grunnlagsinnhenting
        if (bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting.isNullOrEmpty()) {
            log.error {
                "Fant ingen husstandsmedlemmer som er barn av ${grunnlagsdatatype.innhentesForRolle(behandling)!!.rolletype} i " +
                    "nyeste boforholdsgrunnlag i behandling ${behandling.id}"
            }
            throw HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Fant ingen husstandsmedlemmer som er barn av ${grunnlagsdatatype.innhentesForRolle(behandling)!!.rolletype} " +
                    "i nyeste boforholdsgrunnlag i behandling ${behandling.id}",
            )
        }

        boforholdService.oppdatereAutomatiskInnhentetBoforhold(
            behandling,
            jsonTilObjekt<List<BoforholdResponseV2>>(nyesteIkkeAktiverteBoforholdForHusstandsmedlem.data),
            bmsEgneBarnIHusstandenFraNyesteGrunnlagsinnhenting,
            overskriveManuelleOpplysninger,
            gjelderHusstandsmedlem,
        )

        nyesteIkkeAktiverteBoforholdForHusstandsmedlem.aktiv = LocalDateTime.now()
        aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling)
    }

    private fun aktivereInnhentetBoforholdsgrunnlagHvisBearbeidetGrunnlagErAktivertForAlleHusstandsmedlemmene(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD
        val nyesteIkkeBearbeidaBoforholdsgrunnlag =
            behandling.henteNyesteGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
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
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD
        jsonListeTilObjekt<RelatertPersonGrunnlagDto>(bmsNyesteIkkeBearbeidaBoforholdsgrunnlag.data)
            .filter {
                it.gjelderPersonId != null && it.erBarn
            }.groupBy {
                it.gjelderPersonId
            }.forEach {
                val nyesteGrunnlagForHusstandsmedlem =
                    behandling.henteNyesteGrunnlag(
                        Grunnlagstype(grunnlagsdatatype, true),
                        grunnlagsdatatype.innhentesForRolle(behandling)!!,
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
        behandling.grunnlagSistInnhentet == null ||
            behandling.grunnlagsinnhentingFeilet != null ||
            LocalDateTime
                .now()
                .minusMinutes(grenseInnhenting.toLong()) > behandling.grunnlagSistInnhentet

    private fun henteOglagreGrunnlag(
        behandling: Behandling,
        grunnlagsrequest: Map.Entry<Personident, List<GrunnlagRequestDto>>,
        tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter: Boolean,
    ): Map<Grunnlagsdatatype, FeilrapporteringDto?> {
        val innhentetGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(grunnlagsrequest.value)

        val feilrapporteringer: Map<Grunnlagsdatatype, FeilrapporteringDto?> =
            innhentetGrunnlag.hentGrunnlagDto?.let { g ->
                Grunnlagsdatatype
                    .grunnlagsdatatypeobjekter(behandling.tilType())
                    .associateWith { hentFeilrapporteringForGrunnlag(it, grunnlagsrequest.key, g) }
                    .filterNot { it.value == null }
            } ?: Grunnlagsdatatype.gjeldende().associateWith { null }

        val rolleInnhentetFor = behandling.roller.find { it.ident == grunnlagsrequest.key.verdi }!!
        innhentetGrunnlag.hentGrunnlagDto?.let {
            lagreGrunnlagHvisEndret(behandling, rolleInnhentetFor, it, feilrapporteringer)
        }

        val feilVedHentingAvInntekter: FeilrapporteringDto? =
            feilrapporteringer[Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER]
        val tekniskFeilVedHentingAvInntekter = feilVedHentingAvInntekter?.feiltype == HentGrunnlagFeiltype.TEKNISK_FEIL
        innhentetGrunnlag.hentGrunnlagDto?.let {
            lagreInntektsgrunnlagHvisEndret(
                behandling = behandling,
                rolle = rolleInnhentetFor,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentetGrunnlag = SkattepliktigeInntekter(it.ainntektListe, it.skattegrunnlagListe),
                hentetTidspunkt = it.hentetTidspunkt,
                aktiveringstidspunkt = null,
                tekniskFeilsjekk = (!tekniskFeilVedHentingAvInntekter || tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter),
            )
        }

        if (tekniskFeilVedHentingAvInntekter) {
            log.warn {
                "Innhenting av ${Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER} for rolle ${rolleInnhentetFor.rolletype} " +
                    "i behandling ${behandling.id} feilet for type ${feilVedHentingAvInntekter!!.feiltype} " +
                    "med begrunnelse ${feilVedHentingAvInntekter.feilmelding}."
            }
        }

        // Oppdatere inntektstabell med sammenstilte inntekter
        innhentetGrunnlag.hentGrunnlagDto?.let {
            if (innhentetGrunnlagInneholderInntekterEllerYtelser(it)) {
                sammenstilleOgLagreInntekter(
                    behandling,
                    it,
                    rolleInnhentetFor,
                    feilrapporteringer,
                    (!tekniskFeilVedHentingAvInntekter || tekniskFeilVedForrigeInnhentingAvSkattepliktigeInntekter),
                )
            }
        }

        val innhentingAvBoforholdFeilet =
            feilrapporteringer.filter { Grunnlagsdatatype.BOFORHOLD == it.key }.isNotEmpty()

        // Husstandsmedlem og bostedsperiode
        innhentetGrunnlag.hentGrunnlagDto?.let {
            if (behandling.søknadsbarn.isNotEmpty() &&
                Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)?.ident == grunnlagsrequest.key.verdi &&
                !innhentingAvBoforholdFeilet
            ) {
                periodisereOgLagreBoforhold(
                    behandling,
                    it.husstandsmedlemmerOgEgneBarnListe.toSet(),
                )

                if (Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN.behandlingstypeMotRolletyper[behandling.tilType()]?.contains(
                        rolleInnhentetFor.rolletype,
                    ) == true
                ) {
                    periodisereOgLagreBpsBoforholdAndreVoksne(
                        behandling,
                        it.husstandsmedlemmerOgEgneBarnListe.toSet(),
                    )
                }
            }
            if (Grunnlagsdatatype.ANDRE_BARN.innhentesForRolle(behandling)?.ident == grunnlagsrequest.key.verdi) {
                lagreAndreBarnTilBMGrunnlag(behandling, it.husstandsmedlemmerOgEgneBarnListe.toSet())
            }
        }

        val innhentingAvSivilstandFeilet =
            feilrapporteringer.filter { Grunnlagsdatatype.SIVILSTAND == it.key }.isNotEmpty()

        // Oppdatere sivilstandstabell med periodisert sivilstand
        innhentetGrunnlag.hentGrunnlagDto?.let {
            if (it.sivilstandListe.isNotEmpty() && !innhentingAvSivilstandFeilet) {
                periodisereOgLagreSivilstand(behandling, it)
            }
        }

        lagreGrunnlagForUnderholdskostnad(behandling, rolleInnhentetFor, innhentetGrunnlag, feilrapporteringer)

        return feilrapporteringer
    }

    private fun lagreGrunnlagForUnderholdskostnad(
        behandling: Behandling,
        rolleInnhentetFor: Rolle,
        innhentetGrunnlag: HentetGrunnlag,
        feilrapporteringer: Map<Grunnlagsdatatype, FeilrapporteringDto?>,
    ) {
        val innhentingAvBarnetilsynFeilet =
            feilrapporteringer.filter { Grunnlagsdatatype.BARNETILSYN == it.key }.isNotEmpty()

        // Oppdatere barnetilsyn
        innhentetGrunnlag.hentGrunnlagDto?.let { grunnlag ->
            if (grunnlag.barnetilsynListe.isNotEmpty() && !innhentingAvBarnetilsynFeilet) {
                val nyesteBearbeidaBarnetilsynFørLagring =
                    sistAktiverteGrunnlag<BarnetilsynGrunnlagDto>(
                        behandling,
                        Grunnlagstype(Grunnlagsdatatype.BARNETILSYN, true),
                        rolleInnhentetFor,
                    )

                // Lagrer barnetilsyn per søknadsbarn som bearbeida grunnlag
                grunnlag.barnetilsynListe.groupBy { it.barnPersonId }.forEach { barnetilsyn ->

                    if (behandling.søknadsbarn.find { it.personident?.verdi == barnetilsyn.key } != null) {
                        lagreGrunnlagHvisEndret<BarnetilsynGrunnlagDto>(
                            behandling,
                            rolleInnhentetFor,
                            Grunnlagstype(Grunnlagsdatatype.BARNETILSYN, true),
                            barnetilsyn.value.toSet(),
                            null,
                            Personident(barnetilsyn.key),
                        )
                    }
                }

                val nyesteBearbeidaBarnetilsynEtterLagring =
                    sistAktiverteGrunnlag<BarnetilsynGrunnlagDto>(
                        behandling,
                        Grunnlagstype(Grunnlagsdatatype.BARNETILSYN, true),
                        rolleInnhentetFor,
                    )

                if (nyesteBearbeidaBarnetilsynFørLagring.isEmpty() && nyesteBearbeidaBarnetilsynEtterLagring.isNotEmpty()) {
                    grunnlag.barnetilsynListe.groupBy { it.barnPersonId }.forEach { barnetilsyn ->
                        behandling.underholdskostnader
                            .find { it.barnetsRolleIBehandlingen?.personident?.verdi == barnetilsyn.key }
                            ?.let {
                                if (it.barnetilsyn.isEmpty()) {
                                    it.barnetilsyn.addAll(barnetilsyn.value.toSet().tilBarnetilsyn(it))
                                    it.harTilsynsordning = true
                                }
                            }
                    }
                }

                behandling.aktivereBarnetilsynHvisIngenEndringerMåAksepteres()
            }
        }
    }

    private fun periodisereOgLagreSivilstand(
        behandling: Behandling,
        innhentetGrunnlag: HentGrunnlagDto,
    ) {
        val sivilstandPeriodisert =
            SivilstandApi
                .beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    innhentetGrunnlag.sivilstandListe
                        .toSet()
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

    private fun lagreAndreBarnTilBMGrunnlag(
        behandling: Behandling,
        husstandsmedlemmerOgEgneBarn: Set<RelatertPersonGrunnlagDto>,
    ) {
        val søknadsbarnidenter = behandling.søknadsbarn.map { it.ident }
        val andreBarnIkkeIBehandling =
            husstandsmedlemmerOgEgneBarn
                .filter { it.erBarn }
                .filter { erUnder13År(it.fødselsdato) }
                .filter { !søknadsbarnidenter.contains(it.gjelderPersonId) }

        andreBarnIkkeIBehandling.forEach {
            secureLogger.info { "$it er annen barn til BM. Oppretter eller oppdaterer underholdskostnad til kilde OFFENTLIG" }
            behandling.underholdskostnader.find { u -> u.person.ident == it.gjelderPersonId }?.let {
                it.kilde = Kilde.OFFENTLIG
            } ?: underholdService.oppretteUnderholdskostnad(
                behandling,
                BarnDto(personident = Personident(it.gjelderPersonId!!), fødselsdato = it.fødselsdato),
                kilde = Kilde.OFFENTLIG,
            )
        }

        val andreBarnIdenter = andreBarnIkkeIBehandling.map { it.gjelderPersonId }
        behandling.underholdskostnader
            .filter { it.barnetsRolleIBehandlingen == null }
            .filter { !andreBarnIdenter.contains(it.person.ident) }
            .forEach {
                secureLogger.info { "$it er ikke lenger barn til BM i følge offentlige opplysninger. Endrer kilde til Manuell" }
                it.kilde = Kilde.MANUELL
            }
    }

    private fun erUnder13År(fødselsdato: LocalDate?) =
        Period
            .between(
                fødselsdato,
                LocalDate
                    .now()
                    .plusYears(1)
                    .withMonth(1)
                    .withDayOfMonth(1),
            ).years < 13

    private fun periodisereOgLagreBpsBoforholdAndreVoksne(
        behandling: Behandling,
        husstandsmedlemmerOgEgneBarn: Set<RelatertPersonGrunnlagDto>,
    ) {
        val andreVoksneIHusstanden =
            BoforholdApi
                .beregnBoforholdAndreVoksne(
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
        val grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD
        val boforholdPeriodisert =
            BoforholdApi.beregnBoforholdBarnV3(
                behandling.virkningstidspunktEllerSøktFomDato,
                behandling.tilType(),
                husstandsmedlemmerOgEgneBarn.tilBoforholdBarnRequest(behandling, true),
            )

        val nyesteBearbeidaBoforholdFørLagring =
            sistAktiverteGrunnlag<BoforholdResponseV2>(
                behandling,
                Grunnlagstype(grunnlagsdatatype, true),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
            )

        // lagre bearbeidet grunnlag per husstandsmedlem i grunnlagstabellen
        boforholdPeriodisert
            .filter { it.gjelderPersonId != null }
            .groupBy { it.gjelderPersonId }
            .forEach {
                lagreGrunnlagHvisEndret<BoforholdResponseV2>(
                    behandling = behandling,
                    innhentetForRolle = grunnlagsdatatype.innhentesForRolle(behandling)!!,
                    grunnlagstype = Grunnlagstype(grunnlagsdatatype, true),
                    innhentetGrunnlag = it.value.toSet(),
                    gjelderPerson = Personident(it.key!!),
                )
            }

        val innhentetRollesNyesteBearbeidaBoforholdEtterLagring =
            sistAktiverteGrunnlag<BoforholdResponseV2>(
                behandling,
                Grunnlagstype(grunnlagsdatatype, true),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
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

        val endringerSomMåBekreftes = mapper.endringerIAndreVoksneIBpsHusstand(ikkeAktiveGrunnlag, aktiveGrunnlag)

        if (endringerSomMåBekreftes == null || endringerSomMåBekreftes.perioder.isEmpty()) {
            log.info {
                "Bps ikke aktive boforholdsgrunnlag med type " +
                    "${Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN} i behandling ${behandling.id} har " +
                    "ingen endringer som må bekreftes av saksbehandler. Automatisk aktiverer ny innhentet " +
                    "grunnlag."
            }
            ikkeAktiveGrunnlag
                .hentGrunnlagForType(
                    Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                    behandling.bidragspliktig!!.ident!!,
                ).forEach { ikkeAktivtBoforholdBp ->
                    ikkeAktivtBoforholdBp.aktiv = LocalDateTime.now()
                }
        }
    }

    fun aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling: Behandling) {
        val rolleInhentetFor = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)!!
        val ikkeAktiveGrunnlag = behandling.grunnlag.hentAlleIkkeAktiv()
        val aktiveGrunnlag = behandling.grunnlag.hentAlleAktiv()
        if (ikkeAktiveGrunnlag.isEmpty()) return
        val endringerSomMåBekreftes = ikkeAktiveGrunnlag.henteEndringerIBoforhold(aktiveGrunnlag, behandling)

        behandling.husstandsmedlem.barn
            .filter { it.kilde == Kilde.OFFENTLIG }
            .filter { hb -> endringerSomMåBekreftes.none { it.ident == hb.ident } }
            .forEach { hb ->
                val ikkeAktivGrunnlag =
                    ikkeAktiveGrunnlag
                        .hentGrunnlagForType(Grunnlagsdatatype.BOFORHOLD, rolleInhentetFor.ident!!)
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
        innhentetGrunnlag.ainntektListe.size > 0 ||
            innhentetGrunnlag.skattegrunnlagListe.size > 0 ||
            innhentetGrunnlag.barnetilleggListe.size > 0 ||
            innhentetGrunnlag.kontantstøtteListe.size > 0 ||
            innhentetGrunnlag.småbarnstilleggListe.size > 0 ||
            innhentetGrunnlag.utvidetBarnetrygdListe.size > 0

    private fun sammenstilleOgLagreInntekter(
        behandling: Behandling,
        innhentetGrunnlag: HentGrunnlagDto,
        rolleInhentetFor: Rolle,
        feilliste: Map<Grunnlagsdatatype, FeilrapporteringDto?>,
        tekniskFeilsjekk: Boolean,
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
                if (feilrapportering.feiltype != HentGrunnlagFeiltype.FUNKSJONELL_FEIL &&
                    årsbaserteInntekterEllerYtelser?.inntekter?.isEmpty() != false
                ) {
                    log.warn {
                        "Feil ved innhenting av grunnlagstype $type for rolle ${rolleInhentetFor.rolletype} " +
                            "i behandling ${behandling.id}. Lagrer ikke sammenstilte inntekter. Feilmelding: " +
                            feilrapportering.feilmelding
                    }
                    return@forEach
                }
                log.info {
                    "Ignorerer funksjonell feil ved grunnlagsinnhenting av grunnlag $type for rolle " +
                        "${rolleInhentetFor.rolletype} i behandling ${behandling.id}. Feilmelding: " +
                        feilrapportering.feilmelding
                }
            }

            @Suppress("UNCHECKED_CAST")
            if (inntekterOgYtelser.contains(type)) {
                lagreInntektsgrunnlagHvisEndret<SummerteInntekter<SummertÅrsinntekt>>(
                    rolleInhentetFor.behandling,
                    rolleInhentetFor,
                    Grunnlagstype(type, true),
                    årsbaserteInntekterEllerYtelser as SummerteInntekter<SummertÅrsinntekt>,
                    innhentetTidspunkt,
                    null,
                    tekniskFeilsjekk,
                )
            } else if (Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == type) {
                lagreInntektsgrunnlagHvisEndret<SummerteInntekter<SummertMånedsinntekt>>(
                    rolleInhentetFor.behandling,
                    rolleInhentetFor,
                    Grunnlagstype(type, true),
                    årsbaserteInntekterEllerYtelser as SummerteInntekter<SummertMånedsinntekt>,
                    innhentetTidspunkt,
                    null,
                    tekniskFeilsjekk,
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
            ikkeAktiveGrunnlag
                .hentEndringerInntekter(
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
            ikkeAktiveGrunnlag
                .hentGrunnlagForType(type, rolleInhentetFor.ident!!)
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
        behandling.grunnlag
            .hentSisteAktiv()
            .find {
                it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id && grunnlagstype.erBearbeidet == it.erBearbeidet
            }?.let { commonObjectmapper.readValue<Set<T>>(it.data) }
            ?.toSet() ?: emptySet()

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

        val skalLagres =
            innhentetGrunnlag.isNotEmpty() ||
                Grunnlagstype(
                    Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                    false,
                ) == grunnlagstype

        if (erFørstegangsinnhenting && skalLagres || erGrunnlagEndret && nyesteGrunnlag?.aktiv != null) {
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
            if (grunnlagstype.erBearbeidet && aktivert != null || Grunnlagsdatatype.ANDRE_BARN == grunnlagstype.type) {
                aktivereSisteInnhentedeRådata(grunnlagstype.type, innhentetForRolle, behandling)
            }
        } else if (erGrunnlagEndret) {
            val uaktiverteGrunnlag =
                behandling
                    .henteUaktiverteGrunnlag(grunnlagstype, innhentetForRolle)
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
                (aktivtGrunnlag as Set<BoforholdResponseV2>)
                    .toList()
                    .filtrerPerioderEtterVirkningstidspunkt(
                        behandling.husstandsmedlem,
                        behandling.virkningstidspunktEllerSøktFomDato,
                    ).toSet()
            val nyttGrunnlagFiltrert =
                (nyttGrunnlag as Set<BoforholdResponseV2>)
                    .toList()
                    .filtrerPerioderEtterVirkningstidspunkt(
                        behandling.husstandsmedlem,
                        behandling.virkningstidspunktEllerSøktFomDato,
                    ).toSet()
            aktivtGrunnlagFiltrert
                .finnEndringerBoforhold(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    nyttGrunnlagFiltrert,
                ).isNotEmpty()
        } else if (grunnlagstype.type == Grunnlagsdatatype.SIVILSTAND) {
            if (aktivtGrunnlag.isEmpty() && behandling.sivilstand.isNotEmpty()) {
                return true
            }
            try {
                val nyinnhentetGrunnlag =
                    (nyttGrunnlag as Set<Sivilstand>)
                        .toList()
                        .filtrerSivilstandBeregnetEtterVirkningstidspunktV2(behandling.virkningstidspunktEllerSøktFomDato)
                val aktiveGrunnlag =
                    (aktivtGrunnlag as Set<Sivilstand>)
                        .toList()
                        .filtrerSivilstandBeregnetEtterVirkningstidspunktV2(behandling.virkningstidspunktEllerSøktFomDato)
                !nyinnhentetGrunnlag.erDetSammeSom(aktiveGrunnlag)
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
            behandling.grunnlag
                .filter { grunnlagsdatatype == it.type && !it.erBearbeidet }
                .filter { innhentetForRolle == it.rolle }
                .maxByOrNull { it.innhentet }

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
        nyesteRådata.mapNotNull { it.gjelderPersonId }.forEach {
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

    private inline fun <reified T> lagreInntektsgrunnlagHvisEndret(
        behandling: Behandling,
        rolle: Rolle,
        grunnlagstype: Grunnlagstype,
        innhentetGrunnlag: T,
        hentetTidspunkt: LocalDateTime,
        aktiveringstidspunkt: LocalDateTime? = null,
        tekniskFeilsjekk: Boolean = false,
    ) {
        val sistInnhentedeGrunnlagAvType: T? = behandling.hentSisteInnhentaGrunnlag(grunnlagstype, rolle)
        val nyesteGrunnlag = behandling.henteNyesteGrunnlag(grunnlagstype, rolle)

        val erFørstegangsinnhentingAvInntekter =
            sistInnhentedeGrunnlagAvType == null && inneholderInntekter(innhentetGrunnlag)
        val erGrunnlagEndretSidenSistInnhentet =
            sistInnhentedeGrunnlagAvType != null && innhentetGrunnlag != sistInnhentedeGrunnlagAvType

        if (erFørstegangsinnhentingAvInntekter || erGrunnlagEndretSidenSistInnhentet && nyesteGrunnlag?.aktiv != null && tekniskFeilsjekk) {
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
            if (grunnlagstype.erBearbeidet && aktiveringstidspunkt != null) {
                aktivereSisteInnhentedeRådata(grunnlagstype.type, rolle, behandling)
            }
            // Oppdatere inntektstabell med sammenstilte offentlige inntekter
            if (nyesteGrunnlag == null &&
                inntekterOgYtelser.contains(grunnlagstype.type.getOrMigrate()) &&
                grunnlagstype.erBearbeidet
            ) {
                @Suppress("UNCHECKED_CAST")
                inntektService.lagreFørstegangsinnhentingAvSummerteÅrsinntekter(
                    behandling,
                    Personident(rolle.ident!!),
                    (innhentetGrunnlag as SummerteInntekter<SummertÅrsinntekt>).inntekter,
                )
            }
        } else if (erGrunnlagEndretSidenSistInnhentet && tekniskFeilsjekk) {
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

    private inline fun <reified T> Behandling.hentSisteInnhentetGrunnlagSet(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
        gjelderPerson: Personident?,
    ): Set<T> =
        grunnlag
            .hentSisteAktiv()
            .find {
                it.type == grunnlagstype.type &&
                    it.rolle.id == rolle.id &&
                    it.gjelder == gjelderPerson?.verdi &&
                    grunnlagstype.erBearbeidet == it.erBearbeidet
            }?.let { commonObjectmapper.readValue<Set<T>>(it.data) }
            ?.toSet() ?: emptySet()

    private inline fun <reified T> Behandling.hentSisteInnhentaGrunnlag(
        grunnlagstype: Grunnlagstype,
        rolle: Rolle,
    ): T? =
        grunnlag
            .hentSisteAktiv()
            .find {
                it.rolle.id == rolle.id &&
                    it.type == grunnlagstype.type.getOrMigrate() &&
                    it.erBearbeidet == grunnlagstype.erBearbeidet
            }?.let { commonObjectmapper.readValue<T>(it.data) }

    private fun lagreGrunnlagHvisEndret(
        behandling: Behandling,
        rolleInhentetFor: Rolle,
        innhentetGrunnlag: HentGrunnlagDto,
        feilrapporteringer: Map<Grunnlagsdatatype, FeilrapporteringDto?>,
    ) {
        val behandlingstype = behandling.tilType()
        val grunnlagsdatatypeobjekter =
            Grunnlagsdatatype.grunnlagsdatatypeobjekter(behandlingstype, rolleInhentetFor.rolletype)

        grunnlagsdatatypeobjekter
            .filter {
                !setOf(
                    Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                ).contains(it)
            }.forEach {
                val feilrapportering = feilrapporteringer[it]
                if (feilrapportering == null || HentGrunnlagFeiltype.FUNKSJONELL_FEIL == feilrapportering.feiltype) {
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
                    innhentetGrunnlag.barnetilleggListe
                        .filter {
                            harBarnRolleIBehandling(it.barnPersonId, behandling)
                        }.toSet(),
                )
            }

            Grunnlagsdatatype.BARNETILSYN -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.barnetilsynListe
                        .filter {
                            harBarnRolleIBehandling(it.barnPersonId, behandling)
                        }.toSet(),
                )
            }

            Grunnlagsdatatype.KONTANTSTØTTE -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.kontantstøtteListe
                        .filter {
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
                    innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.filter { !it.erBarn }.toSet(),
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

            Grunnlagsdatatype.TILLEGGSSTØNAD -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.tilleggsstønadBarnetilsynListe.toSet(),
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
            Grunnlagsdatatype.ANDRE_BARN -> {
                lagreGrunnlagHvisEndret(
                    behandling,
                    rolleInhentetFor,
                    Grunnlagstype(grunnlagsdatatype, false),
                    innhentetGrunnlag.husstandsmedlemmerOgEgneBarnListe.toSet(),
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
