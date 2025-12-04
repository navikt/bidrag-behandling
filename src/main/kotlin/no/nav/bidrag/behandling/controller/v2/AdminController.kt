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
import no.nav.bidrag.behandling.service.ForholdsmessigFordelingService
import no.nav.bidrag.behandling.service.InntektService
import no.nav.bidrag.behandling.service.PrivatAvtaleService
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.springframework.transaction.annotation.Transactional
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
    private val inntektService: InntektService,
    private val privatAvtaleService: PrivatAvtaleService,
    private val forholsmessigFordelingService: ForholdsmessigFordelingService,
) {
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forespørsel oppdatert uten feil",
            ),
        ],
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

    @PostMapping("/admin/feilfiks/referanser")
    @Operation(
        description =
            "Fikse feil i referanser ",
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
    fun feilfiksReferanser() {
        privatAvtaleService.fiksReferanserPrivatAvtale()
    }

    @PostMapping("/admin/grunnlag/inntekt/oppdater/{behandlingId}")
    @Operation(
        description =
            "Oppdater offentlige ytelser basert på nyeste grunnlag ",
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
    fun oppdaterOffentligeInntekter(
        @PathVariable behandlingId: Long,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).getOrNull() ?: return

        inntektService.justerOffentligePerioderEtterSisteGrunnlag(behandling)
    }

    fun getAge(birthDate: LocalDate): Int = Period.between(birthDate.withMonth(1).withDayOfMonth(1), LocalDate.now()).years
}
