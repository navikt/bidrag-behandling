package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.mockk.every
import io.mockk.mockkObject
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.behandling.utils.hentFil
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.sjablonResponse
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.service.sjablon.SjablonProvider
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class OmgjørVedtakserviceTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer
    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService

    val unleash = FakeUnleash()

    @BeforeEach
    fun initMocks() {
        beregningService =
            BeregningService(
                behandlingService,
                BeregnForskuddApi(),
            )
        vedtakService =
            VedtakService(
                behandlingService,
                beregningService,
                vedtakConsumer,
                sakConsumer,
                unleash,
            )
        every {
            behandlingService.oppdaterBehandling(
                any(),
                any(),
            )
        } returns
            oppretteBehandling(1).tilBehandlingDtoV2(
                emptyList(),
            )

        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        mockkObject(SjablonProvider)
        every {
            SjablonProvider.hentSjablontall()
        } returns sjablonResponse()
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling`() {
        every { vedtakConsumer.hentVedtak(any()) } returns
            commonObjectmapper.readValue(
                hentFil("/__files/vedtak_response.json"),
                VedtakDto::class.java,
            )
        val behandling = vedtakService.omgjørVedtakTilBehandling(1)
    }
}
