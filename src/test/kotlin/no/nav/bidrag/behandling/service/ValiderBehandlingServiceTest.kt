package no.nav.bidrag.behandling.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkClass
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.SjekkRolleDto
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssakerResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

class ValiderBehandlingServiceTest {
    val bidragStønadConsumer: BidragStønadConsumer = mockkClass(BidragStønadConsumer::class)

    val validerBehandlingService: ValiderBehandlingService = ValiderBehandlingService(bidragStønadConsumer)

    @BeforeEach
    fun initMock() {
        every { bidragStønadConsumer.hentLøpendeBidrag(any()) } returns
            LøpendeBidragssakerResponse(
                bidragssakerListe = oppretLøpendeBidragListeMedBareNorskValuta(),
            )
    }

    @Test
    fun `skal validere gyldig SÆRBIDRAG behandling hvis BP har bare løpende bidrag i NOK`() {
        validerBehandlingService.validerKanBehandlesINyLøsning(opprettKanBehandlesINyLøsningRequest())
    }

    @Test
    fun `skal ikke validere hvis ikke SÆRBIDRAG behandling`() {
        every { bidragStønadConsumer.hentLøpendeBidrag(any()) } returns
            LøpendeBidragssakerResponse(
                bidragssakerListe = oppretLøpendeBidragListeMedUtenlandskValuta(),
            )
        validerBehandlingService.validerKanBehandlesINyLøsning(
            opprettKanBehandlesINyLøsningRequest().copy(
                engangsbeløpstype = null,
                stønadstype = Stønadstype.FORSKUDD,
            ),
        )
    }

    @Test
    fun `skal ikke validere gyldig SÆRBIDRAG behandling hvis BP har bare løpende bidrag i utenlandsk valuta`() {
        every { bidragStønadConsumer.hentLøpendeBidrag(any()) } returns
            LøpendeBidragssakerResponse(
                bidragssakerListe = oppretLøpendeBidragListeMedUtenlandskValuta(),
            )
        val expection =
            shouldThrow<HttpClientErrorException> {
                validerBehandlingService.validerKanBehandlesINyLøsning(
                    opprettKanBehandlesINyLøsningRequest(),
                )
            }
        expection.statusCode shouldBe HttpStatus.PRECONDITION_FAILED
    }
}

private fun oppretLøpendeBidragListeMedUtenlandskValuta() =
    listOf(
        LøpendeBidragssak(
            valutakode = "NOK",
            sak = Saksnummer(SAKSNUMMER),
            kravhaver = Personident("12345678901"),
            type = Stønadstype.BIDRAG,
            løpendeBeløp = BigDecimal.ONE,
        ),
        LøpendeBidragssak(
            valutakode = "USD",
            sak = Saksnummer(SAKSNUMMER),
            kravhaver = Personident("12345678901"),
            type = Stønadstype.BIDRAG,
            løpendeBeløp = BigDecimal.ONE,
        ),
    )

private fun oppretLøpendeBidragListeMedBareNorskValuta() =
    listOf(
        LøpendeBidragssak(
            valutakode = "NOK",
            sak = Saksnummer(SAKSNUMMER),
            kravhaver = Personident("12345678901"),
            type = Stønadstype.BIDRAG,
            løpendeBeløp = BigDecimal.ONE,
        ),
    )

private fun opprettKanBehandlesINyLøsningRequest() =
    KanBehandlesINyLøsningRequest(
        engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
        stønadstype = null,
        roller =
            listOf(
                SjekkRolleDto(Rolletype.BIDRAGSPLIKTIG, ident = Personident("12345678901")),
            ),
        saksnummer = SAKSNUMMER,
    )
