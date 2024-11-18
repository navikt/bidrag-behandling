package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockkClass
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningResponse
import no.nav.bidrag.behandling.dto.v2.behandling.SjekkRolleDto
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssakerResponse
import no.nav.bidrag.transport.behandling.stonad.response.SkyldnerStønad
import no.nav.bidrag.transport.behandling.stonad.response.SkyldnerStønaderResponse
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
        expection.validerInneholderMelding("Bidragspliktig har løpende bidrag i utenlandsk valuta")
    }

    @Nested
    inner class BidragValideringMVP1 {
        @Test
        fun `skal validere gyldig BIDRAG`() {
            every { bidragStønadConsumer.hentAlleStønaderForBidragspliktig(any()) } returns
                SkyldnerStønaderResponse(
                    stønader = listOf(),
                )
            shouldNotThrow<HttpClientErrorException> {
                validerBehandlingService.validerKanBehandlesINyLøsning(
                    opprettBidragKanBehandlesINyLøsningRequest(),
                )
            }
        }

        @Test
        fun `skal ikke validere gyldig BIDRAG behandling hvis BP har minst en løpende bidrag`() {
            every { bidragStønadConsumer.hentAlleStønaderForBidragspliktig(any()) } returns
                SkyldnerStønaderResponse(
                    stønader = listOf(opprettSkyldnerStønad()),
                )
            val expection =
                shouldThrow<HttpClientErrorException> {
                    validerBehandlingService.validerKanBehandlesINyLøsning(
                        opprettBidragKanBehandlesINyLøsningRequest(),
                    )
                }
            expection.statusCode shouldBe HttpStatus.PRECONDITION_FAILED
            expection.validerInneholderMelding("Bidragspliktig har en eller flere historiske eller løpende bidrag")
        }

        @Test
        fun `skal ikke validere gyldig BIDRAG behandling hvis flere enn en søknadsbarn`() {
            every { bidragStønadConsumer.hentAlleStønaderForBidragspliktig(any()) } returns
                SkyldnerStønaderResponse(
                    stønader = listOf(),
                )
            val request = opprettBidragKanBehandlesINyLøsningRequest()

            val expection =
                shouldThrow<HttpClientErrorException> {
                    validerBehandlingService.validerKanBehandlesINyLøsning(
                        request.copy(
                            roller = request.roller + SjekkRolleDto(Rolletype.BARN, ident = Personident("333"), false),
                        ),
                    )
                }
            expection.statusCode shouldBe HttpStatus.PRECONDITION_FAILED
            expection.validerInneholderMelding("Behandlingen har flere enn ett søknadsbarn")
        }

        @Test
        fun `skal ikke validere gyldig BIDRAG behandling gjelder klage`() {
            every { bidragStønadConsumer.hentAlleStønaderForBidragspliktig(any()) } returns
                SkyldnerStønaderResponse(
                    stønader = listOf(),
                )
            val request = opprettBidragKanBehandlesINyLøsningRequest()

            val expection =
                shouldThrow<HttpClientErrorException> {
                    validerBehandlingService.validerKanBehandlesINyLøsning(
                        request.copy(
                            vedtakstype = Vedtakstype.KLAGE,
                        ),
                    )
                }
            expection.statusCode shouldBe HttpStatus.PRECONDITION_FAILED
            expection.validerInneholderMelding("Kan ikke behandle klage eller omgjøring")
        }

        @Test
        fun `skal ikke validere gyldig BIDRAG behandling referer til annen behandling`() {
            every { bidragStønadConsumer.hentAlleStønaderForBidragspliktig(any()) } returns
                SkyldnerStønaderResponse(
                    stønader = listOf(),
                )
            val request = opprettBidragKanBehandlesINyLøsningRequest()

            val expection =
                shouldThrow<HttpClientErrorException> {
                    validerBehandlingService.validerKanBehandlesINyLøsning(
                        request.copy(
                            vedtakstype = Vedtakstype.ENDRING,
                            harReferanseTilAnnenBehandling = true,
                        ),
                    )
                }
            expection.statusCode shouldBe HttpStatus.PRECONDITION_FAILED
            expection.validerInneholderMelding("Kan ikke behandle klage eller omgjøring")
        }
    }
}

private fun HttpClientErrorException.validerInneholderMelding(melding: String) {
    val response: KanBehandlesINyLøsningResponse = commonObjectmapper.readValue(responseBodyAsByteArray)
    response.begrunnelser.joinToString("") shouldContain melding
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

private fun opprettSkyldnerStønad(type: Stønadstype = Stønadstype.BIDRAG) = SkyldnerStønad(sak = Saksnummer("123"), kravhaver = Personident("213"), type = type)

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
                SjekkRolleDto(Rolletype.BIDRAGSPLIKTIG, ident = Personident("12345678901"), false),
            ),
        saksnummer = SAKSNUMMER,
    )

private fun opprettBidragKanBehandlesINyLøsningRequest() =
    KanBehandlesINyLøsningRequest(
        engangsbeløpstype = null,
        vedtakstype = Vedtakstype.FASTSETTELSE,
        stønadstype = Stønadstype.BIDRAG,
        roller =
            listOf(
                SjekkRolleDto(Rolletype.BIDRAGSPLIKTIG, ident = Personident("3231"), false),
                SjekkRolleDto(Rolletype.BIDRAGSMOTTAKER, ident = Personident("123"), false),
                SjekkRolleDto(Rolletype.BARN, ident = Personident("123213"), false),
            ),
        saksnummer = SAKSNUMMER,
    )
