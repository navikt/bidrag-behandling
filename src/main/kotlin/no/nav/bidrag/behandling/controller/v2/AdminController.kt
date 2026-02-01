package no.nav.bidrag.behandling.controller.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningService
import no.nav.bidrag.behandling.service.BoforholdService.Companion.tilbakestilleTilOffentligSivilstandshistorikkBasertPåGrunnlag
import no.nav.bidrag.behandling.service.ForholdsmessigFordelingService
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.service.InntektService
import no.nav.bidrag.behandling.service.PrivatAvtaleService
import no.nav.bidrag.behandling.service.VedtakService
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorRequestV2
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}

@BehandlingRestControllerV2
class AdminController(
    private val sakConsumer: BidragSakConsumer,
    private val behandlingRepository: BehandlingRepository,
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
    private val privatAvtaleService: PrivatAvtaleService,
    private val forholsmessigFordelingService: ForholdsmessigFordelingService,
    private val beregningService: BeregningService,
    private val vedtakService: VedtakService,
) {
    @GetMapping("/admin/beregning/input/{behandlingId}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    @Transactional
    fun opprettInputBeregning(
        @PathVariable behandlingId: Long,
    ): BidragsberegningOrkestratorRequestV2 {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        return beregningService.opprettGrunnlagBeregningBidragV2(behandling, true, false)
    }

    @GetMapping("/admin/vedtak/input/{behandlingId}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    @Transactional
    fun opprettFatteVedtakRequest(
        @PathVariable behandlingId: Long,
    ): List<OpprettVedtakRequestDto> = vedtakService.fatteVedtak(behandlingId, null, true).requests.map { it.second }

    @PostMapping("/admin/reset/fattevedtak/{behandlingId}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    @Transactional
    fun resetFattetVedtak(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).getOrNull() ?: return

        behandling.vedtakFattetAv = null
        behandling.vedtakDetaljer = null
        behandling.vedtaksid = null
        behandling.vedtakstidspunkt = null
        behandling.opprettetTidspunkt = LocalDateTime.now().minusSeconds(900)
        behandlingRepository.save(behandling)
    }

    @PostMapping("/admin/avslutt/ff/{behandlingId}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    @Transactional
    fun avsluttFFSøknad(
        @PathVariable behandlingId: Long,
    ) {
        forholsmessigFordelingService.lukkAllFFSaker(behandlingId)
    }

    @PostMapping("/admin/grunnlag/ignorer/{behandlingId}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    @Transactional
    fun ignorerHentGrunnlag(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).getOrNull() ?: return

        behandling.grunnlagSistInnhentet = LocalDateTime.now().plusDays(1000)
        behandling.grunnlagsinnhentingFeilet = null
        behandlingRepository.save(behandling)
    }

    @PostMapping("/admin/grunnlag/reset/{behandlingId}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
    )
    @Transactional
    fun resetHentGrunnlag(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).getOrNull() ?: return

        behandling.grunnlagSistInnhentet = null
        behandling.grunnlagsinnhentingFeilet = null
        behandlingRepository.save(behandling)
    }

    @PostMapping("/admin/opprett/aldersjustering/{saksnummer}")
    @Operation(
        description =
            "Opprett aldersjustering behandling for sak",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @Transactional
    fun opprettAldersjustering(
        @PathVariable saksnummer: String,
        @RequestBody barnIdent: String? = null,
    ): OpprettBehandlingResponse? {
        val sak = sakConsumer.hentSak(saksnummer)
        val søknadsbarn =
            sak.roller
                .filter { it.type == Rolletype.BARN }
                .firstOrNull {
                    barnIdent == null &&
                        listOf(6, 11, 15).contains(getAge(hentPersonFødselsdato(it.fødselsnummer!!.verdi)!!)) ||
                        barnIdent == it.fødselsnummer!!.verdi
                }
                ?: return null
        val request =
            OpprettBehandlingRequest(
                vedtakstype = Vedtakstype.ALDERSJUSTERING,
                søknadFra = SøktAvType.NAV_BIDRAG,
                stønadstype = Stønadstype.BIDRAG,
                søktFomDato = LocalDate.parse("2025-07-01"),
                mottattdato = LocalDate.parse("2025-07-01"),
                søknadsid =
                    BigDecimal
                        .valueOf(Math.random())
                        .multiply(BigDecimal.valueOf(1000))
                        .setScale(0, RoundingMode.HALF_DOWN)
                        .toLong(),
                behandlerenhet = sak.eierfogd.verdi,
                søknadstype = Behandlingstype.ALDERSJUSTERING,
                saksnummer = saksnummer,
                roller =
                    setOf(
                        OpprettRolleDto(
                            rolletype = Rolletype.BIDRAGSPLIKTIG,
                            fødselsdato = null,
                            ident =
                                sak.roller
                                    .find { it.type == Rolletype.BIDRAGSPLIKTIG }!!
                                    .fødselsnummer,
                        ),
                        OpprettRolleDto(
                            rolletype = Rolletype.BIDRAGSMOTTAKER,
                            fødselsdato = null,
                            ident =
                                sak.roller
                                    .find { it.type == Rolletype.BIDRAGSMOTTAKER }!!
                                    .fødselsnummer,
                        ),
                        OpprettRolleDto(
                            rolletype = Rolletype.BARN,
                            fødselsdato = null,
                            ident = søknadsbarn.fødselsnummer,
                        ),
                    ),
            )
        return behandlingService.opprettBehandling(request)
    }

    @PostMapping("/admin/feilfiks/referanser/privatavtale")
    @Operation(
        description =
            "Fikse feil i referanser ",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @Transactional
    fun feilfiksReferanserPrivatAvtale() {
        privatAvtaleService.fiksReferanserPrivatAvtale()
    }

    @PostMapping("/admin/feilfiks/aktivering/grunnlag/{behandlingId}")
    @Operation(
        description =
            "Fikse feil i referanser ",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @Transactional
    fun feilfiksAktiveringGrunnlag(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        behandling.grunnlag.forEach {
            it.aktiv = it.innhentet
        }
    }

    @PostMapping("/admin/grunnlag/oppdater/boforhold/{behandlingId}")
    @Operation(
        description =
            "Oppdater husstandsmedlemmer etter nyeste grunnlagsdata",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @Transactional
    fun revaliderGrunnlag(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).getOrNull() ?: return

        grunnlagService.reperiodiserOgLagreBoforhold(behandling)
    }

    @PostMapping("/admin/feilfiks/sivilstand/perioder/{behandlingId}")
    @Operation(
        description =
            "Fiks feil i perioder hvor fom starter før til",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @Transactional
    fun fiksPerioderFomSomKommerFørTil(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).getOrNull() ?: return

        behandling.sivilstand.forEach { sivilstand ->
            val ugyldigPeriode = sivilstand.datoTom != null && sivilstand.datoFom.isAfter(sivilstand.datoTom)
            if (ugyldigPeriode) {
                val annenPeriodeMedSammeFom = behandling.sivilstand.any { it.id != sivilstand.id && it.datoFom == sivilstand.datoFom }
                if (annenPeriodeMedSammeFom) {
                    log.info {
                        "Sletter sivilstand med id=${sivilstand.id} da det finnes en annen periode med samme fom=${sivilstand.datoFom} for behandlingId=$behandlingId"
                    }
                    behandling.sivilstand.remove(sivilstand)
                } else {
                    log.info {
                        "Retter sivilstand med id=${sivilstand.id} " +
                            "ved å sette datoTom=null (var ${sivilstand.datoTom}) for behandlingId=$behandlingId"
                    }
                    sivilstand.datoTom = null
                }
            }
        }
        behandling.tilbakestilleTilOffentligSivilstandshistorikkBasertPåGrunnlag()
        grunnlagService.oppdatereAktivSivilstandEtterEndretVirkningstidspunkt(behandling)
    }

    @PostMapping("/admin/feilfiks/underhold/rolle/{behandlingId}")
    @Operation(
        description =
            "Fiks manglende virkningstidspunkt/årsak/avslag i rolle tabellen for barn",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @Transactional
    fun fiksUnderholdskostnadSomPekerTilFeilRolle(
        @PathVariable behandlingId: Long,
    ) {
        log.info { "Setter rolle til behandling $behandlingId til null hvis det ikke tilhører samme behandling" }
        behandlingRepository.settUnderholdskostnadRolleTilNull(behandlingId)
    }

    fun getAge(birthDate: LocalDate): Int = Period.between(birthDate.withMonth(1).withDayOfMonth(1), LocalDate.now()).years
}
