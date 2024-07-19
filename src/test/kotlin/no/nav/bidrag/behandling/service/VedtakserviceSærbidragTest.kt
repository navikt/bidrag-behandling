package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.AldersgruppeForskudd
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningForskudd
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.math.BigDecimal
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class VedtakserviceSærbidragTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

    @MockkBean
    lateinit var bidragGrunnlagConsumer: BidragGrunnlagConsumer

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer

    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService

    val unleash = FakeUnleash()

    @BeforeEach
    fun initMocks() {
        clearAllMocks()
        unleash.enableAll()
        beregningService =
            BeregningService(
                behandlingService,
            )
        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                beregningService,
                tilgangskontrollService,
                vedtakConsumer,
                sakConsumer,
                unleash,
            )
        every { notatOpplysningerService.opprettNotat(any()) } returns Unit
        every { grunnlagService.oppdatereGrunnlagForBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangSak(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every {
            behandlingService.oppdaterVedtakFattetStatus(
                any(),
                any(),
            )
        } returns Unit
        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
    }

    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.grunnlagsinnhentingFeilet = ""
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        behandling.boforholdsbegrunnelseKunINotat = "Boforhold"
        behandling.boforholdsbegrunnelseIVedtakOgNotat = "Boforhold kun i notat"
        behandling.refVedtaksid = 553
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse_særbidrag.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde 61 grunnlag") {
                request.grunnlagListe shouldHaveSize 61
            }

            assertSoftly(request.engangsbeløpListe[0]) {
                it.type shouldBe Engangsbeløptype.SÆRBIDRAG
                it.sak shouldBe Saksnummer(behandling.saksnummer)
                it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
                it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
                it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
                it.beløp shouldBe BigDecimal(417)
                it.valutakode shouldBe "NOK"
                it.resultatkode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET.name
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.grunnlagReferanseListe shouldHaveSize 61
                it.betaltBeløp shouldBe BigDecimal(500)
            }
        }

//        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()
//        opprettVedtakRequest.validerSluttberegning()
//        opprettVedtakRequest.validerBosstatusPerioder()
//        opprettVedtakRequest.validerInntektrapportering()
//
//        assertSoftly(opprettVedtakRequest) {
//            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
//
//            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SIVILSTAND_PERIODE)) {
//                shouldHaveSize(1)
//                it[0].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
//                val sivilstandGrunnlag = it.innholdTilObjekt<SivilstandPeriode>()
//                sivilstandGrunnlag[0].sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
//                sivilstandGrunnlag[0].periode.fom shouldBe YearMonth.parse("2022-02")
//                sivilstandGrunnlag[0].periode.til shouldBe null
//            }
//
//            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bmGrunnlag.referanse)) {
//                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
//                it.gjelderReferanse.shouldBe(bmGrunnlag.referanse)
//                innhold.summertMånedsinntektListe.shouldHaveSize(13)
//            }
//            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
//                shouldHaveSize(6)
//                assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
//                    innhold shouldBe behandling.virkningstidspunktbegrunnelseKunINotat
//                    erMedIVedtaksdokumentet shouldBe false
//                    type shouldBe NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
//                }
//            }
//
//            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
//            hentGrunnlagstyper(Grunnlagstype.SJABLON) shouldHaveSize 14
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 4
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 2
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 1
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 5
//            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 1
//        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }
}

private fun OpprettVedtakRequestDto.validerPersongrunnlag() {
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN)) {
        shouldHaveSize(1)
        it.shouldContainPerson(testdataBarn1.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)) {
        shouldHaveSize(4)
        it.shouldContainPerson(testdataHusstandsmedlem1.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER)) {
        shouldHaveSize(1)
        it.shouldContainPerson(testdataBM.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG)) {
        shouldHaveSize(1)
        it.shouldContainPerson(testdataBP.ident)
    }
}

private fun OpprettVedtakRequestDto.validerSluttberegning() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)
    val søknadsbarn2Grunnlag = grunnlagListe.hentPerson(testdataBarn2.ident)

    assertSoftly(hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_FORSKUDD)) {
        shouldHaveSize(10)
        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag!!.referanse) shouldHaveSize 5
    }

    val sluttberegningForskudd =
        hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_FORSKUDD)
            .filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag!!.referanse)
    sluttberegningForskudd shouldHaveSize (5)

    assertSoftly(sluttberegningForskudd[3]) {
        val innhold = innholdTilObjekt<SluttberegningForskudd>()
        innhold.beløp.toBigInteger() shouldBe 1880.toBigInteger()
        innhold.resultatKode shouldBe no.nav.bidrag.domene.enums.beregning.Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
        innhold.aldersgruppe shouldBe AldersgruppeForskudd.ALDER_0_10_ÅR
    }
    val delberegningInntekt =
        hentGrunnlagstyperForReferanser(
            Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
            sluttberegningForskudd[3].grunnlagsreferanseListe,
        )

    delberegningInntekt shouldHaveSize (1)
    val delberegningInnhold = delberegningInntekt[0].innholdTilObjekt<DelberegningSumInntekt>()

    assertSoftly(delberegningInntekt[0]) { delberegning ->
        delberegningInnhold.totalinntekt shouldBe 120000.toBigDecimal()
        delberegningInnhold.skattepliktigInntekt shouldBe 60000.toBigDecimal()
        delberegningInnhold.barnetillegg shouldBe 60000.toBigDecimal()
        delberegning.grunnlagsreferanseListe shouldHaveSize 2
    }

    val delberegningInntektFiltrertPåEgenReferanse =
        grunnlagListe
            .filtrerBasertPåEgenReferanse(referanse = delberegningInntekt[0].grunnlagsreferanseListe[0])
            .first()

    assertSoftly(delberegningInntektFiltrertPåEgenReferanse) {
        it.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
        it.grunnlagsreferanseListe shouldHaveSize 1
    }

    val innhentetAinntekt =
        grunnlagListe
            .filtrerBasertPåEgenReferanse(referanse = delberegningInntektFiltrertPåEgenReferanse.grunnlagsreferanseListe[0])
            .first()
    assertSoftly(innhentetAinntekt) {
        it.type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT
        it.grunnlagsreferanseListe shouldHaveSize 0
        it.gjelderReferanse shouldBe bmGrunnlag.referanse
    }

    val delberegningBarnIHusstand =
        hentGrunnlagstyperForReferanser(
            Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND,
            sluttberegningForskudd[3].grunnlagsreferanseListe,
        )

    assertSoftly(delberegningBarnIHusstand) {
        shouldHaveSize(1)
        assertSoftly(it[0]) { delberegning ->
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().antallBarn shouldBe 3
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().periode.fom shouldBe
                YearMonth.parse(
                    "2023-02",
                )
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().periode.til shouldBe YearMonth.parse("2023-08")
            delberegning.grunnlagsreferanseListe shouldHaveSize 3

            val bosstatusHusstandsmedlem =
                grunnlagListe
                    .filtrerBasertPåEgenReferanse(referanse = delberegning.grunnlagsreferanseListe[0])
                    .first()
            bosstatusHusstandsmedlem.type shouldBe Grunnlagstype.BOSTATUS_PERIODE
            bosstatusHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 1

            val innhentetHusstandsmedlem =
                grunnlagListe
                    .filtrerBasertPåEgenReferanse(referanse = bosstatusHusstandsmedlem.grunnlagsreferanseListe[0])
                    .first()
            innhentetHusstandsmedlem.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
            innhentetHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 0
            innhentetHusstandsmedlem.gjelderReferanse shouldBe bmGrunnlag.referanse
        }
    }
}
