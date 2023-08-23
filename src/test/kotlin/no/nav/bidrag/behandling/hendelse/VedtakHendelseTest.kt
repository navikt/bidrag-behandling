package no.nav.bidrag.behandling.hendelse

import StubUtils
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.kafka.VedtakHendelseListener
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.CommonTestRunner
import no.nav.bidrag.behandling.utils.ROLLE_BA_1
import no.nav.bidrag.behandling.utils.ROLLE_BM
import no.nav.bidrag.behandling.utils.ROLLE_BP
import no.nav.bidrag.behandling.utils.SAKSNUMMER
import no.nav.bidrag.behandling.utils.SOKNAD_ID
import no.nav.bidrag.domain.enums.BehandlingsrefKilde
import no.nav.bidrag.domain.enums.Innkreving
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakKilde
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.transport.behandling.vedtak.Behandlingsreferanse
import no.nav.bidrag.transport.behandling.vedtak.Sporingsdata
import no.nav.bidrag.transport.behandling.vedtak.Stonadsendring
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.*

class VedtakHendelseTest : CommonTestRunner() {
    val stubUtils: StubUtils = StubUtils()


    @Autowired
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var vedtakHendelseListener: VedtakHendelseListener

    @Test
    fun `skal opprette forsendelse for vedtakhendelse og lagre vedtakId`() {
        stubUtils.stubOpprettForsendelse()
        val vedtakId = 123123
        val behandlingRequest = opprettBehandling()
        behandlingRequest.roller = opprettBehandlingRoller(behandlingRequest)
        val behandling = behandlingService.createBehandling(behandlingRequest)
        vedtakHendelseListener.prossesserVedtakHendelse(
            ConsumerRecord(
                "",
                1,
                1,
                "",
                stubUtils.toJsonString(opprettVedtakhendelse(vedtakId, behandling.id!!))
            )
        )
        val oppdatertBehandling = behandlingService.hentBehandlingById(behandling.id!!)
        oppdatertBehandling.vedtakId shouldBe vedtakId
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BM.fødselsnummer?.verdi}\"")
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BP.fødselsnummer?.verdi}\"")
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"${ROLLE_BA_1.fødselsnummer?.verdi}\"")
    }

    private fun opprettBehandling() = Behandling(
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

    private fun opprettBehandlingRoller(behandling: Behandling) = mutableSetOf(
        Rolle(
            ident = ROLLE_BM.fødselsnummer?.verdi!!,
            rolleType = RolleType.BIDRAGS_MOTTAKER,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null
        ),
        Rolle(
            ident = ROLLE_BP.fødselsnummer?.verdi!!,
            rolleType = RolleType.BIDRAGS_PLIKTIG,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null
        ),
        Rolle(
            ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
            rolleType = RolleType.BARN,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null
        )
    )

    private fun opprettVedtakhendelse(vedtakId: Int, behandlingId: Long): VedtakHendelse {
        return VedtakHendelse(
            type = VedtakType.FASTSETTELSE, stonadsendringListe = listOf(
                Stonadsendring(
                    type = StonadType.BIDRAG18AAR,
                    eksternReferanse = "",
                    endring = true,
                    indeksreguleringAar = "2024",
                    innkreving = Innkreving.JA,
                    kravhaverId = "",
                    mottakerId = "",
                    omgjorVedtakId = 1,
                    periodeListe = emptyList(),
                    sakId = SAKSNUMMER,
                    skyldnerId = ""
                )
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
            behandlingsreferanseListe = listOf(
                Behandlingsreferanse(
                    BehandlingsrefKilde.BEHANDLING_ID.name,
                    behandlingId.toString()
                ),
                Behandlingsreferanse(
                    BehandlingsrefKilde.BISYS_SOKNAD.name,
                    SOKNAD_ID.toString()
                )
            )
        )
    }
}