package no.nav.bidrag.behandling.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.consumer.OppgaveConsumer
import no.nav.bidrag.behandling.consumer.dto.OppgaveDto
import no.nav.bidrag.behandling.consumer.dto.OppgaveSokResponse
import no.nav.bidrag.behandling.consumer.dto.OppgaveType
import no.nav.bidrag.behandling.consumer.dto.behandlingstypeNasjonal
import no.nav.bidrag.behandling.consumer.dto.behandlingstypeUtland
import no.nav.bidrag.behandling.consumer.dto.formatterDatoForOppgave
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.transformers.vedtak.skyldnerNav
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettStønadDto
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.opprettVedtakhendelse
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.util.VirkedagerProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.vedtak.Engangsbeløp
import no.nav.bidrag.transport.behandling.vedtak.Stønadsendring
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import stubPersonConsumer
import stubSaksbehandlernavnProvider
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockKExtension::class)
class OppgaveServiceTest {
    lateinit var oppgaveService: OppgaveService

    @MockK
    lateinit var oppgaveConsumer: OppgaveConsumer

    @MockK
    lateinit var bidragStønadConsumer: BidragStønadConsumer

    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @BeforeEach
    fun setUp() {
        oppgaveService = OppgaveService(oppgaveConsumer, bidragStønadConsumer, behandlingRepository)
        stubSaksbehandlernavnProvider()
        stubPersonConsumer()
    }

