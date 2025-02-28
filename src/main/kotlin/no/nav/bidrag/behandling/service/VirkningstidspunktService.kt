package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
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
            request.henteOppdatereNotat()?.let { n ->
                notatService.oppdatereNotat(
                    it,
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                    n.henteNyttNotat() ?: "",
                    it.bidragsmottaker!!,
                )
            }
            oppdaterVirkningstidspunkt(request.virkningstidspunkt, it)
            it
        }

    @Transactional
    fun oppdaterAvslagÅrsak(
        behandling: Behandling,
        request: OppdatereVirkningstidspunkt,
    ) {
        fun oppdaterGebyr() {
            log.info { "Virkningstidspunkt årsak/avslag er endret. Oppdaterer gebyr detaljer ${behandling.id}" }
            gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)
        }
        val erAvslagÅrsakEndret = request.årsak != behandling.årsak || request.avslag != behandling.avslag

        if (erAvslagÅrsakEndret) {
            behandling.årsak = if (request.avslag != null) null else request.årsak ?: behandling.årsak
            behandling.avslag = if (request.årsak != null) null else request.avslag ?: behandling.avslag

            when (behandling.tilType()) {
                TypeBehandling.BIDRAG -> {
                    oppdaterGebyr()
                    if (behandling.stonadstype == Stønadstype.BIDRAG18AAR && behandling.avslag != null) {
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
        nyVirkningstidspunkt: LocalDate?,
        behandling: Behandling,
    ) {
        val forrigeVirkningstidspunkt = behandling.virkningstidspunkt
        val erVirkningstidspunktEndret = nyVirkningstidspunkt != behandling.virkningstidspunkt

        fun oppdatereUnderhold() {
            log.info { "Tilpasse perioder for underhold til ny virkningsdato i behandling ${behandling.id}" }
            underholdService.tilpasseUnderholdEtterVirkningsdato(behandling, forrigeVirkningstidspunkt = forrigeVirkningstidspunkt!!)
        }

        fun oppdaterBoforhold() {
            log.info { "Virkningstidspunkt er endret. Beregner husstandsmedlemsperioder på ny for behandling ${behandling.id}" }
            grunnlagService.oppdaterAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdaterIkkeAktiveBoforholdEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling.id!!)
            grunnlagService.aktiverGrunnlagForBoforholdHvisIngenEndringerMåAksepteres(behandling)
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
            samværService.rekalkulerPerioderSamvær(behandling.id!!, forrigeVirkningstidspunkt = forrigeVirkningstidspunkt)
        }

        fun oppdaterInntekter() {
            log.info { "Virkningstidspunkt er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling.id!!, forrigeVirkningstidspunkt = forrigeVirkningstidspunkt)
        }

        fun oppdaterAndreVoksneIHusstanden() {
            log.info { "Virkningstidspunkt er endret. Beregner andre voksne i husstanden perioder på nytt for behandling ${behandling.id}" }
            grunnlagService.oppdatereAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            grunnlagService.oppdatereIkkeAktiveBoforholdAndreVoksneIHusstandenEtterEndretVirkningstidspunkt(behandling)
            boforholdService.rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandling.id!!)
            grunnlagService.aktivereGrunnlagForBoforholdAndreVoksneIHusstandenHvisIngenEndringerMåAksepteres(behandling)
        }

        if (erVirkningstidspunktEndret) {
            behandling.virkningstidspunkt = nyVirkningstidspunkt ?: behandling.virkningstidspunkt

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

    @Transactional
    fun oppdaterOpphørsdato(
        request: OppdaterOpphørsdatoRequestDto,
        behandling: Behandling,
    ) {
        val requestOpphørsmåned = request.opphørsdato?.withDayOfMonth(1)
        val rolle = behandling.roller.find { it.id == request.idRolle }!!
        val erOpphørsdatoEndret = requestOpphørsmåned != rolle.opphørsdato
        val forrigeOpphørsdato = rolle.opphørsdato
        val erOpphørSlettet = requestOpphørsmåned == null && rolle.opphørsdato != null

        fun oppdatereUnderhold() {
            log.info { "Tilpasse perioder for underhold til ny opphørsdato i behandling ${behandling.id}" }
            underholdService.oppdatereUnderholdsperioderEtterEndretOpphørsdato(behandling, erOpphørSlettet, forrigeOpphørsdato)
        }

        fun oppdaterBoforhold() {
            log.info { "Opphørsdato er endret. Beregner husstandsmedlemsperioder på ny for behandling ${behandling.id}" }
            boforholdService.rekalkulerOgLagreHusstandsmedlemPerioder(behandling.id!!)
        }

        fun oppdaterSamvær() {
            log.info { "Opphørsdato er endret. Oppdaterer perioder på samvær for behandling ${behandling.id}" }
            samværService.rekalkulerPerioderSamvær(behandling.id!!, erOpphørSlettet)
        }

        fun oppdaterInntekter() {
            log.info { "Opphørsdato er endret. Oppdaterer perioder på inntekter for behandling ${behandling.id}" }
            inntektService.rekalkulerPerioderInntekter(behandling.id!!, erOpphørSlettet, forrigeOpphørsdato)
        }

        fun oppdaterAndreVoksneIHusstanden() {
            log.info { "Opphørsdato er endret. Beregner andre voksne i husstanden perioder på nytt for behandling ${behandling.id}" }
            boforholdService.rekalkulerOgLagreAndreVoksneIHusstandPerioder(behandling.id!!)
        }

        if (erOpphørsdatoEndret) {
            rolle.opphørsdato = requestOpphørsmåned
            oppdaterBoforhold()
            oppdaterAndreVoksneIHusstanden()
            oppdaterInntekter()
            oppdatereUnderhold()
            oppdaterSamvær()
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
            oppdaterVirkningstidspunkt(nyVirkningstidspunkt, this)
        }
    }
}
