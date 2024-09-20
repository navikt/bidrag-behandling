package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.transformers.grunnlag.tilPersonobjekter
import no.nav.bidrag.behandling.transformers.vedtak.grunnlagsreferanse_løpende_bidrag
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.vedtak.Vedtaksfiltrering
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto.BidragBeregning
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssakerResponse
import no.nav.bidrag.transport.behandling.vedtak.response.BehandlingsreferanseDto
import no.nav.bidrag.transport.behandling.vedtak.response.HentVedtakForStønadResponse
import no.nav.bidrag.transport.behandling.vedtak.response.StønadsendringDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakForStønad
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class BeregningEvnevurderingServiceTest {
    @MockkBean
    lateinit var bidragStønadConsumer: BidragStønadConsumer

    @MockkBean
    lateinit var bidragVedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var bidragBBMConsumer: BidragBBMConsumer

    @MockkBean(relaxed = true)
    lateinit var beregngVedtaksfiltrering: Vedtaksfiltrering

    lateinit var evnevurderingService: BeregningEvnevurderingService

    @BeforeEach
    fun init() {
        stubPersonConsumer()
        evnevurderingService =
            BeregningEvnevurderingService(bidragStønadConsumer, bidragVedtakConsumer, bidragBBMConsumer, beregngVedtaksfiltrering)

        every { bidragStønadConsumer.hentLøpendeBidrag(any()) } returns
            LøpendeBidragssakerResponse(
                listOf(
                    opprettLøpendeBidraggsak(testdataBarn1.ident, Stønadstype.BIDRAG),
                    opprettLøpendeBidraggsak(testdataBarn2.ident, Stønadstype.BIDRAG),
                    opprettLøpendeBidraggsak(testdataHusstandsmedlem1.ident, Stønadstype.BIDRAG18AAR),
                ),
            )

        every {
            bidragVedtakConsumer.hentVedtakForStønad(
                coMatch {
                    it.kravhaver.verdi == testdataBarn1.ident
                },
            )
        } returns
            opprettVedtakForStønadRespons(testdataBarn1.ident, Stønadstype.BIDRAG)

        every {
            bidragVedtakConsumer.hentVedtakForStønad(
                coMatch {
                    it.kravhaver.verdi == testdataBarn2.ident
                },
            )
        } returns
            opprettVedtakForStønadRespons(testdataBarn1.ident, Stønadstype.BIDRAG)

        every {
            bidragVedtakConsumer.hentVedtakForStønad(
                coMatch {
                    it.kravhaver.verdi == testdataHusstandsmedlem1.ident
                },
            )
        } returns
            opprettVedtakForStønadRespons(testdataHusstandsmedlem1.ident, Stønadstype.BIDRAG18AAR)

        every { bidragBBMConsumer.hentBeregning(any()) } returns
            BidragBeregningResponsDto(
                listOf(
                    opprettBidragBeregning(testdataBarn1.ident, Stønadstype.BIDRAG),
                    opprettBidragBeregning(testdataBarn2.ident, Stønadstype.BIDRAG).copy(
                        samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1,
                        beløpSamvær = BigDecimal(443),
                        beregnetBeløp = BigDecimal(4716),
                        faktiskBeløp = BigDecimal(4720),
                    ),
                    opprettBidragBeregning(testdataHusstandsmedlem1.ident, Stønadstype.BIDRAG18AAR).copy(
                        beregnetBeløp = BigDecimal(6943),
                        faktiskBeløp = BigDecimal(6380),
                    ),
                ),
            )
    }

    @Test
    fun `skal opprette grunnlag for løpende bidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true)
        val resultat = evnevurderingService.opprettGrunnlagLøpendeBidrag(behandling)
        resultat shouldHaveSize 3
        val løpendeBidrag = resultat.find { it.type == Grunnlagstype.LØPENDE_BIDRAG }
        assertSoftly(løpendeBidrag!!) {
            it.referanse shouldBe grunnlagsreferanse_løpende_bidrag
            it.gjelderReferanse shouldBe behandling.tilPersonobjekter().bidragspliktig!!.referanse
            it.grunnlagsreferanseListe.shouldBeEmpty()
            val innhold = this.innholdTilObjekt<LøpendeBidragGrunnlag>()
            innhold.løpendeBidragListe shouldHaveSize 3
            innhold.løpendeBidragListe[0].type shouldBe Stønadstype.BIDRAG
            innhold.løpendeBidragListe[1].type shouldBe Stønadstype.BIDRAG
            innhold.løpendeBidragListe[2].type shouldBe Stønadstype.BIDRAG18AAR
        }
    }
}

fun opprettBidragBeregning(
    kravhaver: String,
    stønadstype: Stønadstype,
) = BidragBeregning(
    saksnummer = SAKSNUMMER,
    stønadstype = stønadstype,
    personidentBarn = Personident(kravhaver),
    beregnetBeløp = BigDecimal(5159),
    faktiskBeløp = BigDecimal(5160),
    beløpSamvær = BigDecimal(0),
    samværsklasse = Samværsklasse.INGEN_SAMVÆR,
    datoSøknad = LocalDate.parse("2024-07-01"),
    gjelderFom = LocalDate.parse("2024-07-01"),
)

fun opprettVedtakForStønadRespons(
    kravhaver: String,
    stønadstype: Stønadstype,
) = HentVedtakForStønadResponse(
    listOf(
        opprettVedtakForStønad(kravhaver, stønadstype),
    ),
)

fun opprettVedtakForStønad(
    kravhaver: String,
    stønadstype: Stønadstype,
) = VedtakForStønad(
    vedtaksid = 1,
    type = Vedtakstype.ENDRING,
    kilde = Vedtakskilde.MANUELT,
    vedtakstidspunkt = LocalDateTime.parse("2024-01-01T00:00:00"),
    behandlingsreferanser =
        listOf(
            BehandlingsreferanseDto(
                kilde = BehandlingsrefKilde.BISYS_SØKNAD,
                referanse = SOKNAD_ID.toString(),
            ),
        ),
    stønadsendring =
        StønadsendringDto(
            type = stønadstype,
            sak = Saksnummer(SAKSNUMMER),
            skyldner = Personident(testdataBP.ident),
            kravhaver = Personident(kravhaver),
            mottaker = Personident(testdataBM.ident),
            førsteIndeksreguleringsår = 0,
            innkreving = Innkrevingstype.MED_INNKREVING,
            beslutning = Beslutningstype.ENDRING,
            omgjørVedtakId = 1,
            eksternReferanse = "123456",
            grunnlagReferanseListe = emptyList(),
            periodeListe = emptyList(),
        ),
)

fun opprettLøpendeBidraggsak(
    kravhaver: String = testdataBarn1.ident,
    type: Stønadstype = Stønadstype.BIDRAG,
) = LøpendeBidragssak(
    sak = Saksnummer(SAKSNUMMER),
    type = type,
    kravhaver = Personident(kravhaver),
    løpendeBeløp = BigDecimal(5160),
)
