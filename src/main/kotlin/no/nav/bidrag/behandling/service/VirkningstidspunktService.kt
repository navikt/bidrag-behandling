package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GrunnlagFraVedtak
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderBarn
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.ManuellVedtakDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBeregnTilDatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterManuellVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManuellVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakPeriodeDto
import no.nav.bidrag.transport.behandling.vedtak.response.finnStønadsendring
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Service
class VirkningstidspunktService(
    private val behandlingRepository: BehandlingRepository,
    private val boforholdService: BoforholdService,
    private val notatService: NotatService,
    private val grunnlagService: GrunnlagService,
    private val inntektService: InntektService,
    private val samværService: SamværService,
    private val underholdService: UnderholdService,
    private val gebyrService: GebyrService,
) {
    fun hentManuelleVedtakForBehandling(behandlingsid: Long): List<ManuellVedtakDto> {
        log.info { "Henter manuelle vedtak for behandling $behandlingsid" }
        secureLogger.info { "Henter manuelle vedtak for behandling $behandlingsid" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        val søknadsbarn = behandling.søknadsbarn.first()
        val grunnlag =
            behandling.grunnlag.hentSisteGrunnlagSomGjelderBarn(
                søknadsbarn.personident!!.verdi,
                Grunnlagsdatatype.MANUELLE_VEDTAK,
            )
        return grunnlag
            .konvertereData<List<ManuellVedtakGrunnlag>>()
            ?.map {
                ManuellVedtakDto(
                    valgt = søknadsbarn.beregningGrunnlagFraVedtak == it.vedtaksid,
                    vedtaksid = it.vedtaksid,
                    barnId = søknadsbarn.id!!,
                    fattetTidspunkt = it.fattetTidspunkt,
                    virkningsDato = it.virkningsDato,
                    vedtakstype = it.vedtakstype,
                    privatAvtale = it.privatAvtale,
                    begrensetRevurdering = it.begrensetRevurdering,
                    resultatSistePeriode = it.resultatSistePeriode,
                    manglerGrunnlag = it.manglerGrunnlag,
                    innkrevingstype = it.innkrevingstype,
                )
            }?.sortedByDescending { it.fattetTidspunkt } ?: emptyList()
    }

    @Transactional
    fun oppdaterBeregnManuellVedtak(
        behandlingsid: Long,
        request: OppdaterManuellVedtakRequest,
    ) {
        secureLogger.info { "Oppdaterer manuell vedtak for behandling $behandlingsid, forespørsel=$request" }

        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingsid)
                .orElseThrow { behandlingNotFoundException(behandlingsid) }

        if (request.barnId == null) {
            behandling.privatAvtale.find { it.rolle == null && it.personIdent == request.barnIdent }?.let {
                it.grunnlagFraVedtak =
                    if (request.vedtaksid != null) {
                        GrunnlagFraVedtak(
                            aldersjusteringForÅr = request.aldersjusteringForÅr,
                            vedtak = request.vedtaksid,
                            grunnlagFraOmgjøringsvedtak = request.grunnlagFraOmgjøringsvedtak ?: false,
                            perioder = hentPerioderVedtak(behandling, request),
                            vedtakstidspunkt =
                                request.vedtaksid?.let {
                                    hentVedtak(it)?.vedtakstidspunkt
                                },
                        )
                    } else {
                        null
                    }
            }
        } else {
            behandling.søknadsbarn
                .find {
                    it.id == request.barnId
                }!!
                .let {
                    it.grunnlagFraVedtak = request.vedtaksid
                    val grunnlagFraVedtakListe =
                        it.grunnlagFraVedtakListe
                            .filter { it.aldersjusteringForÅr != request.aldersjusteringForÅr }
                    it.grunnlagFraVedtakListe =
                        grunnlagFraVedtakListe +
                        listOf(
                            GrunnlagFraVedtak(
                                aldersjusteringForÅr = request.aldersjusteringForÅr,
                                vedtak = request.vedtaksid,
                                grunnlagFraOmgjøringsvedtak = request.grunnlagFraOmgjøringsvedtak ?: false,
                                perioder = if (behandling.erInnkreving) hentPerioderVedtak(behandling, request) else emptyList(),
                                vedtakstidspunkt =
                                    if (behandling.erInnkreving) {
                                        request.vedtaksid?.let {
                                            hentVedtak(it)?.vedtakstidspunkt
                                        }
                                    } else {
                                        null
                                    },
                            ),
                        )
                }
        }
    }

    private fun hentPerioderVedtak(
        behandling: Behandling,
        request: OppdaterManuellVedtakRequest,
    ): List<VedtakPeriodeDto> {
        if (request.vedtaksid == null) return emptyList()
        val stønadsid =
            if (request.barnId == null) {
                val personPrivatAvtale =
                    behandling.privatAvtale
                        .find { it.rolle == null && it.personIdent == request.barnIdent }!!
                        .person!!
                behandling.tilStønadsid(personPrivatAvtale)
            } else {
                val søknadsbarn =
                    behandling.søknadsbarn.first { it.id == request.barnId }
                behandling.tilStønadsid(søknadsbarn)
            }

        val vedtak = hentVedtak(request.vedtaksid)!!
        val stønadsendring = vedtak.finnStønadsendring(stønadsid)
        return stønadsendring!!.periodeListe
    }

    @Transactional
    fun oppdaterBeregnTilDato(
        behandlingsid: Long,
        request: OppdaterBeregnTilDatoRequestDto,
    ): Behandling =
        behandlingRepository
            .findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }
            .let { behandling ->
                oppdaterBeregnTilDato(request, behandling)
                behandling
            }

    @Transactional
    fun oppdaterOpphørsdato(
        behandlingsid: Long,
        request: OppdaterOpphørsdatoRequestDto,
    ): Behandling =
        behandlingRepository
            .findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }
            .let { behandling ->
                request.valider(behandling)
                oppdaterOpphørsdato(request, behandling)
                behandling
            }

    @Transactional
    fun oppdatereVirkningstidspunkt(
        behandlingsid: Long,
        request: OppdatereVirkningstidspunkt,
    ) = behandlingRepository
        .findBehandlingById(behandlingsid)
        .orElseThrow { behandlingNotFoundException(behandlingsid) }
        .let {
            log.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid" }
            secureLogger.info { "Oppdaterer informasjon om virkningstidspunkt for behandling $behandlingsid, forespørsel=$request" }
            request.valider(it)
            oppdaterAvslagÅrsak(it, request)
            val gjelderBarnRolle =
                request.rolleId?.let { rolleId ->
                    it.søknadsbarn.find { it.id == rolleId }
                }
            val stønadstype = gjelderBarnRolle?.stønadstype ?: it.stonadstype
            if (stønadstype == Stønadstype.BIDRAG18AAR) {
                request.oppdaterBegrunnelseVurderingAvSkolegang?.let { n ->
                    gjelderBarnRolle?.let { rolle ->
                        notatService.oppdatereNotat(
                            it,
                            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                            n.henteNyttNotat() ?: "",
                            rolle,
                        )
                    }
                }
            }

            request.henteOppdatereNotat()?.let { n ->
                notatService.oppdatereNotat(
                    it,
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                    n.henteNyttNotat() ?: "",
                    it.bidragsmottaker!!,
                )
                gjelderBarnRolle?.let { rolle ->
                    notatService.oppdatereNotat(
                        it,
                        NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                        n.henteNyttNotat() ?: "",
                        rolle,
                    )
                }
            }
            oppdaterVirkningstidspunkt(request.rolleId, request.virkningstidspunkt, it)
            it
        }

    @Transactional
    fun oppdaterAvslagÅrsak(
        behandling: Behandling,
        request: OppdatereVirkningstidspunkt,
        tvingEndring: Boolean = false,
    ) {
        fun oppdaterGebyr() {
            log.info { "Virkningstidspunkt årsak/avslag er endret. Oppdaterer gebyr detaljer ${behandling.id}" }
            gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)
        }
        val forRolle = request.rolleId?.let { behandling.roller.find { it.id == request.rolleId } }
        val forrigeÅrsak = forRolle?.årsak ?: behandling.årsak
        val forrigeAvslag = forRolle?.avslag ?: behandling.avslag
        val erAvslagÅrsakEndret = tvingEndring || request.årsak != forrigeÅrsak || request.avslag != forrigeAvslag

        if (forRolle != null && request.avslag == Resultatkode.BIDRAGSPLIKTIG_ER_DØD && behandling.erBidrag()) {
            log.info {
                "Avslag er satt til ${Resultatkode.BIDRAGSPLIKTIG_ER_DØD} for ene barnet, setter automatisk samme avslag for alle barn ${behandling.id}"
            }
            oppdaterAvslagÅrsak(behandling, request.copy(rolleId = null), true)
            oppdaterVirkningstidspunkt(
                null,
                behandling.eldsteVirkningstidspunkt,
                behandling,
                tvingEndring = true,
                rekalkulerOpplysningerVedEndring = true,
            )
            return
        }

        if (erAvslagÅrsakEndret) {
            behandling.årsak = if (request.avslag != null) null else request.årsak ?: behandling.årsak
            behandling.avslag = if (request.årsak != null) null else request.avslag ?: behandling.avslag
            if (forRolle != null) {
                forRolle.årsak = if (request.avslag != null) null else request.årsak ?: forRolle.årsak
                forRolle.avslag = if (request.årsak != null) null else request.avslag ?: forRolle.avslag
            } else {
                behandling.søknadsbarn.forEach {
                    it.årsak = if (request.avslag != null) null else request.årsak ?: it.årsak
                    it.avslag = if (request.årsak != null) null else request.avslag ?: it.avslag
                }
            }

            when (behandling.tilType()) {
                TypeBehandling.BIDRAG -> {
                    oppdaterGebyr()
                    if (behandling.avslag != null) {
                        behandling.søknadsbarn.forEach {
                            // Nå er virkningstidspunkt/avslag/årsak ikke knyttet mot rolle. I V3 av bidrag skal det knyttes mot hver søknadsbarn
                            // Da må dette bare endre for søknadsbarn det endres for
                            oppdaterOpphørsdato(
                                OppdaterOpphørsdatoRequestDto(it.id!!, null),
                                behandling,
                            )
                        }
                    }
                }

                else -> {}
            }
        }
    }

    @Transactional
    fun oppdaterVirkningstidspunkt(
        rolleId: Long?,
        nyVirkningstidspunkt: LocalDate?,
        behandling: Behandling,
        tvingEndring: Boolean = false,
        rekalkulerOpplysningerVedEndring: Boolean = true,
    ) {
        val gjelderBarn = behandling.søknadsbarn.find { it.id == rolleId }
        val forrigeVirkningstidspunkt = gjelderBarn?.virkningstidspunkt ?: behandling.eldsteVirkningstidspunkt
        val erVirkningstidspunktEndret = tvingEndring || nyVirkningstidspunkt != forrigeVirkningstidspunkt

        fun oppdatereUnderhold() {
            log.info { "Tilpasse perioder for underhold til ny virkningsdato i behandling ${behandling.id}" }
            underholdService.tilpasseUnderholdEtterVirkningsdato(behandling, forrigeVirkningstidspunkt = forrigeVirkningstidspunkt)
        }

        fun oppdaterBoforhold() {
            log.info { "Virkningstidspunkt er endret. Beregner husstandsmedlemsperioder på ny for behandling ${behandling.id}" }
            grunnlagService.oppdaterAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterAktiveBoforholdBMEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdBMEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling)
            grunnlagService.aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling)
            grunnlagService.aktiverGrunnlagForBoforholdTilBMSøknadsbarnHvisIngenEndringerMåAksepteres(behandling)
        }

        fun oppdaterSivilstand() {
            log.info { "Virkningstidspunkt er endret. Bygger sivilstandshistorikk på ny for behandling ${behandling.id}" }
            grunnlagService.oppdatereAktivSivilstandEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdatereIkkeAktivSivilstandEtterEndretVirkningsdato(behandling)
            boforholdService.oppdatereSivilstandshistorikk(behandling)
            grunnlagService.aktivereSivilstandHvisEndringIkkeKreverGodkjenning(behandling)
        }

        fun oppdaterSamvær() {
            log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på samvær for behandling ${behandling.id}" }
            samværService.rekalkulerPerioderSamvær(behandling, forrigeVirkningstidspunkt = forrigeVirkningstidspunkt)
        }

        fun oppdaterInntekter() {
            log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling, forrigeVirkningstidspunkt = forrigeVirkningstidspunkt)
        }

        fun oppdaterAndreVoksneIHusstanden() {
            log.info { "Virkningstidspunkt er endret. Beregner andre voksne i husstanden perioder på nytt for behandling ${behandling.id}" }
            grunnlagService.oppdatereAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdatereIkkeAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandling)
            grunnlagService.aktivereGrunnlagForBoforholdAndreVoksneIHusstandenHvisIngenEndringerMåAksepteres(behandling)
        }

        if (erVirkningstidspunktEndret) {
            if (gjelderBarn != null) {
                gjelderBarn.virkningstidspunkt = nyVirkningstidspunkt ?: gjelderBarn.virkningstidspunkt
            } else {
                behandling.søknadsbarn.forEach {
                    it.virkningstidspunkt = nyVirkningstidspunkt ?: it.virkningstidspunkt
                }
            }

            behandling.virkningstidspunkt = behandling.eldsteVirkningstidspunkt

            if (rekalkulerOpplysningerVedEndring) {
                when (behandling.tilType()) {
                    TypeBehandling.FORSKUDD -> {
                        oppdaterBoforhold()
                        oppdaterSivilstand()
                        oppdaterInntekter()
                    }

                    TypeBehandling.SÆRBIDRAG -> {
                        oppdaterBoforhold()
                        oppdaterAndreVoksneIHusstanden()
                        oppdaterInntekter()
                    }

                    TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR -> {
                        oppdaterBoforhold()
                        oppdaterAndreVoksneIHusstanden()
                        oppdaterInntekter()
                        oppdatereUnderhold()
                        oppdaterSamvær()
                    }
                }
            }
        }
    }

    @Transactional
    fun oppdaterBeregnTilDato(
        request: OppdaterBeregnTilDatoRequestDto,
        behandling: Behandling,
        tvingEndring: Boolean = false,
        rekalkulerOpplysningerVedEndring: Boolean = true,
    ) {
        val requestBeregnTil = request.beregnTil
        val rolle = behandling.roller.find { it.id == request.idRolle }
        val nåværendeBeregnTil = rolle?.beregnTil
        val erBeregnTilDatoEndret = tvingEndring || requestBeregnTil != nåværendeBeregnTil
        val forrigeBeregnTilDato = behandling.finnBeregnTilDatoBehandling(rolle)

        val erBeregnTilEndretTilInneværende =
            (rolle == null || rolle.beregnTil != BeregnTil.INNEVÆRENDE_MÅNED) && request.beregnTil == BeregnTil.INNEVÆRENDE_MÅNED

        fun oppdatereUnderhold() {
            log.info { "Tilpasse perioder for underhold til ny opphørsdato i behandling ${behandling.id}" }
            underholdService.oppdatereUnderholdsperioderEtterEndretOpphørsdato(
                behandling,
                erBeregnTilEndretTilInneværende,
                forrigeBeregnTilDato,
            )
        }

        fun oppdaterBoforhold() {
            log.info { "Opphørsdato er endret. Beregner husstandsmedlemsperioder på ny for behandling ${behandling.id}" }
            grunnlagService.oppdaterAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterAktiveBoforholdBMEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdBMEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling)
            grunnlagService.aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling)
            grunnlagService.aktiverGrunnlagForBoforholdTilBMSøknadsbarnHvisIngenEndringerMåAksepteres(behandling)
        }

        fun oppdaterSamvær() {
            log.info { "Opphørsdato er endret. Oppdaterer perioder på samvær for behandling ${behandling.id}" }
            samværService.rekalkulerPerioderSamvær(behandling, erBeregnTilEndretTilInneværende)
        }

        fun oppdaterInntekter() {
            log.info { "Opphørsdato er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling.id!!, erBeregnTilEndretTilInneværende, forrigeBeregnTilDato)
        }

        fun oppdaterAndreVoksneIHusstanden() {
            log.info { "Opphørsdato er endret. Beregner andre voksne i husstanden perioder på nytt for behandling ${behandling.id}" }
            boforholdService.rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandling)
        }

        if (erBeregnTilDatoEndret) {
            if (rolle != null) {
                rolle.beregnTil = request.beregnTil
            } else {
                behandling.søknadsbarn.forEach {
                    it.beregnTil = request.beregnTil
                }
            }
            if (rekalkulerOpplysningerVedEndring) {
                oppdaterBoforhold()
                oppdaterAndreVoksneIHusstanden()
                oppdaterInntekter()
                oppdatereUnderhold()
                oppdaterSamvær()
            }
        }
    }

    @Transactional
    fun brukSammeVirkningstidspunktForAlleBarn(behandlingId: Long) {
        val behandling =
            behandlingRepository
                .findBehandlingById(behandlingId)
                .orElseThrow { behandlingNotFoundException(behandlingId) }
        val barnMedEldstVirkningstidspunkt = behandling.søknadsbarn.minBy { it.virkningstidspunktRolle }

        oppdaterOpphørsdato(
            OppdaterOpphørsdatoRequestDto(null, behandling.globalOpphørsdato),
            behandling,
            tvingEndring = true,
            rekalkulerOpplysningerVedEndring = false,
        )
        oppdaterVirkningstidspunkt(
            null,
            barnMedEldstVirkningstidspunkt.virkningstidspunkt,
            behandling,
            tvingEndring = true,
            rekalkulerOpplysningerVedEndring = false,
        )
        oppdaterAvslagÅrsak(
            behandling,
            OppdatereVirkningstidspunkt(årsak = barnMedEldstVirkningstidspunkt.årsak, avslag = barnMedEldstVirkningstidspunkt.avslag),
            tvingEndring = true,
        )

        oppdaterBeregnTilDato(
            OppdaterBeregnTilDatoRequestDto(null, barnMedEldstVirkningstidspunkt.beregnTil),
            behandling,
            tvingEndring = true,
            rekalkulerOpplysningerVedEndring = true,
        )
        var nyNotat = barnMedEldstVirkningstidspunkt.notat.find { it.type == NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT }?.innhold ?: ""
        behandling.søknadsbarn.forEach {
            if (it.id != barnMedEldstVirkningstidspunkt.id) {
                val begrunnelse = it.notat.find { it.type == NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT }?.innhold ?: ""
                nyNotat +=
                    begrunnelse.replace(nyNotat, "").let {
                        if (it.isNotEmpty()) {
                            "<br> $it"
                        } else {
                            ""
                        }
                    }
            }
        }
        behandling.søknadsbarn.forEach {
            notatService.oppdatereNotat(behandling, NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, nyNotat, it)
        }
    }

    @Transactional
    fun oppdaterOpphørsdato(
        request: OppdaterOpphørsdatoRequestDto,
        behandling: Behandling,
        tvingEndring: Boolean = false,
        rekalkulerOpplysningerVedEndring: Boolean = true,
    ) {
        val requestOpphørsmåned = request.opphørsdato?.withDayOfMonth(1)
        val rolle = behandling.roller.find { it.id == request.idRolle }
        val forrigeOpphørsdato = rolle?.opphørsdato ?: behandling.globalOpphørsdato
        val erOpphørsdatoEndret = tvingEndring || requestOpphørsmåned != forrigeOpphørsdato || request.simulerEndring
        val erOpphørSlettet = requestOpphørsmåned == null && forrigeOpphørsdato != null

        fun oppdatereUnderhold() {
            log.info { "Tilpasse perioder for underhold til ny opphørsdato i behandling ${behandling.id}" }
            underholdService.oppdatereUnderholdsperioderEtterEndretOpphørsdato(behandling, erOpphørSlettet, forrigeOpphørsdato)
        }

        fun oppdaterBoforhold() {
            log.info { "Opphørsdato er endret. Beregner husstandsmedlemsperioder på ny for behandling ${behandling.id}" }
            grunnlagService.oppdaterAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterAktiveBoforholdBMEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdBMEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling)
            grunnlagService.aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling)
            grunnlagService.aktiverGrunnlagForBoforholdTilBMSøknadsbarnHvisIngenEndringerMåAksepteres(behandling)
        }

        fun oppdaterSamvær() {
            log.info { "Opphørsdato er endret. Oppdaterer perioder på samvær for behandling ${behandling.id}" }
            samværService.rekalkulerPerioderSamvær(behandling, erOpphørSlettet)
        }

        fun oppdaterInntekter() {
            log.info { "Opphørsdato er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling, erOpphørSlettet, forrigeOpphørsdato)
        }

        fun oppdaterAndreVoksneIHusstanden() {
            log.info { "Opphørsdato er endret. Beregner andre voksne i husstanden perioder på nytt for behandling ${behandling.id}" }
            grunnlagService.oppdatereAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdatereIkkeAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandling)
            grunnlagService.aktivereGrunnlagForBoforholdAndreVoksneIHusstandenHvisIngenEndringerMåAksepteres(behandling)
        }

        if (erOpphørsdatoEndret) {
            if (rolle != null) {
                rolle.opphørsdato = requestOpphørsmåned
            } else {
                behandling.søknadsbarn.forEach {
                    it.opphørsdato = requestOpphørsmåned
                }
            }
            if (rekalkulerOpplysningerVedEndring) {
                oppdaterBoforhold()
                oppdaterAndreVoksneIHusstanden()
                oppdaterInntekter()
                oppdatereUnderhold()
                oppdaterSamvær()
            }
        }
    }

    @Transactional
    fun Behandling.oppdatereVirkningstidspunktSærbidrag() {
        if (tilType() != TypeBehandling.SÆRBIDRAG) return
        val nyVirkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        // Virkningstidspunkt skal alltid være lik det som var i opprinnelig vedtaket.
        // Oppdaterer derfor ikke virkningstidspunkt hvis behandlingen er klage eller omgjøring
        if (virkningstidspunkt != nyVirkningstidspunkt && !erKlageEllerOmgjøring) {
            log.info {
                "Virkningstidspunkt $virkningstidspunkt på særbidrag er ikke riktig som følge av ny kalendermåned." +
                    " Endrer virkningstidspunkt til starten av nåværende kalendermåned $nyVirkningstidspunkt"
            }
            oppdaterVirkningstidspunkt(null, nyVirkningstidspunkt, this)
        }
    }
}
