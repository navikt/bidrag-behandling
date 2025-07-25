package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Ordering
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragForsendelseConsumer
import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.dto.v1.forsendelse.BehandlingStatus
import no.nav.bidrag.behandling.dto.v1.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID
import no.nav.bidrag.behandling.utils.testdata.opprettForsendelseResponsUnderOpprettelse
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.commons.service.forsendelse.FellesForsendelseMapper
import no.nav.bidrag.commons.service.forsendelse.FellesForsendelseService
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.dokument.BidragEnhet.ENHET_FARSKAP
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto
import no.nav.bidrag.transport.dokument.forsendelse.JournalTema
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseRespons
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class ForsendelseServiceTest {
    @MockkBean
    lateinit var bidragForsendelseConsumer: BidragForsendelseConsumer

    @MockkBean
    lateinit var bidragTIlgangskontrollConsumer: BidragTilgangskontrollConsumer

    @MockkBean
    lateinit var fellesForsendelseService: FellesForsendelseService

    @MockkBean
    lateinit var fellesForsendelseMapper: FellesForsendelseMapper
    lateinit var forsendelseService: ForsendelseService

    @BeforeEach
    fun initMocks() {
        forsendelseService =
            ForsendelseService(bidragForsendelseConsumer, bidragTIlgangskontrollConsumer, fellesForsendelseService, fellesForsendelseMapper)
        every { bidragForsendelseConsumer.opprettForsendelse(any()) } returns
            OpprettForsendelseRespons(123)
        every { bidragForsendelseConsumer.slettForsendelse(any()) } returns Unit
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
    }

    @Test
    fun `Skal opprette forsendelse for behandling med tema FAR når enhet er farskap`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = ENHET_FARSKAP,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID.toString(),
                        stonadType = Stønadstype.FORSKUDD,
                        vedtakType = Vedtakstype.KLAGE,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.tema shouldBe JournalTema.FAR
                    it.behandlingInfo!!.barnIBehandling shouldHaveSize 1
                    it.behandlingInfo!!.barnIBehandling shouldContain testdataBarn1.ident
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for gebyr behandling for bidragsmottaker`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = ENHET_FARSKAP,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        engangsBelopType = Engangsbeløptype.GEBYR_MOTTAKER,
                        vedtakType = Vedtakstype.ENDRING,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBM.ident
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for gebyr behandling for bidragspliktig`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = ENHET_FARSKAP,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        engangsBelopType = Engangsbeløptype.GEBYR_SKYLDNER,
                        vedtakType = Vedtakstype.ENDRING,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBP.ident
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med tema BID når enhet er farskap men person ikke har tilgang`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns false
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = ENHET_FARSKAP,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.BIDRAG,
                        vedtakType = Vedtakstype.FASTSETTELSE,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 2) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.tema shouldBe JournalTema.BID
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type FORSKUDD og vedtakType KLAGE`() {
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = "4806",
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.FORSKUDD,
                        vedtakType = Vedtakstype.KLAGE,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe JournalTema.BID
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe Stønadstype.FORSKUDD

                    it.gjelderIdent shouldBe testdataBM.ident
                    it.mottaker?.ident shouldBe testdataBM.ident
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type BIDRAG`() {
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = "4806",
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.BIDRAG,
                        vedtakType = Vedtakstype.FASTSETTELSE,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 2) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe JournalTema.BID
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe Stønadstype.BIDRAG
                },
            )
        }

        verify(ordering = Ordering.SEQUENCE) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBM.ident
                    it.mottaker?.ident shouldBe testdataBM.ident
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBP.ident
                    it.mottaker?.ident shouldBe testdataBP.ident
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type BIDRAG 18 år`() {
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = "4806",
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.BIDRAG18AAR,
                        vedtakType = Vedtakstype.FASTSETTELSE,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)

        verify(exactly = 3) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe JournalTema.BID
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe Stønadstype.BIDRAG18AAR
                },
            )
        }

        verify(ordering = Ordering.SEQUENCE) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBM.ident
                    it.mottaker?.ident shouldBe testdataBM.ident
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBP.ident
                    it.mottaker?.ident shouldBe testdataBP.ident
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBarn1.ident
                    it.mottaker?.ident shouldBe testdataBarn1.ident
                },
            )
        }
    }

    @Test
    fun `Skal ikke opprette forsendelse for behandling med type forskudd fastsettelse hvis vedtak ikke er fattet`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true

        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = ENHET_FARSKAP,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.FORSKUDD,
                        vedtakType = Vedtakstype.FASTSETTELSE,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)

        verify(exactly = 0) {
            bidragForsendelseConsumer.opprettForsendelse(any())
        }
    }

    @Test
    fun `Skal ikke opprette forsendelse for behandling med type forskudd endring hvis vedtak ikke er fattet`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true

        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = ENHET_FARSKAP,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.FORSKUDD,
                        vedtakType = Vedtakstype.ENDRING,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)

        verify(exactly = 0) {
            bidragForsendelseConsumer.opprettForsendelse(any())
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type BIDRAG som er fattet og slette forsendelser for varsel under opprettelse`() {
        every { bidragForsendelseConsumer.hentForsendelserISak(any()) } returns
            listOf(
                opprettForsendelseResponsUnderOpprettelse(1),
                opprettForsendelseResponsUnderOpprettelse(2),
                opprettForsendelseResponsUnderOpprettelse(3).copy(status = ForsendelseStatusTo.DISTRIBUERT),
                opprettForsendelseResponsUnderOpprettelse(4).copy(forsendelseType = ForsendelseTypeTo.NOTAT),
            )
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = "4806",
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.BIDRAG,
                        vedtakType = Vedtakstype.FASTSETTELSE,
                        erFattetBeregnet = true,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)

        verify(exactly = 2) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe JournalTema.BID
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe Stønadstype.BIDRAG
                },
            )
        }

        verify {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBM.ident
                    it.mottaker?.ident shouldBe testdataBM.ident
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe testdataBP.ident
                    it.mottaker?.ident shouldBe testdataBP.ident
                },
            )
        }

        verify {
            bidragForsendelseConsumer.hentForsendelserISak(eq(SAKSNUMMER))
        }

        verify {
            bidragForsendelseConsumer.slettForsendelse(eq(1))
            bidragForsendelseConsumer.slettForsendelse(eq(2))
        }
    }

    @Test
    fun `Skal slette forsendelser for varsel under opprettelse hvis behandlingstatus er feilregistrert`() {
        every { bidragForsendelseConsumer.hentForsendelserISak(any()) } returns
            listOf(
                opprettForsendelseResponsUnderOpprettelse(1),
                opprettForsendelseResponsUnderOpprettelse(2),
                opprettForsendelseResponsUnderOpprettelse(3).copy(status = ForsendelseStatusTo.DISTRIBUERT),
                opprettForsendelseResponsUnderOpprettelse(4).copy(forsendelseType = ForsendelseTypeTo.NOTAT),
            )
        val request =
            InitalizeForsendelseRequest(
                saksnummer = SAKSNUMMER,
                enhet = "4806",
                behandlingStatus = BehandlingStatus.FEILREGISTRERT,
                behandlingInfo =
                    BehandlingInfoDto(
                        soknadId = SOKNAD_ID,
                        stonadType = Stønadstype.BIDRAG,
                        vedtakType = Vedtakstype.FASTSETTELSE,
                        erFattetBeregnet = true,
                    ),
                roller =
                    listOf(
                        testdataBM.tilForsendelseRolleDto(),
                        testdataBP.tilForsendelseRolleDto(),
                        testdataBarn1.tilForsendelseRolleDto(),
                    ),
            )
        forsendelseService.slettEllerOpprettForsendelse(request)
        verify(exactly = 0) {
            bidragForsendelseConsumer.opprettForsendelse(any())
        }

        verify {
            bidragForsendelseConsumer.hentForsendelserISak(eq(SAKSNUMMER))
        }

        verify {
            bidragForsendelseConsumer.slettForsendelse(eq(1))
            bidragForsendelseConsumer.slettForsendelse(eq(2))
        }
    }
}
