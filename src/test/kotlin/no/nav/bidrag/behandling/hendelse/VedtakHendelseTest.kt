package no.nav.bidrag.behandling.hendelse

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.kafka.VedtakHendelseListener
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID
import no.nav.bidrag.behandling.utils.testdata.opprettStønadDto
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.opprettVedtakhendelse
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.vedtak.Behandlingsreferanse
import no.nav.bidrag.transport.behandling.vedtak.Stønadsendring
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class VedtakHendelseTest : TestContainerRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var vedtakHendelseListener: VedtakHendelseListener

    @BeforeEach
    fun resetMocks() {
        WireMock.resetAllRequests()
        stubUtils.stubHentForsendelserForSak()
        stubUtils.stubSlettForsendelse()
        stubUtils.stubOpprettNotat()
        stubUtils.stubOpprettJournalpost("12333")
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        stubUtils.stubTilgangskontrollPersonISak()
    }

    @Test
    fun `skal opprette forsendelse for vedtakhendelse og lagre vedtakId`() {
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = oppretteBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        vedtakHendelseListener.prossesserVedtakHendelse(
            opprettHendelseRecord(
                opprettVedtakhendelse(
                    vedtakId,
                    behandling.id!!,
                ),
            ),
        )
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        oppdatertBehandling.vedtaksid shouldBe vedtakId
        stubUtils.Verify().opprettForsendelseKaltAntallGanger(3)
        stubUtils
            .Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${testdataBM.ident}\"")
        stubUtils
            .Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${testdataBarn1.ident}\"")
        stubUtils
            .Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${testdataBarn2.ident}\"")
        stubUtils.Verify().forsendelseHentetForSak(SAKSNUMMER)
        stubUtils.Verify().forsendelseSlettet("1")
        stubUtils.Verify().forsendelseSlettet("2")
//        stubUtils.Verify().opprettNotatKalt()
//        stubUtils.Verify().opprettJournalpostKaltMed()
    }

    @Test
    fun `skal opprette forsendelse for vedtakhendelse og lagre vedtakId for forskudd`() {
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = oppretteBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        val vedtakHendelse =
            opprettVedtakhendelse(vedtakId, behandling.id!!, stonadType = Stønadstype.FORSKUDD)
        vedtakHendelseListener.prossesserVedtakHendelse(opprettHendelseRecord(vedtakHendelse))
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        oppdatertBehandling.vedtaksid shouldBe vedtakId
        stubUtils.Verify().opprettForsendelseKaltAntallGanger(1)
        stubUtils
            .Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${testdataBM.ident}\"")
    }

    @Test
    fun `skal opprett revurder forskudd oppgave`() {
        stubUtils.stubOpprettOppgave()
        stubUtils.stubSøkOppgave()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBidragBeløpshistorikkHistoriskeSaker(
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            ),
        )
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = oppretteBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        val vedtakHendelse =
            opprettVedtakhendelse(vedtakId, behandling.id!!, stonadType = Stønadstype.BIDRAG)
                .copy(
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
                )
        vedtakHendelseListener.prossesserVedtakHendelse(opprettHendelseRecord(vedtakHendelse))
        stubUtils.Verify().opprettOppgaveKalt(0)
    }

    @Test
    fun `skal opprette revurder forskudd oppgave hvis fattet gjennom bisys`() {
        stubUtils.stubOpprettOppgave()
        stubUtils.stubSøkOppgave()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBidragBeløpshistorikkHistoriskeSaker(
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            ),
        )
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = oppretteBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        val vedtakHendelse =
            opprettVedtakhendelse(vedtakId, behandling.id!!, stonadType = Stønadstype.BIDRAG)
                .copy(
                    behandlingsreferanseListe =
                        listOf(
                            Behandlingsreferanse(
                                BehandlingsrefKilde.BISYS_SØKNAD.name,
                                SOKNAD_ID.toString(),
                            ),
                        ),
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
                )
        vedtakHendelseListener.prossesserVedtakHendelse(opprettHendelseRecord(vedtakHendelse))
        stubUtils.Verify().opprettOppgaveKalt(0)
        stubUtils.Verify().hentBidragBeløpshistorikkHistoriskeSakerKalt(0)
    }

    @Test
    fun `skal ikke opprette revurder forskudd oppgave hvis opprettet av batch`() {
        stubUtils.stubOpprettOppgave()
        stubUtils.stubSøkOppgave()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBidragBeløpshistorikkHistoriskeSaker(
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(
                            ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                            beløp = BigDecimal("5600"),
                        ),
                    ),
            ),
        )
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = oppretteBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        val vedtakHendelse =
            opprettVedtakhendelse(vedtakId, behandling.id!!, stonadType = Stønadstype.BIDRAG)
                .copy(
                    kilde = Vedtakskilde.AUTOMATISK,
                    behandlingsreferanseListe =
                        listOf(
                            Behandlingsreferanse(
                                BehandlingsrefKilde.BISYS_SØKNAD.name,
                                SOKNAD_ID.toString(),
                            ),
                        ),
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
                )
        vedtakHendelseListener.prossesserVedtakHendelse(opprettHendelseRecord(vedtakHendelse))
        stubUtils.Verify().opprettOppgaveKalt(0)
        stubUtils.Verify().hentBidragBeløpshistorikkHistoriskeSakerKalt(0)
    }

    private fun opprettHendelseRecord(vedtakHendelse: VedtakHendelse) =
        ConsumerRecord(
            "",
            1,
            1,
            "",
            stubUtils.toJsonString(vedtakHendelse),
        )

    private fun opprettBehandling() =
        Behandling(
            søktFomDato = LocalDate.now(),
            saksnummer = SAKSNUMMER,
            soknadsid = SOKNAD_ID.toLong(),
            behandlerEnhet = "4806",
            opprettetAv = "Z99999",
            opprettetAvNavn = "Saksbehandler Navn",
            kildeapplikasjon = "bisys",
            engangsbeloptype = null,
            mottattdato = LocalDate.now(),
            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
            vedtakstype = Vedtakstype.FASTSETTELSE,
            stonadstype = Stønadstype.BIDRAG18AAR,
        )
}
