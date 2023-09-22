package no.nav.bidrag.behandling.hendelse

import StubUtils
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.kafka.VedtakHendelseListener
import no.nav.bidrag.behandling.service.CommonTestRunner
import no.nav.bidrag.behandling.utils.ROLLE_BA_1
import no.nav.bidrag.behandling.utils.ROLLE_BM
import no.nav.bidrag.behandling.utils.ROLLE_BP
import no.nav.bidrag.behandling.utils.SAKSNUMMER
import no.nav.bidrag.behandling.utils.SOKNAD_ID
import no.nav.bidrag.domain.enums.BehandlingsrefKilde
import no.nav.bidrag.domain.enums.Innkreving
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakKilde
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.transport.behandling.vedtak.Behandlingsreferanse
import no.nav.bidrag.transport.behandling.vedtak.Sporingsdata
import no.nav.bidrag.transport.behandling.vedtak.Stonadsendring
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.*

class VedtakHendelseTest : CommonTestRunner() {
    val stubUtils: StubUtils = StubUtils()

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var vedtakHendelseListener: VedtakHendelseListener

    @BeforeEach
    fun resetMocks() {
        WireMock.resetAllRequests()
        stubUtils.stubHentForsendelserForSak()
        stubUtils.stubSlettForsendelse()
    }

    @Test
    fun `skal opprette forsendelse for vedtakhendelse og lagre vedtakId`() {
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = opprettBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        vedtakHendelseListener.prossesserVedtakHendelse(opprettHendelseRecord(opprettVedtakhendelse(vedtakId, behandling.id!!)))
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        oppdatertBehandling.vedtakId shouldBe vedtakId
        stubUtils.Verify().opprettForsendelseKaltAntallGanger(3)
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BM.fødselsnummer.verdi}\"")
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BP.fødselsnummer.verdi}\"")
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BA_1.fødselsnummer.verdi}\"")
        stubUtils.Verify().forsendelseHentetForSak(SAKSNUMMER)
        stubUtils.Verify().forsendelseSlettet("1")
        stubUtils.Verify().forsendelseSlettet("2")
    }

    @Test
    fun `skal opprette forsendelse for vedtakhendelse og lagre vedtakId for forskudd`() {
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = opprettBehandlingRoller(behandlingRequest)
        val behandling = behandlingRepository.save(behandlingRequest)
        val vedtakHendelse = opprettVedtakhendelse(vedtakId, behandling.id!!, stonadType = StonadType.FORSKUDD)
        vedtakHendelseListener.prossesserVedtakHendelse(opprettHendelseRecord(vedtakHendelse))
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        oppdatertBehandling.vedtakId shouldBe vedtakId
        stubUtils.Verify().opprettForsendelseKaltAntallGanger(1)
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BM.fødselsnummer?.verdi}\"")
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
            datoFom = Date(),
            datoTom = Date(),
            saksnummer = SAKSNUMMER,
            soknadId = 123123L,
            behandlerEnhet = "4806",
            behandlingType = BehandlingType.BIDRAG18AAR,
            engangsbelopType = null,
            mottatDato = Date(),
            soknadFra = SoknadFraType.BIDRAGSMOTTAKER,
            soknadType = SoknadType.FASTSETTELSE,
            stonadType = StonadType.BIDRAG18AAR,
        )

    private fun opprettBehandlingRoller(behandling: Behandling) =
        mutableSetOf(
            Rolle(
                ident = ROLLE_BM.fødselsnummer?.verdi!!,
                rolleType = Rolletype.BIDRAGSMOTTAKER,
                behandling = behandling,
                fodtDato = null,
                opprettetDato = null,
            ),
            Rolle(
                ident = ROLLE_BP.fødselsnummer?.verdi!!,
                rolleType = Rolletype.BIDRAGSPLIKTIG,
                behandling = behandling,
                fodtDato = null,
                opprettetDato = null,
            ),
            Rolle(
                ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
                rolleType = Rolletype.BARN,
                behandling = behandling,
                fodtDato = null,
                opprettetDato = null,
            ),
        )

    private fun opprettVedtakhendelse(
        vedtakId: Int,
        behandlingId: Long,
        stonadType: StonadType = StonadType.BIDRAG18AAR,
    ): VedtakHendelse {
        return VedtakHendelse(
            type = VedtakType.FASTSETTELSE,
            stonadsendringListe =
            listOf(
                Stonadsendring(
                    type = stonadType,
                    eksternReferanse = "",
                    endring = true,
                    indeksreguleringAar = "2024",
                    innkreving = Innkreving.JA,
                    kravhaverId = "",
                    mottakerId = "",
                    omgjorVedtakId = 1,
                    periodeListe = emptyList(),
                    sakId = SAKSNUMMER,
                    skyldnerId = "",
                ),
            ),
            engangsbelopListe = emptyList(),
            enhetId = "4806",
            id = vedtakId,
            kilde = VedtakKilde.MANUELT,
            opprettetTidspunkt = LocalDateTime.now(),
            opprettetAvNavn = "",
            opprettetAv = "",
            sporingsdata = Sporingsdata("sporing"),
            utsattTilDato = null,
            vedtakTidspunkt = LocalDateTime.now(),
            behandlingsreferanseListe =
            listOf(
                Behandlingsreferanse(
                    BehandlingsrefKilde.BEHANDLING_ID.name,
                    behandlingId.toString(),
                ),
                Behandlingsreferanse(
                    BehandlingsrefKilde.BISYS_SOKNAD.name,
                    SOKNAD_ID.toString(),
                ),
            ),
        )
    }
}