    @Test
    fun `skal opprette revurder forskudd oppgave`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )
        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.sak shouldBe Saksnummer(SAKSNUMMER)
                    it.type shouldBe Stønadstype.FORSKUDD
                    it.skyldner shouldBe skyldnerNav
                    it.kravhaver shouldBe Personident(testdataBarn1.ident)
                },
            )
        }
        verify(exactly = 1) {
            oppgaveConsumer.hentOppgave(
                withArg {
                    it.hentParametre() shouldContain "oppgavetype=GEN"
                    it.hentParametre() shouldContain "saksreferanse=$SAKSNUMMER"
                    it.hentParametre() shouldContain "tema=BID"
                },
            )
        }
        verify(exactly = 1) {
            oppgaveConsumer.opprettOppgave(
                withArg {
                    it.saksreferanse shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.aktivDato shouldBe formatterDatoForOppgave(LocalDate.now())
                    it.fristFerdigstillelse shouldBe formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag())
                    it.personident shouldBe testdataBM.ident
                    it.oppgavetype shouldBe OppgaveType.GEN
                    it.tildeltEnhetsnr shouldBe "4806"
                    it.tilordnetRessurs shouldBe SAKSBEHANDLER_IDENT
                    it.behandlingstype.shouldBe(behandlingstypeNasjonal)
                    it.beskrivelse.shouldContain(revurderForskuddBeskrivelse)
                },
            )
        }
    }

    @Test
    fun `skal opprette revurder forskudd oppgave hvis det er klageenhet`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.opprettetAv = "Z1123"
        behandling.behandlerEnhet = "4806"
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4291"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )
        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.sak shouldBe Saksnummer(SAKSNUMMER)
                    it.type shouldBe Stønadstype.FORSKUDD
                    it.skyldner shouldBe skyldnerNav
                    it.kravhaver shouldBe Personident(testdataBarn1.ident)
                },
            )
        }
        verify(exactly = 1) {
            oppgaveConsumer.hentOppgave(
                withArg {
                    it.hentParametre() shouldContain "oppgavetype=GEN"
                    it.hentParametre() shouldContain "saksreferanse=$SAKSNUMMER"
                    it.hentParametre() shouldContain "tema=BID"
                },
            )
        }
        verify(exactly = 1) {
            oppgaveConsumer.opprettOppgave(
                withArg {
                    it.saksreferanse shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.aktivDato shouldBe formatterDatoForOppgave(LocalDate.now())
                    it.fristFerdigstillelse shouldBe formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag())
                    it.personident shouldBe testdataBM.ident
                    it.oppgavetype shouldBe OppgaveType.GEN
                    it.tildeltEnhetsnr shouldBe "4806"
                    it.tilordnetRessurs shouldBe null
                    it.behandlingstype.shouldBe(behandlingstypeNasjonal)
                    it.beskrivelse.shouldContain(revurderForskuddBeskrivelse)
                },
            )
        }
    }

    @Test
    fun `skal opprette revurder forskudd oppgave for utland`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4865"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )
        verify(exactly = 1) {
            oppgaveConsumer.opprettOppgave(
                withArg {
                    it.saksreferanse shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.aktivDato shouldBe formatterDatoForOppgave(LocalDate.now())
                    it.fristFerdigstillelse shouldBe formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag())
                    it.personident shouldBe testdataBM.ident
                    it.oppgavetype shouldBe OppgaveType.GEN
                    it.tildeltEnhetsnr shouldBe "4865"
                    it.tilordnetRessurs shouldBe SAKSBEHANDLER_IDENT
                    it.behandlingstype.shouldBe(behandlingstypeUtland)
                    it.beskrivelse.shouldContain(revurderForskuddBeskrivelse)
                },
            )
        }
    }

    @Test
    fun `skal opprette revurder forskudd oppgave for farskap`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4860"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )
        verify(exactly = 1) {
            oppgaveConsumer.opprettOppgave(
                withArg {
                    it.saksreferanse shouldBe SAKSNUMMER
                    it.tema shouldBe "FAR"
                    it.aktivDato shouldBe formatterDatoForOppgave(LocalDate.now())
                    it.fristFerdigstillelse shouldBe formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag())
                    it.personident shouldBe testdataBM.ident
                    it.oppgavetype shouldBe OppgaveType.GEN
                    it.tildeltEnhetsnr shouldBe "4860"
                    it.tilordnetRessurs shouldBe SAKSBEHANDLER_IDENT
                    it.behandlingstype.shouldBe(behandlingstypeNasjonal)
                    it.beskrivelse.shouldContain(revurderForskuddBeskrivelse)
                },
            )
        }
    }

    @Test
    fun `skal ikke opprette revurder forskudd oppgave hvis ikke bidrag`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.FORSKUDD,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = skyldnerNav,
                        ),
                    ),
            ),
        )

        verify(exactly = 0) {
            oppgaveConsumer.opprettOppgave(any())
        }
        verify(exactly = 0) {
            bidragStønadConsumer.hentHistoriskeStønader(any())
        }
        verify(exactly = 0) {
            oppgaveConsumer.hentOppgave(any())
        }
    }

    @Test
    fun `skal ikke opprette revurder forskudd oppgave hvis finnes fra før`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns
            OppgaveSokResponse(
                1,
                listOf(
                    OppgaveDto(
                        1,
                        beskrivelse = "--- 20.02.2025 06:59 F_Z994977 E_Z994977 (Z994977, 4806) ---\r\ndsad\r\n\r\n--- 20.02.2025 06:59 Z994977 ---\r\nLøper forskuddet med riktig sats? Vurder om forskuddet må revurderes.\r\n\r\n",
                    ),
                ),
            )
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )

        verify(exactly = 0) {
            oppgaveConsumer.opprettOppgave(any())
        }
    }

    @Test
    fun `skal ikke opprette revurder forskudd oppgave hvis ingen løpende forskudd`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), LocalDate.now().minusMonths(3)),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )

        verify(exactly = 0) {
            oppgaveConsumer.opprettOppgave(any())
        }
    }

    @Test
    fun `skal opprette bare en revurder forskudd oppgave hvis flere barn har løpende forskudd`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                        Stønadsendring(
                            type = Stønadstype.BIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn2.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )

        verify(exactly = 1) {
            oppgaveConsumer.opprettOppgave(
                withArg {
                    it.saksreferanse shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.aktivDato shouldBe formatterDatoForOppgave(LocalDate.now())
                    it.fristFerdigstillelse shouldBe formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag())
                    it.personident shouldBe testdataBM.ident
                    it.oppgavetype shouldBe OppgaveType.GEN
                    it.tildeltEnhetsnr shouldBe "4806"
                    it.tilordnetRessurs shouldBe SAKSBEHANDLER_IDENT
                    it.behandlingstype.shouldBe(behandlingstypeNasjonal)
                    it.beskrivelse.shouldContain(revurderForskuddBeskrivelse)
                },
            )
        }
    }

    @Test
    fun `skal opprette revurder forskudd oppgave for særbidrag`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe = emptyList(),
                engangsbeløpListe =
                    listOf(
                        Engangsbeløp(
                            type = Engangsbeløptype.SÆRBIDRAG,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            beløp = BigDecimal(100),
                            sak = Saksnummer(SAKSNUMMER),
                            valutakode = "NOK",
                            resultatkode = "",
                            referanse = "",
                            delytelseId = "",
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )
        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.sak shouldBe Saksnummer(SAKSNUMMER)
                    it.type shouldBe Stønadstype.FORSKUDD
                    it.skyldner shouldBe skyldnerNav
                    it.kravhaver shouldBe Personident(testdataBarn1.ident)
                },
            )
        }
        verify(exactly = 1) {
            oppgaveConsumer.hentOppgave(
                withArg {
                    it.hentParametre() shouldContain "oppgavetype=GEN"
                    it.hentParametre() shouldContain "saksreferanse=$SAKSNUMMER"
                    it.hentParametre() shouldContain "tema=BID"
                },
            )
        }
        verify(exactly = 1) {
            oppgaveConsumer.opprettOppgave(
                withArg {
                    it.saksreferanse shouldBe SAKSNUMMER
                    it.tema shouldBe "BID"
                    it.aktivDato shouldBe formatterDatoForOppgave(LocalDate.now())
                    it.fristFerdigstillelse shouldBe formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag())
                    it.personident shouldBe testdataBM.ident
                    it.oppgavetype shouldBe OppgaveType.GEN
                    it.tildeltEnhetsnr shouldBe "4806"
                    it.tilordnetRessurs shouldBe SAKSBEHANDLER_IDENT
                    it.behandlingstype.shouldBe(behandlingstypeNasjonal)
                    it.beskrivelse.shouldContain(revurderForskuddBeskrivelse)
                },
            )
        }
    }

    @Test
    fun `skal ikke opprette revurder forskudd oppgave hvis forskudd`() {
        every { oppgaveConsumer.opprettOppgave(any()) } returns OppgaveDto(1)
        every { oppgaveConsumer.hentOppgave(any()) } returns OppgaveSokResponse()
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            )
        oppgaveService.opprettRevurderForskuddOppgave(
            opprettVedtakhendelse(1, 1).copy(
                enhetsnummer = Enhetsnummer("4806"),
                stønadsendringListe =
                    listOf(
                        Stønadsendring(
                            type = Stønadstype.FORSKUDD,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            førsteIndeksreguleringsår = 2024,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            periodeListe = emptyList(),
                            sak = Saksnummer(SAKSNUMMER),
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
                engangsbeløpListe =
                    listOf(
                        Engangsbeløp(
                            type = Engangsbeløptype.GEBYR_MOTTAKER,
                            eksternReferanse = "",
                            beslutning = Beslutningstype.ENDRING,
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            kravhaver = Personident(testdataBarn1.ident),
                            mottaker = Personident(testdataBM.ident),
                            omgjørVedtakId = 1,
                            beløp = BigDecimal(100),
                            sak = Saksnummer(SAKSNUMMER),
                            valutakode = "NOK",
                            resultatkode = "",
                            referanse = "",
                            delytelseId = "",
                            skyldner = Personident(testdataBP.ident),
                        ),
                    ),
            ),
        )
        verify(exactly = 0) {
            bidragStønadConsumer.hentHistoriskeStønader(any())
        }
        verify(exactly = 0) {
            oppgaveConsumer.hentOppgave(any())
        }
        verify(exactly = 0) {
            oppgaveConsumer.opprettOppgave(any())
        }
    }
}
