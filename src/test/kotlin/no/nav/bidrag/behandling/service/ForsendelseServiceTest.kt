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
import no.nav.bidrag.behandling.consumer.BidragTIlgangskontrollConsumer
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.consumer.OpprettForsendelseRespons
import no.nav.bidrag.behandling.dto.forsendelse.BehandlingInfoDto
import no.nav.bidrag.behandling.dto.forsendelse.InitalizeForsendelseRequest
import no.nav.bidrag.behandling.utils.ROLLE_BA_1
import no.nav.bidrag.behandling.utils.ROLLE_BM
import no.nav.bidrag.behandling.utils.ROLLE_BP
import no.nav.bidrag.behandling.utils.SAKSNUMMER
import no.nav.bidrag.behandling.utils.SOKNAD_ID
import no.nav.bidrag.behandling.utils.opprettForsendelseResponsUnderOpprettelse
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.transport.dokument.BidragEnhet.ENHET_FARSKAP
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class ForsendelseServiceTest {

    @MockkBean
    lateinit var bidragForsendelseConsumer: BidragForsendelseConsumer

    @MockkBean
    lateinit var bidragTIlgangskontrollConsumer: BidragTIlgangskontrollConsumer
    lateinit var forsendelseService: ForsendelseService

    @BeforeEach
    fun initMocks() {
        forsendelseService =
            ForsendelseService(bidragForsendelseConsumer, bidragTIlgangskontrollConsumer)
        every { bidragForsendelseConsumer.opprettForsendelse(any()) } returns OpprettForsendelseRespons(
            "2313",
        )
        every { bidragForsendelseConsumer.slettForsendelse(any()) } returns Unit
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
    }

    @Test
    fun `Skal opprette forsendelse for behandling med tema FAR når enhet er farskap`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = ENHET_FARSKAP,
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.FORSKUDD,
                vedtakType = VedtakType.KLAGE,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.tema shouldBe "FAR"
                    it.behandlingInfo!!.barnIBehandling shouldHaveSize 1
                    it.behandlingInfo!!.barnIBehandling shouldContain ROLLE_BA_1.fødselsnummer.verdi
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for gebyr behandling for bidragsmottaker`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = ENHET_FARSKAP,
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                engangsBelopType = EngangsbelopType.GEBYR_MOTTAKER,
                vedtakType = VedtakType.ENDRING,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BM.fødselsnummer.verdi
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for gebyr behandling for bidragspliktig`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = ENHET_FARSKAP,
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                engangsBelopType = EngangsbelopType.GEBYR_SKYLDNER,
                vedtakType = VedtakType.ENDRING,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BP.fødselsnummer.verdi
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med tema BID når enhet er farskap men person ikke har tilgang`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns false
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = ENHET_FARSKAP,
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.BIDRAG,
                vedtakType = VedtakType.FASTSETTELSE,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 2) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.tema shouldBe "BID"
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type FORSKUDD og vedtakType KLAGE`() {
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = "4806",
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.FORSKUDD,
                vedtakType = VedtakType.KLAGE,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 1) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe StonadType.FORSKUDD

                    it.gjelderIdent shouldBe ROLLE_BM.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BM.fødselsnummer?.verdi
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type BIDRAG`() {
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = "4806",
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.BIDRAG,
                vedtakType = VedtakType.FASTSETTELSE,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 2) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe StonadType.BIDRAG
                },
            )
        }

        verify(ordering = Ordering.SEQUENCE) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BM.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BM.fødselsnummer?.verdi
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BP.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BP.fødselsnummer?.verdi
                },
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type BIDRAG 18 år`() {
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = "4806",
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.BIDRAG18AAR,
                vedtakType = VedtakType.FASTSETTELSE,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 3) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe StonadType.BIDRAG18AAR
                },
            )
        }

        verify(ordering = Ordering.SEQUENCE) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BM.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BM.fødselsnummer?.verdi
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BP.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BP.fødselsnummer?.verdi
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BA_1.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BA_1.fødselsnummer?.verdi
                },
            )
        }
    }

    @Test
    fun `Skal ikke opprette forsendelse for behandling med type forskudd fastsettelse hvis vedtak ikke er fattet`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = ENHET_FARSKAP,
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.FORSKUDD,
                vedtakType = VedtakType.FASTSETTELSE,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 0) {
            bidragForsendelseConsumer.opprettForsendelse(any())
        }
    }

    @Test
    fun `Skal ikke opprette forsendelse for behandling med type forskudd endring hvis vedtak ikke er fattet`() {
        every { bidragTIlgangskontrollConsumer.sjekkTilgangTema(any()) } returns true
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = ENHET_FARSKAP,
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.FORSKUDD,
                vedtakType = VedtakType.ENDRING,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 0) {
            bidragForsendelseConsumer.opprettForsendelse(any())
        }
    }

    @Test
    fun `Skal opprette forsendelse for behandling med type BIDRAG som er fattet og slette forsendelser for varsel under opprettelse`() {
        every { bidragForsendelseConsumer.hentForsendelserISak(any()) } returns listOf(
            opprettForsendelseResponsUnderOpprettelse(1),
            opprettForsendelseResponsUnderOpprettelse(2),
            opprettForsendelseResponsUnderOpprettelse(3).copy(status = ForsendelseStatusTo.DISTRIBUERT),
            opprettForsendelseResponsUnderOpprettelse(4).copy(forsendelseType = ForsendelseTypeTo.NOTAT),
        )
        val request = InitalizeForsendelseRequest(
            saksnummer = SAKSNUMMER,
            enhet = "4806",
            behandlingInfo = BehandlingInfoDto(
                soknadId = SOKNAD_ID,
                stonadType = StonadType.BIDRAG,
                vedtakType = VedtakType.FASTSETTELSE,
                erFattetBeregnet = true,
            ),
            roller = listOf(
                ROLLE_BM,
                ROLLE_BP,
                ROLLE_BA_1,
            ),
        )
        forsendelseService.opprettForsendelse(request)
        verify(exactly = 2) {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.enhet shouldBe "4806"
                    it.saksnummer shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.opprettTittel shouldBe true

                    it.behandlingInfo shouldNotBe null
                    it.behandlingInfo!!.soknadId shouldBe SOKNAD_ID
                    it.behandlingInfo!!.stonadType shouldBe StonadType.BIDRAG
                },
            )
        }

        verify {
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BM.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BM.fødselsnummer?.verdi
                },
            )
            bidragForsendelseConsumer.opprettForsendelse(
                withArg {
                    it.gjelderIdent shouldBe ROLLE_BP.fødselsnummer?.verdi
                    it.mottaker?.ident shouldBe ROLLE_BP.fødselsnummer?.verdi
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
}
